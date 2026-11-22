package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind

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

    fun accountBalance(all: List<TransactionEntity>): Long = all.sumOf(::accountDelta)

    fun potBalances(all: List<TransactionEntity>, pots: List<PotEntity>): Map<Long, Long> {
        val byId = pots.associateBy { it.id }
        val result = pots.associate { it.id to 0L }.toMutableMap()
        for (tx in all) {
            val pot = tx.potId?.let(byId::get) ?: continue
            result[pot.id] = result.getValue(pot.id) + potDelta(tx, pot)
        }
        return result
    }
}
