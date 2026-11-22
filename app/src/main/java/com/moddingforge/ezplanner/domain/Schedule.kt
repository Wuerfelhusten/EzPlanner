package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import java.time.LocalDate

/**
 * Due dates of standing orders. Bookings are day-based: an occurrence is due for the whole
 * day from 00:00, no matter at what time the order was created.
 */
object Schedule {

    /**
     * Due date of occurrence [index]. Always computed from the start date, so an order on the
     * 31st books on Feb 28/29 and on the 31st again in March (no drift).
     */
    fun dueDate(order: StandingOrderEntity, index: Int): LocalDate =
        order.startDate.plusMonths(order.interval.months * index)

    /** Occurrences from [StandingOrderEntity.nextIndex] that are due on or before [today]. */
    fun dueOccurrences(order: StandingOrderEntity, today: LocalDate): List<Pair<Int, LocalDate>> {
        if (!order.active) return emptyList()
        val result = mutableListOf<Pair<Int, LocalDate>>()
        var i = order.nextIndex
        while (true) {
            val due = dueDate(order, i)
            if (due.isAfter(today)) break
            if (order.endDate != null && due.isAfter(order.endDate)) break
            result += i to due
            i++
        }
        return result
    }

    /** Next date this order will book, or null if it has ended or is paused. */
    fun nextDueDate(order: StandingOrderEntity): LocalDate? {
        if (!order.active) return null
        val due = dueDate(order, order.nextIndex)
        return if (order.endDate != null && due.isAfter(order.endDate)) null else due
    }

    /**
     * Index to continue with after the order was edited: the first occurrence strictly after
     * [lastBooked] (the last due date already booked), or 0 if nothing was booked yet.
     */
    fun indexAfter(order: StandingOrderEntity, lastBooked: LocalDate?): Int {
        if (lastBooked == null) return 0
        var i = 0
        while (!dueDate(order, i).isAfter(lastBooked)) i++
        return i
    }
}
