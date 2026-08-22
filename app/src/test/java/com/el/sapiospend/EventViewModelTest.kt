package com.el.sapiospend

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.el.sapiospend.billing.Entitlements
import com.el.sapiospend.billing.FreePlanLimits
import com.el.sapiospend.billing.Plan
import com.el.sapiospend.billing.ProFeature
import com.el.sapiospend.data.local.EventRepository
import com.el.sapiospend.domain.template.BudgetTemplates
import com.el.sapiospend.domain.template.CategoryAmount
import com.el.sapiospend.domain.template.CustomCategoryInput
import com.el.sapiospend.fake.FakeDatabase
import com.el.sapiospend.fake.FakeEntitlements
import com.el.sapiospend.ui.viewmodel.EventViewModel
import com.el.sapiospend.ui.viewmodel.UiMessage
import com.el.sapiospend.util.DateUtils
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
    fun `addEvent stores the budget period when one is given`() = runTest {
        val job = launch { viewModel.events.collect {} }

        val start = 1_700_000_000_000L
        val end = start + (29 * 24L * 60 * 60 * 1000)
        viewModel.addEvent("August Salary", 300_000.0, "Personal", startDate = start, endDate = end)
        advanceUntilIdle()

        val event = viewModel.events.value.first()
        assertEquals(start, event.startDate)
        assertEquals(end, event.endDate)
        assertTrue(event.hasPeriod)
        job.cancel()
    }

    @Test
    fun `an event created without dates stays open-ended`() = runTest {
        val job = launch { viewModel.events.collect {} }

        viewModel.addEvent("Wedding", 200_000.0, "Wedding")
        advanceUntilIdle()

        val event = viewModel.events.value.first()
        assertNull(event.startDate)
        assertNull(event.endDate)
        job.cancel()
    }

    @Test
    fun `a backwards date range is straightened rather than stored inverted`() = runTest {
        val job = launch { viewModel.events.collect {} }

        val start = 1_700_000_000_000L
        val end = start + (29 * 24L * 60 * 60 * 1000)
        // Dates handed over the wrong way round would make every pacing figure negative.
        viewModel.addEvent("August Salary", 300_000.0, "Personal", startDate = end, endDate = start)
        advanceUntilIdle()

        val event = viewModel.events.value.first()
        assertEquals(start, event.startDate)
        assertEquals(end, event.endDate)
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

    /**
     * v1.0 gives every feature away (see PlanRules.ALL_FEATURES_FREE), so the free plan
     * has no ceiling and the paywall must never fire. The tier logic that will reinstate
     * the ceiling once billing ships is covered directly in PlanRulesTest.
     */
    @Test
    fun `free plan is not capped and never raises the paywall in v1_0`() = runTest {
        val job = launch { viewModel.events.collect {} }

        repeat(FreePlanLimits.MAX_ACTIVE_EVENTS + 1) { index ->
            viewModel.addEvent("Event $index", 10_000.0)
            advanceUntilIdle()
        }

        assertEquals(FreePlanLimits.MAX_ACTIVE_EVENTS + 1, viewModel.events.value.size)
        assertNull(viewModel.message.value)
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
    fun `a custom plan seeds the user's own categories`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        val lines = listOf(
            CategoryAmount("Bed & mattress", 250_000.0),
            CategoryAmount("Sofa", 200_000.0),
            CategoryAmount("Fridge", 250_000.0)
        )
        viewModel.addEvent("Setting Up My New Apartment", 1_500_000.0, "Personal", customLines = lines)
        advanceUntilIdle()

        val saved = viewModel.budgetLines.value
        assertEquals(3, saved.size)
        assertEquals(700_000.0, saved.sumOf { it.plannedAmount }, 0.01)
        // Unlike a template, custom figures are not scaled to fill the budget — the gap
        // between the two is the "remaining" the user is watching.
        assertTrue(saved.any { it.category == "Sofa" && it.plannedAmount == 200_000.0 })
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    fun `a custom plan is saved even without the templates entitlement`() = runTest {
        // Building your own breakdown is the free alternative the templates paywall
        // offers, so it must survive a plan that cannot use templates. ALL_FEATURES_FREE
        // makes every real plan permissive today, so the denial is stubbed directly.
        val noProFeatures = object : Entitlements by FakeEntitlements(Plan.FREE) {
            override fun canUse(feature: ProFeature): Boolean = false
        }
        val gated = EventViewModel(
            EventRepository(db.eventDao(), db.expenseDao(), db.budgetLineDao()),
            noProFeatures
        )
        val eventsJob = launch { gated.events.collect {} }
        val linesJob = launch { gated.budgetLines.collect {} }

        gated.addEvent(
            "New Laptop", 900_000.0, "Personal",
            template = BudgetTemplates.byId("laptop_purchase"),
            customLines = listOf(CategoryAmount("Laptop", 800_000.0))
        )
        advanceUntilIdle()

        val saved = gated.budgetLines.value
        assertEquals(1, saved.size)
        assertEquals("Laptop", saved.first().category)
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    fun `a custom plan takes precedence over a template`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        viewModel.addEvent(
            "Tolu & Ada", 7_000_000.0, "Wedding",
            template = BudgetTemplates.byId("traditional_wedding"),
            customLines = listOf(CategoryAmount("Catering", 3_000_000.0))
        )
        advanceUntilIdle()

        val saved = viewModel.budgetLines.value
        assertEquals(1, saved.size)
        assertEquals("Catering", saved.first().category)
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    /** Templates are Pro-only in the tier rules, but v1.0 gives them to everyone. */
    fun `free plan applies the template in v1_0`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        viewModel.addEvent("Budget Wedding", 500_000.0, "Wedding", BudgetTemplates.byId("white_wedding"))
        advanceUntilIdle()

        assertEquals(1, viewModel.events.value.size)
        assertTrue(
            "Templates must seed budget lines while everything is free",
            viewModel.budgetLines.value.isNotEmpty()
        )
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    fun `saving a plan writes the rows the user kept`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        viewModel.addEvent("Tolu & Ada", 5_000_000.0, "Wedding")
        advanceUntilIdle()
        val eventId = viewModel.events.value.single().id

        viewModel.savePlan(
            eventId,
            listOf(
                CustomCategoryInput(name = "Catering", amount = "3000000"),
                CustomCategoryInput(name = "Photography", amount = "800000"),
                CustomCategoryInput(name = "", amount = "")
            )
        )
        advanceUntilIdle()

        val saved = viewModel.budgetLines.value.filter { it.eventId == eventId }
        assertEquals(setOf("Catering", "Photography"), saved.map { it.category }.toSet())
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    fun `re-saving an edited plan updates lines in place instead of duplicating them`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        viewModel.addEvent(
            "Tolu & Ada", 5_000_000.0, "Wedding",
            customLines = listOf(CategoryAmount("Catering", 3_000_000.0))
        )
        advanceUntilIdle()
        val eventId = viewModel.events.value.single().id
        val original = viewModel.budgetLines.value.single { it.eventId == eventId }

        viewModel.savePlan(
            eventId,
            listOf(CustomCategoryInput(id = original.id, name = "Catering", amount = "3500000"))
        )
        advanceUntilIdle()

        val saved = viewModel.budgetLines.value.filter { it.eventId == eventId }
        assertEquals(1, saved.size)
        assertEquals(original.id, saved.single().id)
        assertEquals(3_500_000.0, saved.single().plannedAmount, 0.0)
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    fun `a category dropped from the plan stops appearing`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val linesJob = launch { viewModel.budgetLines.collect {} }

        viewModel.addEvent(
            "Tolu & Ada", 5_000_000.0, "Wedding",
            customLines = listOf(CategoryAmount("Catering", 3_000_000.0), CategoryAmount("Drinks", 500_000.0))
        )
        advanceUntilIdle()
        val eventId = viewModel.events.value.single().id
        val catering = viewModel.budgetLines.value.single { it.category == "Catering" }

        viewModel.savePlan(
            eventId,
            listOf(CustomCategoryInput(id = catering.id, name = "Catering", amount = "3000000"))
        )
        advanceUntilIdle()

        val saved = viewModel.budgetLines.value.filter { it.eventId == eventId }
        assertEquals(listOf("Catering"), saved.map { it.category })
        eventsJob.cancel()
        linesJob.cancel()
    }

    @Test
    fun `the editor opens on the plan that is actually stored`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }

        viewModel.addEvent(
            "Tolu & Ada", 5_000_000.0, "Wedding",
            customLines = listOf(CategoryAmount("Catering", 3_000_000.0))
        )
        advanceUntilIdle()
        val eventId = viewModel.events.value.single().id

        // Read straight through rather than off the UI flow: the screen seeds itself
        // this way precisely so it cannot open blank on an event that has a plan.
        val lines = viewModel.plannedLinesFor(eventId)

        assertEquals(listOf("Catering"), lines.map { it.category })
        eventsJob.cancel()
    }

    @Test
    fun `an expense can be corrected in place`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val expensesJob = launch { viewModel.allExpenses.collect {} }

        viewModel.addEvent("Tolu & Ada", 5_000_000.0, "Wedding")
        advanceUntilIdle()
        val eventId = viewModel.events.value.single().id
        viewModel.addExpense(eventId, "Caterer", "Catering", 250_000.0, "deposit")
        advanceUntilIdle()
        val original = viewModel.allExpenses.value.single()

        viewModel.updateExpense(
            original.copy(title = "Caterer (balance)", amount = 2_500_000.0, category = "Food")
        )
        advanceUntilIdle()

        val corrected = viewModel.allExpenses.value.single()
        // Same row, not a delete and a re-add — the id is what a future sync matches on.
        assertEquals(original.id, corrected.id)
        assertEquals("Caterer (balance)", corrected.title)
        assertEquals(2_500_000.0, corrected.amount, 0.0)
        assertEquals("Food", corrected.category)
        eventsJob.cancel()
        expensesJob.cancel()
    }

    @Test
    fun `an expense is recorded on the day it happened, not the day it was typed`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val expensesJob = launch { viewModel.allExpenses.collect {} }

        viewModel.addEvent("August", 300_000.0, "Personal")
        advanceUntilIdle()
        val eventId = viewModel.events.value.single().id
        val threeDaysAgo = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L

        viewModel.addExpense(eventId, "Fuel", "Transport", 20_000.0, date = threeDaysAgo)
        advanceUntilIdle()

        assertEquals(threeDaysAgo, viewModel.allExpenses.value.single().dateCreated)
        eventsJob.cancel()
        expensesJob.cancel()
    }

    @Test
    fun `an event's period can be set after it was created open-ended`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }

        viewModel.addEvent("August", 300_000.0, "Personal")
        advanceUntilIdle()
        val event = viewModel.events.value.single()
        assertNull(event.startDate)

        val (start, end) = DateUtils.monthBounds(0)
        viewModel.updateEvent(event.copy(startDate = start, endDate = end))
        advanceUntilIdle()

        val updated = viewModel.events.value.single()
        assertEquals(start, updated.startDate)
        assertEquals(end, updated.endDate)
        eventsJob.cancel()
    }

    @Test
    fun `an event's period can be removed again`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }

        val (start, end) = DateUtils.monthBounds(0)
        viewModel.addEvent("August", 300_000.0, "Personal", startDate = start, endDate = end)
        advanceUntilIdle()
        val event = viewModel.events.value.single()

        viewModel.updateEvent(event.copy(startDate = null, endDate = null))
        advanceUntilIdle()

        val updated = viewModel.events.value.single()
        assertNull(updated.startDate)
        assertNull(updated.endDate)
        eventsJob.cancel()
    }

    @Test
    fun `a period edited end-first is straightened rather than saved backwards`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }

        viewModel.addEvent("August", 300_000.0, "Personal")
        advanceUntilIdle()
        val event = viewModel.events.value.single()
        val (start, end) = DateUtils.monthBounds(0)

        viewModel.updateEvent(event.copy(startDate = end, endDate = start))
        advanceUntilIdle()

        val updated = viewModel.events.value.single()
        assertEquals(start, updated.startDate)
        assertEquals(end, updated.endDate)
        eventsJob.cancel()
    }

    @Test
    fun `an expense logged against the wrong event can be moved to another`() = runTest {
        val eventsJob = launch { viewModel.events.collect {} }
        val expensesJob = launch { viewModel.allExpenses.collect {} }

        viewModel.addEvent("Tolu & Ada", 5_000_000.0, "Wedding")
        viewModel.addEvent("August", 300_000.0, "Personal")
        advanceUntilIdle()
        val wedding = viewModel.events.value.first { it.name == "Tolu & Ada" }
        val august = viewModel.events.value.first { it.name == "August" }

        viewModel.addExpense(wedding.id, "Fuel", "Transport", 20_000.0)
        advanceUntilIdle()
        val logged = viewModel.allExpenses.value.single()

        viewModel.updateExpense(logged.copy(eventId = august.id))
        advanceUntilIdle()

        val moved = viewModel.allExpenses.value.single()
        assertEquals(logged.id, moved.id)
        assertEquals(august.id, moved.eventId)
        eventsJob.cancel()
        expensesJob.cancel()
    }
}
