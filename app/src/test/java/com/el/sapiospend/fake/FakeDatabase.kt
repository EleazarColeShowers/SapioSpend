package com.el.sapiospend.fake

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.ContributionEntity
import com.el.sapiospend.data.local.EventEntity
import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.data.local.RecurringExpenseEntity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared in-memory storage behind the fake DAOs.
 *
 * The DAOs write to one store rather than each holding their own list, because a
 * cascading soft delete spans all three tables — separate stores would let a test pass
 * while the real cascade was broken.
 */
class FakeDatabase {
    val events = MutableStateFlow<List<EventEntity>>(emptyList())
    val expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val budgetLines = MutableStateFlow<List<BudgetLineEntity>>(emptyList())
    val contributions = MutableStateFlow<List<ContributionEntity>>(emptyList())
    val recurringRules = MutableStateFlow<List<RecurringExpenseEntity>>(emptyList())

    fun eventDao() = FakeEventDao(this)
    fun expenseDao() = FakeExpenseDao(this)
    fun budgetLineDao() = FakeBudgetLineDao(this)
    fun contributionDao() = FakeContributionDao(this)
    fun recurringExpenseDao() = FakeRecurringExpenseDao(this)

    /** The repository over this store, wired the way the app wires the real one. */
    fun repository(now: () -> Long = System::currentTimeMillis) =
        com.el.sapiospend.data.local.EventRepository(
            eventDao(), expenseDao(), budgetLineDao(), contributionDao(), recurringExpenseDao(), now
        )
}
