package com.example.sapiospend.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    // Room re-emits whenever the events table changes, so the UI updates without polling.
    @Query("SELECT * FROM events")
    fun getAllEvents(): Flow<List<EventEntity>>
}