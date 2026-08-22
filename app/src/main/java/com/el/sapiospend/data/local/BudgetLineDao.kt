package com.el.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetLineDao {

    @Insert
    suspend fun insertAll(lines: List<BudgetLineEntity>)

    /**
     * Editing a plan rewrites rows that already exist and adds the ones that don't, and
     * the editor cannot tell the two apart — it hands back whatever the user left on
     * screen. Upsert makes that one call instead of a read-then-branch.
     */
    @Upsert
    suspend fun upsertAll(lines: List<BudgetLineEntity>)

    @Query("SELECT * FROM budget_lines WHERE deletedAt IS NULL")
    fun getAllBudgetLines(): Flow<List<BudgetLineEntity>>

    @Query("SELECT * FROM budget_lines WHERE eventId = :eventId AND deletedAt IS NULL")
    fun getBudgetLinesForEvent(eventId: String): Flow<List<BudgetLineEntity>>

    // A one-shot read rather than the flow above: a save has to diff against the plan as
    // it stands in the database at that instant, not against whatever a UI-scoped flow
    // last emitted, or a line added on another screen would be tombstoned as "removed".
    @Query("SELECT * FROM budget_lines WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun budgetLinesFor(eventId: String): List<BudgetLineEntity>

    @Query("UPDATE budget_lines SET deletedAt = :now, updatedAt = :now WHERE id = :lineId")
    suspend fun markBudgetLineDeleted(lineId: String, now: Long)

    @Query("UPDATE budget_lines SET deletedAt = :now, updatedAt = :now WHERE id IN (:lineIds)")
    suspend fun markBudgetLinesDeleted(lineIds: List<String>, now: Long)

    /**
     * One save of a whole plan: the rows the user kept, and tombstones for the ones they
     * removed. In a transaction because a plan that is half-saved reads as a budget the
     * user did not write — deleted categories back from the dead, or a total that adds
     * up to something nobody typed.
     */
    @Transaction
    suspend fun savePlan(lines: List<BudgetLineEntity>, removedIds: List<String>, now: Long) {
        if (removedIds.isNotEmpty()) markBudgetLinesDeleted(removedIds, now)
        if (lines.isNotEmpty()) upsertAll(lines)
    }
}
