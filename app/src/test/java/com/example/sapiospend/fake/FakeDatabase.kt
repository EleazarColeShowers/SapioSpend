package com.example.sapiospend.fake

import com.example.sapiospend.data.local.BudgetLineEntity
import com.example.sapiospend.data.local.EventEntity
import com.example.sapiospend.data.local.ExpenseEntity
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

    fun eventDao() = FakeEventDao(this)
    fun expenseDao() = FakeExpenseDao(this)
    fun budgetLineDao() = FakeBudgetLineDao(this)
}
