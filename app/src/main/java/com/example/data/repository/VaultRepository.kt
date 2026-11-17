package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.CleanupStatsEntity
import com.example.data.local.ScreenshotDao
import com.example.data.local.ScreenshotEntity
import com.example.data.local.ScreenshotStatus
import com.example.data.local.SourceType
import com.example.security.VaultCryptoManager
import com.example.worker.AutoShredWorker
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VaultRepository(
  private val context: Context,
  private val dao: ScreenshotDao,
  val crypto: VaultCryptoManager,
) {
  val allScreenshots: Flow<List<ScreenshotEntity>> = dao.observeAll()

  val stats: Flow<CleanupStatsEntity> =
    dao.observeStats().map { it ?: CleanupStatsEntity() }

  fun observeById(id: String): Flow<ScreenshotEntity?> = dao.observeById(id)

  suspend fun saveBitmapToVault(
    bitmap: Bitmap,
    title: String,
    sourceType: SourceType,
    timerMinutes: Int?,
    frameCount: Int = 1,
    pendingGalleryDeleteUri: String? = null,
  ): ScreenshotEntity =
    withContext(Dispatchers.IO) {
      val id = UUID.randomUUID().toString()
      val fullFile = crypto.createVaultImageFile(id, isThumb = false)
      val thumbFile = crypto.createVaultImageFile(id, isThumb = true)

      val fullSize = crypto.saveEncryptedBitmap(bitmap, fullFile, quality = 92)
      val thumbBitmap = createThumbnail(bitmap)
      val thumbSize = crypto.saveEncryptedBitmap(thumbBitmap, thumbFile, quality = 82)
      if (thumbBitmap != bitmap) thumbBitmap.recycle()

      val now = System.currentTimeMillis()
      val expiresAt =
        if (timerMinutes == null) null else now + timerMinutes * 60_000L
      val status =
        if (timerMinutes == null) ScreenshotStatus.KEPT_FOREVER.name
        else ScreenshotStatus.ACTIVE.name

      val entity =
        ScreenshotEntity(
          id = id,
          encryptedImagePath = fullFile.absolutePath,
          encryptedThumbPath = thumbFile.absolutePath,
          title = title,
          sourceType = sourceType.name,
          status = status,
          createdAtMillis = now,
          expiresAtMillis = expiresAt,
          pendingGalleryDeleteUri = pendingGalleryDeleteUri,
          fileSizeBytes = fullSize + thumbSize,
          width = bitmap.width,
          height = bitmap.height,
          frameCount = frameCount,
        )
      dao.upsertScreenshot(entity)

      if (sourceType == SourceType.SCROLL_STITCH || sourceType == SourceType.PICK_STITCH) {
        val current = dao.getStats() ?: CleanupStatsEntity()
        dao.upsertStats(current.copy(totalStitchedCount = current.totalStitchedCount + 1))
      }

      if (timerMinutes != null) {
        AutoShredWorker.scheduleNextDelay(context, timerMinutes.toLong())
      }

      entity
    }

  suspend fun decodeUriToBitmap(uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
      runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream)
        }
      }.getOrNull()
    }

  suspend fun decryptThumbnail(entity: ScreenshotEntity): Bitmap? =
    crypto.decryptBitmap(File(entity.encryptedThumbPath))

  suspend fun decryptFullImage(entity: ScreenshotEntity): Bitmap? =
    crypto.decryptBitmap(File(entity.encryptedImagePath))

  suspend fun updateTimer(id: String, timerMinutes: Int?) {
    val expiresAt =
      if (timerMinutes == null) null else System.currentTimeMillis() + timerMinutes * 60_000L
    val status =
      if (timerMinutes == null) ScreenshotStatus.KEPT_FOREVER.name
      else ScreenshotStatus.ACTIVE.name
    dao.updateTimer(id = id, status = status, expiresAtMillis = expiresAt)
    if (timerMinutes != null) {
      AutoShredWorker.scheduleNextDelay(context, timerMinutes.toLong())
    }
  }

  suspend fun markExpiredReadyOrShred(silentAutoShred: Boolean) {
    val now = System.currentTimeMillis()
    val expired = dao.getExpiredActive(now)
    if (expired.isEmpty()) return

    val toDelete = mutableListOf<ScreenshotEntity>()
    for (item in expired) {
      if (silentAutoShred && item.pendingGalleryDeleteUri.isNullOrBlank()) {
        toDelete.add(item)
      } else {
        dao.updateTimer(
          id = item.id,
          status = ScreenshotStatus.CLEANUP_READY.name,
          expiresAtMillis = item.expiresAtMillis,
        )
      }
    }
    if (toDelete.isNotEmpty()) {
      shredItems(toDelete)
    }
  }

  suspend fun shredById(id: String) {
    val item = dao.getById(id) ?: return
    shredItems(listOf(item))
  }

  suspend fun shredItems(items: List<ScreenshotEntity>) =
    withContext(Dispatchers.IO) {
      if (items.isEmpty()) return@withContext
      var reclaimed = 0L
      for (item in items) {
        crypto.shredFile(File(item.encryptedImagePath))
        crypto.shredFile(File(item.encryptedThumbPath))
        reclaimed += item.fileSizeBytes
      }
      dao.deleteByIds(items.map { it.id })
      val current = dao.getStats() ?: CleanupStatsEntity()
      dao.upsertStats(
        current.copy(
          totalShreddedCount = current.totalShreddedCount + items.size,
          totalBytesReclaimed = current.totalBytesReclaimed + reclaimed,
        ),
      )
    }

  suspend fun clearPendingGalleryUris(ids: List<String>) {
    if (ids.isNotEmpty()) {
      dao.clearGalleryUris(ids)
    }
  }

  suspend fun createShareUri(entity: ScreenshotEntity): Uri? =
    withContext(Dispatchers.IO) {
      crypto.clearShareCache()
      val temp =
        crypto.decryptToTempFile(
          source = File(entity.encryptedImagePath),
          fileName = "snapkeep_${entity.id.take(8)}.jpg",
        ) ?: return@withContext null
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", temp)
    }

  suspend fun createTempShareUriFromBitmap(bitmap: Bitmap): Uri? =
    withContext(Dispatchers.IO) {
      crypto.clearShareCache()
      val file = crypto.createTempShareFile("snapkeep_stitch_${System.currentTimeMillis()}.jpg")
      file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

  suspend fun exportCopyToGallery(entity: ScreenshotEntity): Boolean =
    withContext(Dispatchers.IO) {
      val bitmap = decryptFullImage(entity) ?: return@withContext false
      val fileName = "SnapKeep_${System.currentTimeMillis()}.jpg"
      val resolver = context.contentResolver
      val values =
        ContentValues().apply {
          put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
          put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
              MediaStore.Images.Media.RELATIVE_PATH,
              "${Environment.DIRECTORY_PICTURES}/SnapKeep",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
          }
        }

      val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext false

      val success =
        runCatching {
          resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
          }
          true
        }.getOrDefault(false)
      bitmap.recycle()
      success
    }

  private fun createThumbnail(source: Bitmap): Bitmap {
    val maxWidth = 420
    if (source.width <= maxWidth && source.height <= 760) return source
    val aspect = source.height.toFloat() / source.width.toFloat()
    val targetW = minOf(source.width, maxWidth)
    val targetH = (targetW * aspect).toInt().coerceAtMost(760).coerceAtLeast(120)
    // Crop top portion for ultra-long stitched screenshots so thumbnail stays readable
    val cropHeight = minOf(source.height, (source.width * 1.85f).toInt())
    val cropped = Bitmap.createBitmap(source, 0, 0, source.width, cropHeight)
    val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH.coerceAtMost((targetW * 1.85f).toInt()), true)
    if (cropped != source) cropped.recycle()
    return scaled
  }
}
