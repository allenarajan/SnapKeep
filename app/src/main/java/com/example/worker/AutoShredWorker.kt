package com.example.worker

import android.content.Context
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.CleanupStatsEntity
import com.example.data.local.ScreenshotStatus
import com.example.data.preferences.PreferencesRepository
import com.example.security.VaultCryptoManager
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class AutoShredWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val db = AppDatabase.getInstance(applicationContext)
    val dao = db.screenshotDao()
    val prefs = PreferencesRepository(applicationContext).preferences.first()
    val crypto = VaultCryptoManager(applicationContext)
    val now = System.currentTimeMillis()

    val expired = dao.getExpiredActive(now)
    if (expired.isEmpty()) return Result.success()

    var shreddedCount = 0
    var reclaimedBytes = 0L
    var queuedCount = 0

    for (item in expired) {
      val needsGalleryConfirm = !item.pendingGalleryDeleteUri.isNullOrBlank()
      if (prefs.silentAutoShred && !needsGalleryConfirm) {
        crypto.shredFile(File(item.encryptedImagePath))
        crypto.shredFile(File(item.encryptedThumbPath))
        dao.deleteById(item.id)
        shreddedCount++
        reclaimedBytes += item.fileSizeBytes
      } else {
        dao.updateTimer(
          id = item.id,
          status = ScreenshotStatus.CLEANUP_READY.name,
          expiresAtMillis = item.expiresAtMillis,
        )
        queuedCount++
      }
    }

    if (shreddedCount > 0) {
      val currentStats = dao.getStats() ?: CleanupStatsEntity()
      dao.upsertStats(
        currentStats.copy(
          totalShreddedCount = currentStats.totalShreddedCount + shreddedCount,
          totalBytesReclaimed = currentStats.totalBytesReclaimed + reclaimedBytes,
        ),
      )
    }

    if (prefs.areTimerAlertsEnabled) {
      when {
        queuedCount > 0 -> {
          NotificationHelper.postCleanupAlert(
            context = applicationContext,
            title = "Cleanup Ready ($queuedCount)",
            message =
              "$queuedCount temporary screenshot(s) expired. Tap for 1-Tap Cleanup.",
          )
        }
        shreddedCount > 0 -> {
          val mbText = String.format(Locale.US, "%.1f MB", reclaimedBytes / (1024f * 1024f))
          NotificationHelper.postCleanupAlert(
            context = applicationContext,
            title = "Auto-Cleaned $shreddedCount Screenshot(s)",
            message = "Freed $mbText safely from your SnapKeep vault.",
          )
        }
      }
    }

    return Result.success()
  }

  companion object {
    private const val PERIODIC_WORK_NAME = "snapkeep_periodic_shred"
    private const val NEXT_EXPIRY_WORK_NAME = "snapkeep_next_expiry_shred"

    private fun getWorkManager(context: Context): WorkManager {
      if (!WorkManager.isInitialized()) {
        WorkManager.initialize(context.applicationContext, Configuration.Builder().build())
      }
      return WorkManager.getInstance(context.applicationContext)
    }

    fun schedulePeriodic(context: Context) {
      runCatching {
        val request =
          PeriodicWorkRequestBuilder<AutoShredWorker>(15, TimeUnit.MINUTES).build()
        getWorkManager(context)
          .enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
          )
      }
    }

    fun scheduleNextDelay(context: Context, delayMinutes: Long) {
      runCatching {
        val safeDelay = delayMinutes.coerceAtLeast(1L)
        val request =
          OneTimeWorkRequestBuilder<AutoShredWorker>()
            .setInitialDelay(safeDelay, TimeUnit.MINUTES)
            .build()
        getWorkManager(context)
          .enqueueUniqueWork(
            NEXT_EXPIRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
          )
      }
    }
  }
}
