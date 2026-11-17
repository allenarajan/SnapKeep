package com.example.capture

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingCaptureService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var windowManager: WindowManager? = null
  private var overlayView: View? = null
  private var countBadge: TextView? = null
  private var snapButton: TextView? = null
  private var autoButton: TextView? = null
  private var doneButton: TextView? = null

  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private var readerThread: HandlerThread? = null
  private var readerHandler: Handler? = null

  @Volatile private var latestFrameBitmap: Bitmap? = null

  private var captureWidth = 1080
  private var captureHeight = 1920
  private var captureDensity = 320

  private val capturedFrames = mutableListOf<Bitmap>()
  private var isSnapping = false
  private var isAutoSnapRunning = false
  private var autoSnapJob: Job? = null

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        finishAndReturnToApp(publishFrames = capturedFrames.isNotEmpty())
      }
    }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP_AND_STITCH) {
      finishAndReturnToApp(publishFrames = true)
      return START_NOT_STICKY
    }

    startForegroundCompat()

    val token = CaptureSessionBus.pendingToken
    if (token == null) {
      CaptureSessionBus.publishError("Screen capture permission expired. Please start again.")
      stopSelf()
      return START_NOT_STICKY
    }
    CaptureSessionBus.pendingToken = null

    if (!setupProjection(token)) {
      CaptureSessionBus.publishError("Unable to start screen capture on this device.")
      stopSelf()
      return START_NOT_STICKY
    }

    showFloatingBubble()
    CaptureSessionBus.updateCapturing(0)
    return START_NOT_STICKY
  }

  private fun startForegroundCompat() {
    NotificationHelper.ensureChannels(this)
    val stopIntent =
      Intent(this, FloatingCaptureService::class.java).apply { action = ACTION_STOP_AND_STITCH }
    val stopPending =
      PendingIntent.getService(
        this,
        4102,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification: Notification =
      NotificationCompat.Builder(this, NotificationHelper.CHANNEL_CAPTURE)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle(getString(R.string.capture_notification_title))
        .setContentText(getString(R.string.capture_notification_text))
        .setOngoing(true)
        .addAction(0, getString(R.string.capture_stop_action), stopPending)
        .build()

    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
      } else {
        0
      }

    ServiceCompat.startForeground(
      this,
      NotificationHelper.NOTIFICATION_CAPTURE_ID,
      notification,
      type,
    )
  }

  private fun setupProjection(token: PendingMediaProjectionToken): Boolean {
    return runCatching {
      val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
      windowManager = wm

      val metrics = DisplayMetrics()
      @Suppress("DEPRECATION")
      wm.defaultDisplay.getRealMetrics(metrics)
      val maxWidth = 1080
      val scale =
        if (metrics.widthPixels > maxWidth) maxWidth.toFloat() / metrics.widthPixels.toFloat()
        else 1f
      captureWidth = (metrics.widthPixels * scale).toInt().coerceAtLeast(360)
      captureHeight = (metrics.heightPixels * scale).toInt().coerceAtLeast(640)
      captureDensity = metrics.densityDpi

      val projectionManager =
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val projection =
        projectionManager.getMediaProjection(token.resultCode, token.dataIntent) ?: return false
      projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
      mediaProjection = projection

      val thread = HandlerThread("SnapKeepFrameReader").apply { start() }
      readerThread = thread
      val handler = Handler(thread.looper)
      readerHandler = handler

      val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
      reader.setOnImageAvailableListener(
        { imgReader ->
          val bmp = decodeLatestFromReader(imgReader)
          if (bmp != null) {
            latestFrameBitmap = bmp
          }
        },
        handler,
      )
      imageReader = reader

      virtualDisplay =
        projection.createVirtualDisplay(
          "SnapKeepScrollCapture",
          captureWidth,
          captureHeight,
          captureDensity,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          reader.surface,
          null,
          null,
        )
      true
    }.getOrDefault(false)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun showFloatingBubble() {
    val wm = windowManager ?: return
    if (overlayView != null) return

    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val container =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(10), dp(12), dp(12))
        background =
          GradientDrawable().apply {
            setColor(Color.parseColor("#F5FFFFFF"))
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.parseColor("#CBD5E1"))
          }
        elevation = dp(10).toFloat()
      }

    // Drag header + frame badge
    val header =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(2), dp(4), dp(8))
      }

    val badge =
      TextView(this).apply {
        text = "0 shots"
        textSize = 13f
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(Color.parseColor("#0F172A"))
      }
    countBadge = badge

    val closeBtn =
      TextView(this).apply {
        text = "  ✕"
        textSize = 15f
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(Color.parseColor("#64748B"))
        setPadding(dp(10), dp(2), dp(2), dp(2))
        setOnClickListener {
          stopAutoSnap()
          finishAndReturnToApp(publishFrames = false)
        }
      }

    header.addView(badge)
    header.addView(closeBtn)

    val snapBtn =
      createPillButton(
        label = "+ Snap",
        bgHex = "#0A84FF",
        textHex = "#FFFFFF",
        dp = ::dp,
      ).apply {
        setOnClickListener { triggerSingleSnap() }
      }
    snapButton = snapBtn

    val autoBtn =
      createPillButton(
        label = "Auto 2s",
        bgHex = "#E2E8F0",
        textHex = "#0F172A",
        dp = ::dp,
      ).apply {
        setOnClickListener { toggleAutoSnap() }
      }
    autoButton = autoBtn

    val finishBtn =
      createPillButton(
        label = "Stitch ✓",
        bgHex = "#30D158",
        textHex = "#FFFFFF",
        dp = ::dp,
      ).apply {
        isEnabled = false
        alpha = 0.55f
        setOnClickListener {
          stopAutoSnap()
          finishAndReturnToApp(publishFrames = true)
        }
      }
    doneButton = finishBtn

    container.addView(header)
    container.addView(snapBtn)
    container.addView(spaceView(dp(6)))
    container.addView(autoBtn)
    container.addView(spaceView(dp(6)))
    container.addView(finishBtn)

    val overlayType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
      }

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = dp(12)
        y = dp(180)
      }

    var startX = 0
    var startY = 0
    var touchX = 0f
    var touchY = 0f
    header.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          startX = params.x
          startY = params.y
          touchX = event.rawX
          touchY = event.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          params.x = startX - (event.rawX - touchX).toInt()
          params.y = startY + (event.rawY - touchY).toInt()
          runCatching { wm.updateViewLayout(container, params) }
          true
        }
        else -> false
      }
    }

    overlayView = container
    wm.addView(container, params)
  }

  private fun createPillButton(
    label: String,
    bgHex: String,
    textHex: String,
    dp: (Int) -> Int,
  ): TextView {
    return TextView(this).apply {
      text = label
      textSize = 14f
      gravity = Gravity.CENTER
      setTypeface(Typeface.DEFAULT_BOLD)
      setTextColor(Color.parseColor(textHex))
      minWidth = dp(96)
      minHeight = dp(44)
      setPadding(dp(14), dp(10), dp(14), dp(10))
      background =
        GradientDrawable().apply {
          setColor(Color.parseColor(bgHex))
          cornerRadius = dp(22).toFloat()
        }
    }
  }

  private fun spaceView(heightPx: Int): View =
    View(this).apply {
      layoutParams = LinearLayout.LayoutParams(1, heightPx)
    }

  private fun triggerSingleSnap() {
    if (isSnapping || capturedFrames.size >= MAX_FRAMES) return
    serviceScope.launch { captureOneFrame() }
  }

  private fun toggleAutoSnap() {
    if (isAutoSnapRunning) {
      stopAutoSnap()
    } else {
      isAutoSnapRunning = true
      autoButton?.text = "Pause ⏸"
      (autoButton?.background as? GradientDrawable)?.setColor(Color.parseColor("#FF9F0A"))
      autoButton?.setTextColor(Color.WHITE)
      autoSnapJob =
        serviceScope.launch {
          while (isActive && isAutoSnapRunning && capturedFrames.size < MAX_FRAMES) {
            captureOneFrame()
            delay(1800)
          }
          stopAutoSnap()
        }
    }
  }

  private fun stopAutoSnap() {
    isAutoSnapRunning = false
    autoSnapJob?.cancel()
    autoSnapJob = null
    autoButton?.text = "Auto 2s"
    (autoButton?.background as? GradientDrawable)?.setColor(Color.parseColor("#E2E8F0"))
    autoButton?.setTextColor(Color.parseColor("#0F172A"))
  }

  private suspend fun captureOneFrame() {
    if (isSnapping) return
    isSnapping = true
    try {
      overlayView?.visibility = View.INVISIBLE
      delay(135)

      val frame =
        withContext(Dispatchers.Default) {
          var captured: Bitmap? = imageReader?.let { decodeLatestFromReader(it) }
          if (captured == null) {
            delay(55)
            captured = imageReader?.let { decodeLatestFromReader(it) }
          }
          if (captured == null) {
            captured = latestFrameBitmap?.let { it.copy(Bitmap.Config.ARGB_8888, false) }
          }
          captured
        }

      overlayView?.visibility = View.VISIBLE

      if (frame != null) {
        capturedFrames.add(frame)
        val count = capturedFrames.size
        countBadge?.text = if (count == 1) "1 shot" else "$count shots"
        doneButton?.isEnabled = true
        doneButton?.alpha = 1f
        CaptureSessionBus.updateCapturing(count)

        snapButton?.text = "Snapped!"
        delay(280)
        snapButton?.text = "+ Snap"
      }
    } finally {
      overlayView?.visibility = View.VISIBLE
      isSnapping = false
    }
  }

  private fun decodeLatestFromReader(reader: ImageReader): Bitmap? {
    val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return null
    return try {
      val plane = image.planes[0]
      val buffer = plane.buffer
      val pixelStride = plane.pixelStride
      val rowStride = plane.rowStride
      val rowPadding = rowStride - pixelStride * captureWidth
      val fullWidth = captureWidth + rowPadding / pixelStride
      val temp = Bitmap.createBitmap(fullWidth, captureHeight, Bitmap.Config.ARGB_8888)
      temp.copyPixelsFromBuffer(buffer)
      val cropped = Bitmap.createBitmap(temp, 0, 0, captureWidth, captureHeight)
      if (cropped != temp) temp.recycle()
      cropped
    } catch (_: Throwable) {
      null
    } finally {
      image.close()
    }
  }

  private fun finishAndReturnToApp(publishFrames: Boolean) {
    stopAutoSnap()
    removeOverlay()
    releaseProjection()

    if (publishFrames && capturedFrames.isNotEmpty()) {
      CaptureSessionBus.publishFrames(capturedFrames.toList())
    } else {
      capturedFrames.forEach { it.recycle() }
      capturedFrames.clear()
      CaptureSessionBus.clearEvent()
    }

    val reopen =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    runCatching { startActivity(reopen) }
    stopSelf()
  }

  private fun removeOverlay() {
    overlayView?.let { view ->
      runCatching { windowManager?.removeView(view) }
    }
    overlayView = null
  }

  private fun releaseProjection() {
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching {
      imageReader?.setOnImageAvailableListener(null, null)
      imageReader?.close()
    }
    imageReader = null
    runCatching { readerThread?.quitSafely() }
    readerThread = null
    readerHandler = null
    latestFrameBitmap = null
    runCatching {
      mediaProjection?.unregisterCallback(projectionCallback)
      mediaProjection?.stop()
    }
    mediaProjection = null
  }

  override fun onDestroy() {
    stopAutoSnap()
    removeOverlay()
    releaseProjection()
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_STOP_AND_STITCH = "com.example.capture.STOP_AND_STITCH"
    private const val MAX_FRAMES = 14
  }
}
