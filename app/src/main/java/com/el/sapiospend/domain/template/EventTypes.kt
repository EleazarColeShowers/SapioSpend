package com.el.sapiospend.domain.template

/**
 * The kinds of thing a budget can be for.
 *
 * Lives here rather than in a screen because two screens offer the list and the template
 * catalogue is keyed by it — three copies of the same strings is how "Social Gathering"
 * quietly becomes "Social gathering" somewhere and stops matching its own templates.
 */
object EventTypes {

    /** The event type whose budget is a recurring pay packet rather than a one-off occasion. */
    const val PERSONAL = "Personal"

    /** Kept for events created before the type list settled, and for the burial template. */
    const val OTHER = "Other"

    val ALL: List<String> = listOf(
        PERSONAL,
        "Birthday",
        "Wedding",
        "Social Gathering",
        "Corporate",
        OTHER
    )

    /** One line of orientation under each type on the picker. */
    fun blurbFor(eventType: String): String = when (eventType) {
        PERSONAL -> "Monthly budgets, savings goals and anything you're planning for yourself"
        "Birthday" -> "Parties, dinners and birthday trips"
        "Wedding" -> "Traditional, white wedding and engagement"
        "Social Gathering" -> "House parties, dinners and get-togethers"
        "Corporate" -> "Conferences, seminars and company events"
        else -> "Anything that doesn't fit the others"
    }
}
