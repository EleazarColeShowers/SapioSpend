package com.el.sapiospend.fake

import com.el.sapiospend.data.local.BudgetLineDao
import com.el.sapiospend.data.local.BudgetLineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** In-memory BudgetLineDao used only in unit tests. */
class FakeBudgetLineDao(private val db: FakeDatabase) : BudgetLineDao {

    override suspend fun insertAll(lines: List<BudgetLineEntity>) {
        db.budgetLines.value = db.budgetLines.value + lines
    }

    override fun getAllBudgetLines(): Flow<List<BudgetLineEntity>> =
        db.budgetLines.map { list -> list.filter { it.deletedAt == null } }

    override fun getBudgetLinesForEvent(eventId: String): Flow<List<BudgetLineEntity>> =
        db.budgetLines.map { list -> list.filter { it.eventId == eventId && it.deletedAt == null } }

    override suspend fun upsertAll(lines: List<BudgetLineEntity>) {
        val incoming = lines.associateBy { it.id }
        val updated = db.budgetLines.value.map { incoming[it.id] ?: it }
        val existingIds = db.budgetLines.value.mapTo(mutableSetOf()) { it.id }
        db.budgetLines.value = updated + lines.filterNot { it.id in existingIds }
    }

    override suspend fun budgetLinesFor(eventId: String): List<BudgetLineEntity> =
        db.budgetLines.value.filter { it.eventId == eventId && it.deletedAt == null }

    override suspend fun markBudgetLinesDeleted(lineIds: List<String>, now: Long) {
        db.budgetLines.value = db.budgetLines.value.map {
            if (it.id in lineIds) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }

    override suspend fun markBudgetLineDeleted(lineId: String, now: Long) {
        db.budgetLines.value = db.budgetLines.value.map {
            if (it.id == lineId) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }
}
