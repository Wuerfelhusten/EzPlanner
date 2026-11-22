package com.moddingforge.ezplanner.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moddingforge.ezplanner.data.Settings
import com.moddingforge.ezplanner.domain.BudgetState
import com.moddingforge.ezplanner.domain.MonthSummaryCalculator
import com.moddingforge.ezplanner.ui.LocalRepository
import com.moddingforge.ezplanner.ui.components.AmountRow
import com.moddingforge.ezplanner.ui.components.BarGroup
import com.moddingforge.ezplanner.ui.components.BudgetBar
import com.moddingforge.ezplanner.ui.components.DonutChart
import com.moddingforge.ezplanner.ui.components.EmptyHint
import com.moddingforge.ezplanner.ui.components.IncomeExpenseBars
import com.moddingforge.ezplanner.ui.components.MonthSwitcher
import com.moddingforge.ezplanner.ui.components.SectionCard
import com.moddingforge.ezplanner.ui.components.Slice
import com.moddingforge.ezplanner.ui.monthName
import com.moddingforge.ezplanner.ui.theme.Ez
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    val repo = LocalRepository.current
    val transactions by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val types by repo.expenseTypes.collectAsStateWithLifecycle(emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.collectAsStateWithLifecycle(Settings())
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }

    val summary = remember(transactions, types, categories, month) {
        MonthSummaryCalculator.calculate(month, transactions, types, categories)
    }
    val history = remember(transactions, types, categories, month) {
        (5 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            val s = MonthSummaryCalculator.calculate(m, transactions, types, categories)
            BarGroup(
                label = m.monthName().take(3),
                income = s.incomeRegular + s.incomeOther,
                expense = s.expensesByType.sumOf { it.cents },
                highlighted = back == 0,
            )
        }
    }
    val budget = BudgetState(settings.monthlyBudgetCents, summary.budgetUsed, settings.warnPercent)
    val money = Ez.money

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
            SectionCard(title = "Einkommen") {
                AmountRow("Einkommen im Monat", summary.incomeTotal, emphasized = true, color = money.income)
                AmountRow("Davon regulär", summary.incomeRegular, indent = true)
                AmountRow("Davon aus Töpfen", summary.incomeFromPots, indent = true)
                AmountRow("Davon sonstige", summary.incomeOther, indent = true)
            }

            SectionCard(title = "Ausgaben") {
                AmountRow("Ausgaben im Monat", summary.expensesTotal, emphasized = true, color = money.expense)
                summary.expensesByType.forEach { t ->
                    AmountRow("Davon ${t.type?.name ?: "ohne Typ"}", t.cents, indent = true)
                }
                AmountRow("Davon in Töpfe", summary.toPots, indent = true)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AmountRow(
                    "Ergebnis",
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
                    "Ohne Topf-Überweisungen und Kontostand-Anpassungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
