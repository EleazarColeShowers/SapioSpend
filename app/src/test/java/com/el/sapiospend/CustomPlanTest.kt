package com.el.sapiospend

import com.el.sapiospend.domain.template.CustomCategoryInput
import com.el.sapiospend.domain.template.CustomPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPlanTest {

    private fun row(name: String, amount: String) = CustomCategoryInput(name = name, amount = amount)

    @Test
    fun `builds one line per filled category`() {
        // The apartment fit-out from the feature request, typed as the user would type it.
        val lines = CustomPlan.linesOf(
            listOf(
                row("Bed & mattress", "250000"),
                row("Sofa", "200000"),
                row("Fridge", "250000"),
                row("TV", "180000"),
                row("Kitchen items", "150000"),
                row("Curtains", "80000")
            )
        )

        assertEquals(6, lines.size)
        assertEquals("Bed & mattress", lines.first().category)
        assertEquals(1_110_000.0, lines.sumOf { it.amount }, 0.01)
    }

    @Test
    fun `remaining is the budget less what has been allocated`() {
        val inputs = listOf(row("Bed & mattress", "250000"), row("Sofa", "200000"))
        assertEquals(1_050_000.0, CustomPlan.unallocated(1_500_000.0, inputs), 0.01)
    }

    @Test
    fun `remaining goes negative once the categories overshoot`() {
        val inputs = listOf(row("Laptop", "900000"), row("Bag", "150000"))
        assertTrue(CustomPlan.unallocated(1_000_000.0, inputs) < 0)
    }

    @Test
    fun `blank and zero rows are dropped`() {
        // Untouched rows and a category somebody zeroed out must not become plan lines
        // that show up in every breakdown as unfunded.
        val lines = CustomPlan.linesOf(
            listOf(
                row("Sofa", "200000"),
                row("", ""),
                row("Rug", ""),
                row("", "50000"),
                row("Lamp", "0")
            )
        )

        assertEquals(1, lines.size)
        assertEquals("Sofa", lines.first().category)
    }

    @Test
    fun `an amount that is not a number contributes nothing`() {
        assertEquals(0.0, CustomPlan.plannedTotal(listOf(row("Sofa", "."))), 0.01)
    }

    @Test
    fun `the same category typed twice is merged`() {
        val lines = CustomPlan.linesOf(
            listOf(row("Kitchen items", "100000"), row("kitchen ITEMS", "50000"))
        )

        assertEquals(1, lines.size)
        // The first spelling wins, since that is the one the user has been looking at.
        assertEquals("Kitchen items", lines.first().category)
        assertEquals(150_000.0, lines.first().amount, 0.01)
    }

    @Test
    fun `surrounding whitespace is trimmed off category names`() {
        assertEquals("Sofa", CustomPlan.linesOf(listOf(row("  Sofa  ", "1000"))).first().category)
    }

    @Test
    fun `an empty plan allocates nothing`() {
        val blank = CustomPlan.blankRows()

        assertEquals(CustomPlan.INITIAL_ROWS, blank.size)
        assertTrue(CustomPlan.linesOf(blank).isEmpty())
        assertEquals(0.0, CustomPlan.plannedTotal(blank), 0.01)
    }

    @Test
    fun `blank rows get distinct ids`() {
        // Compose keys the editor rows on these; duplicates would make deleting one row
        // scramble the text in another.
        val ids = CustomPlan.blankRows(5).map { it.id }
        assertEquals(5, ids.toSet().size)
    }
}
