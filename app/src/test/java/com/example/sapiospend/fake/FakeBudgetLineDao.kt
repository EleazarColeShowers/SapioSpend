package com.example.sapiospend.fake

import com.example.sapiospend.data.local.BudgetLineDao
import com.example.sapiospend.data.local.BudgetLineEntity
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

    override suspend fun markBudgetLineDeleted(lineId: String, now: Long) {
        db.budgetLines.value = db.budgetLines.value.map {
            if (it.id == lineId) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }
}
