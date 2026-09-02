package com.el.sapiospend.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.el.sapiospend.MainActivity
import com.el.sapiospend.R
import com.el.sapiospend.domain.notify.BudgetAlert
import com.el.sapiospend.domain.notify.BudgetThreshold
import com.el.sapiospend.domain.notify.CheckInSummary
import com.el.sapiospend.domain.notify.EventReminder
import com.el.sapiospend.settings.SettingsRepository
import com.el.sapiospend.util.formatMoney

/**
 * Everything that actually reaches the status bar.
 *
 * Posting is best-effort by design: the runtime permission can be missing on Android 13+
 * and the user can disable a channel at any time. Neither is an error the app should
 * report — they are the user having already said no — so [post] simply does nothing.
 */
class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Read from storage rather than from [com.el.sapiospend.settings.ActiveCurrency].
     *
     * That global is seeded by MainActivity, and the daily tick runs in a process that
     * may never have opened MainActivity — a phone rebooted overnight wakes straight into
     * the alarm. Reading the global there would quietly format a dollar budget in naira.
     * Lazy so the scheduler, which builds a Notifier only to ask [canPost], does not touch
     * SharedPreferences for nothing.
     */
    private val currency by lazy { SettingsRepository.create(context).currency.value }

    /**
     * Whether a notification posted right now would be seen.
     *
     * Checked before doing the work of building one, and before scheduling the daily
     * alarm at all — waking the device to compose a notification the system will drop is
     * pure battery waste.
     */
    fun canPost(): Boolean = hasPermission(context) && manager.areNotificationsEnabled()

    fun notifyBudgetAlert(alert: BudgetAlert) {
        val title = when (alert.threshold) {
            BudgetThreshold.WARNING -> "${alert.eventName} is at ${alert.percentUsed}%"
            BudgetThreshold.EXCEEDED -> "${alert.eventName} is over budget"
        }
        val text = when (alert.threshold) {
            BudgetThreshold.WARNING ->
                "${alert.spent.formatMoney(currency)} of ${alert.budget.formatMoney(currency)} spent — " +
                    "${alert.remaining.formatMoney(currency)} left."
            // The overspend is stated as a positive amount because "over by ₦40,000"
            // is what the user needs to act on; a minus sign in front of a remaining
            // balance takes a second read to mean the same thing.
            BudgetThreshold.EXCEEDED ->
                "Over by ${(-alert.remaining).formatMoney(currency)} on a ${alert.budget.formatMoney(currency)} budget."
        }

        post(
            id = idFor(ALERT_BASE, alert.eventId),
            channel = NotificationChannels.ALERTS,
            title = title,
            text = text,
            eventId = alert.eventId
        )
    }

    fun notifyReminder(reminder: EventReminder) {
        val title = when (reminder.daysRemaining) {
            0 -> "${reminder.eventName} ends today"
            1 -> "${reminder.eventName} ends tomorrow"
            else -> "${reminder.eventName} ends in ${reminder.daysRemaining} days"
        }
        val text = buildString {
            if (reminder.remaining >= 0) append("${reminder.remaining.formatMoney(currency)} left")
            else append("${(-reminder.remaining).formatMoney(currency)} over budget")
            // The daily allowance is the number a reminder exists to deliver, but on the
            // closing day it is just the remaining balance again, so it is left off.
            reminder.safeDailySpend?.takeIf { reminder.daysRemaining > 0 && it > 0 }?.let {
                append(" — ${it.formatMoney(currency)} a day to stay on track")
            }
            append(".")
        }

        post(
            id = idFor(REMINDER_BASE, reminder.eventId),
            channel = NotificationChannels.REMINDERS,
            title = title,
            text = text,
            eventId = reminder.eventId,
            // A reminder that a period is closing is exactly when somebody has spending
            // to write down, so the action is offered rather than making them find the
            // event, then the button, then the form.
            quickAdd = true
        )
    }

    fun notifyCheckIn(summary: CheckInSummary) {
        val title = if (summary.overBudgetCount > 0) {
            "${summary.overBudgetCount} of ${summary.activeEvents} budgets are over"
        } else {
            "${summary.remaining.formatMoney(currency)} left across your budgets"
        }
        val text = "${summary.totalSpent.formatMoney(currency)} spent of ${summary.totalBudget.formatMoney(currency)}. " +
            "Anything to log?"

        post(
            id = CHECK_IN_ID,
            channel = NotificationChannels.CHECK_IN,
            title = title,
            text = text,
            eventId = null,
            // The check-in ends with "Anything to log?" — the action is the answer.
            quickAdd = true
        )
    }

    // The permission check is one call away, in canPost(), which lint cannot follow — and
    // the notify() below is wrapped besides. Suppressed rather than restructured: moving
    // the check inline would mean repeating it at every call site.
    @SuppressLint("MissingPermission")
    private fun post(
        id: Int,
        channel: String,
        title: String,
        text: String,
        eventId: String?,
        quickAdd: Boolean = false
    ) {
        if (!canPost()) return

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            // Amounts and event names run past the single collapsed line often enough
            // that a truncated "₦2,340,000 of ₦2,8…" would be the normal case.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(id, eventId))
            .setAutoCancel(true)

        if (quickAdd) {
            // The action's request code is offset from the notification's own so the two
            // pending intents stay distinct — sharing one would make tapping the body do
            // whatever the button does.
            builder.addAction(
                R.drawable.ic_stat_notify,
                "Log an expense",
                openAppIntent(id + QUICK_ADD_REQUEST_OFFSET, eventId, quickAdd = true)
            )
        }

        val notification = builder.build()

        // Permission is re-checked inside notify() by the compat layer, but the platform
        // still throws if it disappears between the check above and here — a user
        // revoking it from the shade mid-post. A dropped notification is not worth
        // taking the process down for.
        runCatching { manager.notify(id, notification) }
    }

    /**
     * Opens the app on the event the notification is about. Landing on the home screen
     * and making the user find the event again wastes the one tap the notification got.
     *
     * The request code is the notification id so two live notifications cannot share —
     * and therefore overwrite — each other's pending intent extras.
     */
    private fun openAppIntent(requestCode: Int, eventId: String?, quickAdd: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            eventId?.let { putExtra(MainActivity.EXTRA_EVENT_ID, it) }
            if (quickAdd) putExtra(MainActivity.EXTRA_QUICK_ADD, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        // Ids are derived from the event id so a second alert for the same event replaces
        // the first rather than stacking. The kind occupies the high bits and the event
        // the low ones, so an alert and a reminder can never collide into the same id —
        // which, with plain offsets, two different events easily could.
        private const val ALERT_BASE = 1
        private const val REMINDER_BASE = 2
        private const val EVENT_ID_BITS = 20

        /** Keeps an action's pending intent from colliding with its notification's own. */
        private const val QUICK_ADD_REQUEST_OFFSET = 1 shl 24

        // Not const: a shift is not a compile-time constant expression in Kotlin.
        private val CHECK_IN_ID = 3 shl EVENT_ID_BITS

        private fun idFor(base: Int, eventId: String): Int =
            (base shl EVENT_ID_BITS) or (eventId.hashCode() and ((1 shl EVENT_ID_BITS) - 1))

        fun hasPermission(context: Context): Boolean =
            // POST_NOTIFICATIONS only exists from Android 13; before it, being installed
            // was consent enough and the permission is not in the manifest to check.
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
