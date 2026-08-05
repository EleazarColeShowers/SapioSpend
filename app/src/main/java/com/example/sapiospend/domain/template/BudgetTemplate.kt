package com.example.sapiospend.domain.template

/** One category's share of the total budget, expressed as a fraction of 1.0. */
data class TemplateAllocation(val category: String, val share: Double)

/** A category with a concrete naira figure, produced by applying a template to a budget. */
data class CategoryAmount(val category: String, val amount: Double)

/**
 * A starting budget breakdown for a kind of event.
 *
 * Shares rather than fixed amounts, so one template works for a ₦500,000 birthday and a
 * ₦20,000,000 wedding. This is also the structure an AI estimate would eventually
 * produce — same output shape, different way of arriving at the numbers.
 */
data class BudgetTemplate(
    val id: String,
    val name: String,
    val eventType: String,
    val description: String,
    val allocations: List<TemplateAllocation>
) {
    /**
     * Splits [totalBudget] across the categories.
     *
     * Rounding to whole naira loses fractions, so the final category absorbs whatever
     * is left over. Without that the planned lines would quietly fail to add up to the
     * budget they were derived from, and every variance figure downstream would be off.
     */
    fun allocate(totalBudget: Double): List<CategoryAmount> {
        if (allocations.isEmpty() || totalBudget <= 0) return emptyList()

        val head = allocations.dropLast(1).map { allocation ->
            CategoryAmount(allocation.category, kotlin.math.floor(totalBudget * allocation.share))
        }
        val remainder = totalBudget - head.sumOf { it.amount }
        return head + CategoryAmount(allocations.last().category, remainder)
    }
}
