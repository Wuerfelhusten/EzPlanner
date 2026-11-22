package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import java.time.LocalDate
import java.time.YearMonth

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
     * Occurrences that are not booked yet with a due date in [from]..[to], as bookings with
     * id [PLANNED_ID]. Lets the analytics show the whole month (e.g. salary at the end of the
     * month) before the due day has come. Already booked occurrences are never included, because
     * counting starts at [StandingOrderEntity.nextIndex].
     */
    fun planned(orders: List<StandingOrderEntity>, from: LocalDate, to: LocalDate): List<TransactionEntity> {
        val result = mutableListOf<TransactionEntity>()
        for (order in orders) {
            if (!order.active) continue
            var i = order.nextIndex
            while (true) {
                val due = dueDate(order, i)
                if (due.isAfter(to) || (order.endDate != null && due.isAfter(order.endDate))) break
                if (!due.isBefore(from)) result += order.toTransaction(due).copy(id = PLANNED_ID)
                i++
            }
        }
        return result
    }

    const val PLANNED_ID = -1L

    /**
     * Index to continue with after the schedule was edited: the first occurrence after
     * [lastBooked] (the last due date already booked) that is not in the same calendar month,
     * or 0 if nothing was booked yet. Moving a salary from the 22nd to the 28th therefore does
     * not book November a second time.
     */
    fun indexAfter(order: StandingOrderEntity, lastBooked: LocalDate?): Int {
        if (lastBooked == null) return 0
        val bookedMonth = YearMonth.from(lastBooked)
        var i = 0
        while (true) {
            val due = dueDate(order, i)
            if (due.isAfter(lastBooked) && YearMonth.from(due) != bookedMonth) return i
            i++
        }
    }
}

/** The booking a standing order creates on [date]. */
fun StandingOrderEntity.toTransaction(date: LocalDate) = TransactionEntity(
    date = date,
    amountCents = amountCents,
    kind = kind,
    title = title,
    expenseTypeId = if (kind == TxKind.EXPENSE) expenseTypeId else null,
    categoryId = if (kind == TxKind.EXPENSE) categoryId else null,
    incomeType = if (kind == TxKind.INCOME) incomeType else null,
    potId = if (kind == TxKind.TO_POT || kind == TxKind.FROM_POT) potId else null,
    standingOrderId = id,
    dueDate = date,
)
