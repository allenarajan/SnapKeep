package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ScreenshotStatus
import com.example.data.local.SourceType
import com.example.data.repository.VaultRepository
import com.example.security.VaultCryptoManager
import com.example.stitch.LongScreenshotStitcher
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `app name matches SnapKeep`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SnapKeep", appName)
  }

  @Test
  fun `vault crypto encrypts decrypts shreds and verifies pin`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)

    val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(Color.CYAN)
    val file = File(context.cacheDir, "test_secret.skv")

    val size = crypto.saveEncryptedBitmap(bitmap, file)
    assertTrue(size > 0)
    assertTrue(file.exists())

    val decrypted = crypto.decryptBitmap(file)
    assertNotNull(decrypted)
    assertEquals(120, decrypted?.width)
    assertEquals(180, decrypted?.height)

    crypto.shredFile(file)
    assertFalse(file.exists())

    val (hash, salt) = crypto.createSaltedPinHash("2468")
    assertTrue(crypto.verifyPin("2468", salt, hash))
    assertFalse(crypto.verifyPin("1111", salt, hash))
  }

  @Test
  fun `stitcher detects vertical overlap and renders taller image`() = runBlocking {
    val w = 360
    val h = 900
    val pixels = IntArray(w * h) { Color.WHITE }
    val colors = listOf(Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA, Color.DKGRAY, Color.CYAN)
    for (idx in 0 until 15) {
      val startY = 30 + idx * 56
      val left = 24 + ((idx * 37) % 90)
      val right = 210 + ((idx * 43) % 120)
      val color = colors[idx % colors.size]
      for (y in startY until (startY + 32).coerceAtMost(h)) {
        val rowBase = y * w
        for (x in left until right) {
          pixels[rowBase + x] = color
        }
      }
    }
    val tallMaster = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    tallMaster.setPixels(pixels, 0, w, 0, 0, w, h)

    val frame1 = Bitmap.createBitmap(tallMaster, 0, 0, 360, 540)
    val frame2 = Bitmap.createBitmap(tallMaster, 0, 340, 360, 560)

    val plan = LongScreenshotStitcher.createStitchPlan(listOf(frame1, frame2), trimSystemBars = false)
    assertEquals(1, plan.overlapsPx.size)
    assertTrue("Expected ~200px overlap, got ${plan.overlapsPx[0]}", plan.overlapsPx[0] in 175..225)

    val stitched = LongScreenshotStitcher.renderBitmap(plan)
    assertEquals(360, stitched.width)
    assertTrue(stitched.height in 870..930)
  }

  @Test
  fun `vault repository saves timed screenshot and cleans expired items`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getInstance(context)
    val crypto = VaultCryptoManager(context)
    val repo = VaultRepository(context, db.screenshotDao(), crypto)

    val bmp = Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888)
    val saved =
      repo.saveBitmapToVault(
        bitmap = bmp,
        title = "Temp Receipt",
        sourceType = SourceType.IMPORTED,
        timerMinutes = 15,
      )
    assertEquals(ScreenshotStatus.ACTIVE.name, saved.status)

    repo.updateTimer(saved.id, null)
    val updated = repo.observeById(saved.id).first()
    assertEquals(ScreenshotStatus.KEPT_FOREVER.name, updated?.status)

    repo.shredById(saved.id)
    val afterDelete = repo.observeById(saved.id).first()
    assertEquals(null, afterDelete)
  }

  @Test
  fun `expired screenshot moves to cleanup queue or shreds silently`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getInstance(context)
    val dao = db.screenshotDao()
    val crypto = VaultCryptoManager(context)
    val repo = VaultRepository(context, dao, crypto)

    val bmp = Bitmap.createBitmap(80, 100, Bitmap.Config.ARGB_8888)
    val saved =
      repo.saveBitmapToVault(
        bitmap = bmp,
        title = "Expiring Code",
        sourceType = SourceType.IMPORTED,
        timerMinutes = 1,
      )

    // Force expiration timestamp into the past
    dao.updateTimer(saved.id, ScreenshotStatus.ACTIVE.name, System.currentTimeMillis() - 10_000L)
    repo.markExpiredReadyOrShred(silentAutoShred = false)
    val queued = repo.observeById(saved.id).first()
    assertEquals(ScreenshotStatus.CLEANUP_READY.name, queued?.status)

    // Now test silent auto shred on a fresh active expired item
    val saved2 =
      repo.saveBitmapToVault(
        bitmap = bmp,
        title = "Silent Shred",
        sourceType = SourceType.IMPORTED,
        timerMinutes = 1,
      )
    dao.updateTimer(saved2.id, ScreenshotStatus.ACTIVE.name, System.currentTimeMillis() - 10_000L)
    repo.markExpiredReadyOrShred(silentAutoShred = true)
    assertEquals(null, repo.observeById(saved2.id).first())

    // Clean up queued item from test DB
    repo.shredById(saved.id)
  }

  @Test
  fun `main activity launches visibly without black screen and reaches home`() {
    composeTestRule.waitForIdle()
    val flags = composeTestRule.activity.window.attributes.flags
    assertEquals(0, flags and WindowManager.LayoutParams.FLAG_SECURE)

    composeTestRule.waitUntil(timeoutMillis = 4000) {
      composeTestRule.onAllNodesWithText("Welcome to SnapKeep").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Welcome to SnapKeep").assertIsDisplayed()
    composeTestRule.onNodeWithTag("onboarding_skip_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 4000) {
      composeTestRule.onAllNodesWithTag("home_start_bubble_button").fetchSemanticsNodes().isNotEmpty()
    }

    composeTestRule.onNodeWithTag("home_start_bubble_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_import_screenshot_button").performScrollTo().assertIsDisplayed()
  }
}
