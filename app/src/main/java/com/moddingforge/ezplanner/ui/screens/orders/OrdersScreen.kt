package com.moddingforge.ezplanner.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.components.AmountRow
import com.moddingforge.ezplanner.ui.components.EmptyHint
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.components.SectionTitle
import com.moddingforge.ezplanner.ui.formatDate
import com.moddingforge.ezplanner.ui.label
import com.moddingforge.ezplanner.ui.theme.Ez

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(nav: NavController) {
    val repo = LocalRepository.current
    val orders by repo.standingOrders.collectAsStateWithLifecycle(emptyList())
    val pots by repo.pots.collectAsStateWithLifecycle(emptyList())
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())

    val running = orders.filter { Schedule.nextDueDate(it) != null }
    val stopped = orders - running.toSet()
    val groups = listOf(
        "Einkommen" to running.filter { it.kind == TxKind.INCOME },
        "Ausgaben" to running.filter { it.kind == TxKind.EXPENSE },
        "Töpfe" to running.filter { it.kind == TxKind.TO_POT || it.kind == TxKind.FROM_POT },
        "Pausiert / beendet" to stopped,
    ).filter { it.second.isNotEmpty() }

    // Monthly equivalent: a yearly 120 € order counts as 10 € per month.
    fun monthly(list: List<StandingOrderEntity>) = list.sumOf { it.amountCents / it.interval.months }
    val monthlyIncome = monthly(running.filter { it.kind == TxKind.INCOME || it.kind == TxKind.FROM_POT })
    val monthlyOut = monthly(running.filter { it.kind == TxKind.EXPENSE || it.kind == TxKind.TO_POT })

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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (orders.isEmpty()) {
                item { EmptyHint("Noch keine Daueraufträge. Lege z. B. Gehalt, Miete und Versicherungen an.") }
            } else {
                item {
                    SectionCard(title = "Pro Monat umgerechnet") {
                        AmountRow("Eingänge", monthlyIncome, color = Ez.money.income)
                        AmountRow("Abgänge", monthlyOut, color = Ez.money.expense)
                        AmountRow("Bleibt", monthlyIncome - monthlyOut, emphasized = true, signed = true)
                    }
                }
            }
            groups.forEach { (title, list) ->
                item(key = "h-$title") { SectionTitle(title) }
                items(list, key = { it.id }) { order ->
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

@Composable
private fun OrderItem(order: StandingOrderEntity, subtitleExtra: String?, onClick: () -> Unit) {
    val positive = order.kind == TxKind.INCOME || order.kind == TxKind.FROM_POT
    val next = Schedule.nextDueDate(order)
    val color = when {
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
                    listOfNotNull(order.interval.label(), subtitleExtra, status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                Money.formatSigned(if (positive) order.amountCents else -order.amountCents),
                style = MaterialTheme.typography.titleMedium,
                color = color,
            )
        }
    }
}
