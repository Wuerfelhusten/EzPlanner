package com.moddingforge.ezplanner.data

import androidx.room.withTransaction
import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.DefaultData
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.data.db.EzDatabase
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Ledger
import com.moddingforge.ezplanner.domain.Schedule
import com.moddingforge.ezplanner.domain.toTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Everything the UI needs, in one place. */
class EzRepository(
    private val db: EzDatabase,
    val settingsStore: SettingsStore,
) {
    private val txDao = db.transactions()
    private val orderDao = db.standingOrders()
    private val potDao = db.pots()
    private val typeDao = db.expenseTypes()
    private val categoryDao = db.categories()

    val transactions: Flow<List<TransactionEntity>> = txDao.observeAll()
    val standingOrders: Flow<List<StandingOrderEntity>> = orderDao.observeAll()
    val pots: Flow<List<PotEntity>> = potDao.observeAll()
    val expenseTypes: Flow<List<ExpenseTypeEntity>> = typeDao.observeAll()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val settings: Flow<Settings> = settingsStore.settings

    val accountBalance: Flow<Long> = transactions.map(Ledger::accountBalance)

    val potBalances: Flow<Map<Long, Long>> =
        combine(transactions, pots) { all, pots -> Ledger.potBalances(all, pots) }

    fun observePot(id: Long): Flow<PotEntity?> = potDao.observe(id)
    fun observePotTransactions(potId: Long): Flow<List<TransactionEntity>> = txDao.observeForPot(potId)

    // --- Bookings ------------------------------------------------------------------------

    suspend fun getTransaction(id: Long): TransactionEntity? = txDao.get(id)

    suspend fun saveTransaction(tx: TransactionEntity) {
        if (tx.id == 0L) txDao.insert(tx) else txDao.update(tx)
    }

    suspend fun deleteTransaction(tx: TransactionEntity) = txDao.delete(tx)

    /** Restores a deleted booking (undo), keeping its id. */
    suspend fun restoreTransaction(tx: TransactionEntity) {
        txDao.insertIgnore(tx)
    }

    /** "Kontostand setzen": books the difference as an adjustment dated [date]. */
    suspend fun setAccountBalance(targetCents: Long, date: LocalDate) {
        val current = Ledger.accountBalance(txDao.getAll())
        val diff = targetCents - current
        if (diff == 0L) return
        txDao.insert(
            TransactionEntity(date = date, amountCents = diff, kind = TxKind.ADJUSTMENT, title = "Kontostand-Anpassung"),
        )
    }

    // --- Standing orders -------------------------------------------------------------------

    suspend fun getStandingOrder(id: Long): StandingOrderEntity? = orderDao.get(id)

    /**
     * Saves an order. When the schedule of an existing order changed, it continues after the
     * last date already booked, so nothing is booked twice and nothing is skipped.
     */
    suspend fun saveStandingOrder(order: StandingOrderEntity) {
        db.withTransaction {
            val toSave = if (order.id == 0L) {
                order.copy(nextIndex = 0)
            } else {
                val old = orderDao.get(order.id)
                val scheduleChanged = old == null || old.startDate != order.startDate || old.interval != order.interval
                if (scheduleChanged) {
                    order.copy(nextIndex = Schedule.indexAfter(order, txDao.lastDueDate(order.id)))
                } else {
                    order.copy(nextIndex = old.nextIndex)
                }
            }
            orderDao.upsert(toSave)
        }
        runDueStandingOrders()
    }

    suspend fun setStandingOrderActive(order: StandingOrderEntity, active: Boolean) {
        orderDao.upsert(order.copy(active = active))
        if (active) runDueStandingOrders()
    }

    /** Deletes the order; bookings already made stay in the history. */
    suspend fun deleteStandingOrder(order: StandingOrderEntity) = orderDao.delete(order)

    private val runMutex = Mutex()

    /**
     * Books every due occurrence (on or before [today]) of all active orders, each dated on its
     * due day. Missed days (app not opened) are caught up. Safe to call any number of times.
     */
    suspend fun runDueStandingOrders(today: LocalDate = LocalDate.now()): Int = runMutex.withLock {
        db.withTransaction {
            var booked = 0
            for (order in orderDao.getActive()) {
                val due = Schedule.dueOccurrences(order, today)
                if (due.isEmpty()) continue
                for ((_, date) in due) {
                    val id = txDao.insertIgnore(order.toTransaction(date))
                    if (id != -1L) booked++
                }
                orderDao.upsert(order.copy(nextIndex = due.last().first + 1))
            }
            booked
        }
    }

    // --- Pots --------------------------------------------------------------------------------

    suspend fun getPot(id: Long): PotEntity? = potDao.get(id)
    suspend fun savePot(pot: PotEntity): Long = potDao.upsert(pot).let { if (it == -1L) pot.id else it }

    /** Deletes the pot together with all its bookings and standing orders. */
    suspend fun deletePot(pot: PotEntity) = potDao.delete(pot)

    // --- Expense types & categories --------------------------------------------------------

    suspend fun saveExpenseType(type: ExpenseTypeEntity) {
        typeDao.upsert(type)
    }

    /** Custom types only; their bookings and orders move to "Sonderausgaben". */
    suspend fun deleteExpenseType(type: ExpenseTypeEntity) {
        if (type.builtin) return
        db.withTransaction {
            txDao.reassignExpenseType(type.id, DefaultData.TYPE_SPECIAL)
            orderDao.reassignExpenseType(type.id, DefaultData.TYPE_SPECIAL)
            typeDao.delete(type)
        }
    }

    suspend fun saveCategory(category: CategoryEntity) {
        categoryDao.upsert(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    // --- Settings ------------------------------------------------------------------------------

    suspend fun setBudget(cents: Long) = settingsStore.setBudget(cents)
    suspend fun setWarnPercent(percent: Int) = settingsStore.setWarnPercent(percent)

    // --- Backup --------------------------------------------------------------------------------

    suspend fun snapshot(): Snapshot = Snapshot(
        expenseTypes = typeDao.getAll(),
        categories = categoryDao.getAll(),
        pots = potDao.getAll(),
        standingOrders = orderDao.getAll(),
        transactions = txDao.getAll(),
        settings = settingsStore.current(),
    )

    /** Replaces all data with [snapshot]. */
    suspend fun restore(snapshot: Snapshot) {
        db.withTransaction {
            txDao.deleteAll()
            orderDao.deleteAll()
            potDao.deleteAll()
            categoryDao.deleteAll()
            typeDao.deleteAll()
            typeDao.insertAll(snapshot.expenseTypes)
            categoryDao.insertAll(snapshot.categories)
            potDao.insertAll(snapshot.pots)
            orderDao.insertAll(snapshot.standingOrders)
            txDao.insertAll(snapshot.transactions)
        }
        settingsStore.replace(snapshot.settings)
    }
}

data class Snapshot(
    val expenseTypes: List<ExpenseTypeEntity>,
    val categories: List<CategoryEntity>,
    val pots: List<PotEntity>,
    val standingOrders: List<StandingOrderEntity>,
    val transactions: List<TransactionEntity>,
    val settings: Settings,
)
