package com.example.sapiospend.fake

import com.example.sapiospend.data.local.EventDao
import com.example.sapiospend.data.local.EventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory EventDao used only in unit tests. */
class FakeEventDao : EventDao {

    private val store = mutableListOf<EventEntity>()
    private val flow = MutableStateFlow<List<EventEntity>>(emptyList())
    private var nextId = 1

    override suspend fun insertEvent(event: EventEntity) {
        store.add(event.copy(id = nextId++))
        flow.value = store.toList()
    }

    override suspend fun deleteEvent(event: EventEntity) {
        store.removeAll { it.id == event.id }
        flow.value = store.toList()
    }

    override fun getAllEvents(): Flow<List<EventEntity>> = flow
}
