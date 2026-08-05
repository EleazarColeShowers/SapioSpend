package com.example.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    // Ordered newest-first so the most recently added expense appears at the top of the
    // list. dateCreated replaces the old id ordering — UUIDs carry no sequence.
    @Query("SELECT * FROM expenses WHERE eventId = :eventId AND deletedAt IS NULL ORDER BY dateCreated DESC")
    fun getExpensesForEvent(eventId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL ORDER BY dateCreated DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE id = :expenseId")
    suspend fun markExpenseDeleted(expenseId: String, now: Long)
}
