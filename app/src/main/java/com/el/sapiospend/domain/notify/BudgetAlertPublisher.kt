package com.el.sapiospend.domain.notify

import com.el.sapiospend.domain.analytics.EventAnalytics

/**
 * The seam between "the numbers changed" and "tell the user".
 *
 * [com.el.sapiospend.ui.viewmodel.EventViewModel] is the one place that sees every write
 * to the budget as it happens, which makes it the right place to notice a threshold being
 * crossed — but it must not know about notification channels or Android permissions to do
 * it. It calls this; the Android implementation lives in the notify package, and tests get
 * [None].
 */
fun interface BudgetAlertPublisher {

    /** Called with the current state of every event, as often as it changes. */
    fun publish(events: List<EventAnalytics>)

    companion object {
        /** For tests and any build with notifications compiled out. */
        val None = BudgetAlertPublisher { }
    }
}
