package com.moddingforge.ezplanner

import android.app.Application
import com.moddingforge.ezplanner.data.EzRepository
import com.moddingforge.ezplanner.data.SettingsStore
import com.moddingforge.ezplanner.data.db.EzDatabase
import com.moddingforge.ezplanner.update.Updater
import com.moddingforge.ezplanner.work.StandingOrderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EzPlannerApp : Application() {
    /** App-wide scope for fire-and-forget work such as booking due standing orders. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: EzRepository by lazy {
        EzRepository(EzDatabase.create(this), SettingsStore(this))
    }

    val updater: Updater by lazy { Updater(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        StandingOrderWorker.schedule(this)
    }

    /** Called on every app start / return to the app. */
    fun bookDueStandingOrders() {
        appScope.launch { repository.runDueStandingOrders() }
    }
}
