package com.el.sapiospend.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

// eventId is indexed because every screen filters expenses by event — without it
// Room would scan the whole table on each load.
// The CASCADE is a backstop for referential integrity; the app deletes softly
// (see EventRepository), so in practice rows are tombstoned rather than removed.
@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(index = true) val eventId: String,
    val title: String,
    val category: String,
    /**
     * What the line costs in total — the committed amount, whether or not it has been
     * handed over yet. Against the budget this is the honest figure: a caterer booked
     * for ₦2m has spent that money as surely as one already paid.
     */
    val amount: Double,
    val notes: String = "",
    val dateCreated: Long = System.currentTimeMillis(),
    /** Who the money goes to. Blank for the expenses recorded before the field existed. */
    val vendor: String = "",
    /**
     * How much of [amount] has actually been handed over. A deposit is the partial case
     * and the reason this is an amount rather than a paid/unpaid flag: "₦2m booked,
     * ₦600k paid" is the sentence an event planner lives in, and a boolean cannot say it.
     */
    val amountPaid: Double = 0.0,
    /** When the balance falls due, or null for an expense with no deadline attached. */
    val dueDate: Long? = null,
    /**
     * Absolute path to the receipt image in the app's own files directory, or null.
     *
     * A path rather than the picked content:// URI: the grant behind that URI does not
     * survive a reboot, so a stored URI would show a receipt today and nothing next week.
     * See [com.el.sapiospend.receipt.ReceiptStore].
     */
    val receiptPath: String? = null,
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    /** Still owed on this line. Never negative — an overpayment is not a debt. */
    val outstanding: Double get() = (amount - amountPaid).coerceAtLeast(0.0)

    val isSettled: Boolean get() = amountPaid >= amount
}
