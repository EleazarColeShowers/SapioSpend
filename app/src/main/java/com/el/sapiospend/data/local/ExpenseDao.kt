package com.el.sapiospend.data.local

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

    /**
     * A logged expense is a record of something that happened, and people mistype the
     * amount, pick the wrong category, or log it against the wrong day. Correcting the
     * row is the honest fix — deleting and re-adding would lose its id, and with it any
     * chance of a future sync recognising the two as the same expense.
     */
    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE id = :expenseId")
    suspend fun markExpenseDeleted(expenseId: String, now: Long)
}
