package com.moddingforge.ezplanner.ui.screens.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.Settings
import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.BudgetState
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.domain.MonthSummaryCalculator
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.components.AmountRow
import com.moddingforge.ezplanner.ui.components.BudgetBar
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.formatDate
import com.moddingforge.ezplanner.ui.monthName
import com.moddingforge.ezplanner.ui.screens.booking.SetBalanceDialog
import com.moddingforge.ezplanner.ui.subtitle
import com.moddingforge.ezplanner.ui.theme.Ez
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(nav: NavController) {
    val repo = LocalRepository.current
    val balance by repo.accountBalance.collectAsStateWithLifecycle(0L)
    val transactions by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val pots by repo.pots.collectAsStateWithLifecycle(emptyList())
    val potBalances by repo.potBalances.collectAsStateWithLifecycle(emptyMap())
    val orders by repo.standingOrders.collectAsStateWithLifecycle(emptyList())
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.collectAsStateWithLifecycle(Settings())

    val month = YearMonth.now()
    val summary = remember(transactions, types, month) {
        MonthSummaryCalculator.calculate(month, transactions, types, emptyList())
    }
    val budget = BudgetState(settings.monthlyBudgetCents, summary.budgetUsed, settings.warnPercent)
    var showBalanceDialog by remember { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    val activePots = pots.filter { !it.archived }
    val savings = activePots.filter { it.kind == PotKind.SAVINGS }
    val debts = activePots.filter { it.kind == PotKind.DEBT }
    val archived = pots.filter { it.archived }
    val today = LocalDate.now()
    val upcoming = orders
        .mapNotNull { o -> Schedule.nextDueDate(o)?.let { o to it } }
        .filter { (_, d) -> !d.isAfter(today.plusDays(14)) }
        .sortedBy { it.second }
        .take(5)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Übersicht") },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, "Einstellungen")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.newTransaction(TxKind.EXPENSE)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Ausgabe") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(title = "Kontostand", onClick = { showBalanceDialog = true }) {
                    Text(
                        Money.format(balance),
                        style = MaterialTheme.typography.displaySmall,
                        color = if (balance < 0) Ez.money.expense else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Tippen, um den Kontostand zu korrigieren",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                BudgetCard(month, budget, onSetBudget = { nav.navigate(Routes.SETTINGS) })
            }

            item {
                PotSection(
                    title = "Spartöpfe",
                    pots = savings,
                    balances = potBalances,
                    addLabel = "Spartopf hinzufügen",
                    onAdd = { nav.navigate(Routes.newPot(PotKind.SAVINGS)) },
                    onOpen = { nav.navigate(Routes.pot(it.id)) },
                )
            }
            item {
                PotSection(
                    title = "Schuldentöpfe",
                    pots = debts,
                    balances = potBalances,
                    addLabel = "Schuldentopf hinzufügen",
                    onAdd = { nav.navigate(Routes.newPot(PotKind.DEBT)) },
                    onOpen = { nav.navigate(Routes.pot(it.id)) },
                )
            }

            if (upcoming.isNotEmpty()) {
                item {
                    SectionCard(title = "Demnächst fällig") {
                        upcoming.forEach { (order, date) ->
                            val positive = order.kind == TxKind.INCOME || order.kind == TxKind.FROM_POT
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.navigate(Routes.editOrder(order.id)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(order.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (date == today) "heute" else date.formatDate(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    Money.formatSigned(if (positive) order.amountCents else -order.amountCents),
                                    color = if (positive) Ez.money.income else Ez.money.expense,
                                )
                            }
                        }
                    }
                }
            }

            if (archived.isNotEmpty()) {
                item {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Icon(if (showArchived) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        Text("Archivierte Töpfe (${archived.size})")
                    }
                }
                if (showArchived) {
                    items(archived, key = { it.id }) { pot ->
                        SectionCard(onClick = { nav.navigate(Routes.pot(pot.id)) }) {
                            PotRow(pot, potBalances[pot.id] ?: 0)
                        }
                    }
                }
            }
        }
    }

    if (showBalanceDialog) {
        SetBalanceDialog(currentBalance = balance, onDismiss = { showBalanceDialog = false })
    }
}

@Composable
fun BudgetCard(month: YearMonth, budget: BudgetState, onSetBudget: () -> Unit) {
    SectionCard(title = "Monatsbudget ${month.monthName()}") {
        if (budget.budget <= 0) {
            Text(
                "Noch kein Monatsbudget festgelegt. Ausgaben vom Typ „Budget“ werden davon abgezogen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSetBudget) { Text("Budget festlegen") }
            return@SectionCard
        }
        val color = when {
            budget.isOver -> Ez.money.expense
            budget.isWarning -> Ez.money.warning
            else -> Ez.money.income
        }
        Text(
            Money.format(budget.available),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
        Text(
            if (budget.isOver) "über dem Budget" else "noch verfügbar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BudgetBar(budget.ratio, color)
        AmountRow("Verbraucht", budget.used, indent = true)
        AmountRow("Budget", budget.budget, indent = true)
        if (budget.isWarning) {
            Text(
                "Achtung: ${(budget.ratio * 100).toInt()} % des Budgets sind verbraucht.",
                style = MaterialTheme.typography.bodySmall,
                color = Ez.money.warning,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PotSection(
    title: String,
    pots: List<PotEntity>,
    balances: Map<Long, Long>,
    addLabel: String,
    onAdd: () -> Unit,
    onOpen: (PotEntity) -> Unit,
) {
    SectionCard(
        title = title,
        trailing = {
            if (pots.isNotEmpty()) {
                Text(
                    Money.format(pots.sumOf { signedForTotal(it, balances[it.id] ?: 0) }),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) {
        pots.forEachIndexed { i, pot ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(pot) }
                    .padding(vertical = 8.dp),
            ) { PotRow(pot, balances[pot.id] ?: 0) }
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Default.Add, null)
            Text(addLabel)
        }
    }
}

/** For the section total: what I owe counts negative. */
private fun signedForTotal(pot: PotEntity, balance: Long) =
    if (pot.direction == DebtDirection.I_OWE) -balance else balance

@Composable
fun PotRow(pot: PotEntity, balance: Long) {
    val color = when {
        pot.kind == PotKind.SAVINGS -> MaterialTheme.colorScheme.onSurface
        pot.direction == DebtDirection.I_OWE -> Ez.money.expense
        else -> Ez.money.income
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pot.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (pot.kind == PotKind.DEBT && balance == 0L) "${pot.subtitle()} · beglichen" else pot.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money.format(balance), style = MaterialTheme.typography.titleMedium, color = color)
    }
    val target = pot.targetCents
    if (pot.kind == PotKind.SAVINGS && target != null && target > 0) {
        BudgetBar(balance.toFloat() / target, Ez.money.transfer)
        Text(
            "${(balance * 100 / target).coerceAtLeast(0)} % von ${Money.format(target)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
