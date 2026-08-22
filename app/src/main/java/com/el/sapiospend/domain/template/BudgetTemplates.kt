package com.el.sapiospend.domain.template

/**
 * The starter template catalogue, grouped by the event type each template belongs to.
 *
 * A picker that shows every template at once makes the user do the filtering, and a
 * wedding template offered to somebody budgeting for a laptop is noise. So the catalogue
 * is only ever read through [forEventType]: pick a type, see the handful of breakdowns
 * that could plausibly apply to it, or build your own from scratch.
 *
 * The shares are a defensible starting point for the Nigerian market, not gospel — a
 * planner is expected to drag them around. Their real job is to stop the app from
 * opening on a blank page, and to start accumulating the category-level data that a
 * cost-estimating model would later need to be any good.
 *
 * Templates are not persisted by id: applying one writes budget lines and the template is
 * forgotten. Retiring or renaming an entry here therefore cannot orphan saved data.
 */
object BudgetTemplates {

    val all: List<BudgetTemplate> = listOf(

        // --- Personal ---------------------------------------------------------------
        // The one type meant to be used over and over: a salary earner applies the
        // monthly template every month, where a planner applies a wedding template once
        // per client. The rest are one-off things people save towards.
        BudgetTemplate(
            id = "monthly_salary",
            name = "Monthly Budget",
            eventType = EventTypes.PERSONAL,
            description = "A month of take-home pay across essentials, savings and the rest",
            allocations = listOf(
                TemplateAllocation("Rent & Utilities", 0.25),
                TemplateAllocation("Food & Groceries", 0.20),
                TemplateAllocation("Savings & Investments", 0.15),
                TemplateAllocation("Transport & Fuel", 0.12),
                TemplateAllocation("Family & Support", 0.08),
                TemplateAllocation("Personal & Leisure", 0.06),
                TemplateAllocation("Airtime & Data", 0.05),
                TemplateAllocation("Health & Insurance", 0.05),
                TemplateAllocation("Buffer", 0.04)
            )
        ),
        BudgetTemplate(
            id = "savings_goal",
            name = "Savings Goal",
            eventType = EventTypes.PERSONAL,
            // The one template whose budget is money coming in rather than going out.
            // Categories are sources, so logging a "spend" against one records a
            // contribution and the remaining figure reads as how far short of the target
            // you still are.
            description = "A target amount broken down by where the money will come from",
            allocations = listOf(
                TemplateAllocation("Monthly Contribution", 0.65),
                TemplateAllocation("Side Income", 0.15),
                TemplateAllocation("Bonus & Windfalls", 0.12),
                TemplateAllocation("Returns & Interest", 0.08)
            )
        ),
        BudgetTemplate(
            id = "home_setup",
            name = "Moving / Setting Up a Home",
            eventType = EventTypes.PERSONAL,
            description = "Furnishing and equipping a new place from empty",
            allocations = listOf(
                TemplateAllocation("Furniture", 0.25),
                TemplateAllocation("Appliances", 0.25),
                TemplateAllocation("Electronics", 0.12),
                TemplateAllocation("Kitchen Items", 0.12),
                TemplateAllocation("Bedding & Curtains", 0.10),
                TemplateAllocation("Moving & Logistics", 0.08),
                TemplateAllocation("Contingency", 0.08)
            )
        ),
        BudgetTemplate(
            id = "school_expenses",
            name = "School Expenses",
            eventType = EventTypes.PERSONAL,
            description = "A term or session of fees, books and upkeep",
            allocations = listOf(
                TemplateAllocation("Tuition & Fees", 0.50),
                TemplateAllocation("Books & Materials", 0.12),
                TemplateAllocation("Feeding & Upkeep", 0.12),
                TemplateAllocation("Transport", 0.10),
                TemplateAllocation("Uniform & Kit", 0.08),
                TemplateAllocation("Exams & Levies", 0.05),
                TemplateAllocation("Contingency", 0.03)
            )
        ),
        BudgetTemplate(
            id = "laptop_purchase",
            name = "Buying a Laptop",
            eventType = EventTypes.PERSONAL,
            description = "The machine plus everything that has to come with it",
            allocations = listOf(
                TemplateAllocation("Laptop", 0.78),
                TemplateAllocation("Accessories", 0.08),
                TemplateAllocation("Warranty & Protection", 0.05),
                TemplateAllocation("Software & Licences", 0.05),
                TemplateAllocation("Contingency", 0.04)
            )
        ),
        BudgetTemplate(
            id = "business_startup",
            name = "Starting a Business",
            eventType = EventTypes.PERSONAL,
            description = "Getting a small business open and stocked",
            allocations = listOf(
                TemplateAllocation("Initial Stock", 0.25),
                TemplateAllocation("Equipment & Tools", 0.22),
                TemplateAllocation("Rent & Setup", 0.18),
                TemplateAllocation("Working Capital", 0.12),
                TemplateAllocation("Branding & Marketing", 0.10),
                TemplateAllocation("Registration & Licences", 0.08),
                TemplateAllocation("Contingency", 0.05)
            )
        ),

        // --- Birthday ---------------------------------------------------------------
        BudgetTemplate(
            id = "birthday_party",
            name = "Birthday Party",
            eventType = "Birthday",
            description = "Milestone birthday with catering and entertainment",
            allocations = listOf(
                TemplateAllocation("Catering & Drinks", 0.35),
                TemplateAllocation("Venue & Rentals", 0.20),
                TemplateAllocation("Decoration", 0.12),
                TemplateAllocation("Entertainment (MC, DJ)", 0.10),
                TemplateAllocation("Cake & Small Chops", 0.08),
                TemplateAllocation("Photography", 0.07),
                TemplateAllocation("Contingency", 0.08)
            )
        ),
        BudgetTemplate(
            id = "birthday_dinner",
            name = "Birthday Dinner",
            eventType = "Birthday",
            description = "A table booked for a small group rather than a hall",
            allocations = listOf(
                TemplateAllocation("Restaurant & Food", 0.45),
                TemplateAllocation("Drinks", 0.18),
                TemplateAllocation("Cake", 0.10),
                TemplateAllocation("Decoration", 0.08),
                TemplateAllocation("Photography", 0.07),
                TemplateAllocation("Gifts & Favours", 0.07),
                TemplateAllocation("Transport", 0.05)
            )
        ),
        BudgetTemplate(
            id = "birthday_trip",
            name = "Birthday Trip",
            eventType = "Birthday",
            description = "Travelling for the birthday instead of hosting one",
            allocations = listOf(
                TemplateAllocation("Transport & Flights", 0.28),
                TemplateAllocation("Accommodation", 0.27),
                TemplateAllocation("Food & Drinks", 0.18),
                TemplateAllocation("Activities & Tours", 0.12),
                TemplateAllocation("Shopping & Souvenirs", 0.08),
                TemplateAllocation("Contingency", 0.07)
            )
        ),

        // --- Wedding ----------------------------------------------------------------
        BudgetTemplate(
            id = "traditional_wedding",
            name = "Traditional Wedding",
            eventType = "Wedding",
            description = "Traditional ceremony with full catering and aso-ebi",
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
                TemplateAllocation("Entertainment (MC, DJ, Band)", 0.07),
                TemplateAllocation("Attire & Beauty", 0.07),
                TemplateAllocation("Cake & Small Chops", 0.05),
                TemplateAllocation("Logistics & Transport", 0.04),
                TemplateAllocation("Contingency", 0.06)
            )
        ),
        BudgetTemplate(
            id = "engagement",
            name = "Engagement",
            eventType = "Wedding",
            description = "Introduction or engagement ceremony, ring included",
            allocations = listOf(
                TemplateAllocation("Catering & Drinks", 0.30),
                TemplateAllocation("Venue & Rentals", 0.14),
                TemplateAllocation("Decoration", 0.12),
                TemplateAllocation("Photography & Video", 0.10),
                TemplateAllocation("Attire & Beauty", 0.10),
                TemplateAllocation("Ring & Gifts", 0.10),
                TemplateAllocation("Entertainment", 0.07),
                TemplateAllocation("Contingency", 0.07)
            )
        ),

