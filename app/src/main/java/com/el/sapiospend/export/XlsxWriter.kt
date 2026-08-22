package com.el.sapiospend.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A minimal .xlsx writer.
 *
 * An xlsx file is a zip of XML parts, and the subset Excel needs to open a plain data
 * sheet is small enough to emit by hand. Doing it this way avoids Apache POI, which is
 * a large JVM library that pulls in APIs Android does not have — the usual workaround
 * is exporting CSV and calling it "Excel", which loses multiple sheets and number
 * formatting. This is a genuine workbook and it costs no dependencies.
 *
 * Strings are written inline rather than through a shared-strings table: slightly larger
 * files, considerably less machinery, and no difference to the reader.
 *
 * Pure JVM by design — it takes an OutputStream and nothing else, so it is unit-testable
 * without an emulator.
 */
object XlsxWriter {

    sealed interface Cell {
        data class Text(val value: String) : Cell
        data class Number(val value: Double) : Cell
        data object Empty : Cell
    }

    data class Sheet(val name: String, val rows: List<List<Cell>>)

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        val named = deduplicateNames(sheets)

        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(named.size))
            zip.put("_rels/.rels", rootRels())
            zip.put("xl/workbook.xml", workbook(named))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels(named.size))
            named.forEachIndexed { index, sheet ->
                zip.put("xl/worksheets/sheet${index + 1}.xml", worksheet(sheet))
            }
        }
    }

    private fun ZipOutputStream.put(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        repeat(sheetCount) { index ->
            append("""<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${escape(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        repeat(sheetCount) { index ->
            append("""<Relationship Id="rId${index + 1}" """)
            append("""Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """)
            append("""Target="worksheets/sheet${index + 1}.xml"/>""")
        }
        append("</Relationships>")
    }

    private fun worksheet(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            append("""<row r="$rowNumber">""")
            row.forEachIndexed { colIndex, cell ->
                val ref = "${columnName(colIndex)}$rowNumber"
                when (cell) {
                    is Cell.Text -> append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
                    is Cell.Number -> append("""<c r="$ref"><v>${formatNumber(cell.value)}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /**
     * Excel refuses to open a workbook containing two sheets with the same name, and it
     * compares them case-insensitively. Two events called "Wedding" is an ordinary thing
     * for a planner to have, so clashes are renamed rather than left to corrupt the file.
     */
    internal fun deduplicateNames(sheets: List<Sheet>): List<Sheet> {
        val taken = mutableSetOf<String>()
        return sheets.mapIndexed { index, sheet ->
            val base = safeSheetName(sheet.name, index)
            var candidate = base
            var attempt = 2
            while (!taken.add(candidate.lowercase())) {
                val suffix = " ($attempt)"
                candidate = base.take(31 - suffix.length) + suffix
                attempt++
            }
            sheet.copy(name = candidate)
        }
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    internal fun columnName(index: Int): String {
        require(index >= 0) { "Column index must not be negative" }
        var remaining = index
        val builder = StringBuilder()
        while (remaining >= 0) {
            builder.append('A' + (remaining % 26))
            remaining = remaining / 26 - 1
        }
        return builder.reverse().toString()
    }

    /**
     * Excel rejects the whole file on a malformed cell value, so NaN and infinity — which
     * a division by zero upstream could produce — are written as blank zeros rather than
     * corrupting the workbook.
     */
    // toPlainString avoids scientific notation: a ₦20,000,000 budget must land in the
    // cell as 20000000, not 2.0E7.
    private fun formatNumber(value: Double): String =
        if (value.isNaN() || value.isInfinite()) "0" else java.math.BigDecimal(value.toString()).toPlainString()

    // Sheet names are limited to 31 characters and cannot contain : \ / ? * [ ]
    internal fun safeSheetName(name: String, index: Int): String {
        val cleaned = name.replace(Regex("""[:\\/?*\[\]]"""), " ").trim().take(31)
        return cleaned.ifBlank { "Sheet${index + 1}" }
    }

    private fun escape(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                // Control characters are illegal in XML 1.0 and would make the file
                // unopenable; tab, newline and carriage return are the legal exceptions.
                else -> if (char < ' ' && char != '\t' && char != '\n' && char != '\r') append(' ') else append(char)
            }
        }
    }
}
