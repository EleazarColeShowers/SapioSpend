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
        markContributionsDeletedForEvent(eventId, now)
        markRecurringDeletedForEvent(eventId, now)
        markEventDeleted(eventId, now)
    }

    /**
     * Re-denominates one budget: every figure it owns is multiplied by [factor] and the
     * budget is recorded as being in [toCode] from now on.
     *
     * One transaction, because a budget whose total moved to dollars while its expenses
     * stayed in naira is not a half-finished change — it is a budget reporting a
     * thousandfold overspend. The user is warned before this runs and it is the one
     * place in the app that rewrites recorded figures.
     *
     * In SQL rather than by reading the rows and writing them back: an event can carry
     * hundreds of expenses, and this way the whole rewrite is four statements that never
     * leave the database.
     */
    @Transaction
    suspend fun redenominateEvent(eventId: String, factor: Double, toCode: String, now: Long) {
        scaleExpensesForEvent(eventId, factor, now)
        scaleBudgetLinesForEvent(eventId, factor, now)
        scaleContributionsForEvent(eventId, factor, now)
        scaleRecurringForEvent(eventId, factor, now)
        setEventCurrency(eventId, toCode, now)
    }

    @Query("UPDATE events SET currencyCode = :toCode, updatedAt = :now WHERE id = :eventId")
    suspend fun setEventCurrency(eventId: String, toCode: String, now: Long)

    @Query(
        "UPDATE expenses SET amount = amount * :factor, amountPaid = amountPaid * :factor, " +
            "updatedAt = :now WHERE eventId = :eventId AND deletedAt IS NULL"
    )
    suspend fun scaleExpensesForEvent(eventId: String, factor: Double, now: Long)

    @Query(
        "UPDATE budget_lines SET plannedAmount = plannedAmount * :factor, updatedAt = :now " +
            "WHERE eventId = :eventId AND deletedAt IS NULL"
    )
    suspend fun scaleBudgetLinesForEvent(eventId: String, factor: Double, now: Long)

    @Query(
        "UPDATE contributions SET amount = amount * :factor, updatedAt = :now " +
            "WHERE eventId = :eventId AND deletedAt IS NULL"
    )
    suspend fun scaleContributionsForEvent(eventId: String, factor: Double, now: Long)

    @Query(
        "UPDATE recurring_expenses SET amount = amount * :factor, updatedAt = :now " +
            "WHERE eventId = :eventId AND deletedAt IS NULL"
    )
    suspend fun scaleRecurringForEvent(eventId: String, factor: Double, now: Long)

    @Query("UPDATE events SET deletedAt = :now, updatedAt = :now WHERE id = :eventId")
    suspend fun markEventDeleted(eventId: String, now: Long)

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun markExpensesDeletedForEvent(eventId: String, now: Long)

    @Query("UPDATE budget_lines SET deletedAt = :now, updatedAt = :now WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun markBudgetLinesDeletedForEvent(eventId: String, now: Long)

    @Query("UPDATE contributions SET deletedAt = :now, updatedAt = :now WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun markContributionsDeletedForEvent(eventId: String, now: Long)

    // A rule left alive against a deleted event would keep materialising expenses into
    // it — invisible on screen, and back on the totals the moment the event is restored.
    @Query("UPDATE recurring_expenses SET deletedAt = :now, updatedAt = :now WHERE eventId = :eventId AND deletedAt IS NULL")
    suspend fun markRecurringDeletedForEvent(eventId: String, now: Long)
}
