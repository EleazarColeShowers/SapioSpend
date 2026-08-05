package com.example.sapiospend.domain.template

/**
 * The starter template catalogue.
 *
 * The shares are a defensible starting point for the Nigerian market, not gospel — a
 * planner is expected to drag them around. Their real job is to stop the app from
 * opening on a blank page, and to start accumulating the category-level data that a
 * cost-estimating model would later need to be any good.
 */
object BudgetTemplates {

    val all: List<BudgetTemplate> = listOf(
        BudgetTemplate(
            id = "traditional_wedding",
            name = "Traditional Wedding",
            eventType = "Wedding",
            description = "Engagement / traditional ceremony with full catering and aso-ebi",
            allocations = listOf(
                TemplateAllocation("Catering & Drinks", 0.30),
                TemplateAllocation("Venue & Rentals", 0.15),
                TemplateAllocation("Decoration", 0.10),
                TemplateAllocation("Photography & Video", 0.10),
                TemplateAllocation("Attire & Aso-Ebi", 0.09),
                TemplateAllocation("Entertainment (MC, DJ, Band)", 0.07),
                TemplateAllocation("Souvenirs & Gifts", 0.05),
                TemplateAllocation("Logistics & Transport", 0.05),
                TemplateAllocation("Contingency", 0.09)
            )
        ),
        BudgetTemplate(
            id = "white_wedding",
            name = "White Wedding & Reception",
            eventType = "Wedding",
            description = "Church ceremony plus hall reception",
            allocations = listOf(
                TemplateAllocation("Catering & Drinks", 0.32),
                TemplateAllocation("Venue & Rentals", 0.18),
                TemplateAllocation("Decoration", 0.11),
                TemplateAllocation("Photography & Video", 0.10),
                TemplateAllocation("Cake & Small Chops", 0.05),
                TemplateAllocation("Entertainment (MC, DJ, Band)", 0.07),
                TemplateAllocation("Attire & Beauty", 0.07),
                TemplateAllocation("Logistics & Transport", 0.04),
                TemplateAllocation("Contingency", 0.06)
            )
        ),
        BudgetTemplate(
            id = "birthday_party",
            name = "Birthday Party",
            eventType = "Birthday",
            description = "Milestone birthday with catering and entertainment",
            allocations = listOf(
                TemplateAllocation("Catering & Drinks", 0.35),
                TemplateAllocation("Venue & Rentals", 0.20),
                TemplateAllocation("Decoration", 0.12),
                TemplateAllocation("Cake & Small Chops", 0.08),
                TemplateAllocation("Entertainment (MC, DJ)", 0.10),
                TemplateAllocation("Photography", 0.07),
                TemplateAllocation("Contingency", 0.08)
            )
        ),
        BudgetTemplate(
            id = "corporate_event",
            name = "Corporate Event",
            eventType = "Corporate",
            description = "Conference, product launch or end-of-year party",
            allocations = listOf(
                TemplateAllocation("Venue & AV", 0.25),
                TemplateAllocation("Catering", 0.25),
                TemplateAllocation("Branding & Print", 0.12),
                TemplateAllocation("Speakers & Talent", 0.10),
                TemplateAllocation("Photography & Video", 0.08),
                TemplateAllocation("Staffing & Security", 0.08),
                TemplateAllocation("Logistics & Transport", 0.05),
                TemplateAllocation("Contingency", 0.07)
            )
        ),
        BudgetTemplate(
            id = "naming_ceremony",
            name = "Naming Ceremony",
            eventType = "Social Gathering",
            description = "Home or hall naming ceremony",
            allocations = listOf(
                TemplateAllocation("Catering & Drinks", 0.38),
                TemplateAllocation("Venue & Rentals", 0.16),
                TemplateAllocation("Decoration", 0.12),
                TemplateAllocation("Souvenirs", 0.10),
                TemplateAllocation("Photography", 0.08),
                TemplateAllocation("Entertainment", 0.08),
                TemplateAllocation("Contingency", 0.08)
            )
        ),
        BudgetTemplate(
            id = "burial_ceremony",
            name = "Burial Ceremony",
            eventType = "Other",
            description = "Funeral service, reception and family logistics",
            allocations = listOf(
                TemplateAllocation("Catering & Drinks", 0.30),
                TemplateAllocation("Venue & Canopies", 0.15),
                TemplateAllocation("Casket & Mortuary", 0.15),
                TemplateAllocation("Attire & Uniform", 0.10),
                TemplateAllocation("Logistics & Transport", 0.10),
                TemplateAllocation("Programme & Printing", 0.06),
                TemplateAllocation("Photography", 0.06),
                TemplateAllocation("Contingency", 0.08)
            )
        )
    )

    fun byId(id: String): BudgetTemplate? = all.firstOrNull { it.id == id }

    /** Templates matching an event type first, so the picker leads with the likely ones. */
    fun suggestedFor(eventType: String): List<BudgetTemplate> =
        all.sortedByDescending { it.eventType.equals(eventType, ignoreCase = true) }
}
