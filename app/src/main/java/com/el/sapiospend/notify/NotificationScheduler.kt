package com.el.sapiospend.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.el.sapiospend.domain.notify.NotificationPrefs
import com.el.sapiospend.domain.notify.TickSchedule

/**
 * Owns the single daily alarm behind reminders and check-ins.
 *
 * One alarm for the whole app, not one per event. Per-event alarms would have to be
 * cancelled when an event is deleted, rewritten when its dates are edited, and rebuilt
 * from the database after every reboot — three chances to leak an alarm for an event
 * that no longer exists. A daily tick that reads the current database and decides what
 * to say has none of that state to keep in step.
 */
class NotificationScheduler(private val context: Context) {

    private val alarms = context.getSystemService<AlarmManager>()

    /**
     * Brings the alarm in line with [prefs]: set for the next occurrence of the chosen
     * hour, or cancelled when nothing is left that needs it.
     *
     * Safe to call repeatedly — the pending intent is a singleton by request code, so
     * scheduling twice replaces rather than duplicates. Call it after any preference
     * change, at launch, and after each tick fires.
     */
    fun sync(prefs: NotificationPrefs, now: Long = System.currentTimeMillis()) {
        // A notification that cannot be posted is not worth waking the device to build.
        // Re-synced when permission is granted, so this is a pause rather than a stop.
        if (!prefs.needsDailyTick || !Notifier(context).canPost()) {
            cancel()
            return
        }
        schedule(TickSchedule.nextTick(prefs.hourOfDay, now))
    }

    private fun schedule(triggerAt: Long) {
        val manager = alarms ?: return
        // Inexact on purpose. An exact alarm needs SCHEDULE_EXACT_ALARM on Android 12+,
        // which Play only grants to apps whose core function is alarms and clocks — a
        // budget reminder is not one, and the review risk is real. allowWhileIdle is what
        // matters here: it gets the tick through Doze, and a summary that arrives at 9:14
        // instead of 9:00 is the same summary.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, tickIntent())
    }

    fun cancel() {
        alarms?.cancel(tickIntent())
    }

    private fun tickIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyTickReceiver::class.java).setAction(DailyTickReceiver.ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val REQUEST_CODE = 4001
    }
}
