package com.example.sapiospend.data.local

class EventRepository(
    private val dao: EventDao
) {

    val events = dao.getAllEvents()

    suspend fun addEvent(
        event: EventEntity
    ) {
        dao.insertEvent(event)
    }
}