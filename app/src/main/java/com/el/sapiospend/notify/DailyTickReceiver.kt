package com.el.sapiospend.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.el.sapiospend.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wakes the app for its daily tick, and puts the alarm back after the events that lose it.
 *
 * Alarms do not survive a reboot or a package replacement, and a user who reboots nightly
 * would otherwise get exactly one reminder ever. TIME_SET and TIMEZONE_CHANGED matter for
 * the same reason in reverse: the alarm is set for an absolute instant that was "9am
 * local", and after a flight it no longer is.
 */
class DailyTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext

        // onReceive runs on the main thread and the process may be killed as soon as it
        // returns; goAsync holds the broadcast open while the database read finishes.
        // The budget is roughly ten seconds, which a handful of small queries fits inside
        // comfortably — this is not the place to grow slower work.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    // A boot or an upgrade only needs the alarm back. Firing the digest
                    // here as well would post a reminder at whatever time the phone
                    // happened to start up, which is not the hour the user chose.
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED ->
                        NotificationScheduler(appContext)
                            .sync(SettingsRepository.create(appContext).notifications.value)

                    ACTION_TICK -> DigestRunner(appContext).run()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.el.sapiospend.action.DAILY_TICK"
    }
}
