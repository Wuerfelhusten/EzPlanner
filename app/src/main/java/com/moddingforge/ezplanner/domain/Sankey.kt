package com.moddingforge.ezplanner.domain

import com.moddingforge.ezplanner.data.db.CategoryEntity
import com.moddingforge.ezplanner.data.db.ExpenseTypeEntity
import com.moddingforge.ezplanner.data.db.TransactionEntity
import com.moddingforge.ezplanner.data.db.TxKind

enum class SankeyRole { INCOME, OTHER_INCOME, FROM_POTS, DEFICIT, TOTAL, EXPENSE_TYPE, INVESTING, SAVING, REST, CATEGORY }

data class SankeyNode(
    val key: String,
    val label: String,
    val column: Int,
    val role: SankeyRole,
    /** Own color (categories), otherwise chosen from [role] / position by the UI. */
    val color: Long? = null,
)

data class SankeyLink(val from: String, val to: String, val value: Long)

data class SankeyData(val nodes: List<SankeyNode>, val links: List<SankeyLink>) {
    val isEmpty: Boolean get() = links.none { it.value > 0 }

    /** Node value = larger of incoming and outgoing flow. */
    fun value(key: String): Long = maxOf(links.filter { it.to == key }.sumOf { it.value }, links.filter { it.from == key }.sumOf { it.value })
}

/**
 * Money flow of a period:
 *   income sources -> "Gesamt" -> expense types, saving, rest  (-> categories, optional).
 * If more goes out than comes in, the gap is shown as source "Aus dem Konto" (deficit).
 */
object SankeyBuilder {
    fun build(
        list: List<TransactionEntity>,
        types: List<ExpenseTypeEntity>,
        categories: List<CategoryEntity>,
        byCategory: Boolean,
    ): SankeyData {
        val s = MonthSummaryCalculator.summarize(list, types, categories)
        val nodes = mutableListOf<SankeyNode>()
        val links = mutableListOf<SankeyLink>()
        val total = "total"

        // Net per pot: money that only passes through a pot (in and out again in this period) is
        // the same money, so it must not be counted a second time on either side.
        val potNets = list
            .filter { it.kind == TxKind.TO_POT || it.kind == TxKind.FROM_POT }
            .groupBy { it.potId }
            .values.map { txs -> txs.sumOf { if (it.kind == TxKind.TO_POT) it.amountCents else -it.amountCents } }
        val toPots = potNets.filter { it > 0 }.sum()
        val fromPots = -potNets.filter { it < 0 }.sum()

        val inflow = s.incomeRegular + s.incomeOther + fromPots
        val outflow = s.expensesTotal + s.invested + toPots
        fun source(key: String, label: String, role: SankeyRole, value: Long) {
            if (value <= 0) return
            nodes += SankeyNode(key, label, 0, role)
            links += SankeyLink(key, total, value)
        }
        source("in:regular", "Einkommen", SankeyRole.INCOME, s.incomeRegular)
        source("in:other", "Sonstiges Einkommen", SankeyRole.OTHER_INCOME, s.incomeOther)
        source("in:pots", "Aus Töpfen", SankeyRole.FROM_POTS, fromPots)
        source("in:deficit", "Aus dem Konto", SankeyRole.DEFICIT, outflow - inflow)
        if (links.isEmpty()) return SankeyData(emptyList(), emptyList())
        // "Gesamt", not "Einnahmen": it also contains money from pots and from the account.
        nodes += SankeyNode(total, "Gesamt", 1, SankeyRole.TOTAL)

        s.expensesByType.filter { it.cents > 0 }.sortedByDescending { it.cents }.forEach { t ->
            val key = "type:${t.type?.id ?: 0}"
            nodes += SankeyNode(key, t.type?.name ?: "Ohne Typ", 2, SankeyRole.EXPENSE_TYPE)
            links += SankeyLink(total, key, t.cents)
        }
        s.investedByType.filter { it.cents > 0 }.forEach { t ->
            val key = "type:${t.type?.id ?: 0}"
            nodes += SankeyNode(key, t.type?.name.orEmpty(), 2, SankeyRole.INVESTING)
            links += SankeyLink(total, key, t.cents)
        }
        if (toPots > 0) {
            nodes += SankeyNode("saving", "Sparen & Töpfe", 2, SankeyRole.SAVING)
            links += SankeyLink(total, "saving", toPots)
        }
        if (inflow > outflow) {
            nodes += SankeyNode("rest", "Übrig", 2, SankeyRole.REST)
            links += SankeyLink(total, "rest", inflow - outflow)
        }

        if (byCategory) {
            val typeIds = types.map { it.id }.toSet()
            val categoryById = categories.associateBy { it.id }
            val expenses = list.filter { it.kind == TxKind.EXPENSE }
            // Type -> category flows; one node per category, shared by all types.
            val pairs = expenses.groupBy { (it.expenseTypeId?.takeIf(typeIds::contains) ?: 0L) to (it.categoryId?.takeIf(categoryById::containsKey) ?: 0L) }
            val catTotals = pairs.entries.groupBy({ it.key.second }, { it.value.sumOf { tx -> tx.amountCents } }).mapValues { it.value.sum() }
            catTotals.entries.sortedByDescending { it.value }.forEach { (catId, _) ->
                val cat = categoryById[catId]
                nodes += SankeyNode("cat:$catId", cat?.name ?: "Ohne Kategorie", 3, SankeyRole.CATEGORY, cat?.color ?: 0xFF5F6670)
            }
            pairs.forEach { (key, txs) ->
                links += SankeyLink("type:${key.first}", "cat:${key.second}", txs.sumOf { it.amountCents })
            }
        }
        return SankeyData(nodes, links.filter { it.value > 0 })
    }
}
