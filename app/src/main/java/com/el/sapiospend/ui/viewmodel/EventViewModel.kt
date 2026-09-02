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
import com.el.sapiospend.data.local.ContributionEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.EventRepository
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.data.local.RecurringExpenseEntity
import com.el.sapiospend.domain.analytics.BudgetAnalytics
import com.el.sapiospend.domain.payment.PaymentStatus
import com.el.sapiospend.domain.payment.Payments
import com.el.sapiospend.domain.recurring.Recurrence
import com.el.sapiospend.domain.recurring.RecurringExpenses
import com.el.sapiospend.domain.notify.BudgetAlertPublisher
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
    private val entitlements: Entitlements,
    /**
     * Notified whenever the budget picture changes. Defaulted to a no-op so tests and
     * previews construct the ViewModel without an Android context behind them.
     */
    alerts: BudgetAlertPublisher = BudgetAlertPublisher.None
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

    val contributions = repository.allContributions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val recurringRules = repository.allRecurringRules.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val plan: StateFlow<Plan> = entitlements.plan

    /** Drives the "1 event left" hint on Home. Enforcement uses a fresh count, not this. */
    val remainingFreeEvents: StateFlow<Int> = combine(events, plan) { list, currentPlan ->
        PlanRules.remainingFreeEvents(currentPlan, list.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FREE_EVENTS_UNKNOWN)

    /**
     * Watches every write to the budget for a threshold crossing.
     *
     * Driven off the same flows the UI reads rather than hooked onto [addExpense],
     * because an expense being *edited or deleted* changes the totals just as much — and
     * a correction that takes an event back under budget has to be seen, or the alert
     * will not fire again when it goes over a second time.
     *
     * Whether anything is actually posted is the publisher's business; this side only
     * knows the numbers moved.
     */
    init {
        viewModelScope.launch {
            combine(events, allExpenses, budgetLines, contributions) { events, expenses, lines, funding ->
                BudgetAnalytics.portfolio(events, expenses, lines, funding).events
            }.collect(alerts::publish)
        }

        // Rules that came due while the app was closed are charged on the way in, so the
        // first screen the user sees is already current. The daily tick does the same
        // when the app is not opened at all; both go through the repository, which
        // advances each rule's due date in the same transaction and so cannot double up.
        viewModelScope.launch {
            runCatching { repository.materializeRecurring() }
        }
    }

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
        endDate: Long? = null,
        guestCount: Int? = null
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
                guestCount = guestCount,
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
        date: Long = System.currentTimeMillis(),
        vendor: String = "",
        /**
         * Null means "settled" — the caller did not ask the question, and every path that
         * does not (the widget, a notification quick-add) is recording money that has
         * already gone. Defaulting the other way would file every quick entry as a debt.
         */
        amountPaid: Double? = null,
        dueDate: Long? = null,
        receiptPath: String? = null
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
                        dateCreated = date,
                        vendor = vendor,
                        amountPaid = (amountPaid ?: amount).coerceIn(0.0, amount),
                        dueDate = dueDate,
                        receiptPath = receiptPath
                    )
                )
            }.onFailure { _message.value = UiMessage.Error("Could not save the expense: ${it.message}") }
        }
    }

    /**
     * Moves one expense between payment states from the list, without opening the form.
     *
     * Marking a vendor paid is the single most repeated action in the app once bookings
     * start settling, and routing it through the whole edit screen would make it four
     * taps and a save. A deposit still needs the form, because only the user knows how
     * much of it was handed over.
     */
    fun setPaymentStatus(expense: ExpenseEntity, status: PaymentStatus) {
        viewModelScope.launch {
            runCatching { repository.updateExpense(Payments.applyStatus(expense, status)) }
                .onFailure { _message.value = UiMessage.Error("Could not update the payment: ${it.message}") }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    // --- Funding --------------------------------------------------------------------

    /**
     * Records money coming in. [receivedAt] null means it has only been promised, which
     * is the distinction the whole funding feature turns on.
     */
    fun addContribution(
        eventId: String,
        source: String,
        amount: Double,
        receivedAt: Long? = System.currentTimeMillis(),
        notes: String = ""
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addContribution(
                    ContributionEntity(
                        eventId = eventId,
                        source = source,
                        amount = amount,
                        receivedAt = receivedAt,
                        notes = notes
                    )
                )
            }.onFailure { _message.value = UiMessage.Error("Could not save the contribution: ${it.message}") }
        }
    }

    /** Flips a pledge to received, or back — the correction path for a tap on the wrong row. */
    fun setContributionReceived(contribution: ContributionEntity, received: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.updateContribution(
                    contribution.copy(
                        receivedAt = if (received) contribution.receivedAt ?: System.currentTimeMillis() else null
                    )
                )
            }.onFailure { _message.value = UiMessage.Error("Could not update the contribution: ${it.message}") }
        }
    }

    fun deleteContribution(contribution: ContributionEntity) {
        viewModelScope.launch { repository.deleteContribution(contribution) }
    }

    // --- Recurring expenses ---------------------------------------------------------

    /**
     * Sets up a cost that comes back.
     *
     * The first charge is placed by [RecurringExpenses.firstDueDate] rather than taken
     * from [startDate] directly: a rule added today for a payment that went out last week
     * must not immediately invent that payment. It is then materialised straight away, so
     * a rule starting today produces its first expense while the user is still looking at
     * the screen instead of appearing tomorrow morning.
     */
    fun addRecurringRule(
        eventId: String,
        title: String,
        category: String,
        amount: Double,
        recurrence: Recurrence,
        startDate: Long,
        vendor: String = "",
        until: Long? = null
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addRecurringRule(
                    RecurringExpenseEntity(
                        eventId = eventId,
                        title = title,
                        category = category,
                        vendor = vendor,
                        amount = amount,
                        frequency = recurrence.name,
                        nextDueDate = RecurringExpenses.firstDueDate(recurrence, startDate),
                        until = until
                    )
                )
                repository.materializeRecurring()
            }.onFailure { _message.value = UiMessage.Error("Could not save the recurring expense: ${it.message}") }
        }
    }

    /** Pauses or resumes a rule. A paused rule stops charging but stays on screen. */
    fun setRecurringActive(rule: RecurringExpenseEntity, active: Boolean) {
        viewModelScope.launch {
            runCatching {
                // Resuming moves the rule to its next *future* occurrence rather than
                // catching up. A rule paused for six weeks must not wake up owing six
                // weeks of back charges, and switching one back on should not put an
                // expense on the screen the same second — the user asked for it to run
                // again, not for it to bill them now.
                val resumed =
                    if (active) rule.copy(
                        active = true,
                        nextDueDate = RecurringExpenses.firstDueDate(
                            Recurrence.fromName(rule.frequency),
                            rule.nextDueDate
                        )
                    )
                    else rule.copy(active = false)
                repository.updateRecurringRule(resumed)
            }.onFailure { _message.value = UiMessage.Error("Could not update the recurring expense: ${it.message}") }
        }
    }

    fun deleteRecurringRule(rule: RecurringExpenseEntity) {
        viewModelScope.launch { repository.deleteRecurringRule(rule) }
    }

    /** Applied by the paywall today; by the billing client once purchases are live. */
    fun applyPurchase(newPlan: Plan) = entitlements.applyPurchase(newPlan)

    companion object {
        /** Before the first emission the remaining count is unknown, so show no hint. */
        const val FREE_EVENTS_UNKNOWN = -1

        fun factory(
            repository: EventRepository,
            entitlements: Entitlements,
            alerts: BudgetAlertPublisher = BudgetAlertPublisher.None
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { EventViewModel(repository, entitlements, alerts) }
        }
    }
}
