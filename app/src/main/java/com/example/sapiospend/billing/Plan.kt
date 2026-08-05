package com.example.sapiospend.billing

/** Subscription tiers. Agency and White Label arrive once there is a backend to scope them to. */
enum class Plan(val displayName: String, val priceLabel: String) {
    FREE("Free", "₦0"),
    PRO("Pro", "₦5,000/month")
}

/** Everything behind the paywall. Each entry is one line in the upgrade sheet. */
enum class ProFeature(val label: String, val blurb: String) {
    UNLIMITED_EVENTS("Unlimited events", "Run as many events as you have clients"),
    TEMPLATES("Budget templates", "Start from a proven breakdown instead of a blank page"),
    ANALYTICS("Spending analytics", "Planned vs actual, burn rate, and category variance"),
    PDF_EXPORT("PDF export", "Client-ready budget reports"),
    EXCEL_EXPORT("Excel export", "Take the numbers into your own spreadsheets")
}

object FreePlanLimits {
    const val MAX_ACTIVE_EVENTS = 3
}

/**
 * Plan rules as pure functions so they can be unit-tested without Android, and so the
 * same logic can be lifted to a server later.
 *
 * Note that these are client-side checks only. They shape the UI; they are not security.
 * Anyone can patch an APK, so once entitlements are worth money the authoritative check
 * has to happen server-side at the point the paid work is done.
 */
object PlanRules {

    // Every ProFeature is all-or-nothing today. The parameter is here because the
    // Agency tier will need per-feature answers, and callers shouldn't have to change.
    @Suppress("UNUSED_PARAMETER")
    fun allows(plan: Plan, feature: ProFeature): Boolean = when (plan) {
        Plan.PRO -> true
        Plan.FREE -> false
    }

    /** Only live events count — tombstoned ones must not hold a slot hostage. */
    fun canCreateEvent(plan: Plan, activeEventCount: Int): Boolean = when (plan) {
        Plan.PRO -> true
        Plan.FREE -> activeEventCount < FreePlanLimits.MAX_ACTIVE_EVENTS
    }

    fun remainingFreeEvents(plan: Plan, activeEventCount: Int): Int = when (plan) {
        Plan.PRO -> Int.MAX_VALUE
        Plan.FREE -> (FreePlanLimits.MAX_ACTIVE_EVENTS - activeEventCount).coerceAtLeast(0)
    }
}
