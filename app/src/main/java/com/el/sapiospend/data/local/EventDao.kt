package com.el.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Insert
    suspend fun insertBudgetLines(lines: List<BudgetLineEntity>)

    /**
     * Creating an event from a template writes the event and its planned allocations
     * together — a half-applied template would show a budget with no plan behind it.
     */
    @Transaction
    suspend fun insertEventWithBudgetLines(event: EventEntity, lines: List<BudgetLineEntity>) {
        insertEvent(event)
        if (lines.isNotEmpty()) insertBudgetLines(lines)
    }

    @Update
    suspend fun updateEvent(event: EventEntity)

    // Every read filters tombstones, so a soft-deleted event is invisible to the UI
    // while its row survives for a future sync to propagate.
    @Query("SELECT * FROM events WHERE deletedAt IS NULL ORDER BY dateCreated DESC")
    fun getAllEvents(): Flow<List<EventEntity>>

    // A one-shot read rather than a Flow: the free-plan check must see the true count at
    // the moment of the insert, not whatever a UI-scoped flow last emitted.
    @Query("SELECT COUNT(*) FROM events WHERE deletedAt IS NULL")
    suspend fun countActiveEvents(): Int

    /**
     * Tombstones an event and everything hanging off it in one transaction. The FK
     * CASCADE does not fire here — nothing is actually deleted — so the children have
     * to be marked explicitly or they would linger as orphaned live rows.
     */
    @Transaction
    suspend fun softDeleteEventCascading(eventId: String, now: Long) {
        markExpensesDeletedForEvent(eventId, now)
        markBudgetLinesDeletedForEvent(eventId, now)
        markEventDeleted(eventId, now)
    }

    @Query("UPDATE events SET deletedAt = :now, updatedAt = :now WHERE id = :eventId")
    suspend fun markEventDeleted(eventId: String, now: Long)

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun markExpensesDeletedForEvent(eventId: String, now: Long)

    @Query("UPDATE budget_lines SET deletedAt = :now, updatedAt = :now WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun markBudgetLinesDeletedForEvent(eventId: String, now: Long)
}
