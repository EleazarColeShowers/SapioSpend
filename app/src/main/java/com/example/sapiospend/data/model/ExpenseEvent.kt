package com.example.sapiospend.data.model

data class Expense(
    val title: String,
    val category: String,
    val amount: Double
)

data class Event(
    val name: String,
    val budget: Double,
    val expenses: MutableList<Expense> = mutableListOf()
)