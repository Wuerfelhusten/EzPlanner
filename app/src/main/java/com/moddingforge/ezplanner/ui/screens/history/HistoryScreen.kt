package com.moddingforge.ezplanner.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Ledger
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.components.ColorDot
import com.moddingforge.ezplanner.ui.components.EmptyHint
import com.moddingforge.ezplanner.ui.components.HSpace
import com.moddingforge.ezplanner.ui.components.MonthSwitcher
import com.moddingforge.ezplanner.ui.formatDayHeader
import com.moddingforge.ezplanner.ui.label
import com.moddingforge.ezplanner.ui.theme.Ez
import kotlinx.coroutines.launch
import java.time.YearMonth

private enum class Filter(val label: String, val matches: (TransactionEntity) -> Boolean) {
    ALL("Alle", { true }),
    INCOME("Einkommen", { it.kind == TxKind.INCOME }),
    EXPENSE("Ausgaben", { it.kind == TxKind.EXPENSE }),
    POTS("Sparen & Töpfe", { it.kind == TxKind.TO_POT || it.kind == TxKind.FROM_POT || it.kind == TxKind.POT_ADJUSTMENT }),
    ADJUST("Anpassungen", { it.kind == TxKind.ADJUSTMENT }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavController) {
    val repo = LocalRepository.current
    val transactions by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    val pots by repo.pots.collectAsStateWithLifecycle(emptyList())
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    // Sub-filter for expenses: an expense type id (>0) or a category id (<0, negated); 0 = none.
    var sub by rememberSaveable { mutableStateOf(0L) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val inMonth = transactions.filter { YearMonth.from(it.date) == month }
    val visible = inMonth
        .filter(filter.matches)
        .filter { tx ->
            when {
                filter != Filter.EXPENSE || sub == 0L -> true
                sub > 0 -> tx.expenseTypeId == sub
                else -> tx.categoryId == -sub
            }
        }
    val totalIn = visible.sumOf { Ledger.accountDelta(it).coerceAtLeast(0) }
    val totalOut = visible.sumOf { (-Ledger.accountDelta(it)).coerceAtLeast(0) }

    fun delete(tx: TransactionEntity) {
        scope.launch {
            repo.deleteTransaction(tx)
            val result = snackbar.showSnackbar(
                "„${tx.title}“ gelöscht",
                actionLabel = "Rückgängig",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) repo.restoreTransaction(tx)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Verlauf") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { MonthSwitcher(month, { month = it }, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Filter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f; sub = 0 }, label = { Text(f.label) })
                }
            }
            if (filter == Filter.EXPENSE) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(types, key = { "t${it.id}" }) { t ->
                        FilterChip(selected = sub == t.id, onClick = { sub = if (sub == t.id) 0 else t.id }, label = { Text(t.name) })
                    }
                    items(categories, key = { "c${it.id}" }) { c ->
                        FilterChip(
                            selected = sub == -c.id,
                            onClick = { sub = if (sub == -c.id) 0 else -c.id },
                            label = { Text(c.name) },
                            leadingIcon = { ColorDot(Color(c.color.toInt())) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("Ein: ${Money.format(totalIn)}", color = Ez.money.income, style = MaterialTheme.typography.bodyMedium)
                HSpace(16)
                Text("Aus: ${Money.format(totalOut)}", color = Ez.money.expense, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${visible.size} Einträge",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            if (visible.isEmpty()) {
                EmptyHint("Keine Buchungen in diesem Monat.")
            }
            val byDay = visible.groupBy { it.date }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                byDay.forEach { (day, list) ->
                    item(key = "d$day") {
                        Text(
                            day.formatDayHeader(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    items(list, key = { it.id }) { tx ->
                        SwipeToDelete(onDelete = { delete(tx) }) {
                            HistoryItem(tx, types, categories, pots) { nav.navigate(Routes.editTransaction(tx.id)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, "Löschen", tint = MaterialTheme.colorScheme.error) }
        },
    ) { content() }
}

@Composable
private fun HistoryItem(
    tx: TransactionEntity,
    types: List<ExpenseTypeEntity>,
    categories: List<CategoryEntity>,
    pots: List<PotEntity>,
    onClick: () -> Unit,
) {
    val delta = Ledger.accountDelta(tx)
    val pot = pots.firstOrNull { it.id == tx.potId }
    val category = categories.firstOrNull { it.id == tx.categoryId }
    val subtitle = when (tx.kind) {
        TxKind.EXPENSE -> listOfNotNull(types.firstOrNull { it.id == tx.expenseTypeId }?.name, category?.name)
        TxKind.INCOME -> listOf(if (tx.incomeType == com.moddingforge.ezplanner.data.db.IncomeType.OTHER) "Sonstiges Einkommen" else "Einkommen")
        TxKind.TO_POT, TxKind.FROM_POT, TxKind.POT_ADJUSTMENT -> listOfNotNull(pot?.name, tx.kind.label())
        TxKind.ADJUSTMENT -> listOf(tx.kind.label())
    }.joinToString(" · ") + if (tx.date.isAfter(java.time.LocalDate.now())) " · geplant" else ""
    val (amountText, color) = when (tx.kind) {
        TxKind.POT_ADJUSTMENT -> Money.formatSigned(tx.amountCents) to Ez.money.transfer
        TxKind.ADJUSTMENT -> Money.formatSigned(delta) to Ez.money.adjustment
        TxKind.TO_POT, TxKind.FROM_POT -> Money.formatSigned(delta) to Ez.money.transfer
        else -> Money.formatSigned(delta) to if (delta >= 0) Ez.money.income else Ez.money.expense
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (category != null) {
                ColorDot(Color(category.color.toInt()))
                HSpace(10)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tx.title, style = MaterialTheme.typography.bodyLarge)
                    if (tx.standingOrderId != null || tx.dueDate != null) {
                        HSpace(6)
                        Icon(
                            Icons.Default.Repeat,
                            "Dauerauftrag",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(amountText, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}
