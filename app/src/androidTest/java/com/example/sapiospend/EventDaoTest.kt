package com.example.sapiospend

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sapiospend.data.local.AppDatabase
import com.example.sapiospend.data.local.BudgetLineDao
import com.example.sapiospend.data.local.BudgetLineEntity
import com.example.sapiospend.data.local.EventDao
import com.example.sapiospend.data.local.EventEntity
import com.example.sapiospend.data.local.ExpenseDao
import com.example.sapiospend.data.local.ExpenseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    private lateinit var budgetLineDao: BudgetLineDao

    private val now = 1_700_000_000_000L

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        eventDao = db.eventDao()
        expenseDao = db.expenseDao()
        budgetLineDao = db.budgetLineDao()
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
    fun insertedEvent_keepsItsClientGeneratedId() = runTest {
        val event = EventEntity(name = "Wedding", budget = 100_000.0)
        eventDao.insertEvent(event)

        // The id is chosen before the insert, so the caller can reference it immediately
        // without a round trip — which is what makes offline creation possible.
        assertEquals(event.id, eventDao.getAllEvents().first().first().id)
    }

    @Test
    fun softDeleteEvent_hidesItFromQueries() = runTest {
        val event = EventEntity(name = "Wedding", budget = 200_000.0, eventType = "Wedding")
        eventDao.insertEvent(event)

        eventDao.softDeleteEventCascading(event.id, now)

        assertTrue(eventDao.getAllEvents().first().isEmpty())
        assertEquals(0, eventDao.countActiveEvents())
    }

    @Test
    fun insertExpense_linkedToEvent() = runTest {
        val event = EventEntity(name = "Party", budget = 30_000.0)
        eventDao.insertEvent(event)

        expenseDao.insertExpense(
            ExpenseEntity(eventId = event.id, title = "Catering", category = "Food", amount = 10_000.0)
        )

        val expenses = expenseDao.getExpensesForEvent(event.id).first()
        assertEquals(1, expenses.size)
        assertEquals("Catering", expenses[0].title)
        assertEquals(10_000.0, expenses[0].amount, 0.01)
    }

    @Test
    fun softDeleteEvent_cascadesToExpensesAndBudgetLines() = runTest {
        val event = EventEntity(name = "Party", budget = 30_000.0)
        eventDao.insertEventWithBudgetLines(
            event,
            listOf(BudgetLineEntity(eventId = event.id, category = "Venue", plannedAmount = 20_000.0))
        )
        expenseDao.insertExpense(
            ExpenseEntity(eventId = event.id, title = "Venue", category = "Venue", amount = 20_000.0)
        )

        eventDao.softDeleteEventCascading(event.id, now)

        assertTrue("Children must be tombstoned too", expenseDao.getAllExpenses().first().isEmpty())
        assertTrue(budgetLineDao.getAllBudgetLines().first().isEmpty())
    }

    @Test
    fun softDeletedRows_areTombstonedNotRemoved() = runTest {
        val event = EventEntity(name = "Party", budget = 30_000.0)
        eventDao.insertEvent(event)
        eventDao.softDeleteEventCascading(event.id, now)

        // Query the raw table to prove the row survived for a future sync to propagate.
        db.query("SELECT deletedAt FROM events WHERE id = ?", arrayOf(event.id)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(now, cursor.getLong(0))
        }
    }

    @Test
    fun softDeleteExpense_doesNotAffectEvent() = runTest {
        val event = EventEntity(name = "Party", budget = 30_000.0)
        eventDao.insertEvent(event)

        val expense = ExpenseEntity(eventId = event.id, title = "Food", category = "Food", amount = 5_000.0)
        expenseDao.insertExpense(expense)
        expenseDao.markExpenseDeleted(expense.id, now)

        assertEquals(1, eventDao.getAllEvents().first().size)
        assertTrue(expenseDao.getAllExpenses().first().isEmpty())
    }

    @Test
    fun multipleExpenses_sumCorrectly() = runTest {
        val event = EventEntity(name = "Party", budget = 100_000.0)
        eventDao.insertEvent(event)

        expenseDao.insertExpense(ExpenseEntity(eventId = event.id, title = "A", category = "Food", amount = 10_000.0))
        expenseDao.insertExpense(ExpenseEntity(eventId = event.id, title = "B", category = "Venue", amount = 25_000.0))
        expenseDao.insertExpense(ExpenseEntity(eventId = event.id, title = "C", category = "Transport", amount = 5_000.0))

        val total = expenseDao.getExpensesForEvent(event.id).first().sumOf { it.amount }
        assertEquals(40_000.0, total, 0.01)
    }

    @Test
    fun insertEventWithBudgetLines_writesBothOrNeither() = runTest {
        val event = EventEntity(name = "Wedding", budget = 1_000_000.0, eventType = "Wedding")
        val lines = listOf(
            BudgetLineEntity(eventId = event.id, category = "Catering", plannedAmount = 600_000.0),
            BudgetLineEntity(eventId = event.id, category = "Venue", plannedAmount = 400_000.0)
        )

        eventDao.insertEventWithBudgetLines(event, lines)

        assertNotNull(eventDao.getAllEvents().first().find { it.id == event.id })
        assertEquals(2, budgetLineDao.getBudgetLinesForEvent(event.id).first().size)
    }

    @Test
    fun countActiveEvents_ignoresTombstones() = runTest {
        val first = EventEntity(name = "A", budget = 1.0)
        val second = EventEntity(name = "B", budget = 1.0)
        eventDao.insertEvent(first)
        eventDao.insertEvent(second)

        eventDao.softDeleteEventCascading(first.id, now)

        assertEquals(1, eventDao.countActiveEvents())
    }
}
