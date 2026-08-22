package com.el.sapiospend.fake

import com.el.sapiospend.data.local.ExpenseDao
import com.el.sapiospend.data.local.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** In-memory ExpenseDao used only in unit tests. */
class FakeExpenseDao(private val db: FakeDatabase) : ExpenseDao {

    override suspend fun insertExpense(expense: ExpenseEntity) {
        db.expenses.value = db.expenses.value + expense
    }

    override fun getExpensesForEvent(eventId: String): Flow<List<ExpenseEntity>> =
        db.expenses.map { list ->
            list.filter { it.eventId == eventId && it.deletedAt == null }.sortedByDescending { it.dateCreated }
        }

    override fun getAllExpenses(): Flow<List<ExpenseEntity>> =
        db.expenses.map { list -> list.filter { it.deletedAt == null }.sortedByDescending { it.dateCreated } }

    override suspend fun updateExpense(expense: ExpenseEntity) {
        db.expenses.value = db.expenses.value.map { if (it.id == expense.id) expense else it }
    }

    override suspend fun markExpenseDeleted(expenseId: String, now: Long) {
        db.expenses.value = db.expenses.value.map {
            if (it.id == expenseId) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }
}
