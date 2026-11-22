package com.moddingforge.ezplanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date, id")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE potId = :potId ORDER BY date DESC, id DESC")
    fun observeForPot(potId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT MAX(dueDate) FROM transactions WHERE standingOrderId = :orderId")
    suspend fun lastDueDate(orderId: Long): LocalDate?

    @Insert
    suspend fun insert(tx: TransactionEntity): Long

    /** Used by the scheduler; the unique index turns a second booking of a due date into a no-op. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tx: TransactionEntity): Long

    @Insert
    suspend fun insertAll(list: List<TransactionEntity>)

    @Update
    suspend fun update(tx: TransactionEntity)

    @Delete
    suspend fun delete(tx: TransactionEntity)

    @Query("UPDATE transactions SET expenseTypeId = :to WHERE expenseTypeId = :from")
    suspend fun reassignExpenseType(from: Long, to: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface StandingOrderDao {
    @Query("SELECT * FROM standing_orders ORDER BY active DESC, title COLLATE NOCASE")
    fun observeAll(): Flow<List<StandingOrderEntity>>

    @Query("SELECT * FROM standing_orders")
    suspend fun getAll(): List<StandingOrderEntity>

    @Query("SELECT * FROM standing_orders WHERE active = 1")
    suspend fun getActive(): List<StandingOrderEntity>

    @Query("SELECT * FROM standing_orders WHERE id = :id")
    suspend fun get(id: Long): StandingOrderEntity?

    @Upsert
    suspend fun upsert(order: StandingOrderEntity): Long

    @Insert
    suspend fun insertAll(list: List<StandingOrderEntity>)

    @Delete
    suspend fun delete(order: StandingOrderEntity)

    @Query("UPDATE standing_orders SET expenseTypeId = :to WHERE expenseTypeId = :from")
    suspend fun reassignExpenseType(from: Long, to: Long)

    @Query("DELETE FROM standing_orders")
    suspend fun deleteAll()
}

@Dao
interface PotDao {
    @Query("SELECT * FROM pots ORDER BY archived, kind, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PotEntity>>

    @Query("SELECT * FROM pots")
    suspend fun getAll(): List<PotEntity>

    @Query("SELECT * FROM pots WHERE id = :id")
    fun observe(id: Long): Flow<PotEntity?>

    @Query("SELECT * FROM pots WHERE id = :id")
    suspend fun get(id: Long): PotEntity?

    @Upsert
    suspend fun upsert(pot: PotEntity): Long

    @Insert
    suspend fun insertAll(list: List<PotEntity>)

    @Delete
    suspend fun delete(pot: PotEntity)

    @Query("DELETE FROM pots")
    suspend fun deleteAll()
}

@Dao
interface ExpenseTypeDao {
    @Query("SELECT * FROM expense_types ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ExpenseTypeEntity>>

    @Query("SELECT * FROM expense_types")
    suspend fun getAll(): List<ExpenseTypeEntity>

    @Upsert
    suspend fun upsert(type: ExpenseTypeEntity): Long

    @Insert
    suspend fun insertAll(list: List<ExpenseTypeEntity>)

    @Delete
    suspend fun delete(type: ExpenseTypeEntity)

    @Query("DELETE FROM expense_types")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Upsert
    suspend fun upsert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(list: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
