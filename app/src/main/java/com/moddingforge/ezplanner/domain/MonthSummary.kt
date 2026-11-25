package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.data.db.IncomeType
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import java.time.LocalDate
import java.time.YearMonth

data class TypeAmount(val type: ExpenseTypeEntity?, val cents: Long)

data class CategoryAmount(val category: CategoryEntity?, val cents: Long)

/**
 * Totals of a period. Saving (money moved into or out of pots, or invested) is its own area: it is neither
 * income nor expense, so it shows up separately and does not inflate either side.
 */
data class MonthSummary(
    val incomeRegular: Long,
    val incomeOther: Long,
    val expensesByType: List<TypeAmount>,
    /** Expense types that count as saving (e.g. Investieren). */
    val investedByType: List<TypeAmount>,
    val expensesByCategory: List<CategoryAmount>,
    /** Expenses of types that count toward the monthly budget. */
    val budgetUsed: Long,
    /** Account -> pots (saving, lending, repaying debts). */
    val toPots: Long,
    /** Pots -> account (withdrawing savings, being repaid, borrowing). */
    val fromPots: Long,
) {
    val incomeTotal: Long get() = incomeRegular + incomeOther
    val expensesTotal: Long get() = expensesByType.sumOf { it.cents }
    val invested: Long get() = investedByType.sumOf { it.cents }
    val savedNet: Long get() = toPots - fromPots + invested
    /** What is left after expenses and saving. */
    val net: Long get() = incomeTotal - expensesTotal - savedNet
}

object MonthSummaryCalculator {

    fun calculate(
        month: YearMonth,
        all: List<TransactionEntity>,
        types: List<ExpenseTypeEntity>,
        categories: List<CategoryEntity>,
    ): MonthSummary = summarize(all.filter { YearMonth.from(it.date) == month }, types, categories)

    fun calculate(
        from: LocalDate,
        to: LocalDate,
        all: List<TransactionEntity>,
        types: List<ExpenseTypeEntity>,
        categories: List<CategoryEntity>,
    ): MonthSummary = summarize(all.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }, types, categories)

    /** Totals of exactly the given bookings. */
    fun summarize(
        list: List<TransactionEntity>,
        types: List<ExpenseTypeEntity>,
        categories: List<CategoryEntity>,
    ): MonthSummary {
        val typeById = types.associateBy { it.id }
        val categoryById = categories.associateBy { it.id }
        val (invested, expenses) = list.filter { it.kind == TxKind.EXPENSE }
            .partition { tx -> tx.expenseTypeId?.let(typeById::get)?.countsAsSaving == true }

        // Every type is listed (also with 0), in the user's order; unknown/deleted types last.
        val byType = expenses.groupBy { it.expenseTypeId?.takeIf(typeById::containsKey) }
        val typeAmounts = types.filter { !it.countsAsSaving }.map { t -> TypeAmount(t, byType[t.id].orEmpty().sumOf { it.amountCents }) } +
            listOfNotNull(byType[null]?.let { l -> TypeAmount(null, l.sumOf { it.amountCents }) })

        val categoryAmounts = expenses
            .groupBy { it.categoryId?.let(categoryById::get) }
            .map { (cat, l) -> CategoryAmount(cat, l.sumOf { it.amountCents }) }
            .sortedByDescending { it.cents }

        return MonthSummary(
            incomeRegular = list.filter { it.kind == TxKind.INCOME && it.incomeType != IncomeType.OTHER }.sumOf { it.amountCents },
            incomeOther = list.filter { it.kind == TxKind.INCOME && it.incomeType == IncomeType.OTHER }.sumOf { it.amountCents },
            expensesByType = typeAmounts,
            investedByType = types.filter { it.countsAsSaving }
                .map { t -> TypeAmount(t, invested.filter { it.expenseTypeId == t.id }.sumOf { it.amountCents }) },
            expensesByCategory = categoryAmounts,
            budgetUsed = expenses
                .filter { e -> e.expenseTypeId?.let(typeById::get)?.countsTowardBudget == true }
                .sumOf { it.amountCents },
            toPots = list.filter { it.kind == TxKind.TO_POT }.sumOf { it.amountCents },
            fromPots = list.filter { it.kind == TxKind.FROM_POT }.sumOf { it.amountCents },
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
