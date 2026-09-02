package com.el.sapiospend.notify

import android.content.Context
import com.el.sapiospend.data.local.AppDatabase
import com.el.sapiospend.data.local.EventRepository
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.notify.DailyDigest
import com.el.sapiospend.settings.SettingsRepository
import com.el.sapiospend.widget.BudgetWidget
import kotlinx.coroutines.flow.first

/**
 * The daily tick, end to end: read the database, work out what is worth saying, say it,
 * remember what was said, and set tomorrow's alarm.
 *
 * Kept out of the receiver so the sequence is readable in one place, and so the receiver
 * stays the thin piece of Android plumbing it should be.
 */
class DigestRunner(private val context: Context) {

    suspend fun run(now: Long = System.currentTimeMillis()) {
        val prefs = SettingsRepository.create(context).notifications.value
        val notifier = Notifier(context)
        val store = BudgetAlertStore.create(context)

        // Rescheduled first and unconditionally. Everything below can end early — no
        // events, nothing due, notifications switched off in system settings — and a
        // reschedule that only happened on the way out of a successful tick would make
        // one quiet day the last day the app ever notified anybody.
        NotificationScheduler(context).sync(prefs, now)

        if (!notifier.canPost()) return

        val db = AppDatabase.getDatabase(context)
        val repository = EventRepository(
            db.eventDao(),
            db.expenseDao(),
            db.budgetLineDao(),
            db.contributionDao(),
            db.recurringExpenseDao()
        )

        // Recurring charges are applied before the numbers are read, so a reminder about
        // a budget with rent due today is a reminder about a budget that includes the
        // rent. This is also the path that keeps rules current for a user who has not
        // opened the app in weeks.
        runCatching { repository.materializeRecurring(now) }

        // first() on each flow: a one-shot read of the current rows. Collecting would
        // never return, and this runs inside a broadcast the system expects to end.
        val portfolio = BudgetAnalytics.portfolio(
            events = repository.events.first(),
            expenses = repository.allExpenses.first(),
            budgetLines = repository.allBudgetLines.first(),
            contributions = repository.allContributions.first(),
            now = now
        )

        val digest = DailyDigest.build(prefs, portfolio, store.crossed(), now)

        digest.reminders.forEach(notifier::notifyReminder)
        digest.alerts.forEach(notifier::notifyBudgetAlert)
        digest.checkIn?.let(notifier::notifyCheckIn)

        // The tick has just charged whatever recurring rules came due, so the widget is
        // showing yesterday's figures until it is told otherwise.
        BudgetWidget.refresh(context)

        // Written even when nothing was posted, because the evaluation also *clears*
        // crossings that no longer hold — that is what lets an alert fire again after
        // the user corrects the expense that caused it.
        store.setCrossed(digest.alertState)
    }
}
