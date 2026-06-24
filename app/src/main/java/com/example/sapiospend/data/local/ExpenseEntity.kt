package com.example.sapiospend.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// eventId is indexed because every screen filters expenses by event — without it
// Room would scan the whole table on each load.
// CASCADE means deleting an event removes its expenses automatically.
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
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(index = true) val eventId: Int,
    val title: String,
    val category: String,
    val amount: Double
)
