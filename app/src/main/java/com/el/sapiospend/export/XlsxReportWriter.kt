package com.el.sapiospend.export

import com.el.sapiospend.domain.payment.Payments
import com.el.sapiospend.settings.ActiveCurrency
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.util.formatPeriod
import java.io.OutputStream

/**
 * Renders a [BudgetReport] as a workbook: one summary sheet, then a sheet per event.
 *
 * Amounts go in as numbers, never as pre-formatted "₦1,000" strings — the point of the
 * Excel export over a PDF is that the planner can sum and pivot the figures themselves.
 */
object XlsxReportWriter {

    fun write(report: BudgetReport, out: OutputStream) {
        val sheets = mutableListOf<XlsxWriter.Sheet>()

        if (report.portfolio != null) {
            sheets += summarySheet(report)
        }
        report.sections.forEach { section -> sheets += eventSheet(section) }

        // A report with no events would otherwise produce an empty, unopenable workbook.
        if (sheets.isEmpty()) {
            sheets += XlsxWriter.Sheet("Summary", listOf(row(text("No events to export"))))
        }

        XlsxWriter.write(sheets, out)
    }

    private fun summarySheet(report: BudgetReport): XlsxWriter.Sheet {
        val rows = mutableListOf<List<XlsxWriter.Cell>>()
        rows += row(text("SapioSpend — ${report.title}"))
        rows += row(text("Generated"), text(report.generatedAt.formatDate()))
        // The cells hold bare numbers so the planner can sum them, which leaves the
        // unit unstated — say it once here rather than on every amount.
        rows += row(text("Currency"), text(ActiveCurrency.value.code))
        rows += emptyRow()
        rows += row(
            text("Event"), text("Type"), text("Budget"), text("Planned"), text("Spent"),
            text("Paid"), text("Outstanding"), text("Funding received"), text("Remaining"),
            text("% Used"), text("Guests"), text("Cost per guest"), text("Status")
        )

        report.sections.forEach { section ->
            val a = section.analytics
            rows += row(
                text(a.eventName),
                text(a.eventType),
                number(a.budget),
                number(a.totalPlanned),
                number(a.totalSpent),
                number(a.totalPaid),
                number(a.outstanding),
                number(a.funding.received),
                number(a.remaining),
                number(percentUsed(a.totalSpent, a.budget)),
                // Blank rather than zero when nobody was counted: a 0 in a guest column
                // averages into any total the planner builds on top of this sheet.
                a.guestCount?.let { number(it.toDouble()) } ?: XlsxWriter.Cell.Empty,
                a.costPerGuest?.let { number(it) } ?: XlsxWriter.Cell.Empty,
                text(if (a.isOverBudget) "Over budget" else "On track")
            )
        }

        report.portfolio?.let { portfolio ->
            rows += emptyRow()
            rows += row(
                text("TOTAL"),
                text(""),
                number(portfolio.totalBudget),
                XlsxWriter.Cell.Empty,
                number(portfolio.totalSpent),
                number(portfolio.totalRemaining)
            )
            rows += emptyRow()
            rows += row(text("Spend by category (all events)"))
            rows += row(text("Category"), text("Planned"), text("Actual"), text("Variance"))
            portfolio.topCategories.forEach { category ->
                rows += row(
                    text(category.category),
                    number(category.planned),
                    number(category.actual),
                    number(category.variance)
                )
            }
        }

        return XlsxWriter.Sheet("Summary", rows)
    }

