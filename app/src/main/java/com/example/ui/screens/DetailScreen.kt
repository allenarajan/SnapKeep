package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ScreenshotEntity
import com.example.data.local.ScreenshotStatus
import com.example.data.preferences.AppPreferences
import com.example.ui.components.FrostedCard
import com.example.ui.components.SnapPillButton
import com.example.ui.components.StatusPill
import com.example.ui.components.formatPresetReadable
import com.example.ui.components.formatRemainingTime
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosIndigo
import com.example.ui.theme.IosMint
import com.example.ui.theme.IosRose
import com.example.ui.theme.LocalSnapKeepColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
  item: ScreenshotEntity?,
  preferences: AppPreferences,
  nowMillis: Long,
  loadFullImage: suspend (ScreenshotEntity) -> Bitmap?,
  onBack: () -> Unit,
  onUpdateTimer: (String, Int?) -> Unit,
  onShareAndDelete: (ScreenshotEntity) -> Unit,
  onExportToGallery: (ScreenshotEntity) -> Unit,
  onShredNow: (ScreenshotEntity) -> Unit,
) {
  BackHandler(onBack = onBack)
  val extra = LocalSnapKeepColors.current
  var showTimerDialog by remember { mutableStateOf(false) }
  var showShredConfirm by remember { mutableStateOf(false) }

  if (item == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Screenshot no longer in vault.", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        SnapPillButton(text = "Go Back", onClick = onBack, testTag = "detail_missing_back")
      }
    }
    return
  }

  val fullBitmap by
    produceState<Bitmap?>(initialValue = null, key1 = item.id) {
      value = loadFullImage(item)
    }

  var zoomScale by remember { mutableFloatStateOf(1f) }
  val isForever = item.status == ScreenshotStatus.KEPT_FOREVER.name

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = {
        Column {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              if (isForever) "Kept Forever"
              else "Auto-deletes in ${formatRemainingTime(item.expiresAtMillis, nowMillis)}",
            style = MaterialTheme.typography.labelMedium,
            color = if (isForever) IosMint else IosAmber,
          )
        }
      },
      navigationIcon = {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("detail_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Vault",
          )
        }
      },
      actions = {
        IconButton(
          onClick = { onExportToGallery(item) },
          modifier = Modifier.size(48.dp).testTag("detail_export_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Download,
            contentDescription = "Save copy to gallery",
            tint = IosBlue,
          )
        }
        IconButton(
          onClick = { showShredConfirm = true },
          modifier = Modifier.size(48.dp).testTag("detail_shred_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.DeleteForever,
            contentDescription = "Shred screenshot",
            tint = IosRose,
          )
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )

    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier =
          Modifier
            .weight(1f)
            .widthIn(max = 700.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .pointerInput(Unit) {
              detectTransformGestures { _, _, zoom, _ ->
                zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
              }
            }
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
      ) {
        val bmp = fullBitmap
        if (bmp == null) {
          Box(modifier = Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = IosBlue)
          }
        } else {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = item.title,
            contentScale = ContentScale.FillWidth,
            modifier =
              Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      FrostedCard(
        modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth().padding(bottom = 14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (isForever) {
            SnapPillButton(
              text = "Set Timer",
              icon = Icons.Filled.Timer,
              containerColor = IosAmber,
              onClick = { showTimerDialog = true },
              modifier = Modifier.weight(1f),
              testTag = "detail_set_timer_button",
            )
          } else {
            SnapPillButton(
              text = "Keep Forever",
              icon = Icons.Filled.AllInclusive,
              containerColor = IosMint,
              onClick = { onUpdateTimer(item.id, null) },
              modifier = Modifier.weight(1f),
              testTag = "detail_keep_forever_button",
            )
          }

          SnapPillButton(
            text = "Share & Delete",
            icon = Icons.Filled.Share,
            containerColor = IosIndigo,
            onClick = { onShareAndDelete(item) },
            modifier = Modifier.weight(1f),
            testTag = "detail_share_delete_button",
          )
        }
        if (!isForever) {
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(
            onClick = { showTimerDialog = true },
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("detail_change_timer_button"),
          ) {
            StatusPill(
              text = "Change Auto-Delete Timer",
              containerColor = extra.amberSoft,
              contentColor = IosAmber,
              icon = Icons.Filled.Timer,
            )
          }
        }
      }
    }
  }

  if (showTimerDialog) {
    val presets =
      listOf(
        preferences.timerPreset1Minutes,
        preferences.timerPreset2Minutes,
        preferences.timerPreset3Minutes,
      )
    AlertDialog(
      onDismissRequest = { showTimerDialog = false },
      shape = RoundedCornerShape(26.dp),
      title = { Text("Pick Auto-Delete Timer", style = MaterialTheme.typography.titleLarge) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          presets.forEachIndexed { idx, mins ->
            SnapPillButton(
              text = "Delete after ${formatPresetReadable(mins)}",
              icon = Icons.Filled.Timer,
              containerColor = IosAmber,
              onClick = {
                showTimerDialog = false
                onUpdateTimer(item.id, mins)
              },
              modifier = Modifier.fillMaxWidth(),
              testTag = "detail_timer_option_$idx",
            )
          }
          SnapPillButton(
            text = "Keep Forever",
            icon = Icons.Filled.AllInclusive,
            containerColor = IosMint,
            onClick = {
              showTimerDialog = false
              onUpdateTimer(item.id, null)
            },
            modifier = Modifier.fillMaxWidth(),
            testTag = "detail_timer_forever",
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showTimerDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showShredConfirm) {
    AlertDialog(
      onDismissRequest = { showShredConfirm = false },
      shape = RoundedCornerShape(26.dp),
      title = { Text("Shred Screenshot?", style = MaterialTheme.typography.titleLarge) },
      text = {
        Text(
          "This will permanently erase the encrypted screenshot from your phone. It cannot be undone.",
          style = MaterialTheme.typography.bodyLarge,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showShredConfirm = false
            onShredNow(item)
            onBack()
          },
          modifier = Modifier.testTag("detail_confirm_shred"),
        ) {
          Text("Shred Now", color = IosRose)
        }
      },
      dismissButton = {
        TextButton(onClick = { showShredConfirm = false }) { Text("Cancel") }
      },
    )
  }
}
