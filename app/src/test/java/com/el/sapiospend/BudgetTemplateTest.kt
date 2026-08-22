package com.el.sapiospend

import com.el.sapiospend.domain.template.BudgetTemplate
import com.el.sapiospend.domain.template.BudgetTemplates
import com.el.sapiospend.domain.template.EventTypes
import com.el.sapiospend.domain.template.TemplateAllocation
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
    fun `forEventType returns only that type's templates`() {
        val wedding = BudgetTemplates.forEventType("Wedding")

        assertTrue(wedding.isNotEmpty())
        assertTrue(
            "A picker for one type must not show another type's templates",
            wedding.all { it.eventType == "Wedding" }
        )
    }

    @Test
    fun `forEventType ignores case`() {
        assertEquals(
            BudgetTemplates.forEventType("Social Gathering"),
            BudgetTemplates.forEventType("social gathering")
        )
    }

    @Test
    fun `forEventType returns nothing for a type nobody wrote templates for`() {
        // Events created before the type list settled carry types like "General". Falling
        // back to the whole catalogue there would put weddings in front of a laptop budget.
        assertTrue(BudgetTemplates.forEventType("General").isEmpty())
    }

    @Test
    fun `every event type offered in the picker has at least one template`() {
        EventTypes.ALL.forEach { type ->
            assertTrue(
                "$type has no templates — the step would show an empty list",
                BudgetTemplates.forEventType(type).isNotEmpty()
            )
        }
    }

    @Test
    fun `every template belongs to a type the picker offers`() {
        // A template filed under a type nobody can select is unreachable.
        BudgetTemplates.all.forEach { template ->
            assertTrue(
                "${template.name} is filed under an unreachable type: ${template.eventType}",
                EventTypes.ALL.contains(template.eventType)
            )
        }
    }

    @Test
    fun `template names are unique within their event type`() {
        EventTypes.ALL.forEach { type ->
            val names = BudgetTemplates.forEventType(type).map { it.name }
            assertEquals("$type lists a name twice", names.size, names.toSet().size)
        }
    }

    @Test
    fun `byId finds a known template and misses an unknown one`() {
        assertNotNull(BudgetTemplates.byId("traditional_wedding"))
        assertEquals(null, BudgetTemplates.byId("no_such_template"))
    }
}
