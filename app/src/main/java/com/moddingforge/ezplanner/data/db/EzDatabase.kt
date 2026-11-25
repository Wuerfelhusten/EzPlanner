package com.moddingforge.ezplanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        StandingOrderEntity::class,
        PotEntity::class,
        ExpenseTypeEntity::class,
        CategoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class EzDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun standingOrders(): StandingOrderDao
    abstract fun pots(): PotDao
    abstract fun expenseTypes(): ExpenseTypeDao
    abstract fun categories(): CategoryDao

    companion object {
        fun create(context: Context): EzDatabase =
            Room.databaseBuilder(context, EzDatabase::class.java, "ezplanner.db")
                .addCallback(SeedCallback)
                .addMigrations(Migration1To2, Migration2To3)
                .build()
    }
}

/** 2.3.0: built-in expense type "Investieren" (ETF, shares) for existing installs. */
private object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO expense_types (name, countsTowardBudget, sortOrder, builtin) " +
                "SELECT 'Investieren', 0, 3, 1 WHERE NOT EXISTS (SELECT 1 FROM expense_types WHERE name = 'Investieren')",
        )
        // A custom type the user may already have called "Investieren" becomes the built-in one.
        db.execSQL("UPDATE expense_types SET builtin = 1 WHERE name = 'Investieren'")
    }
}

/** 2.5.0: types can count as saving; "Investieren" does. */
private object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE expense_types ADD COLUMN countsAsSaving INTEGER NOT NULL DEFAULT 0")
        // Built-in but none of the three original types = "Investieren", even if it was renamed.
        db.execSQL("UPDATE expense_types SET countsAsSaving = 1, countsTowardBudget = 0 WHERE builtin = 1 AND id NOT IN (1, 2, 3)")
    }
}

/** Default data for a fresh install. Ids of the built-in expense types are fixed (see [DefaultData]). */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultData.expenseTypes.forEach {
            db.execSQL(
                "INSERT INTO expense_types (id, name, countsTowardBudget, sortOrder, builtin, countsAsSaving) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(it.id, it.name, if (it.countsTowardBudget) 1 else 0, it.sortOrder, if (it.builtin) 1 else 0, if (it.countsAsSaving) 1 else 0),
            )
        }
        DefaultData.categories.forEach {
            db.execSQL(
                "INSERT INTO categories (name, color, sortOrder) VALUES (?, ?, ?)",
                arrayOf<Any>(it.name, it.color, it.sortOrder),
            )
        }
    }
}

object DefaultData {
    const val TYPE_FIXED = 1L
    const val TYPE_BUDGET = 2L
    const val TYPE_SPECIAL = 3L
    /** Fixed id on fresh installs only; upgraded installs get it by migration (see [Migration1To2]). */
    const val TYPE_INVEST = 4L

    val expenseTypes = listOf(
        ExpenseTypeEntity(TYPE_FIXED, "Fixkosten", countsTowardBudget = false, sortOrder = 0, builtin = true),
        ExpenseTypeEntity(TYPE_BUDGET, "Budget", countsTowardBudget = true, sortOrder = 1, builtin = true),
        ExpenseTypeEntity(TYPE_SPECIAL, "Sonderausgaben", countsTowardBudget = false, sortOrder = 2, builtin = true),
        ExpenseTypeEntity(TYPE_INVEST, "Investieren", countsTowardBudget = false, sortOrder = 3, builtin = true, countsAsSaving = true),
    )

    val categories = listOf(
        CategoryEntity(name = "Wohnen", color = 0xFF7FA7FF, sortOrder = 0),
        CategoryEntity(name = "Lebensmittel", color = 0xFF4CD08A, sortOrder = 1),
        CategoryEntity(name = "Auto & Mobilität", color = 0xFFFFB454, sortOrder = 2),
        CategoryEntity(name = "Versicherungen", color = 0xFFB18CFF, sortOrder = 3),
        CategoryEntity(name = "Abos & Verträge", color = 0xFF4DD0E1, sortOrder = 4),
        CategoryEntity(name = "Freizeit", color = 0xFFFF7A9A, sortOrder = 5),
        CategoryEntity(name = "Gesundheit", color = 0xFFA5D66F, sortOrder = 6),
        CategoryEntity(name = "Urlaub", color = 0xFFFFD54F, sortOrder = 7),
        CategoryEntity(name = "Sonstiges", color = 0xFF9AA0A6, sortOrder = 8),
    )
}
