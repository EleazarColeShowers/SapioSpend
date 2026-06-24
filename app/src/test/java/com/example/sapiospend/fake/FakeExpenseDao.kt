package com.example.sapiospend.fake

import com.example.sapiospend.data.local.ExpenseDao
import com.example.sapiospend.data.local.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory ExpenseDao used only in unit tests. */
class FakeExpenseDao : ExpenseDao {

    private val store = mutableListOf<ExpenseEntity>()
    private val allFlow = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    private var nextId = 1

    override suspend fun insertExpense(expense: ExpenseEntity) {
        store.add(expense.copy(id = nextId++))
        allFlow.value = store.toList()
    }

    override suspend fun deleteExpense(expense: ExpenseEntity) {
        store.removeAll { it.id == expense.id }
        allFlow.value = store.toList()
    }

    override fun getExpensesForEvent(eventId: Int): Flow<List<ExpenseEntity>> =
        MutableStateFlow(store.filter { it.eventId == eventId })

    override fun getAllExpenses(): Flow<List<ExpenseEntity>> = allFlow
}
