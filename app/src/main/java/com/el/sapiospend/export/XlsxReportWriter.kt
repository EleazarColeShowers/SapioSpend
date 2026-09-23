package com.el.sapiospend.export

import com.el.sapiospend.domain.payment.Payments
import com.el.sapiospend.settings.ActiveCurrency
import com.el.sapiospend.util.formatDate
import com.el.sapiospend.settings.ActiveRates
import com.el.sapiospend.util.convertedTo
import com.el.sapiospend.util.inDisplayCurrency
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
            // This sheet has one currency in its header and a row per budget, and those
            // budgets may each be kept in a different one — so every figure here is
            // converted into the sheet's currency. The budget's own sheet, further on,
            // reports it untouched in the currency it is actually kept in.
            fun shown(value: Double) = own(value.convertedTo(ActiveCurrency.value, a.currency, ActiveRates.value))
            rows += row(
                text(a.eventName),
                text(a.eventType),
                shown(a.budget),
                shown(a.totalPlanned),
                shown(a.totalSpent),
                shown(a.totalPaid),
                shown(a.outstanding),
                shown(a.funding.received),
                shown(a.remaining),
                number(percentUsed(a.totalSpent, a.budget)),
                // Blank rather than zero when nobody was counted: a 0 in a guest column
                // averages into any total the planner builds on top of this sheet.
                a.guestCount?.let { number(it.toDouble()) } ?: XlsxWriter.Cell.Empty,
                a.costPerGuest?.let { shown(it) } ?: XlsxWriter.Cell.Empty,
                text(if (a.isOverBudget) "Over budget" else "On track")
            )
        }

        report.portfolio?.let { portfolio ->
            rows += emptyRow()
            rows += row(
                text("TOTAL"),
                text(""),
                money(portfolio.totalBudget),
                XlsxWriter.Cell.Empty,
                money(portfolio.totalSpent),
                money(portfolio.totalRemaining)
            )
            rows += emptyRow()
            rows += row(text("Spend by category (all events)"))
            rows += row(text("Category"), text("Planned"), text("Actual"), text("Variance"))
            portfolio.topCategories.forEach { category ->
                rows += row(
                    text(category.category),
                    money(category.planned),
                    money(category.actual),
                    money(category.variance)
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
        // This budget's own currency, and its figures exactly as recorded — the one
        // sheet in the workbook where nothing has been through an exchange rate.
        rows += row(text("Currency"), text(a.currency.code))
        rows += row(text("Budget"), own(a.budget))
        rows += row(text("Planned"), own(a.totalPlanned))
        rows += row(text("Spent"), own(a.totalSpent))
        rows += row(text("Remaining"), own(a.remaining))
        rows += row(text("Paid so far"), own(a.totalPaid))
        rows += row(text("Still owed"), own(a.outstanding))
        if (a.payments.overdueCount > 0) {
            rows += row(text("Overdue"), own(a.payments.overdueAmount))
        }
        a.guestCount?.takeIf { it > 0 }?.let { guests ->
            rows += row(text("Guests"), number(guests.toDouble()))
            a.costPerGuest?.let { rows += row(text("Cost per guest"), own(it)) }
            a.budgetPerGuest?.let { rows += row(text("Budget per guest"), own(it)) }
        }
        if (a.funding.total > 0) {
            rows += row(text("Funding received"), own(a.funding.received))
            rows += row(text("Funding pledged"), own(a.funding.pledged))
            rows += row(text("Cash position"), own(a.cashPosition))
        }
        rows += row(text("Daily burn rate"), own(a.dailyBurnRate))
        rows += row(text("Days tracked"), number(a.daysTracked.toDouble()))
        formatPeriod(a.periodStart, a.periodEnd)?.let { rows += row(text("Period"), text(it)) }
        a.daysRemaining?.let { rows += row(text("Days remaining"), number(it.toDouble())) }
        a.safeDailySpend?.let { rows += row(text("Safe daily spend"), own(maxOf(it, 0.0))) }
        a.projectedTotalSpend?.let { rows += row(text("Projected at this pace"), own(it)) }
        rows += emptyRow()

        if (a.categories.isNotEmpty()) {
            rows += row(text("Category"), text("Planned"), text("Actual"), text("Variance"), text("Note"))
            a.categories.forEach { category ->
                rows += row(
                    text(category.category),
                    own(category.planned),
                    own(category.actual),
                    own(category.variance),
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
                own(expense.amount),
                own(expense.amountPaid),
                own(expense.outstanding),
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
                    own(contribution.amount),
                    text(if (contribution.isReceived) "Received" else "Promised"),
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
    /**
     * A money cell for a figure that spans budgets, converted into the currency the
     * summary sheet says it is in.
     *
     * Pooled figures are in the base currency; the sheet is headed with the display one.
     * A figure belonging to a single budget goes through [own] instead, and [number] is
     * for the cells that are not money at all — guest counts, day counts, percentages —
     * which must not be multiplied by an exchange rate.
     */
    private fun money(value: Double) = XlsxWriter.Cell.Number(value.inDisplayCurrency())

    /**
     * A money cell in the currency its own budget is kept in, written as recorded.
     *
     * For an event's own sheet, which is headed with that currency. No conversion, so
     * the workbook and the app cannot report a budget differently.
     */
    private fun own(value: Double) = XlsxWriter.Cell.Number(value)

    /** A cell that holds a count or a percentage, not an amount of money. */
    private fun number(value: Double) = XlsxWriter.Cell.Number(value)
}
