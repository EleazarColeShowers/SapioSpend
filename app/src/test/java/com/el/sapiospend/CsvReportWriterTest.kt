package com.el.sapiospend

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.export.CsvReportWriter
import com.el.sapiospend.export.ReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CsvReportWriterTest {

    private val now = 1_700_000_000_000L

    private fun csvOf(expenses: List<ExpenseEntity>): List<String> {
        val report = ReportBuilder.forEvent(
            EventEntity(id = "e1", name = "Tolu & Ada", budget = 1_000_000.0, eventType = "Wedding"),
            expenses,
            emptyList<BudgetLineEntity>(),
            now = now
        )
        val out = ByteArrayOutputStream()
        CsvReportWriter.write(report, out)
        return out.toString("UTF-8").lines().filter { it.isNotBlank() }
    }

    private fun expense(
        title: String = "Catering",
        vendor: String = "Chidi",
        amount: Double = 500_000.0,
        paid: Double = 500_000.0,
        notes: String = ""
    ) = ExpenseEntity(
        eventId = "e1",
        title = title,
        category = "Food",
        vendor = vendor,
        amount = amount,
        amountPaid = paid,
        notes = notes,
        dateCreated = now
    )

    @Test
    fun `the file opens with a byte order mark so Excel reads it as UTF-8`() {
        val out = ByteArrayOutputStream()
        CsvReportWriter.write(
            ReportBuilder.forEvent(
                EventEntity(id = "e1", name = "E", budget = 1.0),
                emptyList(),
                emptyList(),
                now = now
            ),
            out
        )

        assertEquals('\uFEFF', out.toString("UTF-8").first())
    }

    @Test
    fun `one header row, then one row per expense`() {
        val lines = csvOf(listOf(expense(), expense(title = "Cake")))

        assertEquals(3, lines.size)
        assertTrue(lines.first().startsWith("\uFEFFEvent,Event type,Date,Item,Category,Vendor,"))
    }

    @Test
    fun `payment columns carry the committed, paid and outstanding split`() {
        val row = csvOf(listOf(expense(amount = 500_000.0, paid = 200_000.0)))[1].split(",")

        assertTrue(row.contains("500000"))
        assertTrue(row.contains("200000"))
        assertTrue("the balance is stated rather than left to be worked out", row.contains("300000"))
        assertTrue(row.contains("Deposit paid"))
    }

    @Test
    fun `a comma or a quote in a field is escaped rather than shifting every column`() {
        val row = csvOf(listOf(expense(title = "Cake, large", notes = "said \"final price\"")))[1]

        assertTrue(row.contains("\"Cake, large\""))
        assertTrue(row.contains("\"said \"\"final price\"\"\""))
    }

    @Test
    fun `a field that would be read as a formula is neutralised`() {
        val row = csvOf(listOf(expense(vendor = "=HYPERLINK(\"http://x\")")))[1]

        assertTrue("a leading = must not survive into the cell", row.contains("'=HYPERLINK"))
    }

    @Test
    fun `amounts are written plainly, with no grouping to split a cell in two`() {
        val row = csvOf(listOf(expense(amount = 1_250_000.0, paid = 1_250_000.0)))[1]

        assertTrue(row.contains("1250000"))
        assertTrue("no thousands separators", !row.contains("1,250,000"))
    }
}
