package com.el.sapiospend.ui.text

import androidx.annotation.StringRes
import com.el.sapiospend.R
import com.el.sapiospend.domain.budget.BudgetDirection

/**
 * The words a budget is described in, chosen by which way its money moves.
 *
 * `strings.xml` already fixes one noun per concept so that "event", "budget" and "plan"
 * cannot come back as three names for the same thing. This is the same discipline
 * applied to the one place where a budget genuinely needs a second set of nouns: a
 * savings goal does not have expenses, it has contributions, and its total is not money
 * to spend but a target to reach.
 *
 * Resource ids rather than literals, and one object rather than a conditional at each
 * call site, so a screen asks for `words.spent` and can never accidentally pair
 * "Contributions" with "Remaining". Adding a third direction later is a case here, not a
 * hunt through the screens.
 */
data class BudgetWords(
    // --- The overview figures ---
    @get:StringRes val total: Int,
    /**
     * The label on the field the total is typed into, which carries the currency symbol
     * and so is a separate string from [total] rather than the same one reused.
     */
    @get:StringRes val totalField: Int,
    @get:StringRes val spent: Int,
    @get:StringRes val remaining: Int,
    /** The compact form of [remaining], for the home cards. */
    @get:StringRes val left: Int,
    /** "%1$d%% of the total used" / "...of the target reached" */
    @get:StringRes val progressPercent: Int,

    // --- The list of entries ---
    @get:StringRes val entries: Int,
    @get:StringRes val entriesCount: Int,
    /** Takes the visible count and the total count. */
    @get:StringRes val entriesNarrowed: Int,
    @get:StringRes val emptyTitle: Int,
    @get:StringRes val emptySubtitle: Int,
    @get:StringRes val searchHint: Int,

    // --- The form behind + ---
    @get:StringRes val addEntry: Int,
    @get:StringRes val formTitleNew: Int,
    @get:StringRes val formTitleEdit: Int,
    @get:StringRes val formSubtitleNew: Int,
    @get:StringRes val formSubtitleEdit: Int,
    @get:StringRes val titleField: Int,
    @get:StringRes val titleFieldHint: Int,
    @get:StringRes val amountHelp: Int,
    @get:StringRes val vendorField: Int,
    @get:StringRes val dateField: Int,
    @get:StringRes val saveEntry: Int,
    @get:StringRes val deleteEntry: Int,
    @get:StringRes val deleteEntryTitle: Int,

    // --- The breakdown ---
    @get:StringRes val categoriesTitle: Int,
    @get:StringRes val categoriesEdit: Int,
    @get:StringRes val categoriesSet: Int,
    @get:StringRes val categoriesHint: Int,
    @get:StringRes val categoriesEmptyPrompt: Int,

    /**
     * Whether the sections that only make sense for money going out should be drawn at
     * all. Funding asks who is paying for this and payment status asks how much of a
     * bill has been settled; neither is a question a savings goal has an answer to, and
     * showing them is how the screen starts contradicting its own vocabulary.
     */
    val showsFundingAndPayments: Boolean
) {
    companion object {

        fun of(direction: BudgetDirection): BudgetWords =
            if (direction.isSaving) SAVING else SPENDING

        val SPENDING = BudgetWords(
            total = R.string.label_total,
            totalField = R.string.field_total,
            spent = R.string.label_spent,
            remaining = R.string.label_remaining,
            left = R.string.label_left,
            progressPercent = R.string.progress_percent_used,
            entries = R.string.entries_expenses,
            entriesCount = R.string.entries_expenses_count,
            entriesNarrowed = R.string.entries_expenses_narrowed,
            emptyTitle = R.string.entry_empty_expenses,
            emptySubtitle = R.string.entry_empty_expenses_sub,
            searchHint = R.string.entry_search_expenses,
            addEntry = R.string.entry_add_expense,
            formTitleNew = R.string.entry_form_new_expense,
            formTitleEdit = R.string.entry_form_edit_expense,
            formSubtitleNew = R.string.entry_form_new_expense_sub,
            formSubtitleEdit = R.string.entry_form_edit_sub,
            titleField = R.string.entry_title_expense,
            titleFieldHint = R.string.entry_title_expense_hint,
            amountHelp = R.string.entry_amount_help_expense,
            vendorField = R.string.entry_vendor_expense,
            dateField = R.string.entry_date_expense,
            saveEntry = R.string.entry_save_expense,
            deleteEntry = R.string.entry_delete_expense,
            deleteEntryTitle = R.string.entry_delete_expense_title,
            categoriesTitle = R.string.categories_title,
            categoriesEdit = R.string.categories_edit,
            categoriesSet = R.string.categories_set,
            categoriesHint = R.string.categories_hint,
            categoriesEmptyPrompt = R.string.categories_empty_prompt,
            showsFundingAndPayments = true
        )

        val SAVING = BudgetWords(
            total = R.string.label_target,
            totalField = R.string.field_target,
            spent = R.string.label_saved,
            remaining = R.string.label_still_to_go,
            left = R.string.label_to_go,
            progressPercent = R.string.progress_percent_reached,
            entries = R.string.entries_contributions,
            entriesCount = R.string.entries_contributions_count,
            entriesNarrowed = R.string.entries_contributions_narrowed,
            emptyTitle = R.string.entry_empty_contributions,
            emptySubtitle = R.string.entry_empty_contributions_sub,
            searchHint = R.string.entry_search_contributions,
            addEntry = R.string.entry_add_contribution,
            formTitleNew = R.string.entry_form_new_contribution,
            formTitleEdit = R.string.entry_form_edit_contribution,
            formSubtitleNew = R.string.entry_form_new_contribution_sub,
            formSubtitleEdit = R.string.entry_form_edit_sub,
            titleField = R.string.entry_title_contribution,
            titleFieldHint = R.string.entry_title_contribution_hint,
            amountHelp = R.string.entry_amount_help_contribution,
            vendorField = R.string.entry_vendor_contribution,
            dateField = R.string.entry_date_contribution,
            saveEntry = R.string.entry_save_contribution,
            deleteEntry = R.string.entry_delete_contribution,
            deleteEntryTitle = R.string.entry_delete_contribution_title,
            categoriesTitle = R.string.categories_sources_title,
            categoriesEdit = R.string.categories_sources_edit,
            categoriesSet = R.string.categories_sources_set,
            categoriesHint = R.string.categories_sources_hint,
            categoriesEmptyPrompt = R.string.categories_sources_empty_prompt,
            showsFundingAndPayments = false
        )
    }
}
