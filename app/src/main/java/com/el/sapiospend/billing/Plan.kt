package com.el.sapiospend.billing

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

    /**
     * v1.0 ships every Pro feature to everyone.
     *
     * Play Billing is not wired, so there is no way for a user to actually buy Pro.
     * Gating features behind a purchase that cannot be made leaves the upgrade path dead
     * on arrival and reads as broken functionality to a Play reviewer. The tier rules
     * below are left intact and tested; flip this to false in the same release that turns
     * billing on.
     *
     * Deliberately not `const`: keeping it a `val` stops the tier branches from being
     * folded away as dead code, so they keep compiling and stay covered by tests.
     */
    val ALL_FEATURES_FREE = true

    /** Whether Pro-only surfaces should be open to [plan] right now. */
    fun proFeaturesUnlocked(plan: Plan): Boolean = ALL_FEATURES_FREE || plan == Plan.PRO

    fun allows(plan: Plan, feature: ProFeature): Boolean =
        ALL_FEATURES_FREE || tierAllows(plan, feature)

    /** Only live events count — tombstoned ones must not hold a slot hostage. */
    fun canCreateEvent(plan: Plan, activeEventCount: Int): Boolean =
        ALL_FEATURES_FREE || tierCanCreateEvent(plan, activeEventCount)

    fun remainingFreeEvents(plan: Plan, activeEventCount: Int): Int =
        if (ALL_FEATURES_FREE) Int.MAX_VALUE else tierRemainingFreeEvents(plan, activeEventCount)

    // --- Tier rules -------------------------------------------------------------
    // The subscription logic itself, independent of the v1.0 giveaway above. These are
    // what the paid product will run on once ALL_FEATURES_FREE is turned off.

    // Every ProFeature is all-or-nothing today. The parameter is here because the
    // Agency tier will need per-feature answers, and callers shouldn't have to change.
    @Suppress("UNUSED_PARAMETER")
    fun tierAllows(plan: Plan, feature: ProFeature): Boolean = when (plan) {
        Plan.PRO -> true
        Plan.FREE -> false
    }

    fun tierCanCreateEvent(plan: Plan, activeEventCount: Int): Boolean = when (plan) {
        Plan.PRO -> true
        Plan.FREE -> activeEventCount < FreePlanLimits.MAX_ACTIVE_EVENTS
    }

    fun tierRemainingFreeEvents(plan: Plan, activeEventCount: Int): Int = when (plan) {
        Plan.PRO -> Int.MAX_VALUE
        Plan.FREE -> (FreePlanLimits.MAX_ACTIVE_EVENTS - activeEventCount).coerceAtLeast(0)
    }
}
