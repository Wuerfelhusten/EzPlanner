package com.moddingforge.ezplanner.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moddingforge.ezplanner.data.Settings
import com.moddingforge.ezplanner.domain.BudgetState
import com.moddingforge.ezplanner.domain.Ledger
import com.moddingforge.ezplanner.domain.Money
import com.moddingforge.ezplanner.domain.MonthSummaryCalculator
import com.moddingforge.ezplanner.domain.SankeyBuilder
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.components.AmountRow
import com.moddingforge.ezplanner.ui.components.BarGroup
import com.moddingforge.ezplanner.ui.components.BudgetBar
import com.moddingforge.ezplanner.ui.components.DonutChart
import com.moddingforge.ezplanner.ui.components.EmptyHint
import com.moddingforge.ezplanner.ui.components.HSpace
import com.moddingforge.ezplanner.ui.components.IncomeExpenseBars
import com.moddingforge.ezplanner.ui.components.MonthSwitcher
import com.moddingforge.ezplanner.ui.components.SankeyChart
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.components.Slice
import com.moddingforge.ezplanner.ui.components.shareImage
import com.moddingforge.ezplanner.ui.label
import com.moddingforge.ezplanner.ui.monthName
import com.moddingforge.ezplanner.ui.theme.Ez
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val orders by repo.standingOrders.collectAsStateWithLifecycle(emptyList())
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.collectAsStateWithLifecycle(Settings())
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    // Count standing orders that are not due yet (e.g. salary at the end of the month).
    var withPlanned by rememberSaveable { mutableStateOf(true) }
    var sankeyYear by rememberSaveable { mutableStateOf(false) }
    var sankeyByCategory by rememberSaveable { mutableStateOf(false) }

    // Bookings plus (optionally) planned ones, for the year of the month and the 6 months before.
    val rangeStart = minOf(month.minusMonths(5).atDay(1), LocalDate.of(month.year, 1, 1))
    val rangeEnd = LocalDate.of(month.year, 12, 31)
    val planned = remember(orders, withPlanned, rangeStart, rangeEnd) {
        if (withPlanned) Schedule.planned(orders, rangeStart, rangeEnd) else emptyList()
    }
    val all = remember(transactions, planned) { transactions + planned }

    val summary = remember(all, types, categories, month) { MonthSummaryCalculator.calculate(month, all, types, categories) }
    val plannedInMonth = planned.filter { YearMonth.from(it.date) == month }
    val history = remember(all, types, categories, month) {
        (5 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            val s = MonthSummaryCalculator.calculate(m, all, types, categories)
            BarGroup(m.monthName().take(3), s.incomeTotal, s.expensesTotal, highlighted = back == 0)
        }
    }
    val sankey = remember(all, types, categories, month, sankeyYear, sankeyByCategory) {
        val list = if (sankeyYear) all.filter { it.date.year == month.year } else all.filter { YearMonth.from(it.date) == month }
        SankeyBuilder.build(list, types, categories, sankeyByCategory)
    }
    val budget = BudgetState(settings.monthlyBudgetCents, summary.budgetUsed, settings.warnPercent)
    val money = Ez.money
    val sankeyBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val layer = rememberGraphicsLayer()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(title = { Text("Auswertungen") }) },
        bottomBar = { MonthSwitcher(month, { month = it }, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Geplante Daueraufträge einrechnen", style = MaterialTheme.typography.bodyLarge)
                        val hint = if (withPlanned && plannedInMonth.isNotEmpty()) {
                            val plus = plannedInMonth.sumOf { Ledger.accountDelta(it).coerceAtLeast(0) }
                            val minus = plannedInMonth.sumOf { (-Ledger.accountDelta(it)).coerceAtLeast(0) }
                            val parts = listOfNotNull(
                                plus.takeIf { it > 0 }?.let(Money::formatSigned),
                                minus.takeIf { it > 0 }?.let { Money.formatSigned(-it) },
                            ).joinToString(" / ")
                            val count = plannedInMonth.size
                            "$count noch nicht fällige ${if (count == 1) "Buchung" else "Buchungen"}: $parts"
                        } else {
                            "Nur bereits gebuchte Beträge"
                        }
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = withPlanned, onCheckedChange = { withPlanned = it })
                }
            }

            SectionCard(title = "Einkommen") {
                AmountRow("Einkommen im Monat", summary.incomeTotal, emphasized = true, color = money.income)
                AmountRow("Davon regulär", summary.incomeRegular, indent = true)
                AmountRow("Davon sonstiges", summary.incomeOther, indent = true)
            }

            SectionCard(title = "Ausgaben") {
                AmountRow("Ausgaben im Monat", summary.expensesTotal, emphasized = true, color = money.expense)
                summary.expensesByType.forEach { t ->
                    AmountRow("Davon ${t.type?.name ?: "ohne Typ"}", t.cents, indent = true)
                }
            }

            SectionCard(title = "Sparen & Investieren") {
                AmountRow("Gespart (netto)", summary.savedNet, emphasized = true, signed = true, color = money.transfer)
                summary.investedByType.forEach { t -> AmountRow(t.type?.name.orEmpty(), t.cents, indent = true) }
                AmountRow("In Töpfe", summary.toPots, indent = true)
                AmountRow("Aus Töpfen", summary.fromPots, indent = true)
                Text(
                    "Sparen ist weder Einkommen noch Ausgabe – das Geld bleibt deins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SectionCard(title = "Ergebnis") {
                AmountRow("Einkommen", summary.incomeTotal, indent = true)
                AmountRow("Ausgaben", -summary.expensesTotal, indent = true, signed = true)
                AmountRow("Gespart", -summary.savedNet, indent = true, signed = true)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AmountRow(
                    "Übrig",
                    summary.net,
                    emphasized = true,
                    signed = true,
                    color = if (summary.net >= 0) money.income else money.expense,
                )
            }

            if (budget.budget > 0) {
                SectionCard(title = "Monatsbudget") {
                    val color = when {
                        budget.isOver -> money.expense
                        budget.isWarning -> money.warning
                        else -> money.income
                    }
                    AmountRow("Budget", budget.budget)
                    AmountRow("Verbraucht", budget.used, indent = true)
                    AmountRow(if (budget.isOver) "Überzogen" else "Davon zur Verfügung", budget.available, color = color, emphasized = true)
                    BudgetBar(budget.ratio, color)
                }
            }

            val periodLabel = if (sankeyYear) "${month.year}" else month.label()
            SectionCard(
                title = "Geldfluss $periodLabel",
                trailing = {
                    if (!sankey.isEmpty) {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching {
                                    shareImage(context, layer.toImageBitmap(), "EzPlanner-Geldfluss-$periodLabel.png".replace(' ', '-'), "Geldfluss teilen")
                                }
                            }
                        }) { Icon(Icons.Default.Share, "Als Bild teilen") }
                    }
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !sankeyYear, onClick = { sankeyYear = false }, label = { Text("Monat") })
                    FilterChip(selected = sankeyYear, onClick = { sankeyYear = true }, label = { Text("Jahr") })
                    HSpace(4)
                    FilterChip(selected = sankeyByCategory, onClick = { sankeyByCategory = !sankeyByCategory }, label = { Text("Kategorien") })
                }
                if (sankey.isEmpty) {
                    EmptyHint("Keine Buchungen in diesem Zeitraum.")
                } else {
                    SankeyChart(
                        sankey,
                        Modifier.padding(top = 12.dp),
                        captureModifier = Modifier.drawWithContent {
                            layer.record {
                                drawRect(sankeyBackground)
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(layer)
                        },
                    )
                }
            }

            SectionCard(title = "Ausgaben nach Kategorie") {
                if (summary.expensesByCategory.isEmpty()) {
                    EmptyHint("Keine Ausgaben in diesem Monat.")
                } else {
                    DonutChart(
                        summary.expensesByCategory.map { c ->
                            Slice(
                                label = c.category?.name ?: "Ohne Kategorie",
                                cents = c.cents,
                                color = c.category?.let { Color(it.color.toInt()) } ?: Color(0xFF5F6670),
                            )
                        },
                    )
                }
            }

            SectionCard(title = "Letzte 6 Monate") {
                IncomeExpenseBars(history, money.income, money.expense)
                Text(
                    "Ohne Sparen, Investieren und Kontostand-Anpassungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
