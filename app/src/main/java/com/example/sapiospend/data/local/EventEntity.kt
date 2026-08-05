package com.example.sapiospend.data.local

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
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val budget: Double,
    val eventType: String = "General",
    val dateCreated: Long = System.currentTimeMillis(),
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

/** Stand-in owner for the single local user, replaced by a real account id later. */
object LocalOwner {
    const val ID = "local"
}
