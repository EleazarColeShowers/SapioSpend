package com.el.sapiospend.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.el.sapiospend.billing.Entitlements
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.PlanRules
import com.el.sapiospend.billing.ProFeature
import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.EventRepository
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.domain.plan.BudgetPlanEditor
import com.el.sapiospend.domain.template.BudgetTemplate
import com.el.sapiospend.domain.template.CategoryAmount
import com.el.sapiospend.domain.template.CustomCategoryInput
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
     * Creates an event, optionally seeding planned allocations from a template or from
     * categories the user wrote themselves.
     *
     * The plan check re-reads the live count rather than trusting UI state, so a stale
     * screen or two fast taps cannot slip a fourth event past the free tier.
     */
    fun addEvent(
        name: String,
        budget: Double,
        eventType: String = "General",
        template: BudgetTemplate? = null,
        customLines: List<CategoryAmount> = emptyList(),
        startDate: Long? = null,
        endDate: Long? = null
    ) {
        viewModelScope.launch {
            if (!entitlements.canCreateEvent(repository.activeEventCount())) {
                _message.value = UiMessage.EventLimitReached
                return@launch
            }

            val (start, end) = normalizePeriod(startDate, endDate)

            val event = EventEntity(
                name = name,
                budget = budget,
                eventType = eventType,
                startDate = start,
                endDate = end
            )
            // A custom plan wins over a template because it is the more deliberate of the
            // two: the user typed those figures. It is also ungated — TEMPLATES sells a
            // ready-made breakdown, and building your own is precisely the blank page it
            // sells the alternative to.
            //
            // A template the user is not entitled to is ignored rather than refused —
            // the event is still worth creating, just without the planned breakdown.
            val allocations = when {
                customLines.isNotEmpty() -> customLines
                template != null && entitlements.canUse(ProFeature.TEMPLATES) -> template.allocate(budget)
                else -> emptyList()
            }
            val lines = allocations.map {
                BudgetLineEntity(eventId = event.id, category = it.category, plannedAmount = it.amount)
            }

            // A write can fail on a full disk. Uncaught it would take the whole app down
            // from a coroutine, which is a poor way to learn storage ran out.
            runCatching { repository.addEvent(event, lines) }
                .onFailure { _message.value = UiMessage.Error("Could not save the event: ${it.message}") }
        }
    }

    /**
     * Saves an edited event, period included.
     *
     * The period is straightened here as well as on the way in, because an event's dates
     * are editable now and a reversed range turns every pacing figure — days left, safe
     * daily spend, whether the money is outrunning the calendar — negative.
     */
    fun updateEvent(event: EventEntity) {
        viewModelScope.launch {
            val (start, end) = normalizePeriod(event.startDate, event.endDate)
            runCatching { repository.updateEvent(event.copy(startDate = start, endDate = end)) }
                .onFailure { _message.value = UiMessage.Error("Could not save the event: ${it.message}") }
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            runCatching { repository.updateExpense(expense) }
                .onFailure { _message.value = UiMessage.Error("Could not save the expense: ${it.message}") }
        }
    }

    /** A range handed over end-first is swapped rather than refused; it is a mis-tap. */
    private fun normalizePeriod(startDate: Long?, endDate: Long?): Pair<Long?, Long?> =
        if (startDate != null && endDate != null && endDate < startDate) endDate to startDate
        else startDate to endDate

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { repository.deleteEvent(event) }
    }

    /**
     * The plan as it stands in the database, for the editor to open on.
     *
     * A one-shot read rather than [budgetLines]: that flow starts on an empty list and
     * fills in a frame later, and an editor seeded from it would open blank on an event
     * that has a plan — then save that blank over the real one.
     */
    suspend fun plannedLinesFor(eventId: String): List<BudgetLineEntity> =
        repository.budgetLinesFor(eventId)

    /**
     * Saves the planned allocations for an event from the rows the editor is showing.
     *
     * The planned side of the budget is ungated on purpose. Analytics — the readout of
     * plan against reality — is what Pro sells; being able to say what you *intended* to
     * spend is the ordinary half of budgeting, and a plan the user cannot write is a
     * budget app that only records regret.
     */
    fun savePlan(eventId: String, rows: List<CustomCategoryInput>) {
        viewModelScope.launch {
            val existing = repository.budgetLinesFor(eventId)
            val edit = BudgetPlanEditor.edit(eventId, rows, existing)
            runCatching { repository.savePlan(edit.lines, edit.removedIds) }
                .onFailure { _message.value = UiMessage.Error("Could not save the plan: ${it.message}") }
        }
    }

    /**
     * [date] is when the money was spent, which is not always when it was typed in — a
     * receipt logged three days late belongs in the month it happened, or the trend
     * chart and every period figure quietly attribute it to the wrong one.
     */
    fun addExpense(
        eventId: String,
        title: String,
        category: String,
        amount: Double,
        notes: String = "",
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addExpense(
                    ExpenseEntity(
                        eventId = eventId,
                        title = title,
                        category = category,
                        amount = amount,
                        notes = notes,
                        dateCreated = date
                    )
                )
            }.onFailure { _message.value = UiMessage.Error("Could not save the expense: ${it.message}") }
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
