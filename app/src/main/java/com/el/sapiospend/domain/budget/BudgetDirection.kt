package com.el.sapiospend.domain.budget

/**
 * Which way the money moves through a budget.
 *
 * Almost every budget is money going out: a total, and expenses eating into it. A
 * savings goal is the same arithmetic run backwards — the total is a target, the
 * categories are where the money will come from, and each entry logged against one
 * brings the remaining figure *down* towards zero because the goal is getting closer.
 *
 * The maths was always right. What was missing was a word for it: the app called every
 * entry an expense, so somebody recording their monthly contribution to a savings goal
 * was asked to fill in a form headed "Record a spending item" with a payment status of
 * paid or unpaid. This enum is what lets the same screens say the other set of words.
 *
 * Stored on the event by [name], never by ordinal, so reordering this cannot
 * re-interpret a saved budget.
 */
enum class BudgetDirection {
    /** Money going out. A wedding, a salary month, a party. The default. */
    SPENDING,

    /** Money coming in towards a target. A savings goal. */
    SAVING;

    val isSaving: Boolean get() = this == SAVING

    companion object {
        val DEFAULT = SPENDING

        /** An unrecognised stored value falls back rather than throwing on a read. */
        fun fromName(name: String?): BudgetDirection =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