    private fun eventSheet(section: EventReportSection): XlsxWriter.Sheet {
        val a = section.analytics
        val rows = mutableListOf<List<XlsxWriter.Cell>>()

        rows += row(text(a.eventName))
        rows += row(text("Type"), text(a.eventType))
        rows += row(text("Currency"), text(ActiveCurrency.value.code))
        rows += row(text("Budget"), number(a.budget))
        rows += row(text("Planned"), number(a.totalPlanned))
        rows += row(text("Spent"), number(a.totalSpent))
        rows += row(text("Remaining"), number(a.remaining))
        rows += row(text("Paid so far"), number(a.totalPaid))
        rows += row(text("Still owed"), number(a.outstanding))
        if (a.payments.overdueCount > 0) {
            rows += row(text("Overdue"), number(a.payments.overdueAmount))
        }
        a.guestCount?.takeIf { it > 0 }?.let { guests ->
            rows += row(text("Guests"), number(guests.toDouble()))
            a.costPerGuest?.let { rows += row(text("Cost per guest"), number(it)) }
            a.budgetPerGuest?.let { rows += row(text("Budget per guest"), number(it)) }
        }
        if (a.funding.total > 0) {
            rows += row(text("Funding received"), number(a.funding.received))
            rows += row(text("Funding pledged"), number(a.funding.pledged))
            rows += row(text("Cash position"), number(a.cashPosition))
        }
        rows += row(text("Daily burn rate"), number(a.dailyBurnRate))
        rows += row(text("Days tracked"), number(a.daysTracked.toDouble()))
        formatPeriod(a.periodStart, a.periodEnd)?.let { rows += row(text("Period"), text(it)) }
        a.daysRemaining?.let { rows += row(text("Days remaining"), number(it.toDouble())) }
        a.safeDailySpend?.let { rows += row(text("Safe daily spend"), number(maxOf(it, 0.0))) }
        a.projectedTotalSpend?.let { rows += row(text("Projected at this pace"), number(it)) }
        rows += emptyRow()

        if (a.categories.isNotEmpty()) {
            rows += row(text("Category"), text("Planned"), text("Actual"), text("Variance"), text("Note"))
            a.categories.forEach { category ->
                rows += row(
                    text(category.category),
                    number(category.planned),
                    number(category.actual),
                    number(category.variance),
                    text(
                        when {
                            category.isUnplanned -> "Not in plan"
                            category.isOverPlan -> "Over plan"
                            else -> ""
                        }
                    )
                )
            }
            rows += emptyRow()
        }

        rows += row(
            text("Date"), text("Expense"), text("Category"), text("Vendor"), text("Amount"),
            text("Paid"), text("Outstanding"), text("Status"), text("Due date"), text("Notes")
        )
        section.expenses.forEach { expense ->
            rows += row(
                text(expense.dateCreated.formatDate()),
                text(expense.title),
                text(expense.category),
                text(expense.vendor),
                number(expense.amount),
                number(expense.amountPaid),
                number(expense.outstanding),
                text(Payments.statusOf(expense).label),
                expense.dueDate?.let { text(it.formatDate()) } ?: XlsxWriter.Cell.Empty,
                text(expense.notes)
            )
        }

        if (section.contributions.isNotEmpty()) {
            rows += emptyRow()
            rows += row(text("Funding"))
            rows += row(text("Date"), text("From"), text("Amount"), text("Status"), text("Notes"))
            section.contributions.forEach { contribution ->
                rows += row(
                    text((contribution.receivedAt ?: contribution.dateCreated).formatDate()),
                    text(contribution.source),
                    number(contribution.amount),
                    text(if (contribution.isReceived) "Received" else "Pledged"),
                    text(contribution.notes)
                )
            }
        }

        return XlsxWriter.Sheet(a.eventName, rows)
    }

    /**
     * Computed from the underlying doubles and rounded to two places. Widening the
     * Float on EventAnalytics would put "35.71428680419922" in a cell a client reads.
     */
    internal fun percentUsed(spent: Double, budget: Double): Double =
        if (budget > 0) Math.round(spent / budget * 10_000.0) / 100.0 else 0.0

    private fun row(vararg cells: XlsxWriter.Cell) = cells.toList()
    private fun emptyRow() = emptyList<XlsxWriter.Cell>()
    private fun text(value: String) = XlsxWriter.Cell.Text(value)
    private fun number(value: Double) = XlsxWriter.Cell.Number(value)
}
