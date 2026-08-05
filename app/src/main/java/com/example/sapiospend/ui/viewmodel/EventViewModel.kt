package com.example.sapiospend.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sapiospend.billing.Entitlements
import com.example.sapiospend.billing.Plan
import com.example.sapiospend.billing.PlanRules
import com.example.sapiospend.billing.ProFeature
import com.example.sapiospend.data.local.BudgetLineEntity
import com.example.sapiospend.data.local.EventEntity
import com.example.sapiospend.data.local.EventRepository
import com.example.sapiospend.data.local.ExpenseEntity
import com.example.sapiospend.domain.template.BudgetTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One-shot messages the UI shows and then clears. */
sealed interface UiMessage {
    data object EventLimitReached : UiMessage
    data class Error(val text: String) : UiMessage
}

class EventViewModel(
    private val repository: EventRepository,
    private val entitlements: Entitlements
) : ViewModel() {

    val events = repository.events.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allExpenses = repository.allExpenses.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val budgetLines = repository.allBudgetLines.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val plan: StateFlow<Plan> = entitlements.plan

    /** Drives the "1 event left" hint on Home. Enforcement uses a fresh count, not this. */
    val remainingFreeEvents: StateFlow<Int> = combine(events, plan) { list, currentPlan ->
        PlanRules.remainingFreeEvents(currentPlan, list.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FREE_EVENTS_UNKNOWN)

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun canUse(feature: ProFeature): Boolean = entitlements.canUse(feature)

    /**
     * Creates an event, optionally seeding planned allocations from a template.
     *
     * The plan check re-reads the live count rather than trusting UI state, so a stale
     * screen or two fast taps cannot slip a fourth event past the free tier.
     */
    fun addEvent(
        name: String,
        budget: Double,
        eventType: String = "General",
        template: BudgetTemplate? = null
    ) {
        viewModelScope.launch {
            if (!entitlements.canCreateEvent(repository.activeEventCount())) {
                _message.value = UiMessage.EventLimitReached
                return@launch
            }

            val event = EventEntity(name = name, budget = budget, eventType = eventType)
            // A template the user is not entitled to is ignored rather than refused —
            // the event is still worth creating, just without the planned breakdown.
            val lines = template
                ?.takeIf { entitlements.canUse(ProFeature.TEMPLATES) }
                ?.allocate(budget)
                ?.map { BudgetLineEntity(eventId = event.id, category = it.category, plannedAmount = it.amount) }
                .orEmpty()

            // A write can fail on a full disk. Uncaught it would take the whole app down
            // from a coroutine, which is a poor way to learn storage ran out.
            runCatching { repository.addEvent(event, lines) }
                .onFailure { _message.value = UiMessage.Error("Could not save the event: ${it.message}") }
        }
    }

    fun updateEvent(event: EventEntity) {
        viewModelScope.launch { repository.updateEvent(event) }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { repository.deleteEvent(event) }
    }

    fun addExpense(eventId: String, title: String, category: String, amount: Double, notes: String = "") {
        viewModelScope.launch {
            repository.addExpense(
                ExpenseEntity(eventId = eventId, title = title, category = category, amount = amount, notes = notes)
            )
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    /** Applied by the paywall today; by the billing client once purchases are live. */
    fun applyPurchase(newPlan: Plan) = entitlements.applyPurchase(newPlan)

    companion object {
        /** Before the first emission the remaining count is unknown, so show no hint. */
        const val FREE_EVENTS_UNKNOWN = -1

        fun factory(
            repository: EventRepository,
            entitlements: Entitlements
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { EventViewModel(repository, entitlements) }
        }
    }
}
