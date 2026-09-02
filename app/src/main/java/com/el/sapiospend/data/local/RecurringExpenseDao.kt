package com.el.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {

    @Insert
    suspend fun insert(rule: RecurringExpenseEntity)

    @Update
    suspend fun update(rule: RecurringExpenseEntity)

    @Query("SELECT * FROM recurring_expenses WHERE deletedAt IS NULL ORDER BY nextDueDate ASC")
    fun getAllRules(): Flow<List<RecurringExpenseEntity>>

    @Query("SELECT * FROM recurring_expenses WHERE eventId = :eventId AND deletedAt IS NULL ORDER BY nextDueDate ASC")
    fun getRulesForEvent(eventId: String): Flow<List<RecurringExpenseEntity>>

    // A one-shot read rather than the flow above: materialisation runs from the daily
    // alarm and from app start, neither of which is collecting anything.
    @Query("SELECT * FROM recurring_expenses WHERE deletedAt IS NULL AND active = 1")
    suspend fun activeRules(): List<RecurringExpenseEntity>

    @Query("UPDATE recurring_expenses SET deletedAt = :now, updatedAt = :now WHERE id = :ruleId")
    suspend fun markRuleDeleted(ruleId: String, now: Long)

    @Insert
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateAll(rules: List<RecurringExpenseEntity>)

    /**
     * Writes the expenses a set of rules has come due for, and the rules' advanced due
     * dates, together.
     *
     * In one transaction because the advanced date is the only record that an occurrence
     * has been charged: writing the expenses and failing before the rules would charge
     * the same week again on the next tick, and doing it the other way round would skip
     * a payment silently.
     */
    @Transaction
    suspend fun materialize(expenses: List<ExpenseEntity>, advanced: List<RecurringExpenseEntity>) {
        if (expenses.isNotEmpty()) insertExpenses(expenses)
        if (advanced.isNotEmpty()) updateAll(advanced)
    }
}
