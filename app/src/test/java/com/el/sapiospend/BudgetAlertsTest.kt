package com.el.sapiospend

import com.el.sapiospend.domain.analytics.EventAnalytics
import com.el.sapiospend.domain.notify.BudgetAlerts
import com.el.sapiospend.domain.notify.BudgetThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertsTest {

    private fun analytics(
        id: String = "e1",
        budget: Double = 1_000_000.0,
        spent: Double
    ) = EventAnalytics(
        eventId = id,
        eventName = "Wedding",
        eventType = "Wedding",
        budget = budget,
        totalPlanned = budget,
        totalSpent = spent,
        expenseCount = 1,
        categories = emptyList(),
        daysTracked = 1,
        dailyBurnRate = spent
    )

    @Test
    fun `no alert below the warning threshold`() {
        val result = BudgetAlerts.evaluate(listOf(analytics(spent = 700_000.0)), emptySet())

        assertTrue(result.toPost.isEmpty())
        assertTrue(result.crossed.isEmpty())
    }

    @Test
    fun `crossing 80 percent warns once`() {
        val events = listOf(analytics(spent = 820_000.0))

        val first = BudgetAlerts.evaluate(events, emptySet())
        assertEquals(1, first.toPost.size)
        assertEquals(BudgetThreshold.WARNING, first.toPost.single().threshold)

        // The same numbers evaluated again — every expense added, every app launch —
        // must stay quiet.
        val second = BudgetAlerts.evaluate(events, first.crossed)
        assertTrue(second.toPost.isEmpty())
    }

    @Test
    fun `a jump past both thresholds reports only the level actually reached`() {
        val result = BudgetAlerts.evaluate(listOf(analytics(spent = 1_400_000.0)), emptySet())

        assertEquals(1, result.toPost.size)
        assertEquals(BudgetThreshold.EXCEEDED, result.toPost.single().threshold)
        // Both are recorded, so the warning cannot arrive late on the next expense.
        assertEquals(2, result.crossed.size)
    }

    @Test
    fun `going over after a warning alerts again at the higher level`() {
        val warned = BudgetAlerts.evaluate(listOf(analytics(spent = 850_000.0)), emptySet())

        val exceeded = BudgetAlerts.evaluate(listOf(analytics(spent = 1_100_000.0)), warned.crossed)

        assertEquals(BudgetThreshold.EXCEEDED, exceeded.toPost.single().threshold)
    }

    @Test
    fun `correcting the spend clears the crossing so it can alert again`() {
        val over = BudgetAlerts.evaluate(listOf(analytics(spent = 1_100_000.0)), emptySet())

        // The user deletes the expense that blew the budget.
        val corrected = BudgetAlerts.evaluate(listOf(analytics(spent = 400_000.0)), over.crossed)
        assertTrue(corrected.toPost.isEmpty())
        assertTrue(corrected.crossed.isEmpty())

        // And spends it again months later, which is news a second time.
        val again = BudgetAlerts.evaluate(listOf(analytics(spent = 1_100_000.0)), corrected.crossed)
        assertEquals(BudgetThreshold.EXCEEDED, again.toPost.single().threshold)
    }

    @Test
    fun `a deleted event drops out of the stored state`() {
        val both = BudgetAlerts.evaluate(
            listOf(analytics(id = "e1", spent = 1_100_000.0), analytics(id = "e2", spent = 900_000.0)),
            emptySet()
        )
        assertEquals(3, both.crossed.size)

        val remaining = BudgetAlerts.evaluate(listOf(analytics(id = "e2", spent = 900_000.0)), both.crossed)

        assertTrue(remaining.crossed.all { it.startsWith("e2@") })
    }

    @Test
    fun `a zero budget never alerts`() {
        val result = BudgetAlerts.evaluate(listOf(analytics(budget = 0.0, spent = 50_000.0)), emptySet())

        assertTrue(result.toPost.isEmpty())
        assertTrue(result.crossed.isEmpty())
    }

    @Test
    fun `the alert carries the figures the notification needs`() {
        val alert = BudgetAlerts
            .evaluate(listOf(analytics(spent = 1_250_000.0)), emptySet())
            .toPost
            .single()

        assertEquals("Wedding", alert.eventName)
        assertEquals(125, alert.percentUsed)
        assertEquals(-250_000.0, alert.remaining, 0.01)
    }
}
