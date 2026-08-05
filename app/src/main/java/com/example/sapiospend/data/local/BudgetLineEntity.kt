package com.example.sapiospend.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A planned allocation for one category of an event — "Catering: ₦2,800,000".
 *
 * Expenses record what was actually spent; budget lines record what was meant to be
 * spent. Keeping them in separate tables is what makes planned-vs-actual analytics
 * possible, and it is the shape a template writes into (and, later, the shape an AI
 * estimate would fill in).
 */
@Entity(
    tableName = "budget_lines",
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class BudgetLineEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(index = true) val eventId: String,
    val category: String,
    val plannedAmount: Double,
    val ownerId: String = LocalOwner.ID,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
