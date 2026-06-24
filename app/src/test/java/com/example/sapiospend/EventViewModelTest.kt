package com.example.sapiospend

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.sapiospend.data.local.EventRepository
import com.example.sapiospend.fake.FakeEventDao
import com.example.sapiospend.fake.FakeExpenseDao
import com.example.sapiospend.ui.viewmodel.EventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Fake DAOs replace Room so these run as plain JVM tests — no emulator, much faster.
@OptIn(ExperimentalCoroutinesApi::class)
class EventViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: EventViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repository = EventRepository(FakeEventDao(), FakeExpenseDao())
        viewModel = EventViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial events list is empty`() = runTest {
        assertEquals(emptyList<Any>(), viewModel.events.value)
    }

    @Test
    fun `addEvent increases events count by one`() = runTest {
        val collected = mutableListOf<List<Any>>()
        val job = launch { viewModel.events.collect { collected.add(it) } }

        viewModel.addEvent("Birthday Party", 50_000.0, "Birthday")
        advanceUntilIdle()

        assertEquals(1, viewModel.events.value.size)
        job.cancel()
    }

    @Test
    fun `addEvent stores correct name and budget`() = runTest {
        val job = launch { viewModel.events.collect {} }

        viewModel.addEvent("Wedding Dinner", 200_000.0, "Wedding")
        advanceUntilIdle()

        val event = viewModel.events.value.first()
        assertEquals("Wedding Dinner", event.name)
        assertEquals(200_000.0, event.budget, 0.01)
        assertEquals("Wedding", event.eventType)
        job.cancel()
    }

    @Test
    fun `deleteEvent removes event from list`() = runTest {
        val job = launch { viewModel.events.collect {} }

        viewModel.addEvent("Party", 10_000.0, "Birthday")
        advanceUntilIdle()

        val event = viewModel.events.value.first()
        viewModel.deleteEvent(event)
        advanceUntilIdle()

        assertTrue(viewModel.events.value.isEmpty())
        job.cancel()
    }

    @Test
    fun `addExpense increases allExpenses count`() = runTest {
        // Each collect suspends forever, so each flow needs its own launch job.
        val eventsJob = launch { viewModel.events.collect {} }
        val expensesJob = launch { viewModel.allExpenses.collect {} }

        viewModel.addEvent("Event", 30_000.0, "Other")
        advanceUntilIdle()

        viewModel.addExpense(eventId = 1, title = "Catering", category = "Food", amount = 5_000.0)
        advanceUntilIdle()

        assertEquals(1, viewModel.allExpenses.value.size)
        eventsJob.cancel()
        expensesJob.cancel()
    }

    @Test
    fun `deleteExpense removes expense from allExpenses`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val expensesJob = launch { viewModel.allExpenses.collect {} }

        viewModel.addEvent("Event", 30_000.0, "Other")
        viewModel.addExpense(eventId = 1, title = "Catering", category = "Food", amount = 5_000.0)
        advanceUntilIdle()

        val expense = viewModel.allExpenses.value.first()
        viewModel.deleteExpense(expense)
        advanceUntilIdle()

        assertTrue(viewModel.allExpenses.value.isEmpty())
        eventsJob.cancel()
        expensesJob.cancel()
    }

    @Test
    fun `multiple events accumulate correctly`() = runTest {
        val job = launch { viewModel.events.collect {} }

        viewModel.addEvent("Event A", 10_000.0, "Birthday")
        viewModel.addEvent("Event B", 20_000.0, "Wedding")
        viewModel.addEvent("Event C", 30_000.0, "Other")
        advanceUntilIdle()

        assertEquals(3, viewModel.events.value.size)
        job.cancel()
    }
}
