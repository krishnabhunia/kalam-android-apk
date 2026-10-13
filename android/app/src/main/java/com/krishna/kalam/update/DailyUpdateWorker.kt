package com.krishna.kalam.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.krishna.kalam.core.Logger
import com.krishna.kalam.data.Prefs
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** WorkManager retains this unique schedule through app restarts and device reboots. */
class DailyUpdateWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!UpdateChecks.dailyEnabled(applicationContext)) return Result.success()
        return try {
            Logger.init(applicationContext)
            // Read the current beta preference, rather than freezing it in the scheduled request.
            if (UpdateChecks.check(applicationContext, Prefs.includeBeta(applicationContext))) Result.success()
            else Result.retry()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.e("Updates", "daily update check failed", e)
            Result.retry()
        }
    }
}

object DailyUpdateSchedule {
    private const val NAME = "kalam-daily-github-update-check"

    fun apply(context: Context) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!UpdateChecks.dailyEnabled(context)) {
            manager.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DailyUpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS) // Opening Kalam already performs an immediate check.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        // Preserve the existing enqueue time; reopening the app must not postpone tomorrow's check.
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
