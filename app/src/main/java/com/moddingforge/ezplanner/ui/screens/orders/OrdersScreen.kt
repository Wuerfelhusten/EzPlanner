package com.moddingforge.ezplanner.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.Interval
import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.components.AmountRow
import com.moddingforge.ezplanner.ui.components.EmptyHint
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.formatDate
import com.moddingforge.ezplanner.ui.label
import com.moddingforge.ezplanner.ui.theme.Ez

private enum class Sort(val label: String) { AMOUNT("Betrag"), NAME("Name"), DUE("Fälligkeit") }

private enum class Group(val title: String) { INCOME("Einkommen"), EXPENSE("Ausgaben"), SAVING("Sparen & Töpfe"), STOPPED("Pausiert / beendet") }

private fun StandingOrderEntity.group(): Group = when {
    Schedule.nextDueDate(this) == null -> Group.STOPPED
    kind == TxKind.INCOME -> Group.INCOME
    kind == TxKind.EXPENSE -> Group.EXPENSE
    else -> Group.SAVING
}

/** Monthly equivalent: a yearly 120 € order counts as 10 € per month. */
private fun List<StandingOrderEntity>.perMonth(): Long = sumOf { it.amountCents / it.interval.months }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(nav: NavController) {
    val repo = LocalRepository.current
    val orders by repo.standingOrders.collectAsStateWithLifecycle(emptyList())
    val pots by repo.pots.collectAsStateWithLifecycle(emptyList())
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    var sort by rememberSaveable { mutableStateOf(Sort.AMOUNT) }

    val comparator: Comparator<StandingOrderEntity> = when (sort) {
        Sort.AMOUNT -> compareByDescending<StandingOrderEntity> { it.amountCents }.thenBy { it.title.lowercase() }
        Sort.NAME -> compareBy { it.title.lowercase() }
        Sort.DUE -> compareBy<StandingOrderEntity> { Schedule.nextDueDate(it) }.thenBy { it.title.lowercase() }
    }
    val byGroup = orders.groupBy { it.group() }

    val income = byGroup[Group.INCOME].orEmpty().perMonth()
    val expenses = byGroup[Group.EXPENSE].orEmpty().perMonth()
    val saving = byGroup[Group.SAVING].orEmpty().filter { it.kind == TxKind.TO_POT }.perMonth() -
        byGroup[Group.SAVING].orEmpty().filter { it.kind == TxKind.FROM_POT }.perMonth()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Daueraufträge") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.newOrder()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Dauerauftrag") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (orders.isEmpty()) {
                item { EmptyHint("Noch keine Daueraufträge. Lege z. B. Gehalt, Miete und Versicherungen an.") }
            } else {
                item {
                    SectionCard(title = "Pro Monat umgerechnet") {
                        AmountRow("Einkommen", income, color = Ez.money.income)
                        AmountRow("Ausgaben", -expenses, signed = true, color = Ez.money.expense)
                        AmountRow("Sparen", -saving, signed = true, color = Ez.money.transfer)
                        AmountRow("Bleibt", income - expenses - saving, emphasized = true, signed = true)
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        item {
                            Text("Sortieren:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(Sort.entries) { s ->
                            FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                        }
                    }
                }
            }

            Group.entries.forEach { group ->
                val list = byGroup[group].orEmpty()
                if (list.isEmpty()) return@forEach
                item(key = "g-$group") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                        Text(group.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (group != Group.STOPPED) {
                            Text(
                                "${Money.format(list.perMonth())} / Monat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Within a group: one block per interval (monthly first), each sorted.
                Interval.entries.forEach { interval ->
                    val inInterval = list.filter { it.interval == interval }.sortedWith(comparator)
                    if (inInterval.isEmpty()) return@forEach
                    item(key = "i-$group-$interval") {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
                            Text(
                                interval.label(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                Money.format(inInterval.sumOf { it.amountCents }),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(inInterval, key = { it.id }) { order ->
                        OrderItem(
                            order,
                            subtitleExtra = when (order.kind) {
                                TxKind.EXPENSE -> types.firstOrNull { it.id == order.expenseTypeId }?.name
                                TxKind.TO_POT, TxKind.FROM_POT -> pots.firstOrNull { it.id == order.potId }?.name
                                else -> null
                            },
                            onClick = { nav.navigate(Routes.editOrder(order.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderItem(order: StandingOrderEntity, subtitleExtra: String?, onClick: () -> Unit) {
    val positive = order.kind == TxKind.INCOME || order.kind == TxKind.FROM_POT
    val next = Schedule.nextDueDate(order)
    val color: Color = when {
        next == null -> MaterialTheme.colorScheme.onSurfaceVariant
        order.kind == TxKind.TO_POT || order.kind == TxKind.FROM_POT -> Ez.money.transfer
        positive -> Ez.money.income
        else -> Ez.money.expense
    }
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(order.title, style = MaterialTheme.typography.titleMedium)
                val status = when {
                    !order.active -> "pausiert"
                    next == null -> "beendet"
                    else -> "nächste: ${next.formatDate()}"
                }
                Text(
                    listOfNotNull(subtitleExtra, status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.formatSigned(if (positive) order.amountCents else -order.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
                if (order.interval != Interval.MONTHLY && next != null) {
                    Text(
                        "≈ ${Money.format(order.amountCents / order.interval.months)} / Monat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
