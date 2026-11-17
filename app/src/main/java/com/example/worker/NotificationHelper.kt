package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

  const val CHANNEL_CAPTURE = "snapkeep_capture_channel"
  const val CHANNEL_ALERTS = "snapkeep_alerts_channel"
  const val NOTIFICATION_CAPTURE_ID = 4101
  const val NOTIFICATION_ALERT_ID = 4201
  const val EXTRA_OPEN_TAB = "extra_open_tab"
  const val TAB_CLEANUP = "cleanup"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      val captureChannel =
        NotificationChannel(
          CHANNEL_CAPTURE,
          context.getString(R.string.notification_channel_capture),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = context.getString(R.string.notification_channel_capture_desc)
        }

      val alertChannel =
        NotificationChannel(
          CHANNEL_ALERTS,
          context.getString(R.string.notification_channel_alerts),
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = context.getString(R.string.notification_channel_alerts_desc)
        }

      manager.createNotificationChannel(captureChannel)
      manager.createNotificationChannel(alertChannel)
    }
  }

  fun postCleanupAlert(
    context: Context,
    title: String,
    message: String,
  ) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }

    ensureChannels(context)
    val intent =
      Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_OPEN_TAB, TAB_CLEANUP)
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        4202,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ALERTS)
        .setSmallIcon(android.R.drawable.ic_menu_delete)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_ALERT_ID, notification)
  }
}
