package com.example.sapiospend

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.sapiospend.billing.FreePlanLimits
import com.example.sapiospend.billing.Plan
import com.example.sapiospend.data.local.EventRepository
import com.example.sapiospend.domain.template.BudgetTemplates
import com.example.sapiospend.fake.FakeDatabase
import com.example.sapiospend.fake.FakeEntitlements
import com.example.sapiospend.ui.viewmodel.EventViewModel
import com.example.sapiospend.ui.viewmodel.UiMessage
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
import org.junit.Assert.assertNull
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

    private lateinit var db: FakeDatabase
    private lateinit var entitlements: FakeEntitlements
    private lateinit var viewModel: EventViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db = FakeDatabase()
        entitlements = FakeEntitlements(Plan.FREE)
        val repository = EventRepository(db.eventDao(), db.expenseDao(), db.budgetLineDao())
        viewModel = EventViewModel(repository, entitlements)
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
        val job = launch { viewModel.events.collect {} }

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
    fun `deleting an event tombstones it rather than dropping the row`() = runTest {
        val job = launch { viewModel.events.collect {} }

        viewModel.addEvent("Party", 10_000.0, "Birthday")
        advanceUntilIdle()
        viewModel.deleteEvent(viewModel.events.value.first())
        advanceUntilIdle()

        // Invisible to the app, still present for a future sync to propagate.
        assertEquals(1, db.events.value.size)
        assertTrue(db.events.value.first().deletedAt != null)
        job.cancel()
    }

    @Test
    fun `deleting an event also tombstones its expenses`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val expensesJob = launch { viewModel.allExpenses.collect {} }

        viewModel.addEvent("Event", 30_000.0, "Other")
        advanceUntilIdle()
        val event = viewModel.events.value.first()
        viewModel.addExpense(event.id, "Catering", "Food", 5_000.0)
        advanceUntilIdle()

        viewModel.deleteEvent(event)
        advanceUntilIdle()

        assertTrue("Orphaned expenses must not survive the cascade", viewModel.allExpenses.value.isEmpty())
        eventsJob.cancel()
        expensesJob.cancel()
    }

    @Test
    fun `addExpense increases allExpenses count`() = runTest {
        // Each collect suspends forever, so each flow needs its own launch job.
        val eventsJob = launch { viewModel.events.collect {} }
        val expensesJob = launch { viewModel.allExpenses.collect {} }

        viewModel.addEvent("Event", 30_000.0, "Other")
        advanceUntilIdle()

        val eventId = viewModel.events.value.first().id
        viewModel.addExpense(eventId = eventId, title = "Catering", category = "Food", amount = 5_000.0)
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
        advanceUntilIdle()
        viewModel.addExpense(viewModel.events.value.first().id, "Catering", "Food", 5_000.0)
        advanceUntilIdle()

        viewModel.deleteExpense(viewModel.allExpenses.value.first())
        advanceUntilIdle()

        assertTrue(viewModel.allExpenses.value.isEmpty())
        eventsJob.cancel()
        expensesJob.cancel()
    }

    @Test
    fun `multiple events accumulate correctly`() = runTest {
        entitlements.applyPurchase(Plan.PRO)
        val job = launch { viewModel.events.collect {} }

        viewModel.addEvent("Event A", 10_000.0, "Birthday")
        viewModel.addEvent("Event B", 20_000.0, "Wedding")
        viewModel.addEvent("Event C", 30_000.0, "Other")
        advanceUntilIdle()

        assertEquals(3, viewModel.events.value.size)
        job.cancel()
    }

    @Test
    fun `free plan blocks the fourth event and raises the paywall`() = runTest {
        val job = launch { viewModel.events.collect {} }

        repeat(FreePlanLimits.MAX_ACTIVE_EVENTS + 1) { index ->
            viewModel.addEvent("Event $index", 10_000.0)
            advanceUntilIdle()
        }

        assertEquals(FreePlanLimits.MAX_ACTIVE_EVENTS, viewModel.events.value.size)
        assertEquals(UiMessage.EventLimitReached, viewModel.message.value)
        job.cancel()
    }

    @Test
    fun `deleting an event frees a slot on the free plan`() = runTest {
        val job = launch { viewModel.events.collect {} }

        repeat(FreePlanLimits.MAX_ACTIVE_EVENTS) { index ->
            viewModel.addEvent("Event $index", 10_000.0)
            advanceUntilIdle()
        }
        viewModel.deleteEvent(viewModel.events.value.first())
        advanceUntilIdle()
        viewModel.consumeMessage()

        viewModel.addEvent("Replacement", 10_000.0)
        advanceUntilIdle()

        // A tombstoned event must not keep holding a paid slot hostage.
        assertEquals(FreePlanLimits.MAX_ACTIVE_EVENTS, viewModel.events.value.size)
        assertNull(viewModel.message.value)
        assertTrue(viewModel.events.value.any { it.name == "Replacement" })
        job.cancel()
    }

    @Test
    fun `pro plan allows more than the free event limit`() = runTest {
        entitlements.applyPurchase(Plan.PRO)
        val job = launch { viewModel.events.collect {} }

        repeat(FreePlanLimits.MAX_ACTIVE_EVENTS + 2) { index ->
            viewModel.addEvent("Event $index", 10_000.0)
            advanceUntilIdle()
        }

        assertEquals(FreePlanLimits.MAX_ACTIVE_EVENTS + 2, viewModel.events.value.size)
        assertNull(viewModel.message.value)
        job.cancel()
    }

    @Test
    fun `pro template seeds budget lines that sum to the budget`() = runTest {
        entitlements.applyPurchase(Plan.PRO)
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        val template = BudgetTemplates.byId("traditional_wedding")!!
        viewModel.addEvent("Tolu & Ada", 7_000_000.0, "Wedding", template)
        advanceUntilIdle()

        val lines = viewModel.budgetLines.value
        assertEquals(template.allocations.size, lines.size)
        assertEquals(7_000_000.0, lines.sumOf { it.plannedAmount }, 0.01)
        assertEquals(viewModel.events.value.first().id, lines.first().eventId)
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    fun `free plan creates the event but ignores the template`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        viewModel.addEvent("Budget Wedding", 500_000.0, "Wedding", BudgetTemplates.byId("white_wedding"))
        advanceUntilIdle()

        assertEquals(1, viewModel.events.value.size)
        assertTrue("Templates are Pro-only", viewModel.budgetLines.value.isEmpty())
        eventsJob.cancel()
        linesJob.cancel()
    }
}
