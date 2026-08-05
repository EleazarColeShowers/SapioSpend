package com.example.sapiospend

import com.example.sapiospend.domain.template.BudgetTemplate
import com.example.sapiospend.domain.template.BudgetTemplates
import com.example.sapiospend.domain.template.TemplateAllocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTemplateTest {

    @Test
    fun `every template's shares sum to one hundred percent`() {
        BudgetTemplates.all.forEach { template ->
            val total = template.allocations.sumOf { it.share }
            assertEquals("${template.name} shares must sum to 1.0", 1.0, total, 0.0001)
        }
    }

    @Test
    fun `every template has a unique id`() {
        val ids = BudgetTemplates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `no template repeats a category`() {
        BudgetTemplates.all.forEach { template ->
            val categories = template.allocations.map { it.category }
            assertEquals("${template.name} repeats a category", categories.size, categories.toSet().size)
        }
    }

    @Test
    fun `allocations sum exactly to the budget despite rounding`() {
        // 7,000,000 across nine shares is exactly the case where floor-rounding each line
        // would leave the plan short of the budget it came from.
        BudgetTemplates.all.forEach { template ->
            val allocated = template.allocate(7_000_000.0).sumOf { it.amount }
            assertEquals("${template.name} must allocate the whole budget", 7_000_000.0, allocated, 0.001)
        }
    }

    @Test
    fun `allocations sum exactly for an awkward budget`() {
        val template = BudgetTemplates.byId("birthday_party")!!
        val allocated = template.allocate(333_333.0).sumOf { it.amount }
        assertEquals(333_333.0, allocated, 0.001)
    }

    @Test
    fun `allocation produces one line per category`() {
        val template = BudgetTemplates.byId("corporate_event")!!
        assertEquals(template.allocations.size, template.allocate(1_000_000.0).size)
    }

    @Test
    fun `allocation is proportional to the share`() {
        val template = BudgetTemplate(
            id = "t", name = "T", eventType = "Other", description = "",
            allocations = listOf(
                TemplateAllocation("Food", 0.5),
                TemplateAllocation("Venue", 0.3),
                TemplateAllocation("Rest", 0.2)
            )
        )
        val result = template.allocate(1_000_000.0)

        assertEquals(500_000.0, result[0].amount, 0.01)
        assertEquals(300_000.0, result[1].amount, 0.01)
        assertEquals(200_000.0, result[2].amount, 0.01)
    }

    @Test
    fun `zero or negative budget allocates nothing`() {
        val template = BudgetTemplates.all.first()
        assertTrue(template.allocate(0.0).isEmpty())
        assertTrue(template.allocate(-5_000.0).isEmpty())
    }

    @Test
    fun `suggestedFor puts matching event types first`() {
        val suggestions = BudgetTemplates.suggestedFor("Wedding")
        assertTrue(suggestions.first().eventType.equals("Wedding", ignoreCase = true))
        assertEquals(BudgetTemplates.all.size, suggestions.size)
    }

    @Test
    fun `byId finds a known template and misses an unknown one`() {
        assertNotNull(BudgetTemplates.byId("traditional_wedding"))
        assertEquals(null, BudgetTemplates.byId("no_such_template"))
    }
}
