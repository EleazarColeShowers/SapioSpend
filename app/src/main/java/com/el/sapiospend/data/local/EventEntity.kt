package com.el.sapiospend.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    /** Heads to divide the spend by, or null when the user has not said. */
    val guestCount: Int? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    /** True once the budget is bounded at both ends — the case pacing maths needs. */
    val hasPeriod: Boolean get() = startDate != null && endDate != null

    /** Only a positive count divides; zero would make every per-head figure infinite. */
    val hasGuestCount: Boolean get() = (guestCount ?: 0) > 0
}

/** Stand-in owner for the single local user, replaced by a real account id later. */
object LocalOwner {
    const val ID = "local"
}
