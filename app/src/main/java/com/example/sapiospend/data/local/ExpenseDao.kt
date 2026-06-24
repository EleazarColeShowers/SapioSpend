package com.example.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    // Ordered newest-first so the most recently added expense appears at the top of the list.
    @Query("SELECT * FROM expenses WHERE eventId = :eventId ORDER BY id DESC")
    fun getExpensesForEvent(eventId: Int): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY id DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
}
