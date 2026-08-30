package com.el.sapiospend

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.analytics.PortfolioAnalytics
import com.el.sapiospend.domain.notify.CheckInCadence
import com.el.sapiospend.domain.notify.DailyDigest
import com.el.sapiospend.domain.notify.NotificationPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DailyDigestTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    /** A budget period ending [endsInDays] whole days after today. */
    private fun event(
        id: String = "e1",
        budget: Double = 1_000_000.0,
        endsInDays: Int? = 3
    ) = EventEntity(
        id = id,
        name = "Wedding",
        budget = budget,
        dateCreated = now - day,
        startDate = now - day,
        endDate = endsInDays?.let { now + it * day }
    )

    private fun portfolio(
        events: List<EventEntity>,
        expenses: List<ExpenseEntity> = emptyList()
    ): PortfolioAnalytics = BudgetAnalytics.portfolio(events, expenses, emptyList<BudgetLineEntity>(), now)

    private fun expense(eventId: String, amount: Double) =
        ExpenseEntity(eventId = eventId, title = "Catering", category = "Food", amount = amount)

    @Test
    fun `a reminder fires at the chosen lead time`() {
        val digest = DailyDigest.build(
            NotificationPrefs(reminderLeadDays = 3, checkIn = CheckInCadence.OFF),
            portfolio(listOf(event(endsInDays = 3))),
            emptySet(),
            now
        )

        assertEquals(1, digest.reminders.size)
        assertEquals(3, digest.reminders.single().daysRemaining)
    }

    @Test
    fun `no reminder on a day that is not the lead day`() {
        val digest = DailyDigest.build(
            NotificationPrefs(reminderLeadDays = 3),
            portfolio(listOf(event(endsInDays = 5))),
            emptySet(),
            now
        )

        assertTrue(digest.reminders.isEmpty())
    }

    @Test
    fun `the closing day always reminds, whatever the lead time`() {
        val digest = DailyDigest.build(
            NotificationPrefs(reminderLeadDays = 7),
            portfolio(listOf(event(endsInDays = 0))),
            emptySet(),
            now
        )

        assertEquals(0, digest.reminders.single().daysRemaining)
    }

    @Test
    fun `an open-ended event is never reminded about`() {
        val digest = DailyDigest.build(
            NotificationPrefs(reminderLeadDays = 0),
            portfolio(listOf(event(endsInDays = null))),
            emptySet(),
            now
        )

        assertTrue(digest.reminders.isEmpty())
    }

    @Test
    fun `reminders stop once the period is over`() {
        val ended = event().copy(startDate = now - 10 * day, endDate = now - 5 * day)

        val digest = DailyDigest.build(
            NotificationPrefs(reminderLeadDays = 0),
            portfolio(listOf(ended)),
            emptySet(),
            now
        )

        assertTrue(digest.reminders.isEmpty())
    }

    @Test
    fun `reminders are suppressed when the user switched them off`() {
        val digest = DailyDigest.build(
            NotificationPrefs(eventReminders = false, reminderLeadDays = 0),
            portfolio(listOf(event(endsInDays = 0))),
            emptySet(),
            now
        )

        assertTrue(digest.reminders.isEmpty())
    }

    @Test
    fun `a daily check-in summarises every live budget`() {
        val digest = DailyDigest.build(
            NotificationPrefs(checkIn = CheckInCadence.DAILY),
            portfolio(listOf(event()), listOf(expense("e1", 250_000.0))),
            emptySet(),
            now
        )

        val checkIn = requireNotNull(digest.checkIn)
        assertEquals(1, checkIn.activeEvents)
        assertEquals(250_000.0, checkIn.totalSpent, 0.01)
        assertEquals(750_000.0, checkIn.remaining, 0.01)
    }

    @Test
    fun `a weekly check-in only fires on its day`() {
        val prefs = NotificationPrefs(checkIn = CheckInCadence.WEEKLY)
        val portfolio = portfolio(listOf(event()))

        val monday = instantOn(Calendar.MONDAY)
        val thursday = instantOn(Calendar.THURSDAY)

        assertNotNull(DailyDigest.build(prefs, portfolio, emptySet(), monday).checkIn)
        assertNull(DailyDigest.build(prefs, portfolio, emptySet(), thursday).checkIn)
    }

    @Test
    fun `an empty app is never nudged`() {
        val digest = DailyDigest.build(
            NotificationPrefs(checkIn = CheckInCadence.DAILY),
            portfolio(emptyList()),
            emptySet(),
            now
        )

        assertNull(digest.checkIn)
        assertTrue(digest.isEmpty)
    }

    @Test
    fun `the sweep catches a threshold crossed while the app was closed`() {
        val digest = DailyDigest.build(
            NotificationPrefs(),
            portfolio(listOf(event()), listOf(expense("e1", 1_200_000.0))),
            emptySet(),
            now
        )

        assertEquals(1, digest.alerts.size)
        assertEquals(2, digest.alertState.size)
    }

    @Test
    fun `crossing state is kept current even with alerts switched off`() {
        val digest = DailyDigest.build(
            NotificationPrefs(budgetAlerts = false),
            portfolio(listOf(event()), listOf(expense("e1", 1_200_000.0))),
            emptySet(),
            now
        )

        // Nothing is posted, but the breach is recorded — so turning alerts back on
        // does not replay news the user has already lived through.
        assertTrue(digest.alerts.isEmpty())
        assertEquals(2, digest.alertState.size)
    }

    /** Midday on the next [dayOfWeek], so the digest's local-time weekday is unambiguous. */
    private fun instantOn(dayOfWeek: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}
