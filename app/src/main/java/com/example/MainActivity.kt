package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SourceType
import com.example.ui.MainViewModel
import com.example.ui.SnapKeepApp
import com.example.worker.NotificationHelper

class MainActivity : FragmentActivity() {

  private val viewModel: MainViewModel by viewModels()
  private var openCleanupSignal by mutableIntStateOf(0)
  private var suppressNextBackgroundLock = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIncomingIntent(intent)

    setContent {
      val prefs by viewModel.preferences.collectAsStateWithLifecycle()

      LaunchedEffect(prefs.isScreenShieldEnabled) {
        if (prefs.isScreenShieldEnabled) {
          window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
      }

      SnapKeepApp(
        viewModel = viewModel,
        openCleanupSignal = openCleanupSignal,
        onSuppressNextBackgroundLock = { suppressNextBackgroundLock = true },
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  override fun onStop() {
    super.onStop()
    if (!isChangingConfigurations) {
      if (suppressNextBackgroundLock) {
        suppressNextBackgroundLock = false
      } else {
        viewModel.onAppBackgrounded()
      }
    }
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent == null) return
    if (intent.getStringExtra(NotificationHelper.EXTRA_OPEN_TAB) == NotificationHelper.TAB_CLEANUP) {
      openCleanupSignal++
    }

    when (intent.action) {
      Intent.ACTION_SEND -> {
        val uri = extractUriExtra(intent)
        if (uri != null) {
          viewModel.importSingleUriForMark(uri, SourceType.SHARED)
        }
      }
      Intent.ACTION_SEND_MULTIPLE -> {
        val uris = extractMultipleUriExtras(intent)
        if (uris.isNotEmpty()) {
          viewModel.handleMultipleUris(uris, fromShare = true)
        }
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun extractUriExtra(intent: Intent): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
      intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
  }

  @Suppress("DEPRECATION")
  private fun extractMultipleUriExtras(intent: Intent): List<Uri> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
      intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
  }
}
