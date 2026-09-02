package com.el.sapiospend.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A cost that comes back — weekly venue hire, a monthly retainer, rent out of a salary
 * budget.
 *
 * The rule is stored, not the occurrences: writing a year of future expenses at setup
 * time would have every total on every screen counting money nobody has spent yet.
 * Instead [nextDueDate] walks forward and one expense is materialised as each date
 * arrives — see [com.el.sapiospend.domain.recurring.RecurringExpenses].
 *
 * That date is also the deduplication key. It advances in the same transaction that
 * writes the expense, so a tick that fires twice — an alarm and an app launch in the
 * same minute — cannot produce the same charge twice.
 */
@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RecurringExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(index = true) val eventId: String,
    val title: String,
    val category: String,
    val vendor: String = "",
    val amount: Double,
    /** Name of a [com.el.sapiospend.domain.recurring.Recurrence]; stored as text so
     *  reordering the enum cannot re-interpret a saved rule. */
    val frequency: String,
    /** The next occurrence this rule owes an expense for. */
    val nextDueDate: Long,
    /**
     * When to stop, inclusive, or null to run until the user switches it off. An event
     * with an end date gets this set from it, so a wedding's weekly payments do not
     * carry on into the following year.
     */
    val until: Long? = null,
    val active: Boolean = true,
    val dateCreated: Long = System.currentTimeMillis(),
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
