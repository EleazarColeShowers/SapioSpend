package com.example.sapiospend.billing

import kotlinx.coroutines.flow.StateFlow

/**
 * What the current user is allowed to do.
 *
 * This is the seam the Play Billing client plugs into: a BillingEntitlements
 * implementation would listen to purchase updates and push the resulting plan through
 * the same StateFlow, leaving every caller untouched. Google Play requires its own
 * billing library for in-app subscriptions, and that library needs products configured
 * in the Play Console, so it is deliberately not wired here yet.
 */
interface Entitlements {

    val plan: StateFlow<Plan>

    fun canUse(feature: ProFeature): Boolean = PlanRules.allows(plan.value, feature)

    fun canCreateEvent(activeEventCount: Int): Boolean =
        PlanRules.canCreateEvent(plan.value, activeEventCount)

    fun remainingFreeEvents(activeEventCount: Int): Int =
        PlanRules.remainingFreeEvents(plan.value, activeEventCount)

    /** Called when a purchase is confirmed — or revoked, by passing [Plan.FREE]. */
    fun applyPurchase(newPlan: Plan)
}
