package com.example.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetLineDao {

    @Insert
    suspend fun insertAll(lines: List<BudgetLineEntity>)

    @Query("SELECT * FROM budget_lines WHERE deletedAt IS NULL")
    fun getAllBudgetLines(): Flow<List<BudgetLineEntity>>

    @Query("SELECT * FROM budget_lines WHERE eventId = :eventId AND deletedAt IS NULL")
    fun getBudgetLinesForEvent(eventId: String): Flow<List<BudgetLineEntity>>

    @Query("UPDATE budget_lines SET deletedAt = :now, updatedAt = :now WHERE id = :lineId")
    suspend fun markBudgetLineDeleted(lineId: String, now: Long)
}
