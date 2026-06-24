package com.example.sapiospend.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// eventType defaults to "General" so callers that don't need categorisation
// can skip the field without breaking anything.
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val budget: Double,
    val eventType: String = "General"
)