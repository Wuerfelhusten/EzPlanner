package com.moddingforge.ezplanner.data.backup

import com.moddingforge.ezplanner.data.Settings
import com.moddingforge.ezplanner.data.Snapshot
import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.DebtDirection
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.data.db.IncomeType
import com.moddingforge.ezplanner.data.db.Interval
import com.moddingforge.ezplanner.data.db.PotEntity
import com.moddingforge.ezplanner.data.db.PotKind
import com.moddingforge.ezplanner.data.db.StandingOrderEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind
import com.moddingforge.ezplanner.domain.Ledger
import com.moddingforge.ezplanner.domain.Money
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Backup format (JSON, versioned). Dates are ISO strings ("2026-11-22"), amounts are cents.
 * Kept separate from the Room entities so the database can change without breaking old backups.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: String,
    val settings: BSettings,
    val expenseTypes: List<BExpenseType>,
    val categories: List<BCategory>,
    val pots: List<BPot>,
    val standingOrders: List<BStandingOrder>,
    val transactions: List<BTransaction>,
) {
    companion object {
        const val FORMAT = "ezplanner-backup"
    }
}

@Serializable data class BSettings(val monthlyBudgetCents: Long, val warnPercent: Int)

@Serializable data class BExpenseType(val id: Long, val name: String, val countsTowardBudget: Boolean, val sortOrder: Int, val builtin: Boolean)

@Serializable data class BCategory(val id: Long, val name: String, val color: Long, val sortOrder: Int)

@Serializable data class BPot(
    val id: Long, val name: String, val kind: String, val person: String? = null, val direction: String? = null,
    val targetCents: Long? = null, val note: String = "", val archived: Boolean = false,
)

@Serializable data class BStandingOrder(
    val id: Long, val title: String, val amountCents: Long, val kind: String, val interval: String,
    val startDate: String, val endDate: String? = null, val nextIndex: Int, val expenseTypeId: Long? = null,
    val categoryId: Long? = null, val incomeType: String? = null, val potId: Long? = null,
    val active: Boolean = true, val note: String = "",
)

@Serializable data class BTransaction(
    val id: Long, val date: String, val amountCents: Long, val kind: String, val title: String, val note: String = "",
    val expenseTypeId: Long? = null, val categoryId: Long? = null, val incomeType: String? = null,
    val potId: Long? = null, val standingOrderId: Long? = null, val dueDate: String? = null,
)

object BackupManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun toJson(s: Snapshot, now: LocalDate = LocalDate.now()): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            createdAt = now.toString(),
            settings = BSettings(s.settings.monthlyBudgetCents, s.settings.warnPercent),
            expenseTypes = s.expenseTypes.map { BExpenseType(it.id, it.name, it.countsTowardBudget, it.sortOrder, it.builtin) },
            categories = s.categories.map { BCategory(it.id, it.name, it.color, it.sortOrder) },
            pots = s.pots.map {
                BPot(it.id, it.name, it.kind.name, it.person, it.direction?.name, it.targetCents, it.note, it.archived)
            },
            standingOrders = s.standingOrders.map {
                BStandingOrder(
                    it.id, it.title, it.amountCents, it.kind.name, it.interval.name, it.startDate.toString(),
                    it.endDate?.toString(), it.nextIndex, it.expenseTypeId, it.categoryId, it.incomeType?.name,
                    it.potId, it.active, it.note,
                )
            },
            transactions = s.transactions.map {
                BTransaction(
                    it.id, it.date.toString(), it.amountCents, it.kind.name, it.title, it.note, it.expenseTypeId,
                    it.categoryId, it.incomeType?.name, it.potId, it.standingOrderId, it.dueDate?.toString(),
                )
            },
        ),
    )

    /** Parses a backup; throws [IllegalArgumentException] with a readable message if it is not one. */
    fun fromJson(text: String): Snapshot {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Datei ist kein gültiges EzPlanner-Backup.", e)
        }
        require(file.format == BackupFile.FORMAT) { "Die Datei ist kein EzPlanner-Backup." }
        require(file.version <= 1) { "Das Backup stammt aus einer neueren App-Version." }
        return try {
            Snapshot(
                expenseTypes = file.expenseTypes.map { ExpenseTypeEntity(it.id, it.name, it.countsTowardBudget, it.sortOrder, it.builtin) },
                categories = file.categories.map { CategoryEntity(it.id, it.name, it.color, it.sortOrder) },
                pots = file.pots.map {
                    PotEntity(
                        it.id, it.name, PotKind.valueOf(it.kind), it.person, it.direction?.let(DebtDirection::valueOf),
                        it.targetCents, it.note, it.archived,
                    )
                },
                standingOrders = file.standingOrders.map {
                    StandingOrderEntity(
                        it.id, it.title, it.amountCents, TxKind.valueOf(it.kind), Interval.valueOf(it.interval),
                        LocalDate.parse(it.startDate), it.endDate?.let(LocalDate::parse), it.nextIndex, it.expenseTypeId,
                        it.categoryId, it.incomeType?.let(IncomeType::valueOf), it.potId, it.active, it.note,
                    )
                },
                transactions = file.transactions.map {
                    TransactionEntity(
                        it.id, LocalDate.parse(it.date), it.amountCents, TxKind.valueOf(it.kind), it.title, it.note,
                        it.expenseTypeId, it.categoryId, it.incomeType?.let(IncomeType::valueOf), it.potId,
                        it.standingOrderId, it.dueDate?.let(LocalDate::parse),
                    )
                },
                settings = Settings(file.settings.monthlyBudgetCents, file.settings.warnPercent),
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Das Backup ist beschädigt: ${e.message}", e)
        }
    }

    /** All bookings as CSV (semicolon separated, German number format, opens directly in Excel). */
    fun toCsv(s: Snapshot): String {
        val types = s.expenseTypes.associateBy { it.id }
        val categories = s.categories.associateBy { it.id }
        val pots = s.pots.associateBy { it.id }
        val sb = StringBuilder("﻿") // BOM so Excel detects UTF-8
        sb.appendLine("Datum;Art;Titel;Betrag;Wirkung Konto;Ausgabentyp;Kategorie;Einkommensart;Topf;Dauerauftrag;Notiz")
        for (tx in s.transactions.sortedWith(compareBy({ it.date }, { it.id }))) {
            val cells = listOf(
                tx.date.toString(),
                kindLabel(tx.kind),
                tx.title,
                Money.toInput(tx.amountCents).ifEmpty { "0" },
                Money.toInput(Ledger.accountDelta(tx)).ifEmpty { "0" },
                tx.expenseTypeId?.let(types::get)?.name.orEmpty(),
                tx.categoryId?.let(categories::get)?.name.orEmpty(),
                when (tx.incomeType) { IncomeType.REGULAR -> "Regulär"; IncomeType.OTHER -> "Sonstiges"; null -> "" },
                tx.potId?.let(pots::get)?.name.orEmpty(),
                if (tx.standingOrderId != null) "ja" else "",
                tx.note,
            )
            sb.appendLine(cells.joinToString(";") { csvCell(it) })
        }
        return sb.toString()
    }

    private fun csvCell(v: String): String =
        if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun kindLabel(kind: TxKind) = when (kind) {
        TxKind.INCOME -> "Einkommen"
        TxKind.EXPENSE -> "Ausgabe"
        TxKind.TO_POT -> "In Topf"
        TxKind.FROM_POT -> "Aus Topf"
        TxKind.ADJUSTMENT -> "Kontostand-Anpassung"
        TxKind.POT_ADJUSTMENT -> "Topf-Korrektur"
    }
}
