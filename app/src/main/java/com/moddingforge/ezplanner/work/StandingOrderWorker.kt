package com.moddingforge.ezplanner.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moddingforge.ezplanner.EzPlannerApp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Books due standing orders once a day shortly after midnight (the app also does it on every start). */
class StandingOrderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as EzPlannerApp).repository
        repo.runDueStandingOrders()
        return Result.success()
    }

    companion object {
        private const val NAME = "standing-orders-daily"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            val nextRun = LocalDate.now().plusDays(1).atTime(0, 5)
            val delay = Duration.between(now, nextRun)
            val request = PeriodicWorkRequestBuilder<StandingOrderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
