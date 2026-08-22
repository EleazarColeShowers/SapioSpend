package com.el.sapiospend.data.local

// Keeps the ViewModel from importing Room directly — makes it easier to swap
// the data source in tests without touching the ViewModel. When a server is added,
// a remote data source plugs in here and nothing above this layer changes.
class EventRepository(
    private val eventDao: EventDao,
    private val expenseDao: ExpenseDao,
    private val budgetLineDao: BudgetLineDao,
    // Injectable so tests can pin the tombstone timestamp instead of racing the clock.
    private val now: () -> Long = System::currentTimeMillis
) {
    val events = eventDao.getAllEvents()
    val allExpenses = expenseDao.getAllExpenses()
    val allBudgetLines = budgetLineDao.getAllBudgetLines()

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
}
