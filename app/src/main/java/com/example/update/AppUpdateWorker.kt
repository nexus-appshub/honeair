package com.example.update

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

import com.example.notifications.LocalNotificationManager

class AppUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("AppUpdateWorker", "Running background update check from WorkManager...")
            val result = AppUpdateManager.checkForUpdate(applicationContext)
            if (result is UpdateCheckResult.UpdateAvailable) {
                Log.d("AppUpdateWorker", "New version detected in background: v${result.info.latestVersionName}")
                val info = result.info
                AppUpdateManager.showUpdateNotification(
                    context = applicationContext,
                    info = info,
                    currentVersion = AppUpdateManager.getAppVersionName(applicationContext)
                )
            } else {
                Log.d("AppUpdateWorker", "App is up to date in background check.")
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_TAG = "AppsHubBackgroundUpdateCheck"
        private const val UNIQUE_WORK_NAME = "com.example.update.AppsHubPeriodicUpdateCheck"

        /**
         * Schedules a periodic background update check that continues running even if the app is closed.
         */
        fun schedulePeriodicCheck(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                // WorkManager periodic interval minimum is 15 minutes
                val periodicWorkRequest = PeriodicWorkRequestBuilder<AppUpdateWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES // 5 min flex interval
                )
                    .setConstraints(constraints)
                    .addTag(WORK_TAG)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        10, TimeUnit.MINUTES
                    )
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest
                )

                // Also execute an immediate background check request
                val immediateWorkRequest = OneTimeWorkRequestBuilder<AppUpdateWorker>()
                    .setConstraints(constraints)
                    .addTag(WORK_TAG)
                    .build()

                WorkManager.getInstance(context).enqueue(immediateWorkRequest)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
