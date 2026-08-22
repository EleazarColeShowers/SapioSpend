package com.el.sapiospend

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.export.ReportBuilder
import com.el.sapiospend.export.XlsxReportWriter
import com.el.sapiospend.export.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The workbook is assembled by hand, so these tests check the things Excel would reject
 * the file over: missing parts, malformed XML, duplicate sheet names.
 */
class XlsxWriterTest {

    private fun readParts(bytes: ByteArray): Map<String, String> {
        val parts = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return parts
    }

    private fun write(sheets: List<XlsxWriter.Sheet>): Map<String, String> {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)
        return readParts(out.toByteArray())
    }

    private fun assertWellFormed(xml: String) {
        // Throws if the document does not parse, which is exactly what Excel would do.
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `workbook contains every part excel requires`() {
        val parts = write(listOf(XlsxWriter.Sheet("Data", listOf(listOf(XlsxWriter.Cell.Text("hi"))))))

        assertTrue(parts.containsKey("[Content_Types].xml"))
        assertTrue(parts.containsKey("_rels/.rels"))
        assertTrue(parts.containsKey("xl/workbook.xml"))
        assertTrue(parts.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
    }

    @Test
    fun `every part is well-formed xml`() {
        val parts = write(
            listOf(
                XlsxWriter.Sheet("One", listOf(listOf(XlsxWriter.Cell.Text("a"), XlsxWriter.Cell.Number(1.0)))),
                XlsxWriter.Sheet("Two", listOf(listOf(XlsxWriter.Cell.Text("b"))))
            )
        )
        parts.values.forEach { assertWellFormed(it) }
    }

    @Test
    fun `each sheet gets its own part and relationship`() {
        val parts = write(
            listOf(
                XlsxWriter.Sheet("One", listOf(listOf(XlsxWriter.Cell.Text("a")))),
                XlsxWriter.Sheet("Two", listOf(listOf(XlsxWriter.Cell.Text("b")))),
                XlsxWriter.Sheet("Three", listOf(listOf(XlsxWriter.Cell.Text("c"))))
            )
        )

        assertTrue(parts.containsKey("xl/worksheets/sheet3.xml"))
        assertTrue(parts.getValue("xl/_rels/workbook.xml.rels").contains("worksheets/sheet3.xml"))
        assertTrue(parts.getValue("[Content_Types].xml").contains("/xl/worksheets/sheet3.xml"))
    }

    @Test
    fun `duplicate sheet names are made unique`() {
        val sheets = listOf(
            XlsxWriter.Sheet("Wedding", emptyList()),
            XlsxWriter.Sheet("Wedding", emptyList()),
            XlsxWriter.Sheet("wedding", emptyList())
        )
        val names = XlsxWriter.deduplicateNames(sheets).map { it.name }

        // Excel compares sheet names case-insensitively, so all three must differ.
        assertEquals(3, names.map { it.lowercase() }.toSet().size)
        assertEquals("Wedding", names.first())
    }

    @Test
    fun `sheet names are stripped of illegal characters and truncated`() {
        val name = XlsxWriter.safeSheetName("Tolu & Ada / Reception [Lagos] : the very long one", 0)

        assertTrue(name.length <= 31)
        assertFalse(name.contains("/"))
        assertFalse(name.contains("["))
        assertFalse(name.contains(":"))
    }

    @Test
    fun `a blank sheet name falls back to a positional name`() {
        assertEquals("Sheet3", XlsxWriter.safeSheetName("   ", 2))
    }

    @Test
    fun `column names roll over past Z`() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AB", XlsxWriter.columnName(27))
        assertEquals("BA", XlsxWriter.columnName(52))
    }

    @Test
    fun `xml special characters in data do not break the sheet`() {
        val parts = write(
            listOf(
                XlsxWriter.Sheet(
                    "Data",
                    listOf(listOf(XlsxWriter.Cell.Text("Tolu & Ada <\"quoted\"> 'apostrophe'")))
                )
            )
        )

        assertWellFormed(parts.getValue("xl/worksheets/sheet1.xml"))
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("&amp;"))
    }

    @Test
    fun `large amounts are written without scientific notation`() {
        val parts = write(
            listOf(XlsxWriter.Sheet("Data", listOf(listOf(XlsxWriter.Cell.Number(20_000_000.0)))))
        )
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")

        assertTrue(sheet.contains("20000000"))
        assertFalse("Excel must not receive 2.0E7", sheet.contains("E7"))
    }

    @Test
    fun `NaN is written as zero rather than corrupting the sheet`() {
        val parts = write(
            listOf(XlsxWriter.Sheet("Data", listOf(listOf(XlsxWriter.Cell.Number(Double.NaN)))))
        )

        assertWellFormed(parts.getValue("xl/worksheets/sheet1.xml"))
        assertFalse(parts.getValue("xl/worksheets/sheet1.xml").contains("NaN"))
    }

    @Test
    fun `a full report exports to a readable workbook`() {
        val event = EventEntity(id = "e1", name = "Tolu & Ada", budget = 7_000_000.0, eventType = "Wedding")
        val report = ReportBuilder.forAllEvents(
            events = listOf(event),
            expenses = listOf(
                ExpenseEntity(eventId = "e1", title = "Deposit", category = "Catering & Drinks", amount = 1_500_000.0)
            ),
            budgetLines = listOf(
                BudgetLineEntity(eventId = "e1", category = "Catering & Drinks", plannedAmount = 2_100_000.0)
            )
        )

        val out = ByteArrayOutputStream()
        XlsxReportWriter.write(report, out)
        val parts = readParts(out.toByteArray())

        parts.values.forEach { assertWellFormed(it) }
        // Summary sheet plus one sheet for the event.
        assertTrue(parts.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(parts.getValue("xl/worksheets/sheet2.xml").contains("Catering &amp; Drinks"))
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("7000000"))
    }

    @Test
    fun `percent used lands in the cell rounded, not at full float precision`() {
        // 2.5M of 7M is 35.714285714..., which must not reach a client's spreadsheet raw.
        assertEquals(35.71, XlsxReportWriter.percentUsed(2_500_000.0, 7_000_000.0), 0.0001)
        assertEquals(0.0, XlsxReportWriter.percentUsed(1_000.0, 0.0), 0.0001)
        assertEquals(100.0, XlsxReportWriter.percentUsed(500.0, 500.0), 0.0001)
    }

    @Test
    fun `a report with no events still produces an openable workbook`() {
        val report = ReportBuilder.forAllEvents(emptyList(), emptyList(), emptyList())

        val out = ByteArrayOutputStream()
        XlsxReportWriter.write(report, out)
        val parts = readParts(out.toByteArray())

        assertTrue(parts.containsKey("xl/worksheets/sheet1.xml"))
        parts.values.forEach { assertWellFormed(it) }
    }
}
