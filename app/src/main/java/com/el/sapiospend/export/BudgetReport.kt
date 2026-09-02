package com.el.sapiospend.export

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.ContributionEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.analytics.EventAnalytics
import com.el.sapiospend.domain.analytics.PortfolioAnalytics

/**
 * The data both exporters render. PDF and Excel differ only in presentation, so the
 * numbers are assembled once here — otherwise the two formats drift and a client gets a
 * PDF that disagrees with the spreadsheet attached to the same email.
 */
data class BudgetReport(
    val title: String,
    val generatedAt: Long,
    val sections: List<EventReportSection>,
    /**
     * Set on an all-events report and null on a single-event one. This — not the section
     * count — is what distinguishes the two: a planner with exactly one event still
     * exports an all-events report, and it still needs its summary.
     */
    val portfolio: PortfolioAnalytics?
)

data class EventReportSection(
    val analytics: EventAnalytics,
    val expenses: List<ExpenseEntity>,
    /** Money in, so an exported report can be handed to whoever is funding the event. */
    val contributions: List<ContributionEntity> = emptyList()
)

object ReportBuilder {

    fun forEvent(
        event: EventEntity,
        expenses: List<ExpenseEntity>,
        budgetLines: List<BudgetLineEntity>,
        contributions: List<ContributionEntity> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): BudgetReport {
        val analytics = BudgetAnalytics.forEvent(event, expenses, budgetLines, contributions, now)
        return BudgetReport(
            title = event.name,
            generatedAt = now,
            sections = listOf(
                EventReportSection(
                    analytics = analytics,
                    expenses = expenses.filter { it.eventId == event.id }.sortedByDescending { it.dateCreated },
                    contributions = contributions.filter { it.eventId == event.id }.sortedByDescending { it.dateCreated }
                )
            ),
            portfolio = null
        )
    }

    fun forAllEvents(
        events: List<EventEntity>,
        expenses: List<ExpenseEntity>,
        budgetLines: List<BudgetLineEntity>,
        contributions: List<ContributionEntity> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): BudgetReport {
        val portfolio = BudgetAnalytics.portfolio(events, expenses, budgetLines, contributions, now)
        return BudgetReport(
            title = "All Events",
            generatedAt = now,
            sections = portfolio.events.map { analytics ->
                EventReportSection(
                    analytics = analytics,
                    expenses = expenses.filter { it.eventId == analytics.eventId }.sortedByDescending { it.dateCreated },
                    contributions = contributions.filter { it.eventId == analytics.eventId }.sortedByDescending { it.dateCreated }
                )
            },
            portfolio = portfolio
        )
    }
}
