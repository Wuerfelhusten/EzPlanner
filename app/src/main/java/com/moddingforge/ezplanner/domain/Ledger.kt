package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import java.time.LocalDate

/**
 * Balances are never stored; they are always the sum of all bookings. That keeps everything
 * consistent when a booking is edited or deleted.
 */
object Ledger {

    /** Effect of a booking on the account balance. */
    fun accountDelta(tx: TransactionEntity): Long = when (tx.kind) {
        TxKind.INCOME, TxKind.FROM_POT -> tx.amountCents
        TxKind.EXPENSE, TxKind.TO_POT -> -tx.amountCents
        TxKind.ADJUSTMENT -> tx.amountCents
        TxKind.POT_ADJUSTMENT -> 0
    }

    /**
     * Effect of a booking on a pot's balance.
     *
     * Savings pots and "owes me" debt pots grow when money goes from the account into them.
     * An "I owe" debt pot is the amount I still owe: borrowing (pot -> account) raises it,
     * repaying (account -> pot) lowers it.
     */
    fun potDelta(tx: TransactionEntity, pot: PotEntity): Long {
        val iOwe = pot.direction == DebtDirection.I_OWE
        return when (tx.kind) {
            TxKind.TO_POT -> if (iOwe) -tx.amountCents else tx.amountCents
            TxKind.FROM_POT -> if (iOwe) tx.amountCents else -tx.amountCents
            TxKind.POT_ADJUSTMENT -> tx.amountCents
            else -> 0
        }
    }

    /**
     * Balance on [asOf] (end of that day). Bookings dated later are planned and do not count yet,
     * e.g. an expense entered today for the 1st of next month.
     */
    fun accountBalance(all: List<TransactionEntity>, asOf: LocalDate): Long =
        all.filter { !it.date.isAfter(asOf) }.sumOf(::accountDelta)

    /** Correction that brings [pot] to [targetCents] on [date] without touching the account; null if it already matches. */
    fun potCorrection(all: List<TransactionEntity>, pot: PotEntity, targetCents: Long, date: LocalDate): TransactionEntity? {
        val diff = targetCents - potBalances(all, listOf(pot), date).getValue(pot.id)
        if (diff == 0L) return null
        return TransactionEntity(
            date = date, amountCents = diff, kind = TxKind.POT_ADJUSTMENT, title = "Stand angepasst", potId = pot.id,
        )
    }

    fun potBalances(all: List<TransactionEntity>, pots: List<PotEntity>, asOf: LocalDate): Map<Long, Long> {
        val byId = pots.associateBy { it.id }
        val result = pots.associate { it.id to 0L }.toMutableMap()
        for (tx in all) {
            if (tx.date.isAfter(asOf)) continue
            val pot = tx.potId?.let(byId::get) ?: continue
            result[pot.id] = result.getValue(pot.id) + potDelta(tx, pot)
        }
        return result
    }
}
