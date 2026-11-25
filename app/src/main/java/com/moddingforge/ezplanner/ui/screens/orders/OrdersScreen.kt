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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Ledger
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.components.AmountRow
import com.moddingforge.ezplanner.ui.components.EmptyHint
import com.moddingforge.ezplanner.ui.components.HSpace
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.formatDate
import com.moddingforge.ezplanner.ui.label
import com.moddingforge.ezplanner.ui.theme.Ez
import java.time.LocalDate

private enum class Sort(val label: String) { AMOUNT("Betrag"), NAME("Name"), DUE("Fälligkeit") }

/** One block of the list: income, one per expense type, saving, stopped. */
private data class Section(val key: String, val title: String, val orders: List<StandingOrderEntity>, val showPerMonth: Boolean = true)

/** Monthly equivalent: a yearly 120 € order counts as 10 € per month. */
private fun List<StandingOrderEntity>.perMonth(): Long = sumOf { it.amountCents / it.interval.months }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(nav: NavController) {
    val repo = LocalRepository.current
    val orders by repo.standingOrders.collectAsStateWithLifecycle(emptyList())
    val transactions by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val pots by repo.pots.collectAsStateWithLifecycle(emptyList())
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    var sort by rememberSaveable { mutableStateOf(Sort.AMOUNT) }
    val today = LocalDate.now()

    val comparator: Comparator<StandingOrderEntity> = when (sort) {
        Sort.AMOUNT -> compareByDescending<StandingOrderEntity> { it.amountCents }.thenBy { it.title.lowercase() }
        Sort.NAME -> compareBy { it.title.lowercase() }
        Sort.DUE -> compareBy<StandingOrderEntity> { Schedule.nextDueDate(it) }.thenBy { it.title.lowercase() }
    }

    val running = orders.filter { Schedule.nextDueDate(it) != null }
    val stopped = orders.filter { Schedule.nextDueDate(it) == null }
    val expenses = running.filter { it.kind == TxKind.EXPENSE }
    val typeIds = types.map { it.id }.toSet()
    val sections = buildList {
        add(Section("income", "Einkommen", running.filter { it.kind == TxKind.INCOME }))
        // Every expense type gets its own block: Fixkosten, Budget, Sonderausgaben, Investieren, own types.
        types.sortedBy { it.countsAsSaving }.forEach { t -> add(Section("type-${t.id}", t.name, expenses.filter { it.expenseTypeId == t.id })) }
        add(Section("type-none", "Ausgaben ohne Typ", expenses.filter { it.expenseTypeId !in typeIds }))
        add(Section("saving", "Sparen & Töpfe", running.filter { it.kind == TxKind.TO_POT || it.kind == TxKind.FROM_POT }))
        add(Section("stopped", "Pausiert / beendet", stopped, showPerMonth = false))
    }.filter { it.orders.isNotEmpty() }

    // One-time bookings with a future date (e.g. workshop next month) - planned, not yet in the balance.
    val plannedOnce = transactions
        .filter { it.standingOrderId == null && it.dueDate == null && it.date.isAfter(today) }
        .sortedBy { it.date }

    val income = running.filter { it.kind == TxKind.INCOME }.perMonth()
    val savingTypes = types.filter { it.countsAsSaving }
    val savingTypeIds = savingTypes.map { it.id }.toSet()
    val intoPots = running.filter { it.kind == TxKind.TO_POT }.perMonth() - running.filter { it.kind == TxKind.FROM_POT }.perMonth()
    val saving = intoPots + expenses.filter { it.expenseTypeId in savingTypeIds }.perMonth()
    val expenseTotal = expenses.filter { it.expenseTypeId !in savingTypeIds }.perMonth()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Aufträge") }) },
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
            if (orders.isEmpty() && plannedOnce.isEmpty()) {
                item { EmptyHint("Noch keine Daueraufträge. Lege z. B. Gehalt, Miete und Versicherungen an.") }
            }
            if (orders.isNotEmpty()) {
                item {
                    SectionCard(title = "Daueraufträge pro Monat umgerechnet") {
                        AmountRow("Einkommen", income, color = Ez.money.income)
                        AmountRow("Ausgaben", -expenseTotal, signed = true, color = Ez.money.expense)
                        types.filter { !it.countsAsSaving }.forEach { t ->
                            val v = expenses.filter { it.expenseTypeId == t.id }.perMonth()
                            if (v > 0) AmountRow("Davon ${t.name}", -v, signed = true, indent = true)
                        }
                        AmountRow("Sparen", -saving, signed = true, color = Ez.money.transfer)
                        savingTypes.forEach { t ->
                            val v = expenses.filter { it.expenseTypeId == t.id }.perMonth()
                            if (v > 0) AmountRow("Davon ${t.name}", -v, signed = true, indent = true)
                        }
                        AmountRow("Bleibt", income - expenseTotal - saving, emphasized = true, signed = true)
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

            sections.forEach { section ->
                item(key = "g-${section.key}") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                        Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (section.showPerMonth) {
                            Text(
                                "${Money.format(section.orders.perMonth())} / Monat",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Within a section: one block per interval (monthly first), each sorted.
                Interval.entries.forEach { interval ->
                    val inInterval = section.orders.filter { it.interval == interval }.sortedWith(comparator)
                    if (inInterval.isEmpty()) return@forEach
                    item(key = "i-${section.key}-$interval") {
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
                            isSaving = order.kind == TxKind.EXPENSE && order.expenseTypeId in savingTypeIds,
                            subtitleExtra = when (order.kind) {
                                TxKind.EXPENSE -> categories.firstOrNull { it.id == order.categoryId }?.name
                                TxKind.TO_POT, TxKind.FROM_POT -> pots.firstOrNull { it.id == order.potId }?.name
                                else -> null
                            },
                            onClick = { nav.navigate(Routes.editOrder(order.id)) },
                        )
                    }
                }
            }

            item(key = "planned-header") {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Geplante Einzelbuchungen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (plannedOnce.isNotEmpty()) {
                        Text(
                            Money.formatSigned(plannedOnce.sumOf { Ledger.accountDelta(it) }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (plannedOnce.isEmpty()) {
                item(key = "planned-empty") {
                    Text(
                        "Einmalige Buchungen mit einem Datum in der Zukunft erscheinen hier, z. B. eine Sonderausgabe für die Werkstatt im nächsten Monat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(plannedOnce, key = { "p${it.id}" }) { tx ->
                PlannedItem(
                    tx,
                    isSaving = tx.kind == TxKind.EXPENSE && tx.expenseTypeId in savingTypeIds,
                    subtitle = listOfNotNull(
                        types.firstOrNull { it.id == tx.expenseTypeId }?.name,
                        categories.firstOrNull { it.id == tx.categoryId }?.name,
                        pots.firstOrNull { it.id == tx.potId }?.name,
                    ).joinToString(" · "),
                    onClick = { nav.navigate(Routes.editTransaction(tx.id)) },
                )
            }
            item(key = "planned-add") {
                OutlinedButton(onClick = { nav.navigate(Routes.newTransaction(TxKind.EXPENSE)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Event, null)
                    HSpace(8)
                    Text("Einmalige Buchung planen")
                }
            }
        }
    }
}

@Composable
private fun OrderItem(order: StandingOrderEntity, isSaving: Boolean, subtitleExtra: String?, onClick: () -> Unit) {
    val positive = order.kind == TxKind.INCOME || order.kind == TxKind.FROM_POT
    val next = Schedule.nextDueDate(order)
    val color: Color = when {
        next == null -> MaterialTheme.colorScheme.onSurfaceVariant
        isSaving || order.kind == TxKind.TO_POT || order.kind == TxKind.FROM_POT -> Ez.money.transfer
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

@Composable
private fun PlannedItem(tx: TransactionEntity, isSaving: Boolean, subtitle: String, onClick: () -> Unit) {
    val delta = Ledger.accountDelta(tx)
    val color = if (isSaving) Ez.money.transfer else when (tx.kind) {
        TxKind.TO_POT, TxKind.FROM_POT, TxKind.POT_ADJUSTMENT -> Ez.money.transfer
        TxKind.ADJUSTMENT -> Ez.money.adjustment
        else -> if (delta >= 0) Ez.money.income else Ez.money.expense
    }
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOf(tx.date.formatDate(), subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.formatSigned(if (tx.kind == TxKind.POT_ADJUSTMENT) tx.amountCents else delta), style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}
