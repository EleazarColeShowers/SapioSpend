package com.el.sapiospend

import com.el.sapiospend.data.local.ContributionEntity
import com.el.sapiospend.domain.funding.Funding
import org.junit.Assert.assertEquals
import org.junit.Test

class FundingTest {

    private fun contribution(amount: Double, received: Boolean, id: String = "c1") =
        ContributionEntity(
            id = id,
            eventId = "e1",
            source = "Client",
            amount = amount,
            receivedAt = if (received) 1L else null
        )

    @Test
    fun `pledges are counted apart from money that has actually arrived`() {
        val summary = Funding.summarize(
            listOf(
                contribution(500_000.0, received = true, id = "a"),
                contribution(300_000.0, received = false, id = "b")
            )
        )

        assertEquals(500_000.0, summary.received, 0.01)
        assertEquals(300_000.0, summary.pledged, 0.01)
        assertEquals(800_000.0, summary.total, 0.01)
        assertEquals(2, summary.contributorCount)
    }

    @Test
    fun `cash position is what came in less what has gone out`() {
        val summary = Funding.summarize(listOf(contribution(500_000.0, received = true)))

        assertEquals(200_000.0, summary.cashPosition(paidOut = 300_000.0), 0.01)
        // Paying more than has arrived is the case worth surfacing: somebody is out of
        // pocket, whatever the budget says.
        assertEquals(-100_000.0, summary.cashPosition(paidOut = 600_000.0), 0.01)
    }

    @Test
    fun `a pledge counts towards covering the budget, and cover never goes negative`() {
        val summary = Funding.summarize(
            listOf(
                contribution(400_000.0, received = true, id = "a"),
                contribution(400_000.0, received = false, id = "b")
            )
        )

        assertEquals(200_000.0, summary.shortfall(budget = 1_000_000.0), 0.01)
        assertEquals(0.0, summary.shortfall(budget = 500_000.0), 0.01)
    }

    @Test
    fun `an event with no funding recorded reports zeroes`() {
        val summary = Funding.summarize(emptyList())

        assertEquals(0.0, summary.total, 0.01)
        assertEquals(1_000_000.0, summary.shortfall(budget = 1_000_000.0), 0.01)
    }
}
