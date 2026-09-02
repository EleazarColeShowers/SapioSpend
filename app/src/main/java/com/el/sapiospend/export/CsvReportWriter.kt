package com.el.sapiospend.export

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.payment.Payments
import com.el.sapiospend.settings.ActiveCurrency
import com.el.sapiospend.util.formatDate
import java.io.OutputStream

/**
 * Renders a [BudgetReport] as one flat CSV table — a row per expense, with its event on
 * the row.
 *
 * Deliberately not a transcription of the PDF. A CSV exists to be opened in a
 * spreadsheet and pivoted, and a file with summary blocks, blank spacer rows and a
 * category table wedged above the data cannot be: the first thing the user would have to
 * do is delete them. Everything a summary would say is derivable from these columns, so
 * the summary is the PDF's job and the workbook's, and this stays one clean table.
 *
 * Amounts are bare numbers for the same reason they are in the workbook — the currency
 * is stated in its own column rather than glued to every figure.
 */
object CsvReportWriter {

    private val HEADER = listOf(
        "Event",
        "Event type",
        "Date",
        "Item",
        "Category",
        "Vendor",
        "Currency",
        "Amount",
        "Paid",
        "Outstanding",
        "Payment status",
        "Due date",
        "Notes"
    )

    fun write(report: BudgetReport, out: OutputStream) {
        // Written as UTF-8 with a byte-order mark. Without it Excel on Windows reads the
        // file as the system codepage and turns every ₦ and every accented vendor name
        // into mojibake — the single most common complaint about CSV exports.
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(BYTE_ORDER_MARK)

        writer.appendLine(HEADER.joinToString(",") { escape(it) })

        val currency = ActiveCurrency.value.code
        report.sections.forEach { section ->
            val analytics = section.analytics
            section.expenses.forEach { expense ->
                writer.appendLine(
                    listOf(
                        analytics.eventName,
                        analytics.eventType,
                        expense.dateCreated.formatDate(),
                        expense.title,
                        expense.category,
                        expense.vendor,
                        currency,
                        number(expense.amount),
                        number(expense.amountPaid),
                        number(expense.outstanding),
                        statusOf(expense, report.generatedAt),
                        expense.dueDate?.formatDate().orEmpty(),
                        expense.notes
                    ).joinToString(",") { escape(it) }
                )
            }
        }

        writer.flush()
    }

    /** Overdue is reported in place of the plain status: it is the one that needs acting on. */
    private fun statusOf(expense: ExpenseEntity, now: Long): String =
        if (Payments.isOverdue(expense, now)) "Overdue" else Payments.statusOf(expense).label

    /**
     * A plain decimal, never grouped. "1,250,000" in a CSV cell is two columns.
     */
    private fun number(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) "%.0f".format(value)
        else "%.2f".format(value)

    /**
     * RFC 4180 quoting, plus a guard against formula injection.
     *
     * A vendor saved as "=cmd|' /c calc'!A1" is a live formula the moment the file is
     * opened in Excel, so any cell starting with one of the trigger characters is
     * prefixed with an apostrophe — the spreadsheet then shows the text and runs nothing.
     * The user's own data is not a threat here, but an exported report gets forwarded to
     * clients, and a file that executes on open is not something to hand anybody.
     */
    private fun escape(value: String): String {
        val guarded = if (value.isNotEmpty() && value.first() in FORMULA_TRIGGERS) "'$value" else value
        return if (guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + guarded.replace("\"", "\"\"") + "\""
        } else {
            guarded
        }
    }

    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t')

    /** Written as an escape rather than a literal, which lint reads as a corrupt file. */
    private const val BYTE_ORDER_MARK = "\uFEFF"
}
