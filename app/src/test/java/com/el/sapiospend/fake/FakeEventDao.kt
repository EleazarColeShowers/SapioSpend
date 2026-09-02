package com.el.sapiospend.fake

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.data.local.EventDao
import com.el.sapiospend.data.local.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** In-memory EventDao used only in unit tests. Mirrors the real DAO's tombstone filtering. */
class FakeEventDao(private val db: FakeDatabase) : EventDao {

    override suspend fun insertEvent(event: EventEntity) {
        db.events.value = db.events.value + event
    }

    override suspend fun insertBudgetLines(lines: List<BudgetLineEntity>) {
        db.budgetLines.value = db.budgetLines.value + lines
    }

    override suspend fun updateEvent(event: EventEntity) {
        db.events.value = db.events.value.map { if (it.id == event.id) event else it }
    }

    override fun getAllEvents(): Flow<List<EventEntity>> =
        db.events.map { list -> list.filter { it.deletedAt == null }.sortedByDescending { it.dateCreated } }

    override suspend fun countActiveEvents(): Int = db.events.value.count { it.deletedAt == null }

    override suspend fun markEventDeleted(eventId: String, now: Long) {
        db.events.value = db.events.value.map {
            if (it.id == eventId) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }

    override suspend fun markExpensesDeletedForEvent(eventId: String, now: Long) {
        db.expenses.value = db.expenses.value.map {
            if (it.eventId == eventId && it.deletedAt == null) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }

    override suspend fun markBudgetLinesDeletedForEvent(eventId: String, now: Long) {
        db.budgetLines.value = db.budgetLines.value.map {
            if (it.eventId == eventId && it.deletedAt == null) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }

    override suspend fun markContributionsDeletedForEvent(eventId: String, now: Long) {
        db.contributions.value = db.contributions.value.map {
            if (it.eventId == eventId && it.deletedAt == null) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }

    override suspend fun markRecurringDeletedForEvent(eventId: String, now: Long) {
        db.recurringRules.value = db.recurringRules.value.map {
            if (it.eventId == eventId && it.deletedAt == null) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }
}
