package com.lagradost.cloudstream3.ui.calendartasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.utils.CalendarTvChannelHelper

class CalendarSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!GoogleSyncClient.isAuthenticated()) {
            return Result.success()
        }
        
        try {
            val events = GoogleSyncClient.fetchCalendarEvents()
            val tasks = GoogleSyncClient.fetchTasks()
            
            val cache = CalendarTaskCache(
                events = events,
                tasks = tasks,
                lastSyncTime = System.currentTimeMillis()
            )
            
            // Save cache
            CloudStreamApp.setKey("google_calendar_tasks_cache", cache)
            
            // Sync to Android TV launcher channel
            CalendarTvChannelHelper.syncToTvLauncher(appContext, cache)
            
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }
    companion object {
        private const val CALENDAR_WORK_NAME = "work_calendar_sync"

        fun enqueuePeriodicWork(context: Context?) {
            if (context == null) return

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val periodicWork = androidx.work.PeriodicWorkRequest.Builder(
                CalendarSyncWorker::class.java,
                2,
                java.util.concurrent.TimeUnit.HOURS
            )
                .addTag(CALENDAR_WORK_NAME)
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CALENDAR_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }
    }
}
