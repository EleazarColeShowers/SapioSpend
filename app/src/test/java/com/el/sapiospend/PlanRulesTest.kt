package com.el.sapiospend

import com.el.sapiospend.billing.FreePlanLimits
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.PlanRules
import com.el.sapiospend.billing.ProFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two layers are covered here.
 *
 * The `tier*` tests pin the subscription logic itself, which is dormant in v1.0 but is
 * what the paid product will run on. The "v1.0" tests pin the giveaway that sits on top
 * of it, so that shipping every feature free stays a deliberate, visible decision rather
 * than something that quietly regresses.
 */
class PlanRulesTest {

    // --- Tier rules: dormant in v1.0, live once billing ships --------------------

    @Test
    fun `free tier allows events up to the limit`() {
        for (count in 0 until FreePlanLimits.MAX_ACTIVE_EVENTS) {
            assertTrue(
                "$count events should still allow another",
                PlanRules.tierCanCreateEvent(Plan.FREE, count)
            )
        }
    }

    @Test
    fun `free tier blocks the event after the limit`() {
        assertFalse(PlanRules.tierCanCreateEvent(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS))
    }

    @Test
    fun `free tier stays blocked if the count somehow exceeds the limit`() {
        assertFalse(PlanRules.tierCanCreateEvent(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS + 5))
    }

    @Test
    fun `pro tier is never blocked`() {
        assertTrue(PlanRules.tierCanCreateEvent(Plan.PRO, 0))
        assertTrue(PlanRules.tierCanCreateEvent(Plan.PRO, 500))
    }

    @Test
    fun `tier remaining free events counts down and floors at zero`() {
        assertEquals(
            FreePlanLimits.MAX_ACTIVE_EVENTS,
            PlanRules.tierRemainingFreeEvents(Plan.FREE, 0)
        )
        assertEquals(
            1,
            PlanRules.tierRemainingFreeEvents(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS - 1)
        )
        assertEquals(
            0,
            PlanRules.tierRemainingFreeEvents(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS)
        )
        assertEquals(
            0,
            PlanRules.tierRemainingFreeEvents(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS + 10)
        )
    }

    @Test
    fun `every pro feature is locked on the free tier and open on the pro tier`() {
        ProFeature.entries.forEach { feature ->
            assertFalse("$feature must be locked on free", PlanRules.tierAllows(Plan.FREE, feature))
            assertTrue("$feature must be open on pro", PlanRules.tierAllows(Plan.PRO, feature))
        }
    }

    // --- v1.0: nothing is gated, because nothing can be bought ------------------

    @Test
    fun `v1_0 ships every feature to everyone`() {
        assertTrue(
            "v1.0 must not gate features behind a purchase that cannot be made",
            PlanRules.ALL_FEATURES_FREE
        )
    }

    @Test
    fun `every pro feature is open on the free plan while everything is free`() {
        ProFeature.entries.forEach { feature ->
            assertTrue("$feature must be open in v1.0", PlanRules.allows(Plan.FREE, feature))
        }
    }

    @Test
    fun `free plan has no event ceiling while everything is free`() {
        assertTrue(PlanRules.canCreateEvent(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS))
        assertTrue(PlanRules.canCreateEvent(Plan.FREE, 500))
    }

    @Test
    fun `pro surfaces are unlocked for both plans while everything is free`() {
        assertTrue(PlanRules.proFeaturesUnlocked(Plan.FREE))
        assertTrue(PlanRules.proFeaturesUnlocked(Plan.PRO))
    }

    /**
     * The remaining-events banner keys off this. Reporting a full allowance keeps the
     * "you have N events left" nudge off screen while there is no limit to nudge about.
     */
    @Test
    fun `remaining free events is unbounded while everything is free`() {
        assertEquals(Int.MAX_VALUE, PlanRules.remainingFreeEvents(Plan.FREE, 0))
        assertEquals(Int.MAX_VALUE, PlanRules.remainingFreeEvents(Plan.FREE, 500))
    }
}
