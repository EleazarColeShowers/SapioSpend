package com.example.sapiospend.data.local

// Keeps the ViewModel from importing Room directly — makes it easier to swap
// the data source in tests without touching the ViewModel.
class EventRepository(
    private val eventDao: EventDao,
    private val expenseDao: ExpenseDao
) {
    val events = eventDao.getAllEvents()
    val allExpenses = expenseDao.getAllExpenses()

    suspend fun addEvent(event: EventEntity) = eventDao.insertEvent(event)
    suspend fun deleteEvent(event: EventEntity) = eventDao.deleteEvent(event)
    suspend fun addExpense(expense: ExpenseEntity) = expenseDao.insertExpense(expense)
    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.deleteExpense(expense)
}