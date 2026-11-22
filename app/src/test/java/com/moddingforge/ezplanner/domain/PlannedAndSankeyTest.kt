package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.DefaultData
import com.moddingforge.ezplanner.data.db.IncomeType
import com.moddingforge.ezplanner.data.db.Interval
import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannedAndSankeyTest {
    private fun d(s: String) = LocalDate.parse(s)

    private val salary = StandingOrderEntity(
        id = 7, title = "Gehalt", amountCents = 224728, kind = TxKind.INCOME, interval = Interval.MONTHLY,
        startDate = d("2026-11-28"), incomeType = IncomeType.REGULAR,
    )

    @Test
    fun `salary later in the month is planned, booked ones are not`() {
        val planned = Schedule.planned(listOf(salary), d("2026-11-01"), d("2026-11-30"))
        assertEquals(1, planned.size)
        assertEquals(d("2026-11-28"), planned[0].date)
        assertEquals(TxKind.INCOME, planned[0].kind)
        assertEquals(Schedule.PLANNED_ID, planned[0].id)

        // After booking November (nextIndex = 1) only December onwards is planned.
        val rest = Schedule.planned(listOf(salary.copy(nextIndex = 1)), d("2026-11-01"), d("2026-12-31"))
        assertEquals(listOf(d("2026-12-28")), rest.map { it.date })

        val s = MonthSummaryCalculator.calculate(java.time.YearMonth.of(2026, 11), planned, DefaultData.expenseTypes, DefaultData.categories)
        assertEquals(224728L, s.incomeTotal)
    }

    @Test
    fun `sankey balances inflow and outflow`() {
        val day = d("2026-11-10")
        fun tx(kind: TxKind, cents: Long, type: Long? = null, cat: Long? = null) =
            TransactionEntity(date = day, amountCents = cents, kind = kind, title = "t", expenseTypeId = type, categoryId = cat, incomeType = if (kind == TxKind.INCOME) IncomeType.REGULAR else null, potId = if (kind == TxKind.TO_POT) 1 else null)
        val categories = DefaultData.categories.mapIndexed { i, c -> c.copy(id = i + 1L) }
        val list = listOf(
            tx(TxKind.INCOME, 200000),
            tx(TxKind.EXPENSE, 80000, DefaultData.TYPE_FIXED, 1),
            tx(TxKind.EXPENSE, 30000, DefaultData.TYPE_BUDGET, 2),
            tx(TxKind.TO_POT, 50000),
        )
        val data = SankeyBuilder.build(list, DefaultData.expenseTypes, categories, byCategory = true)
        assertEquals(200000L, data.value("total"))
        assertEquals(40000L, data.value("rest"))
        assertEquals(50000L, data.value("saving"))
        assertEquals(80000L, data.value("cat:1"))
        assertTrue(data.nodes.none { it.key == "in:deficit" })

        // More out than in: the gap comes "from the account".
        val deficit = SankeyBuilder.build(list + tx(TxKind.EXPENSE, 100000, DefaultData.TYPE_SPECIAL), DefaultData.expenseTypes, categories, false)
        assertEquals(60000L, deficit.value("in:deficit"))
        assertTrue(deficit.nodes.none { it.key == "rest" })
    }
}
