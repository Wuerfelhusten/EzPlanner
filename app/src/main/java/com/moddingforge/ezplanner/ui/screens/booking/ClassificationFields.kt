package com.moddingforge.ezplanner.ui.screens.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.DefaultData
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.data.db.IncomeType
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.ui.actionLabel
import com.moddingforge.ezplanner.ui.components.ChoiceChips
import com.moddingforge.ezplanner.ui.components.ColorDot
import com.moddingforge.ezplanner.ui.subtitle

enum class KindGroup(val label: String) {
    EXPENSE("Ausgabe"),
    INCOME("Einkommen"),
    POT("Topf"),
}

/** What a booking or standing order is: kind plus the details that belong to that kind. */
data class Classification(
    val group: KindGroup = KindGroup.EXPENSE,
    val expenseTypeId: Long = DefaultData.TYPE_BUDGET,
    val categoryId: Long? = null,
    val incomeType: IncomeType = IncomeType.REGULAR,
    val potId: Long? = null,
    /** TO_POT, FROM_POT or POT_ADJUSTMENT. */
    val potAction: TxKind = TxKind.TO_POT,
) {
    val kind: TxKind
        get() = when (group) {
            KindGroup.EXPENSE -> TxKind.EXPENSE
            KindGroup.INCOME -> TxKind.INCOME
            KindGroup.POT -> potAction
        }

    companion object {
        fun of(
            kind: TxKind,
            expenseTypeId: Long?,
            categoryId: Long?,
            incomeType: IncomeType?,
            potId: Long?,
            defaultExpenseType: Long = DefaultData.TYPE_BUDGET,
        ) = Classification(
            group = when (kind) {
                TxKind.INCOME -> KindGroup.INCOME
                TxKind.TO_POT, TxKind.FROM_POT, TxKind.POT_ADJUSTMENT -> KindGroup.POT
                else -> KindGroup.EXPENSE
            },
            expenseTypeId = expenseTypeId ?: defaultExpenseType,
            categoryId = categoryId,
            incomeType = incomeType ?: IncomeType.REGULAR,
            potId = potId,
            potAction = if (kind == TxKind.FROM_POT || kind == TxKind.POT_ADJUSTMENT) kind else TxKind.TO_POT,
        )
    }
}

/**
 * Kind selector and the fields that depend on it. Only the fields of the selected kind are
 * shown: expense type + category for expenses, income type for income, pot + action for pots.
 */
@Composable
fun ClassificationFields(
    value: Classification,
    onChange: (Classification) -> Unit,
    types: List<ExpenseTypeEntity>,
    categories: List<CategoryEntity>,
    pots: List<PotEntity>,
    allowPotAdjustment: Boolean,
    showError: Boolean,
    onCreatePot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            KindGroup.entries.forEachIndexed { i, g ->
                SegmentedButton(
                    selected = value.group == g,
                    onClick = { onChange(value.copy(group = g)) },
                    shape = SegmentedButtonDefaults.itemShape(i, KindGroup.entries.size),
                ) { Text(g.label) }
            }
        }

        when (value.group) {
            KindGroup.EXPENSE -> {
                ChoiceChips(
                    title = "Ausgabentyp",
                    options = types.map { it.id },
                    selected = value.expenseTypeId,
                    onSelect = { onChange(value.copy(expenseTypeId = it)) },
                    label = { id -> types.firstOrNull { it.id == id }?.name.orEmpty() },
                )
                types.firstOrNull { it.id == value.expenseTypeId }?.let { t ->
                    Text(
                        if (t.countsTowardBudget) "Wird vom Monatsbudget abgezogen." else "Zählt nicht zum Monatsbudget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChoiceChips(
                    title = "Kategorie (optional)",
                    options = listOf<Long?>(null) + categories.map { it.id },
                    selected = value.categoryId,
                    onSelect = { onChange(value.copy(categoryId = it)) },
                    label = { id -> id?.let { categories.firstOrNull { c -> c.id == it }?.name } ?: "Keine" },
                    leading = { id ->
                        categories.firstOrNull { it.id == id }?.let { ColorDot(Color(it.color.toInt())) }
                    },
                )
            }

            KindGroup.INCOME -> ChoiceChips(
                title = "Art des Einkommens",
                options = IncomeType.entries,
                selected = value.incomeType,
                onSelect = { onChange(value.copy(incomeType = it)) },
                label = { if (it == IncomeType.REGULAR) "Regulär (z. B. Gehalt)" else "Sonstiges" },
            )

            KindGroup.POT -> {
                if (pots.isEmpty()) {
                    Text(
                        "Noch kein Spar- oder Schuldentopf vorhanden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCreatePot) { Text("Topf anlegen") }
                } else {
                    ChoiceChips(
                        title = "Topf",
                        options = pots.map { it.id },
                        selected = value.potId ?: -1L,
                        onSelect = { onChange(value.copy(potId = it)) },
                        label = { id -> pots.firstOrNull { it.id == id }?.let { "${it.name} · ${it.subtitle()}" }.orEmpty() },
                    )
                    if (showError && value.potId == null) {
                        Text("Bitte einen Topf wählen", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    val pot = pots.firstOrNull { it.id == value.potId }
                    val actions = listOf(TxKind.TO_POT, TxKind.FROM_POT) +
                        if (allowPotAdjustment) listOf(TxKind.POT_ADJUSTMENT) else emptyList()
                    ChoiceChips(
                        title = "Vorgang",
                        options = actions,
                        selected = value.potAction,
                        onSelect = { onChange(value.copy(potAction = it)) },
                        label = { pot.actionLabel(it) },
                    )
                    Text(
                        potActionHint(value.potAction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun potActionHint(action: TxKind) = when (action) {
    TxKind.TO_POT -> "Geld geht vom Konto ab."
    TxKind.FROM_POT -> "Geld kommt aufs Konto."
    else -> "Ändert nur den Topf, nicht das Konto. Negative Beträge verringern den Topf."
}
