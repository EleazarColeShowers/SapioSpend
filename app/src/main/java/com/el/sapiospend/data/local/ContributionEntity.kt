package com.el.sapiospend.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Money coming *in* against an event — a client deposit, a sponsor, the aunt who said
 * she would cover the cake.
 *
 * Separate from the event's [EventEntity.budget] rather than added to it. The budget is
 * the ceiling the planner set; funding is what has actually been promised and paid
 * towards it, and the gap between the two is the number that decides whether the event
 * can go ahead. Folding contributions into the budget would erase that gap.
 *
 * [receivedAt] is what separates a pledge from cash in hand: null means promised,
 * a timestamp means it arrived. Nothing else is needed to say both.
 */
@Entity(
    tableName = "contributions",
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ContributionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(index = true) val eventId: String,
    /** Who it came from — "Client deposit", "Uncle Emeka", "Sponsor: MTN". */
    val source: String,
    val amount: Double,
    /** When the money actually arrived, or null while it is only promised. */
    val receivedAt: Long? = null,
    val notes: String = "",
    val dateCreated: Long = System.currentTimeMillis(),
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    val isReceived: Boolean get() = receivedAt != null
}
