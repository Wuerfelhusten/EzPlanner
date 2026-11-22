package com.moddingforge.ezplanner.ui.screens.pots

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Ledger
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.actionLabel
import com.moddingforge.ezplanner.ui.components.BackTopBar
import com.moddingforge.ezplanner.ui.components.ConfirmDialog
import com.moddingforge.ezplanner.ui.components.EmptyHint
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.formatDate
import com.moddingforge.ezplanner.ui.label
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.ui.screens.overview.PotRow
import com.moddingforge.ezplanner.ui.theme.Ez
import kotlinx.coroutines.launch

@Composable
fun PotDetailScreen(nav: NavController, id: Long) {
    val repo = LocalRepository.current
    val pot by repo.observePot(id).collectAsStateWithLifecycle(null)
    val history by repo.observePotTransactions(id).collectAsStateWithLifecycle(emptyList())
    val allOrders by repo.standingOrders.collectAsStateWithLifecycle(emptyList())
    val potOrders = allOrders.filter { it.potId == id }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    val p = pot ?: run {
        Scaffold(topBar = { BackTopBar("Topf", { nav.popBackStack() }) }) { Column(Modifier.padding(it)) {} }
        return
    }
    val today = java.time.LocalDate.now()
    val balance = history.filter { !it.date.isAfter(today) }.sumOf { Ledger.potDelta(it, p) }

    Scaffold(
        topBar = {
            BackTopBar(p.name, { nav.popBackStack() }) {
                IconButton(onClick = { nav.navigate(Routes.editPot(p.id)) }) { Icon(Icons.Default.Edit, "Bearbeiten") }
                IconButton(onClick = { scope.launch { repo.savePot(p.copy(archived = !p.archived)) } }) {
                    if (p.archived) Icon(Icons.Default.Unarchive, "Wiederherstellen") else Icon(Icons.Default.Archive, "Archivieren")
                }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Löschen") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard {
                    PotRow(p, balance)
                    if (p.note.isNotBlank()) {
                        Text(
                            p.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (p.archived) {
                        Text("Archiviert", color = Ez.money.warning, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            item {
                // "I owe": borrowing comes first, then repaying. Otherwise: pay in, then take out.
                val actions = if (p.kind == PotKind.DEBT && p.direction == DebtDirection.I_OWE) {
                    listOf(TxKind.FROM_POT, TxKind.TO_POT)
                } else {
                    listOf(TxKind.TO_POT, TxKind.FROM_POT)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.forEach { kind ->
                            FilledTonalButton(
                                onClick = { nav.navigate(Routes.newTransaction(kind, p.id)) },
                                modifier = Modifier.weight(1f),
                            ) { Text(p.actionLabel(kind), maxLines = 1) }
                        }
                    }
                    FilledTonalButton(
                        onClick = { nav.navigate(Routes.newTransaction(TxKind.POT_ADJUSTMENT, p.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${p.actionLabel(TxKind.POT_ADJUSTMENT)} (ohne Konto)") }
                    FilledTonalButton(
                        onClick = { nav.navigate(Routes.newOrder(p.id)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Dauerauftrag einrichten (z. B. monatlich sparen)") }
                }
            }
            if (potOrders.isNotEmpty()) {
                item {
                    SectionCard(title = "Daueraufträge") {
                        potOrders.forEachIndexed { i, o ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.navigate(Routes.editOrder(o.id)) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(
                                            o.interval.label(),
                                            p.actionLabel(o.kind),
                                            Schedule.nextDueDate(o)?.let { "nächste: ${it.formatDate()}" } ?: "pausiert/beendet",
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(Money.format(o.amountCents), color = Ez.money.transfer)
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Verlauf") {
                    if (history.isEmpty()) EmptyHint("Noch keine Bewegungen.")
                    history.forEachIndexed { i, tx ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val delta = Ledger.potDelta(tx, p)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { nav.navigate(Routes.editTransaction(tx.id)) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tx.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    tx.date.formatDate() +
                                        (if (tx.kind == TxKind.POT_ADJUSTMENT) " · ohne Konto" else "") +
                                        (if (tx.date.isAfter(today)) " · geplant" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                Money.formatSigned(delta),
                                color = if (delta >= 0) Ez.money.income else Ez.money.expense,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Topf löschen?",
            text = "„${p.name}“ wird mit allen ${history.size} Buchungen und zugehörigen Daueraufträgen gelöscht. " +
                "Dadurch ändert sich auch der Kontostand. Zum Aufräumen ohne Datenverlust besser archivieren.",
            confirmLabel = "Endgültig löschen",
            onConfirm = {
                scope.launch {
                    repo.deletePot(p)
                    nav.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
