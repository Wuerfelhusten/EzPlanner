package com.moddingforge.ezplanner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val monthlyBudgetCents: Long = 0,
    /** Warn when this share of the budget is used up. */
    val warnPercent: Int = 80,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val budgetKey = longPreferencesKey("monthly_budget_cents")
    private val warnKey = intPreferencesKey("warn_percent")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            monthlyBudgetCents = p[budgetKey] ?: 0,
            warnPercent = p[warnKey] ?: 80,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setBudget(cents: Long) {
        context.dataStore.edit { it[budgetKey] = cents }
    }

    suspend fun setWarnPercent(percent: Int) {
        context.dataStore.edit { it[warnKey] = percent.coerceIn(1, 100) }
    }

    suspend fun replace(settings: Settings) {
        context.dataStore.edit {
            it[budgetKey] = settings.monthlyBudgetCents
            it[warnKey] = settings.warnPercent
        }
    }
}
