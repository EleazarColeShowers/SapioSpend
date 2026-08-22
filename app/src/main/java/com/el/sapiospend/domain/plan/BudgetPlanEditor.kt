package com.el.sapiospend.domain.plan

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.domain.template.CustomCategoryInput
import com.el.sapiospend.domain.template.CustomPlan
import com.el.sapiospend.util.formatAmountInput

/** What one save of the plan editor asks the database to do. */
data class PlanEdit(
    /** Rows to write — existing lines keep their id and are updated in place. */
    val lines: List<BudgetLineEntity>,
    /** Lines that were on the plan before the edit and are not on it now. */
    val removedIds: List<String>
)

/**
 * Turns budget lines into editable rows and back again.
 *
 * The whole trick is that a row's id **is** its budget line id. Loading a plan seeds each
 * row with the id of the line it came from, so saving can upsert — an edited category
 * updates the row the user was looking at instead of tombstoning it and inserting a
 * near-identical one. Rows the user adds start life with a fresh UUID, which becomes the
 * id of a new line; rows they clear out fall into [PlanEdit.removedIds].
 *
 * Kept free of Room and Compose so the id bookkeeping — the part that is easy to get
 * subtly wrong and invisible when it is — is covered by plain JVM tests.
 */
object BudgetPlanEditor {

    /** Same ceiling as a plan built at creation time; the two edit the same table. */
    const val MAX_LINES = CustomPlan.MAX_CATEGORIES

    /**
     * Existing lines as editable rows, biggest allocation first — the order someone
     * reviewing a budget reads it in, and stable for as long as the screen is open
     * because it is computed once on load rather than re-sorted as amounts are typed.
     */
    fun rowsFrom(lines: List<BudgetLineEntity>): List<CustomCategoryInput> =
        lines.sortedByDescending { it.plannedAmount }
            .map { CustomCategoryInput(id = it.id, name = it.category, amount = it.plannedAmount.formatAmountInput()) }

    /**
     * What the rows on screen mean for storage.
     *
     * A row is kept when it names a category and carries a figure above zero. Blank and
     * zero rows are dropped rather than saved: a ₦0 allocation is not a plan, it is a
     * category that shows up empty in every breakdown, chart and export from then on.
     * Dropping one that used to exist is a deliberate removal, which is why the same
     * rule produces both halves of the edit.
     */
    fun edit(
        eventId: String,
        rows: List<CustomCategoryInput>,
        existing: List<BudgetLineEntity>,
        now: Long = System.currentTimeMillis()
    ): PlanEdit {
        val kept = rows.mapNotNull { row ->
            val name = row.name.trim()
            val amount = row.amount.toDoubleOrNull() ?: 0.0
            if (name.isEmpty() || amount <= 0) return@mapNotNull null
            BudgetLineEntity(
                id = row.id,
                eventId = eventId,
                category = name,
                plannedAmount = amount,
                updatedAt = now
            )
        }
        val keptIds = kept.mapTo(mutableSetOf()) { it.id }
        return PlanEdit(
            lines = kept,
            removedIds = existing.map { it.id }.filterNot { it in keptIds }
        )
    }

    /** What the rows currently add up to — the figure checked against the total budget. */
    fun plannedTotal(rows: List<CustomCategoryInput>): Double =
        rows.sumOf { row ->
            if (row.name.isBlank()) 0.0 else (row.amount.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
        }

    /**
     * Categories that have been spent against but were never planned for. Offered as
     * one-tap additions, because the plan a user most wants to write is usually the one
     * their own receipts have already named.
     */
    fun unplannedCategories(rows: List<CustomCategoryInput>, spentCategories: List<String>): List<String> {
        val planned = rows.mapTo(mutableSetOf()) { it.name.trim().lowercase() }
        return spentCategories.distinct().filterNot { it.trim().lowercase() in planned }
    }
}
