package com.el.sapiospend.notify

import android.content.Context
import com.el.sapiospend.domain.analytics.EventAnalytics
import com.el.sapiospend.domain.notify.BudgetAlertPublisher
import com.el.sapiospend.domain.notify.BudgetAlerts
import com.el.sapiospend.settings.SettingsRepository

/**
 * Posts budget alerts the moment an expense pushes an event past a threshold, rather than
 * waiting for the next daily tick.
 *
 * Immediacy is the whole point of this alert: being told at 9am tomorrow that yesterday's
 * catering deposit blew the budget is a receipt, not a warning. The daily sweep in
 * [DigestRunner] remains as a backstop and shares the same crossing state, so a threshold
 * is never announced twice.
 */
class AndroidBudgetAlertPublisher(context: Context) : BudgetAlertPublisher {

    private val appContext = context.applicationContext
    private val store = BudgetAlertStore.create(appContext)
    private val notifier = Notifier(appContext)

    override fun publish(events: List<EventAnalytics>) {
        val prefs = SettingsRepository.create(appContext).notifications.value

        // The evaluation runs even with alerts switched off, so the stored crossings stay
        // current. Otherwise turning alerts back on would replay every breach that
        // happened while they were off — a burst of stale news.
        val evaluation = BudgetAlerts.evaluate(events, store.crossed())

        if (prefs.budgetAlerts) evaluation.toPost.forEach(notifier::notifyBudgetAlert)
        store.setCrossed(evaluation.crossed)
    }
}
