package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.DefaultData
import com.moddingforge.ezplanner.data.db.IncomeType
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerAndSummaryTest {
    private val day = LocalDate.parse("2026-11-10")
    private fun tx(kind: TxKind, cents: Long, type: Long? = null, pot: Long? = null, income: IncomeType? = null, date: LocalDate = day) =
        TransactionEntity(date = date, amountCents = cents, kind = kind, title = "t", expenseTypeId = type, potId = pot, incomeType = income)

    private val savings = PotEntity(id = 1, name = "Notgroschen", kind = PotKind.SAVINGS)
    private val lent = PotEntity(id = 2, name = "Max", kind = PotKind.DEBT, person = "Max", direction = DebtDirection.OWES_ME)
    private val borrowed = PotEntity(id = 3, name = "Anna", kind = PotKind.DEBT, person = "Anna", direction = DebtDirection.I_OWE)

    @Test
    fun `account balance from all kinds`() {
        val all = listOf(
            tx(TxKind.INCOME, 200000),
            tx(TxKind.EXPENSE, 50000, DefaultData.TYPE_FIXED),
            tx(TxKind.TO_POT, 10000, pot = 1),
            tx(TxKind.FROM_POT, 2000, pot = 1),
            tx(TxKind.ADJUSTMENT, -500),
            tx(TxKind.POT_ADJUSTMENT, 425000, pot = 1),
        )
        assertEquals(200000 - 50000 - 10000 + 2000 - 500, Ledger.accountBalance(all, day))
    }

    @Test
    fun `future bookings do not count yet`() {
        val all = listOf(
            tx(TxKind.INCOME, 200000),
            tx(TxKind.EXPENSE, 50000, DefaultData.TYPE_BUDGET, date = day.plusMonths(1)), // entered for next month
            tx(TxKind.TO_POT, 10000, pot = 1, date = day.plusDays(1)),
        )
        assertEquals(200000L, Ledger.accountBalance(all, day))
        assertEquals(0L, Ledger.potBalances(all, listOf(savings), day)[1])
        assertEquals(200000L - 50000 - 10000, Ledger.accountBalance(all, day.plusMonths(1)))
        assertEquals(10000L, Ledger.potBalances(all, listOf(savings), day.plusDays(1))[1])
    }

    @Test
    fun `pot balances incl debt directions`() {
        val all = listOf(
            tx(TxKind.POT_ADJUSTMENT, 425000, pot = 1),
            tx(TxKind.TO_POT, 90000, pot = 1),
            // Lent Max 50, he repaid 20 -> he owes 30.
            tx(TxKind.TO_POT, 5000, pot = 2),
            tx(TxKind.FROM_POT, 2000, pot = 2),
            // Borrowed 100 from Anna, repaid 40 -> I owe 60.
            tx(TxKind.FROM_POT, 10000, pot = 3),
            tx(TxKind.TO_POT, 4000, pot = 3),
        )
        val b = Ledger.potBalances(all, listOf(savings, lent, borrowed), day)
        assertEquals(515000L, b[1])
        assertEquals(3000L, b[2])
        assertEquals(6000L, b[3])
    }

    @Test
    fun `only budget types reduce the budget`() {
        val all = listOf(
            tx(TxKind.INCOME, 224728, income = IncomeType.REGULAR),
            tx(TxKind.INCOME, 5000, income = IncomeType.OTHER),
            tx(TxKind.EXPENSE, 101399, DefaultData.TYPE_FIXED),
            tx(TxKind.EXPENSE, 33523, DefaultData.TYPE_BUDGET),
            tx(TxKind.EXPENSE, 40000, DefaultData.TYPE_SPECIAL), // workshop, outside the budget
            tx(TxKind.TO_POT, 90000, pot = 1),
            tx(TxKind.EXPENSE, 9999, DefaultData.TYPE_BUDGET, date = day.minusMonths(1)), // other month
        )
        val s = MonthSummaryCalculator.calculate(YearMonth.of(2026, 11), all, DefaultData.expenseTypes, DefaultData.categories)
        assertEquals(33523L, s.budgetUsed)
        assertEquals(224728L, s.incomeRegular)
        assertEquals(5000L, s.incomeOther)
        assertEquals(90000L, s.toPots)
        // Saving is its own area: not part of the expenses.
        assertEquals(101399L + 33523 + 40000, s.expensesTotal)
        assertEquals(90000L, s.savedNet)
        assertEquals(224728L + 5000 - (101399 + 33523 + 40000) - 90000, s.net)

        val budget = BudgetState(61484, s.budgetUsed, 80)
        assertEquals(27961L, budget.available)
        assertFalse(budget.isWarning)
        assertTrue(BudgetState(40000, s.budgetUsed, 80).isWarning)
        assertTrue(BudgetState(30000, s.budgetUsed, 80).isOver)
    }

    @Test
    fun `investing counts as saving`() {
        val all = listOf(
            tx(TxKind.INCOME, 200000, income = IncomeType.REGULAR),
            tx(TxKind.EXPENSE, 50000, DefaultData.TYPE_INVEST),
            tx(TxKind.EXPENSE, 30000, DefaultData.TYPE_FIXED),
            tx(TxKind.TO_POT, 10000, pot = 1),
        )
        val s = MonthSummaryCalculator.summarize(all, DefaultData.expenseTypes, DefaultData.categories)
        assertEquals(30000L, s.expensesTotal)
        assertEquals(50000L, s.invested)
        assertEquals(60000L, s.savedNet)
        assertEquals(110000L, s.net)
        assertEquals(30000L, s.expensesByCategory.sumOf { it.cents })
        assertTrue(s.expensesByType.none { it.type?.id == DefaultData.TYPE_INVEST })
        // The money still leaves the account.
        assertEquals(110000L, Ledger.accountBalance(all, day))
        val flow = SankeyBuilder.build(all, DefaultData.expenseTypes, DefaultData.categories, true)
        assertEquals(50000L, flow.value("type:${DefaultData.TYPE_INVEST}"))
        assertEquals(110000L, flow.value("rest"))
    }

    @Test
    fun `setting a pot balance changes only the pot`() {
        for (pot in listOf(savings, lent, borrowed)) {
            val all = listOf(tx(TxKind.INCOME, 100000), tx(TxKind.POT_ADJUSTMENT, 425000, pot = pot.id))
            for (target in listOf(500000L, 100000L, 0L)) {
                val after = all + Ledger.potCorrection(all, pot, target, day)!!
                assertEquals(target, Ledger.potBalances(after, listOf(pot), day)[pot.id])
                assertEquals(100000L, Ledger.accountBalance(after, day))
                val s = MonthSummaryCalculator.summarize(after, DefaultData.expenseTypes, DefaultData.categories)
                assertEquals(100000L, s.incomeTotal)
                assertEquals(0L, s.expensesTotal)
                assertEquals(0L, s.savedNet)
            }
            assertEquals(null, Ledger.potCorrection(all, pot, 425000, day))
        }
    }

    @Test
    fun `money parse and format`() {
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1250L, Money.parse("12.50"))
        assertEquals(123400L, Money.parse("1.234"))
        assertEquals(-2000L, Money.parse("-20"))
        assertEquals(null, Money.parse("abc"))
        assertEquals(null, Money.parse("1,2,3"))
        assertEquals(null, Money.parse("1,234"))
        assertEquals("1.234,56 €", Money.format(123456))
        assertEquals("-5,00 €", Money.format(-500))
        assertEquals("1234,56", Money.toInput(123456))
    }
}
