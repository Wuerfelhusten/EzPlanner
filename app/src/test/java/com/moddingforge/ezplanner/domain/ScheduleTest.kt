package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.Interval
import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    private fun order(
        start: LocalDate,
        interval: Interval = Interval.MONTHLY,
        nextIndex: Int = 0,
        end: LocalDate? = null,
        active: Boolean = true,
    ) = StandingOrderEntity(
        id = 1, title = "Miete", amountCents = 25000, kind = TxKind.EXPENSE, interval = interval,
        startDate = start, endDate = end, nextIndex = nextIndex, active = active,
    )

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `due on its start day, regardless of time of creation`() {
        val o = order(d("2026-11-22"))
        assertEquals(listOf(0 to d("2026-11-22")), Schedule.dueOccurrences(o, d("2026-11-22")))
        assertTrue(Schedule.dueOccurrences(o, d("2026-11-21")).isEmpty())
    }

    @Test
    fun `end of month stays on 31st where possible`() {
        val o = order(d("2026-01-31"))
        val dates = (0..3).map { Schedule.dueDate(o, it) }
        assertEquals(listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30")), dates)
    }

    @Test
    fun `leap year february`() {
        val o = order(d("2028-01-31"))
        assertEquals(d("2028-02-29"), Schedule.dueDate(o, 1))
    }

    @Test
    fun `missed months are caught up`() {
        val o = order(d("2026-08-01"), nextIndex = 1)
        val due = Schedule.dueOccurrences(o, d("2026-11-22"))
        assertEquals(listOf(1 to d("2026-09-01"), 2 to d("2026-10-01"), 3 to d("2026-11-01")), due)
    }

    @Test
    fun `quarterly half yearly yearly`() {
        val start = d("2026-01-15")
        assertEquals(d("2026-04-15"), Schedule.dueDate(order(start, Interval.QUARTERLY), 1))
        assertEquals(d("2026-07-15"), Schedule.dueDate(order(start, Interval.HALF_YEARLY), 1))
        assertEquals(d("2027-01-15"), Schedule.dueDate(order(start, Interval.YEARLY), 1))
    }

    @Test
    fun `end date and pause stop bookings`() {
        val ended = order(d("2026-01-01"), end = d("2026-03-15"))
        assertEquals(3, Schedule.dueOccurrences(ended, d("2026-11-22")).size)
        assertNull(Schedule.nextDueDate(ended.copy(nextIndex = 3)))

        val paused = order(d("2026-01-01"), active = false)
        assertTrue(Schedule.dueOccurrences(paused, d("2026-11-22")).isEmpty())
    }

    @Test
    fun `index after last booked date`() {
        val o = order(d("2026-01-10"))
        assertEquals(0, Schedule.indexAfter(o, null))
        assertEquals(3, Schedule.indexAfter(o, d("2026-03-10")))
        assertEquals(3, Schedule.indexAfter(o, d("2026-03-20")))
    }
}
