package com.moddingforge.ezplanner.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Kind of a booking. Amounts are stored positive; the kind decides the sign
 * (see [com.moddingforge.ezplanner.domain.Ledger]). Only [ADJUSTMENT] and
 * [POT_ADJUSTMENT] carry a signed amount.
 */
enum class TxKind {
    /** Money comes in (salary, refunds, ...). */
    INCOME,
    /** Money goes out; classified by an expense type. */
    EXPENSE,
    /** Account -> pot (saving, lending money, repaying a debt I owe). */
    TO_POT,
    /** Pot -> account (withdrawing savings, getting repaid, borrowing). */
    FROM_POT,
    /** Manual correction of the account balance ("Kontostand setzen"). Signed. */
    ADJUSTMENT,
    /** Pot-only change without touching the account (interest, correction). Signed. */
    POT_ADJUSTMENT,
}

enum class IncomeType { REGULAR, OTHER }

enum class PotKind { SAVINGS, DEBT }

/** Direction of a debt pot. */
enum class DebtDirection {
    /** Somebody owes me money (I lent it). */
    OWES_ME,
    /** I owe somebody money (I borrowed it). */
    I_OWE,
}

enum class Interval(val months: Long) {
    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12),
}

@Entity(tableName = "expense_types")
data class ExpenseTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Expenses of this type are deducted from the monthly budget. */
    val countsTowardBudget: Boolean,
    val sortOrder: Int = 0,
    /** Built-in types can be renamed but not deleted. */
    val builtin: Boolean = false,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB color used in charts and chips. */
    val color: Long,
    val sortOrder: Int = 0,
)

@Entity(tableName = "pots")
data class PotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: PotKind,
    /** Debt pots: the other person. */
    val person: String? = null,
    /** Debt pots: who owes whom. */
    val direction: DebtDirection? = null,
    /** Savings pots: optional goal. */
    val targetCents: Long? = null,
    val note: String = "",
    val archived: Boolean = false,
)

@Entity(
    tableName = "standing_orders",
    foreignKeys = [
        ForeignKey(ExpenseTypeEntity::class, ["id"], ["expenseTypeId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(PotEntity::class, ["id"], ["potId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("expenseTypeId"), Index("categoryId"), Index("potId")],
)
data class StandingOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountCents: Long,
    /** INCOME, EXPENSE, TO_POT or FROM_POT. */
    val kind: TxKind,
    val interval: Interval,
    /** First due date. Further dates are computed from it (31st stays 31st where possible). */
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    /** Index of the next occurrence that has not been booked yet. */
    val nextIndex: Int = 0,
    val expenseTypeId: Long? = null,
    val categoryId: Long? = null,
    val incomeType: IncomeType? = null,
    val potId: Long? = null,
    val active: Boolean = true,
    val note: String = "",
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(ExpenseTypeEntity::class, ["id"], ["expenseTypeId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(PotEntity::class, ["id"], ["potId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(StandingOrderEntity::class, ["id"], ["standingOrderId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("date"),
        Index("expenseTypeId"),
        Index("categoryId"),
        Index("potId"),
        // One booking per standing order and due date, so the scheduler can never book twice.
        Index(value = ["standingOrderId", "dueDate"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Booking date (day only, no time). */
    val date: LocalDate,
    val amountCents: Long,
    val kind: TxKind,
    val title: String,
    val note: String = "",
    val expenseTypeId: Long? = null,
    val categoryId: Long? = null,
    val incomeType: IncomeType? = null,
    val potId: Long? = null,
    val standingOrderId: Long? = null,
    /** Due date of the standing order occurrence this booking came from. */
    val dueDate: LocalDate? = null,
)