        // --- Social Gathering -------------------------------------------------------
        BudgetTemplate(
            id = "house_party",
            name = "House Party",
            eventType = "Social Gathering",
            description = "Hosting at home — drinks-led, no venue to pay for",
            allocations = listOf(
                TemplateAllocation("Drinks", 0.30),
                TemplateAllocation("Food & Small Chops", 0.28),
                TemplateAllocation("Music & Sound", 0.14),
                TemplateAllocation("Decoration", 0.10),
                TemplateAllocation("Disposables & Cleanup", 0.08),
                TemplateAllocation("Extras", 0.10)
            )
        ),
        BudgetTemplate(
            id = "social_dinner",
            name = "Dinner",
            eventType = "Social Gathering",
            description = "A sit-down dinner for friends or family",
            allocations = listOf(
                TemplateAllocation("Food", 0.45),
                TemplateAllocation("Drinks", 0.20),
                TemplateAllocation("Venue & Table Setting", 0.15),
                TemplateAllocation("Dessert", 0.08),
                TemplateAllocation("Service & Tips", 0.07),
                TemplateAllocation("Transport", 0.05)
            )
        ),
        BudgetTemplate(
            id = "get_together",
            name = "Get-together",
            eventType = "Social Gathering",
            description = "Informal hangout, reunion or catch-up",
            allocations = listOf(
                TemplateAllocation("Food & Small Chops", 0.35),
                TemplateAllocation("Drinks", 0.25),
                TemplateAllocation("Venue & Rentals", 0.15),
                TemplateAllocation("Games & Activities", 0.10),
                TemplateAllocation("Decoration", 0.08),
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

        // --- Corporate --------------------------------------------------------------
        BudgetTemplate(
            id = "corporate_event",
            name = "Corporate Event",
            eventType = "Corporate",
            description = "Product launch, AGM or end-of-year party",
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
            id = "conference",
            name = "Conference",
            eventType = "Corporate",
            description = "Multi-session event with delegates and speakers",
            allocations = listOf(
                TemplateAllocation("Venue & AV", 0.24),
                TemplateAllocation("Catering", 0.22),
                TemplateAllocation("Speakers & Facilitators", 0.12),
                TemplateAllocation("Branding & Print", 0.10),
                TemplateAllocation("Delegate Materials", 0.08),
                TemplateAllocation("Photography & Media", 0.08),
                TemplateAllocation("Staffing & Security", 0.08),
                TemplateAllocation("Contingency", 0.08)
            )
        ),
        BudgetTemplate(
            id = "seminar",
            name = "Seminar",
            eventType = "Corporate",
            description = "Half or full-day training session with handouts",
            allocations = listOf(
                TemplateAllocation("Venue & AV", 0.25),
                TemplateAllocation("Catering", 0.22),
                TemplateAllocation("Facilitators", 0.15),
                TemplateAllocation("Materials & Handouts", 0.12),
                TemplateAllocation("Publicity & Invitations", 0.10),
                TemplateAllocation("Certificates & Souvenirs", 0.08),
                TemplateAllocation("Contingency", 0.08)
            )
        ),
        BudgetTemplate(
            id = "team_building",
            name = "Team Building",
            eventType = "Corporate",
            description = "Offsite or retreat, travel and activities included",
            allocations = listOf(
                TemplateAllocation("Venue & Resort", 0.28),
                TemplateAllocation("Food & Drinks", 0.22),
                TemplateAllocation("Transport", 0.16),
                TemplateAllocation("Activities & Facilitator", 0.16),
                TemplateAllocation("Merchandise & Prizes", 0.10),
                TemplateAllocation("Contingency", 0.08)
            )
        ),

        // --- Other ------------------------------------------------------------------
        BudgetTemplate(
            id = "burial_ceremony",
            name = "Burial Ceremony",
            eventType = EventTypes.OTHER,
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

    /** Declaration order is the display order, so the likeliest template leads each list. */
    private val byType: Map<String, List<BudgetTemplate>> =
        all.groupBy { it.eventType.lowercase() }

    fun byId(id: String): BudgetTemplate? = all.firstOrNull { it.id == id }

    /**
     * The templates belonging to [eventType], and nothing else.
     *
     * An unknown type (an event created before the type list settled, say) returns
     * nothing rather than falling back to the whole catalogue — the custom plan is the
     * better answer there than a list of weddings.
     */
    fun forEventType(eventType: String): List<BudgetTemplate> =
        byType[eventType.lowercase()].orEmpty()
}
