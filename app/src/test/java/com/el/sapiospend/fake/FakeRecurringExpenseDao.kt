package com.el.sapiospend.fake

import com.el.sapiospend.data.local.ExpenseEntity
import com.el.sapiospend.data.local.RecurringExpenseDao
import com.el.sapiospend.data.local.RecurringExpenseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** In-memory RecurringExpenseDao used only in unit tests. */
class FakeRecurringExpenseDao(private val db: FakeDatabase) : RecurringExpenseDao {

    override suspend fun insert(rule: RecurringExpenseEntity) {
        db.recurringRules.value = db.recurringRules.value + rule
    }

    override suspend fun update(rule: RecurringExpenseEntity) {
        db.recurringRules.value = db.recurringRules.value.map { if (it.id == rule.id) rule else it }
    }

    override fun getAllRules(): Flow<List<RecurringExpenseEntity>> =
        db.recurringRules.map { list -> list.filter { it.deletedAt == null }.sortedBy { it.nextDueDate } }

    override fun getRulesForEvent(eventId: String): Flow<List<RecurringExpenseEntity>> =
        db.recurringRules.map { list ->
            list.filter { it.eventId == eventId && it.deletedAt == null }.sortedBy { it.nextDueDate }
        }

    override suspend fun activeRules(): List<RecurringExpenseEntity> =
        db.recurringRules.value.filter { it.deletedAt == null && it.active }

    override suspend fun markRuleDeleted(ruleId: String, now: Long) {
        db.recurringRules.value = db.recurringRules.value.map {
            if (it.id == ruleId) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }

    override suspend fun insertExpenses(expenses: List<ExpenseEntity>) {
        db.expenses.value = db.expenses.value + expenses
    }

    override suspend fun updateAll(rules: List<RecurringExpenseEntity>) {
        val incoming = rules.associateBy { it.id }
        db.recurringRules.value = db.recurringRules.value.map { incoming[it.id] ?: it }
    }
}
