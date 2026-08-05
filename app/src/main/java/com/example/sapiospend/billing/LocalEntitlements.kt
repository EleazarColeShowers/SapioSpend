package com.example.sapiospend.billing

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the plan on-device.
 *
 * Until Play Billing is connected this is the only source of truth, which means it is
 * trivially editable by anyone with root — acceptable while nothing is being charged,
 * and the reason [applyPurchase] is only reachable from a debug build (see PaywallSheet).
 */
class LocalEntitlements(private val prefs: SharedPreferences) : Entitlements {

    private val _plan = MutableStateFlow(readPlan())
    override val plan: StateFlow<Plan> = _plan.asStateFlow()

    override fun applyPurchase(newPlan: Plan) {
        prefs.edit().putString(KEY_PLAN, newPlan.name).apply()
        _plan.value = newPlan
    }

    // An unrecognised stored value falls back to FREE rather than throwing — a corrupt
    // preference should degrade the plan, never crash the app on launch.
    private fun readPlan(): Plan =
        prefs.getString(KEY_PLAN, null)
            ?.let { stored -> Plan.entries.firstOrNull { it.name == stored } }
            ?: Plan.FREE

    companion object {
        private const val PREFS_NAME = "sapio_entitlements"
        private const val KEY_PLAN = "plan"

        fun create(context: Context): LocalEntitlements =
            LocalEntitlements(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
