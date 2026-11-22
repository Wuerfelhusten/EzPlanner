package com.moddingforge.ezplanner.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Money is always handled as Long cents. */
object Money {
    private val locale: Locale = Locale.GERMANY

    private fun numberFormat(): NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    /** 123456 -> "1.234,56 €", -500 -> "-5,00 €". */
    fun format(cents: Long): String = numberFormat().format(BigDecimal.valueOf(cents, 2)) + " €"

    /** Like [format] but with an explicit "+" for positive amounts. */
    fun formatSigned(cents: Long): String = (if (cents > 0) "+" else "") + format(cents)

    /** Value for an input field: 123456 -> "1234,56", 0 -> "". */
    fun toInput(cents: Long): String =
        if (cents == 0L) "" else BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

    /**
     * Parses user input such as "12", "12,5", "1.234,56", "1234.56" or "-20" into cents.
     * A single "." followed by at most two digits is read as decimal point, otherwise "." is a
     * thousands separator. Returns null for anything that is not a valid amount.
     */
    fun parse(input: String): Long? {
        var s = input.trim().replace(" ", "").replace("€", "").replace(" ", "")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        if (negative || s.startsWith("+")) s = s.substring(1)
        if (s.isEmpty()) return null

        val normalized = when {
            s.contains(',') -> {
                if (s.count { it == ',' } > 1) return null
                s.replace(".", "").replace(',', '.')
            }
            s.count { it == '.' } == 1 && s.substringAfter('.').length <= 2 -> s
            else -> s.replace(".", "")
        }
        if (!normalized.matches(Regex("""\d+(\.\d{0,2})?"""))) return null
        val value = BigDecimal(normalized).movePointRight(2).longValueExact()
        return if (negative) -value else value
    }
}
