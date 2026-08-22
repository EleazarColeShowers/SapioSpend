package com.el.sapiospend.domain.template

import java.util.UUID

/**
 * One row of a custom plan while it is still being typed.
 *
 * The amount stays a String because a half-typed "25" and an empty field are different
 * things to the person typing, and both round-trip badly through Double. [CustomPlan]
 * turns these into real figures only at the point they are needed.
 *
 * The id exists so Compose can key the rows: without it, deleting the second of five
 * rows shifts every field's state up one and the user watches their typing move.
 */
data class CustomCategoryInput(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val amount: String = ""
)

/**
 * A budget the user builds themselves: their own categories, their own naira figures.
 *
 * Templates answer "how is a wedding usually split?". This answers "here is exactly what
 * I am buying" — a new apartment, a laptop, a term of school fees — which is the case no
 * fixed catalogue can cover. The output is the same [CategoryAmount] list a template
 * produces, so everything downstream (budget lines, planned-vs-actual, exports) is
 * unchanged by which route the plan came from.
 */
object CustomPlan {

    /** Enough for a full house fit-out; past this the list stops being scannable. */
    const val MAX_CATEGORIES = 40

    /** How many blank rows a fresh custom plan opens with. */
    const val INITIAL_ROWS = 3

    fun blankRows(count: Int = INITIAL_ROWS): List<CustomCategoryInput> =
        List(count) { CustomCategoryInput() }

    /**
     * The rows that are actually worth saving, as concrete amounts.
     *
     * Blank and zero rows are dropped rather than saved empty — a plan line of ₦0 shows
     * up in every breakdown and report as a category nobody funded. Two rows naming the
     * same category are merged instead of both being written, since analytics groups by
     * category name anyway and a split line would only ever be displayed added together.
     */
    fun linesOf(inputs: List<CustomCategoryInput>): List<CategoryAmount> {
        val merged = LinkedHashMap<String, CategoryAmount>()
        inputs.forEach { input ->
            val name = input.name.trim()
            val amount = input.amount.toDoubleOrNull() ?: 0.0
            if (name.isEmpty() || amount <= 0) return@forEach

            val key = name.lowercase()
            val existing = merged[key]
            merged[key] = existing?.copy(amount = existing.amount + amount)
                ?: CategoryAmount(name, amount)
        }
        return merged.values.toList()
    }

    /** What the categories add up to — the figure the user is checking against the budget. */
    fun plannedTotal(inputs: List<CustomCategoryInput>): Double =
        linesOf(inputs).sumOf { it.amount }

    /**
     * Budget minus what has been allocated. Negative once the categories overshoot, which
     * is allowed: catching an overshoot is the point of showing the number at all.
     */
    fun unallocated(budget: Double, inputs: List<CustomCategoryInput>): Double =
        budget - plannedTotal(inputs)
}
