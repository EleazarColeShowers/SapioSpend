package com.el.sapiospend.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Double.formatNaira(): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 0
    return "₦${nf.format(this)}"
}

fun Long.formatDate(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))

/**
 * An amount as it should appear *inside a text field* — plain digits, no separators or
 * currency mark, and no trailing ".0" on a whole number. Whatever comes back out of the
 * field has to parse with toDoubleOrNull, so this deliberately does not use the grouped
 * formatting [formatNaira] applies for display.
 */
fun Double.formatAmountInput(): String =
    if (this == kotlin.math.floor(this) && !this.isInfinite()) "%.0f".format(this) else this.toString()
