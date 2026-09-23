package com.el.sapiospend.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.el.sapiospend.domain.budget.BudgetDirection
import com.el.sapiospend.settings.AppCurrency
import java.util.UUID

/**
 * Ids are client-generated UUIDs rather than autoincrementing ints so two devices can
 * create events offline without colliding — a prerequisite for any future sync.
 *
 * ownerId scopes every row to an account. Today there is only the local user, but the
 * column exists now so adding real accounts becomes a backfill, not a migration.
 *
 * deletedAt makes deletes soft. A hard delete is indistinguishable from "never existed"
 * when two devices reconcile, so rows are tombstoned and filtered out of every query.
 *
 * guestCount is nullable rather than zero-defaulted for the same reason the dates are:
 * "nobody counted" and "an event for nobody" are different facts, and cost per head is
 * only worth showing for the first of the two when it has actually been answered.
 *
 * direction says whether the total is money to spend or a target to reach. Held as the
 * enum's name rather than a boolean so a third kind of budget does not need a migration,
 * and defaulted to SPENDING because that is what every budget created before it existed
 * was.
 *
 * currencyCode is the currency this budget's own figures — its total, its expenses, its
 * plan, its funding — are recorded in. Null means the budget predates the column and is
 * therefore denominated in the app-wide base currency, which is exactly what it was
 * recorded in; that is a real answer rather than a missing one, which is why this is
 * nullable where moneyDirection is not. Read it through [currency].
 *
 * startDate/endDate bound the budget in time. Both are null for the open-ended events
 * the app shipped with, which is why they are nullable rather than defaulted to the
 * creation date — "no period" and "a period that happens to start today" produce very
 * different burn-rate advice, and the two must stay distinguishable.
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val budget: Double,
    val eventType: String = "General",
    /** [BudgetDirection] by name. Read it through [direction] rather than comparing strings. */
    val moneyDirection: String = BudgetDirection.DEFAULT.name,
    /** Heads to divide the spend by, or null when the user has not said. */
    val guestCount: Int? = null,
    /** [AppCurrency] by code, or null for a budget that follows the app-wide base. */
    val currencyCode: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    /** The typed form of [moneyDirection]. */
    val direction: BudgetDirection get() = BudgetDirection.fromName(moneyDirection)

    /**
     * The currency this budget's figures are in.
     *
     * [base] is what an unset [currencyCode] means — pass the app's base currency, which
     * is what the figures of a budget created before this column were recorded in.
     */
    fun currency(base: AppCurrency): AppCurrency = AppCurrency.fromCode(currencyCode, base)

    /** True when this budget's total is a target to reach rather than money to spend. */
    val isSavingsGoal: Boolean get() = direction.isSaving

    /** True once the budget is bounded at both ends — the case pacing maths needs. */
    val hasPeriod: Boolean get() = startDate != null && endDate != null

    /** Only a positive count divides; zero would make every per-head figure infinite. */
    val hasGuestCount: Boolean get() = (guestCount ?: 0) > 0
}

/** Stand-in owner for the single local user, replaced by a real account id later. */
object LocalOwner {
    const val ID = "local"
}
