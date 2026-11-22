package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.data.db.IncomeType
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import java.time.YearMonth

data class TypeAmount(val type: ExpenseTypeEntity?, val cents: Long)

data class CategoryAmount(val category: CategoryEntity?, val cents: Long)

data class MonthSummary(
    val month: YearMonth,
    val incomeRegular: Long,
    val incomeOther: Long,
    val incomeFromPots: Long,
    val expensesByType: List<TypeAmount>,
    val toPots: Long,
    val expensesByCategory: List<CategoryAmount>,
    /** Expenses of types that count toward the monthly budget. */
    val budgetUsed: Long,
) {
    val incomeTotal: Long get() = incomeRegular + incomeOther + incomeFromPots
    val expensesTotal: Long get() = expensesByType.sumOf { it.cents } + toPots
    val net: Long get() = incomeTotal - expensesTotal
}

object MonthSummaryCalculator {

    fun calculate(
        month: YearMonth,
        all: List<TransactionEntity>,
        types: List<ExpenseTypeEntity>,
        categories: List<CategoryEntity>,
    ): MonthSummary {
        val inMonth = all.filter { YearMonth.from(it.date) == month }
        val typeById = types.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        val expenses = inMonth.filter { it.kind == TxKind.EXPENSE }

        // Every type is listed (also with 0), in the user's order; unknown/deleted types last.
        val byType = expenses.groupBy { it.expenseTypeId?.takeIf(typeById::containsKey) }
        val typeAmounts = types.map { t -> TypeAmount(t, byType[t.id].orEmpty().sumOf { it.amountCents }) } +
            listOfNotNull(byType[null]?.let { list -> TypeAmount(null, list.sumOf { it.amountCents }) })

        val categoryAmounts = expenses
            .groupBy { it.categoryId?.let(categoryById::get) }
            .map { (cat, list) -> CategoryAmount(cat, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.cents }

        return MonthSummary(
            month = month,
            incomeRegular = inMonth.filter { it.kind == TxKind.INCOME && it.incomeType != IncomeType.OTHER }
                .sumOf { it.amountCents },
            incomeOther = inMonth.filter { it.kind == TxKind.INCOME && it.incomeType == IncomeType.OTHER }
                .sumOf { it.amountCents },
            incomeFromPots = inMonth.filter { it.kind == TxKind.FROM_POT }.sumOf { it.amountCents },
            expensesByType = typeAmounts,
            toPots = inMonth.filter { it.kind == TxKind.TO_POT }.sumOf { it.amountCents },
            expensesByCategory = categoryAmounts,
            budgetUsed = expenses
                .filter { e -> e.expenseTypeId?.let(typeById::get)?.countsTowardBudget == true }
                .sumOf { it.amountCents },
        )
    }
}

/** State of the monthly budget for display. */
data class BudgetState(val budget: Long, val used: Long, val warnPercent: Int) {
    val available: Long get() = budget - used
    val ratio: Float get() = if (budget <= 0) 0f else used.toFloat() / budget
    val isOver: Boolean get() = budget > 0 && used > budget
    val isWarning: Boolean get() = budget > 0 && !isOver && ratio * 100 >= warnPercent
}
