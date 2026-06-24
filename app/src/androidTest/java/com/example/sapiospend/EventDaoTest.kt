package com.example.sapiospend

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sapiospend.data.local.AppDatabase
import com.example.sapiospend.data.local.EventDao
import com.example.sapiospend.data.local.EventEntity
import com.example.sapiospend.data.local.ExpenseDao
import com.example.sapiospend.data.local.ExpenseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// In-memory database so each test starts completely clean and nothing persists to disk.
// Runs on-device because Room's annotation processor generates Android-specific code.
@RunWith(AndroidJUnit4::class)
class EventDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var eventDao: EventDao
    private lateinit var expenseDao: ExpenseDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        eventDao = db.eventDao()
        expenseDao = db.expenseDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertEvent_and_retrieveAll() = runTest {
        val event = EventEntity(name = "Birthday Party", budget = 50_000.0, eventType = "Birthday")
        eventDao.insertEvent(event)

        val events = eventDao.getAllEvents().first()
        assertEquals(1, events.size)
        assertEquals("Birthday Party", events[0].name)
        assertEquals(50_000.0, events[0].budget, 0.01)
    }

    @Test
    fun deleteEvent_removesFromDatabase() = runTest {
        val event = EventEntity(name = "Wedding", budget = 200_000.0, eventType = "Wedding")
        eventDao.insertEvent(event)

        val inserted = eventDao.getAllEvents().first().first()
        eventDao.deleteEvent(inserted)

        val events = eventDao.getAllEvents().first()
        assertTrue(events.isEmpty())
    }

    @Test
    fun insertExpense_linkedToEvent() = runTest {
        eventDao.insertEvent(EventEntity(name = "Party", budget = 30_000.0))
        val eventId = eventDao.getAllEvents().first().first().id

        expenseDao.insertExpense(
            ExpenseEntity(eventId = eventId, title = "Catering", category = "Food", amount = 10_000.0)
        )

        val expenses = expenseDao.getExpensesForEvent(eventId).first()
        assertEquals(1, expenses.size)
        assertEquals("Catering", expenses[0].title)
        assertEquals(10_000.0, expenses[0].amount, 0.01)
    }

    @Test
    fun deleteEvent_cascadesExpenses() = runTest {
        eventDao.insertEvent(EventEntity(name = "Party", budget = 30_000.0))
        val eventId = eventDao.getAllEvents().first().first().id

        expenseDao.insertExpense(
            ExpenseEntity(eventId = eventId, title = "Venue", category = "Venue", amount = 20_000.0)
        )

        val event = eventDao.getAllEvents().first().first()
        eventDao.deleteEvent(event)

        val expenses = expenseDao.getAllExpenses().first()
        assertTrue("Cascade delete should remove linked expenses", expenses.isEmpty())
    }

    @Test
    fun deleteExpense_doesNotAffectEvent() = runTest {
        eventDao.insertEvent(EventEntity(name = "Party", budget = 30_000.0))
        val eventId = eventDao.getAllEvents().first().first().id

        expenseDao.insertExpense(
            ExpenseEntity(eventId = eventId, title = "Food", category = "Food", amount = 5_000.0)
        )
        val expense = expenseDao.getAllExpenses().first().first()
        expenseDao.deleteExpense(expense)

        val events = eventDao.getAllEvents().first()
        assertEquals(1, events.size)
    }

    @Test
    fun multipleExpenses_sumCorrectly() = runTest {
        eventDao.insertEvent(EventEntity(name = "Party", budget = 100_000.0))
        val eventId = eventDao.getAllEvents().first().first().id

        expenseDao.insertExpense(ExpenseEntity(eventId = eventId, title = "A", category = "Food", amount = 10_000.0))
        expenseDao.insertExpense(ExpenseEntity(eventId = eventId, title = "B", category = "Venue", amount = 25_000.0))
        expenseDao.insertExpense(ExpenseEntity(eventId = eventId, title = "C", category = "Transport", amount = 5_000.0))

        val total = expenseDao.getExpensesForEvent(eventId).first().sumOf { it.amount }
        assertEquals(40_000.0, total, 0.01)
    }
}
