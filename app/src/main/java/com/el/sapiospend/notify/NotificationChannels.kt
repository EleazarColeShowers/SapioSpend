package com.el.sapiospend.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Three channels rather than one, because they are three different bargains with the
 * user: an alert says money is going, a reminder says time is going, and a check-in is a
 * nudge. Somebody who wants the first two and not the third can say so in system
 * settings without silencing the app — which is what they would otherwise do.
 */
object NotificationChannels {

    const val ALERTS = "budget_alerts"
    const val REMINDERS = "event_reminders"
    const val CHECK_IN = "spend_check_in"

    /**
     * Idempotent: creating a channel that exists updates its name and leaves the
     * importance the user chose alone, so this can run on every launch.
     */
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(ALERTS, "Budget alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When spending on an event passes 80% or goes over budget"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS, "Event reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a budget period is about to end"
            }
        )
        // Deliberately low: a check-in is the app asking for attention rather than
        // reporting something that happened, so it belongs in the shade without a sound.
        manager.createNotificationChannel(
            NotificationChannel(CHECK_IN, "Spending check-in", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A regular summary of where your budgets stand"
            }
        )
    }
}
