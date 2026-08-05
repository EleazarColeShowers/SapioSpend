package com.example.sapiospend.data.local

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
    val amount: Double,
    val notes: String = "",
    val dateCreated: Long = System.currentTimeMillis(),
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
