package com.example.sapiospend

import com.example.sapiospend.billing.FreePlanLimits
import com.example.sapiospend.billing.Plan
import com.example.sapiospend.billing.PlanRules
import com.example.sapiospend.billing.ProFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRulesTest {

    @Test
    fun `free plan allows events up to the limit`() {
        for (count in 0 until FreePlanLimits.MAX_ACTIVE_EVENTS) {
            assertTrue("$count events should still allow another", PlanRules.canCreateEvent(Plan.FREE, count))
        }
    }

    @Test
    fun `free plan blocks the event after the limit`() {
        assertFalse(PlanRules.canCreateEvent(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS))
    }

    @Test
    fun `free plan stays blocked if the count somehow exceeds the limit`() {
        assertFalse(PlanRules.canCreateEvent(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS + 5))
    }

    @Test
    fun `pro plan is never blocked`() {
        assertTrue(PlanRules.canCreateEvent(Plan.PRO, 0))
        assertTrue(PlanRules.canCreateEvent(Plan.PRO, 500))
    }

    @Test
    fun `remaining free events counts down and floors at zero`() {
        assertEquals(FreePlanLimits.MAX_ACTIVE_EVENTS, PlanRules.remainingFreeEvents(Plan.FREE, 0))
        assertEquals(1, PlanRules.remainingFreeEvents(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS - 1))
        assertEquals(0, PlanRules.remainingFreeEvents(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS))
        assertEquals(0, PlanRules.remainingFreeEvents(Plan.FREE, FreePlanLimits.MAX_ACTIVE_EVENTS + 10))
    }

    @Test
    fun `every pro feature is locked on free and open on pro`() {
        ProFeature.entries.forEach { feature ->
            assertFalse("$feature must be locked on free", PlanRules.allows(Plan.FREE, feature))
            assertTrue("$feature must be open on pro", PlanRules.allows(Plan.PRO, feature))
        }
    }
}
