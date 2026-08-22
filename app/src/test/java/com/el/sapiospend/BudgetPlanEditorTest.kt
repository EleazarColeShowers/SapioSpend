package com.el.sapiospend

import com.el.sapiospend.data.local.BudgetLineEntity
import com.el.sapiospend.domain.plan.BudgetPlanEditor
import com.el.sapiospend.domain.template.CustomCategoryInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The id bookkeeping is the part of the plan editor that is easy to get wrong and
 * invisible when it is: a save that loses ids silently tombstones every line and writes
 * new ones, which looks identical on screen and rewrites the user's whole plan underneath.
 */
class BudgetPlanEditorTest {

    private fun line(id: String, category: String, amount: Double) =
        BudgetLineEntity(id = id, eventId = EVENT, category = category, plannedAmount = amount)

    @Test
    fun `rows are seeded from stored lines, biggest allocation first`() {
        val rows = BudgetPlanEditor.rowsFrom(
            listOf(line("a", "Drinks", 50_000.0), line("b", "Catering", 400_000.0))
        )

        assertEquals(listOf("Catering", "Drinks"), rows.map { it.name })
        // The row id is the budget line id — that is what makes an edit an update.
        assertEquals(listOf("b", "a"), rows.map { it.id })
    }

    @Test
    fun `whole amounts seed the field without a trailing decimal`() {
        val rows = BudgetPlanEditor.rowsFrom(listOf(line("a", "Catering", 400_000.0)))

        assertEquals("400000", rows.single().amount)
    }

    @Test
    fun `editing an existing row updates it in place rather than replacing it`() {
        val existing = listOf(line("a", "Catering", 400_000.0))
        val rows = listOf(CustomCategoryInput(id = "a", name = "Catering", amount = "450000"))

        val edit = BudgetPlanEditor.edit(EVENT, rows, existing)

        assertEquals(listOf("a"), edit.lines.map { it.id })
        assertEquals(450_000.0, edit.lines.single().plannedAmount, 0.0)
        assertTrue(edit.removedIds.isEmpty())
    }

    @Test
    fun `a row added on screen becomes a new line`() {
        val existing = listOf(line("a", "Catering", 400_000.0))
        val rows = listOf(
            CustomCategoryInput(id = "a", name = "Catering", amount = "400000"),
            CustomCategoryInput(id = "new", name = "Drinks", amount = "50000")
        )

        val edit = BudgetPlanEditor.edit(EVENT, rows, existing)

        assertEquals(listOf("a", "new"), edit.lines.map { it.id })
        assertTrue(edit.removedIds.isEmpty())
    }

    @Test
    fun `a row deleted from the screen is reported as removed`() {
        val existing = listOf(line("a", "Catering", 400_000.0), line("b", "Drinks", 50_000.0))
        val rows = listOf(CustomCategoryInput(id = "a", name = "Catering", amount = "400000"))

        val edit = BudgetPlanEditor.edit(EVENT, rows, existing)

        assertEquals(listOf("b"), edit.removedIds)
    }

    @Test
    fun `clearing a row's amount removes it rather than saving a zero allocation`() {
        val existing = listOf(line("a", "Catering", 400_000.0))
        val rows = listOf(CustomCategoryInput(id = "a", name = "Catering", amount = ""))

        val edit = BudgetPlanEditor.edit(EVENT, rows, existing)

        assertTrue(edit.lines.isEmpty())
        assertEquals(listOf("a"), edit.removedIds)
    }

    @Test
    fun `blank and unparseable rows are dropped without being reported as removals`() {
        val rows = listOf(
            CustomCategoryInput(id = "1", name = "", amount = "5000"),
            CustomCategoryInput(id = "2", name = "Drinks", amount = "not a number"),
            CustomCategoryInput(id = "3", name = "   ", amount = "")
        )

        val edit = BudgetPlanEditor.edit(EVENT, rows, existing = emptyList())

        assertTrue(edit.lines.isEmpty())
        assertTrue(edit.removedIds.isEmpty())
    }

    @Test
    fun `category names are trimmed so a stray space cannot split a category in two`() {
        val edit = BudgetPlanEditor.edit(
            EVENT,
            listOf(CustomCategoryInput(id = "1", name = "  Catering  ", amount = "1000")),
            existing = emptyList()
        )

        assertEquals("Catering", edit.lines.single().category)
    }

    @Test
    fun `every saved line belongs to the event being edited`() {
        val edit = BudgetPlanEditor.edit(
            EVENT,
            listOf(CustomCategoryInput(id = "1", name = "Catering", amount = "1000")),
            existing = emptyList()
        )

        assertEquals(EVENT, edit.lines.single().eventId)
    }

    @Test
    fun `planned total ignores rows that would not be saved`() {
        val rows = listOf(
            CustomCategoryInput(name = "Catering", amount = "400000"),
            CustomCategoryInput(name = "", amount = "999999"),
            CustomCategoryInput(name = "Drinks", amount = "50000")
        )

        assertEquals(450_000.0, BudgetPlanEditor.plannedTotal(rows), 0.0)
    }

    @Test
    fun `spent categories already on the plan are not suggested again`() {
        val rows = listOf(CustomCategoryInput(name = "catering", amount = "1000"))

        val suggestions = BudgetPlanEditor.unplannedCategories(
            rows,
            spentCategories = listOf("Catering", "Transport", "Transport")
        )

        // Matched case-insensitively, and each name offered once however many receipts
        // carry it.
        assertEquals(listOf("Transport"), suggestions)
    }

    private companion object {
        const val EVENT = "event-1"
    }
}
