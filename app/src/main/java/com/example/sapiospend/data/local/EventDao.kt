package com.example.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert
    suspend fun insertEvent(
        event: EventEntity
    )

    @Delete
    suspend fun deleteEvent(
        event: EventEntity
    )

    @Query("SELECT * FROM events")
    fun getAllEvents(): Flow<List<EventEntity>>
}