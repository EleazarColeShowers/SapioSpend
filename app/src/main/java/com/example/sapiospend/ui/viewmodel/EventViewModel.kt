package com.example.sapiospend.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sapiospend.data.local.EventEntity
import com.example.sapiospend.data.local.EventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventViewModel(
    private val repository: EventRepository
) : ViewModel() {

    val events =
        repository.events
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    fun addEvent(name: String, budget: Double) {
        viewModelScope.launch {
            repository.addEvent(EventEntity(name = name, budget = budget))
        }
    }

    companion object {
        fun factory(repository: EventRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { EventViewModel(repository) }
        }
    }
}