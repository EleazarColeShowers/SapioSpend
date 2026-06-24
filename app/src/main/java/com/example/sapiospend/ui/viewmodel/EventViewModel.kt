package com.example.sapiospend.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.sapiospend.data.local.EventEntity
import com.example.sapiospend.data.local.EventRepository
import com.example.sapiospend.data.local.ExpenseEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EventViewModel(private val repository: EventRepository) : ViewModel() {

    // WhileSubscribed(5000) keeps the flow alive for 5 s after the last collector
    // drops — long enough to survive a config change without leaking on real navigation.
    val events = repository.events.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allExpenses = repository.allExpenses.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun addEvent(name: String, budget: Double, eventType: String = "General") {
        viewModelScope.launch {
            repository.addEvent(EventEntity(name = name, budget = budget, eventType = eventType))
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch { repository.deleteEvent(event) }
    }

    fun addExpense(eventId: Int, title: String, category: String, amount: Double) {
        viewModelScope.launch {
            repository.addExpense(
                ExpenseEntity(eventId = eventId, title = title, category = category, amount = amount)
            )
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch { repository.deleteExpense(expense) }
    }

    companion object {
        // viewModelFactory avoids pulling in Hilt just to inject one dependency.
        fun factory(repository: EventRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { EventViewModel(repository) }
        }
    }
}