package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SourceType {
  SCROLL_STITCH,
  PICK_STITCH,
  IMPORTED,
  SHARED,
}

enum class ScreenshotStatus {
  ACTIVE,
  KEPT_FOREVER,
  CLEANUP_READY,
}

@Entity(tableName = "screenshots")
data class ScreenshotEntity(
  @PrimaryKey val id: String,
  val encryptedImagePath: String,
  val encryptedThumbPath: String,
  val title: String,
  val sourceType: String,
  val status: String,
  val createdAtMillis: Long,
  val expiresAtMillis: Long?,
  val pendingGalleryDeleteUri: String? = null,
  val fileSizeBytes: Long,
  val width: Int,
  val height: Int,
  val frameCount: Int = 1,
)

@Entity(tableName = "cleanup_stats")
data class CleanupStatsEntity(
  @PrimaryKey val id: Int = 1,
  val totalShreddedCount: Int = 0,
  val totalBytesReclaimed: Long = 0L,
  val totalStitchedCount: Int = 0,
)
