package com.el.sapiospend.domain.funding

import com.el.sapiospend.data.local.ContributionEntity

/**
 * What an event has been promised and what it has actually got.
 *
 * [received] is cash in hand, [pledged] is money someone has committed to but not sent.
 * They are kept apart everywhere: a planner who counts a pledge as funding and books a
 * venue against it is exactly the person this feature exists to protect.
 */
data class FundingSummary(
    val received: Double,
    val pledged: Double,
    val contributorCount: Int
) {
    /** Everything promised, whether or not it has arrived. */
    val total: Double get() = received + pledged

    /** Cash in hand against money already committed to vendors. Negative means a hole. */
    fun cashPosition(paidOut: Double): Double = received - paidOut

    /** What still has to be raised to cover [budget]. Zero once the funding covers it. */
    fun shortfall(budget: Double): Double = (budget - total).coerceAtLeast(0.0)
}

object Funding {

    fun summarize(contributions: List<ContributionEntity>): FundingSummary =
        FundingSummary(
            received = contributions.filter { it.isReceived }.sumOf { it.amount },
            pledged = contributions.filterNot { it.isReceived }.sumOf { it.amount },
            contributorCount = contributions.size
        )

    val EMPTY = FundingSummary(received = 0.0, pledged = 0.0, contributorCount = 0)
}
