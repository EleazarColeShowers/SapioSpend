package com.el.sapiospend.domain.recurring

import java.util.Calendar

/**
 * How often a recurring cost comes round.
 *
 * A short list on purpose: these are the three rhythms budgets actually run on — a
 * weekly rehearsal space, a fortnightly instalment, a monthly retainer or rent. An
 * arbitrary "every N days" rule would be more general and would mean a picker nobody can
 * answer quickly.
 */
enum class Recurrence(val label: String, private val field: Int, private val step: Int) {
    WEEKLY("Every week", Calendar.DAY_OF_YEAR, 7),
    FORTNIGHTLY("Every 2 weeks", Calendar.DAY_OF_YEAR, 14),
    MONTHLY("Every month", Calendar.MONTH, 1);

    /**
     * The occurrence after [from].
     *
     * Calendar rather than millisecond arithmetic: a week is not always 168 hours across
     * a daylight-saving change, and adding a fixed span would drift the time of day and
     * eventually the date. Calendar also clamps a monthly rule started on the 31st down
     * to the last day of a shorter month — after which it keeps the shorter day, which
     * is the trade for not carrying a separate "intended day of month" around.
     */
    fun next(from: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = from
            add(field, step)
        }.timeInMillis

    companion object {
        val DEFAULT = MONTHLY

        /** An unrecognised stored value falls back rather than throwing on a database read. */
        fun fromName(name: String?): Recurrence =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
