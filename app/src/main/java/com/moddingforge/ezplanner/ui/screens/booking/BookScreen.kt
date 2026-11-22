package com.moddingforge.ezplanner.ui.screens.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.Routes
import com.moddingforge.ezplanner.ui.components.DateField
import com.moddingforge.ezplanner.ui.components.HSpace
import com.moddingforge.ezplanner.ui.components.MoneyField
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.theme.Ez
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(nav: NavController) {
    val repo = LocalRepository.current
    val balance by repo.accountBalance.collectAsStateWithLifecycle(0L)
    var showBalance by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Buchen") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                Icons.Default.ArrowUpward, Ez.money.expense, "Ausgabe erfassen",
                "Einmalige Ausgabe: Budget, Fixkosten oder Sonderausgabe",
            ) { nav.navigate(Routes.newTransaction(TxKind.EXPENSE)) }
            ActionCard(
                Icons.Default.ArrowDownward, Ez.money.income, "Einkommen erfassen",
                "Gehalt, Erstattung, Geschenk …",
            ) { nav.navigate(Routes.newTransaction(TxKind.INCOME)) }
            ActionCard(
                Icons.Default.Savings, Ez.money.transfer, "Topf-Buchung",
                "Sparen, entnehmen, Geld verliehen oder zurückgezahlt",
            ) { nav.navigate(Routes.newTransaction(TxKind.TO_POT)) }
            ActionCard(
                Icons.Default.Repeat, MaterialTheme.colorScheme.primary, "Dauerauftrag einrichten",
                "Monatlich, vierteljährlich, halbjährlich oder jährlich",
            ) { nav.navigate(Routes.newOrder()) }
            ActionCard(
                Icons.Default.AccountBalance, Ez.money.adjustment, "Kontostand setzen",
                "Aktuell ${Money.format(balance)} – Abweichung wird als Anpassung gebucht",
            ) { showBalance = true }
            ActionCard(
                Icons.Default.Tune, MaterialTheme.colorScheme.onSurfaceVariant, "Budget & Einstellungen",
                "Monatsbudget, Warnung, Ausgabentypen, Kategorien, Backup",
            ) { nav.navigate(Routes.SETTINGS) }
        }
    }

    if (showBalance) SetBalanceDialog(balance, onDismiss = { showBalance = false })
}

@Composable
private fun ActionCard(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint) }
            HSpace(14)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Kontostand setzen": enter the real balance; the difference is booked as adjustment. */
@Composable
fun SetBalanceDialog(currentBalance: Long, onDismiss: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(Money.toInput(currentBalance)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val parsed = Money.parse(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kontostand setzen") },
        text = {
            Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Aktuell in der App: ${Money.format(currentBalance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyField(input, { input = it }, "Tatsächlicher Kontostand", allowNegative = true)
                DateField(date, { date = it }, "Stichtag")
                if (parsed != null && parsed != currentBalance) {
                    Text(
                        "Anpassung: ${Money.formatSigned(parsed - currentBalance)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ez.money.adjustment,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = {
                    val target = parsed ?: return@TextButton
                    scope.launch {
                        repo.setAccountBalance(target, date)
                        onDismiss()
                    }
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
