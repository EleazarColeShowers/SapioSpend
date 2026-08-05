package com.example.sapiospend.fake

import com.example.sapiospend.billing.Entitlements
import com.example.sapiospend.billing.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Entitlements with no persistence, so tests can flip plans directly. */
class FakeEntitlements(initial: Plan = Plan.FREE) : Entitlements {

    private val _plan = MutableStateFlow(initial)
    override val plan: StateFlow<Plan> = _plan

    override fun applyPurchase(newPlan: Plan) {
        _plan.value = newPlan
    }
}
