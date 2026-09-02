package com.el.sapiospend.data.local

import com.el.sapiospend.domain.recurring.Materialization
import com.el.sapiospend.domain.recurring.RecurringExpenses

// Keeps the ViewModel from importing Room directly — makes it easier to swap
// the data source in tests without touching the ViewModel. When a server is added,
// a remote data source plugs in here and nothing above this layer changes.
class EventRepository(
    private val eventDao: EventDao,
    private val expenseDao: ExpenseDao,
    private val budgetLineDao: BudgetLineDao,
    private val contributionDao: ContributionDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    // Injectable so tests can pin the tombstone timestamp instead of racing the clock.
    private val now: () -> Long = System::currentTimeMillis
) {
    val events = eventDao.getAllEvents()
    val allExpenses = expenseDao.getAllExpenses()
    val allBudgetLines = budgetLineDao.getAllBudgetLines()
    val allContributions = contributionDao.getAllContributions()
    val allRecurringRules = recurringExpenseDao.getAllRules()

    suspend fun activeEventCount(): Int = eventDao.countActiveEvents()

    suspend fun addEvent(event: EventEntity, budgetLines: List<BudgetLineEntity> = emptyList()) =
        eventDao.insertEventWithBudgetLines(event, budgetLines)

    suspend fun updateEvent(event: EventEntity) =
        eventDao.updateEvent(event.copy(updatedAt = now()))

    suspend fun deleteEvent(event: EventEntity) =
        eventDao.softDeleteEventCascading(event.id, now())

    suspend fun budgetLinesFor(eventId: String): List<BudgetLineEntity> =
        budgetLineDao.budgetLinesFor(eventId)

    /**
     * Saves an edited plan: the lines the user kept, and tombstones for the ones they
     * removed. The timestamp is stamped here rather than in the editor so every row of
     * one save shares an instant — a sync reconciling two devices orders by updatedAt,
     * and rows of the same edit drifting apart by a millisecond would let half an edit
     * win over the other half.
     */
    suspend fun savePlan(lines: List<BudgetLineEntity>, removedIds: List<String>) {
        val stamp = now()
        budgetLineDao.savePlan(lines.map { it.copy(updatedAt = stamp) }, removedIds, stamp)
    }

    suspend fun addExpense(expense: ExpenseEntity) = expenseDao.insertExpense(expense)

    suspend fun updateExpense(expense: ExpenseEntity) =
        expenseDao.updateExpense(expense.copy(updatedAt = now()))

    suspend fun deleteExpense(expense: ExpenseEntity) =
        expenseDao.markExpenseDeleted(expense.id, now())

    // --- Funding --------------------------------------------------------------------

    suspend fun addContribution(contribution: ContributionEntity) =
        contributionDao.insert(contribution)

    suspend fun updateContribution(contribution: ContributionEntity) =
        contributionDao.update(contribution.copy(updatedAt = now()))

    suspend fun deleteContribution(contribution: ContributionEntity) =
        contributionDao.markContributionDeleted(contribution.id, now())

    // --- Recurring expenses ---------------------------------------------------------

    suspend fun addRecurringRule(rule: RecurringExpenseEntity) =
        recurringExpenseDao.insert(rule)

    suspend fun updateRecurringRule(rule: RecurringExpenseEntity) =
        recurringExpenseDao.update(rule.copy(updatedAt = now()))

    suspend fun deleteRecurringRule(rule: RecurringExpenseEntity) =
        recurringExpenseDao.markRuleDeleted(rule.id, now())

    /**
     * Writes the expenses every active rule has come due for.
     *
     * Reads the rules one-shot rather than from [allRecurringRules]: this runs from the
     * daily alarm and from app start, where nothing is collecting a flow, and it has to
     * see the rows as they stand at that instant rather than whatever a UI-scoped flow
     * last emitted — a stale read here charges an occurrence twice.
     *
     * Returns what it wrote so a caller can report it; safe to call repeatedly, since a
     * rule whose due date has not arrived produces nothing.
     */
    suspend fun materializeRecurring(at: Long = now()): Materialization {
        val result = RecurringExpenses.materialize(recurringExpenseDao.activeRules(), at)
        if (!result.isEmpty) recurringExpenseDao.materialize(result.expenses, result.rules)
        return result
    }
}
