package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {
  @Query("SELECT * FROM screenshots ORDER BY createdAtMillis DESC")
  fun observeAll(): Flow<List<ScreenshotEntity>>

  @Query("SELECT * FROM screenshots WHERE id = :id LIMIT 1")
  fun observeById(id: String): Flow<ScreenshotEntity?>

  @Query("SELECT * FROM screenshots WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): ScreenshotEntity?

  @Query(
    "SELECT * FROM screenshots WHERE status = 'ACTIVE' AND expiresAtMillis IS NOT NULL AND expiresAtMillis <= :nowMillis",
  )
  suspend fun getExpiredActive(nowMillis: Long): List<ScreenshotEntity>

  @Query("SELECT * FROM screenshots WHERE status = 'CLEANUP_READY' ORDER BY createdAtMillis DESC")
  suspend fun getCleanupReady(): List<ScreenshotEntity>

  @Query("SELECT * FROM cleanup_stats WHERE id = 1")
  fun observeStats(): Flow<CleanupStatsEntity?>

  @Query("SELECT * FROM cleanup_stats WHERE id = 1")
  suspend fun getStats(): CleanupStatsEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertScreenshot(item: ScreenshotEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertStats(stats: CleanupStatsEntity)

  @Query("UPDATE screenshots SET status = :status, expiresAtMillis = :expiresAtMillis WHERE id = :id")
  suspend fun updateTimer(id: String, status: String, expiresAtMillis: Long?)

  @Query("UPDATE screenshots SET pendingGalleryDeleteUri = NULL WHERE id IN (:ids)")
  suspend fun clearGalleryUris(ids: List<String>)

  @Query("DELETE FROM screenshots WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM screenshots WHERE id IN (:ids)")
  suspend fun deleteByIds(ids: List<String>)
}
