package com.moddingforge.ezplanner.ui

import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.Interval
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.TxKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val German = Locale.GERMANY
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", German)
private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", German)

fun LocalDate.formatDate(): String = format(dateFormat)

fun LocalDate.formatDayHeader(): String = format(dayHeaderFormat)

fun YearMonth.monthName(): String = month.getDisplayName(TextStyle.FULL_STANDALONE, German)

fun YearMonth.label(): String = "${monthName()} $year"

fun Interval.label(): String = when (this) {
    Interval.MONTHLY -> "Monatlich"
    Interval.QUARTERLY -> "Vierteljährlich"
    Interval.HALF_YEARLY -> "Halbjährlich"
    Interval.YEARLY -> "Jährlich"
}

fun TxKind.label(): String = when (this) {
    TxKind.INCOME -> "Einkommen"
    TxKind.EXPENSE -> "Ausgabe"
    TxKind.TO_POT -> "In Topf"
    TxKind.FROM_POT -> "Aus Topf"
    TxKind.ADJUSTMENT -> "Kontostand-Anpassung"
    TxKind.POT_ADJUSTMENT -> "Topf-Korrektur"
}

/** Human wording of a pot transfer, depending on the pot (savings, lent, borrowed). */
fun PotEntity?.actionLabel(kind: TxKind): String {
    val pot = this
    return when {
        pot == null || pot.kind == PotKind.SAVINGS -> when (kind) {
            TxKind.TO_POT -> "Einzahlen"
            TxKind.FROM_POT -> "Entnehmen"
            else -> "Korrektur / Zinsen"
        }
        pot.direction == DebtDirection.I_OWE -> when (kind) {
            TxKind.FROM_POT -> "Geld geliehen bekommen"
            TxKind.TO_POT -> "Zurückgezahlt"
            else -> "Schuld korrigieren"
        }
        else -> when (kind) {
            TxKind.TO_POT -> "Geld verliehen"
            TxKind.FROM_POT -> "Rückzahlung erhalten"
            else -> "Forderung korrigieren"
        }
    }
}

/** "Max schuldet mir" / "Ich schulde Max" / "Spartopf". */
fun PotEntity.subtitle(): String = when {
    kind == PotKind.SAVINGS -> "Spartopf"
    direction == DebtDirection.I_OWE -> "Ich schulde ${person.orEmpty().ifBlank { "jemandem" }}"
    else -> "${person.orEmpty().ifBlank { "Jemand" }} schuldet mir"
}
