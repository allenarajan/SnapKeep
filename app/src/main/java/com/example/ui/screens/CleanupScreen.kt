package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ScreenshotEntity
import com.example.data.local.ScreenshotStatus
import com.example.ui.components.FrostedCard
import com.example.ui.components.SnapPillButton
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatRemainingTime
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosMint
import com.example.ui.theme.IosRose
import com.example.ui.theme.LocalSnapKeepColors

@Composable
fun CleanupScreen(
  queueItems: List<ScreenshotEntity>,
  upcomingTimedItems: List<ScreenshotEntity>,
  nowMillis: Long,
  loadThumbnail: suspend (ScreenshotEntity) -> Bitmap?,
  onCleanAllExpired: () -> Unit,
  onKeepForever: (String) -> Unit,
  onShredSingle: (ScreenshotEntity) -> Unit,
) {
  val extra = LocalSnapKeepColors.current

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "1-Tap Cleanup Queue",
        style = MaterialTheme.typography.headlineLarge,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Expired screenshots wait here if confirmation is on or an original gallery copy needs your tap to remove.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(14.dp))

      if (queueItems.isNotEmpty()) {
        val totalBytes = queueItems.sumOf { it.fileSizeBytes }
        SnapPillButton(
          text = "Clean All (${queueItems.size}) • Free ${formatFileSize(totalBytes)}",
          icon = Icons.Filled.CleaningServices,
          containerColor = IosRose,
          contentColor = Color.White,
          onClick = onCleanAllExpired,
          modifier = Modifier.fillMaxWidth(),
          height = 60.dp,
          testTag = "cleanup_clean_all_button",
        )
        Spacer(modifier = Modifier.height(14.dp))
      }

      LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        if (queueItems.isEmpty()) {
          item {
            FrostedCard(modifier = Modifier.fillMaxWidth()) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Image(
                  painter = painterResource(id = R.drawable.img_empty_vault),
                  contentDescription = "No expired screenshots",
                  contentScale = ContentScale.Crop,
                  modifier =
                    Modifier
                      .size(width = 180.dp, height = 128.dp)
                      .clip(RoundedCornerShape(20.dp)),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = stringResource(R.string.empty_cleanup_title),
                  style = MaterialTheme.typography.titleLarge,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = stringResource(R.string.empty_cleanup_body),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }
        } else {
          item {
            Text(
              text = "Ready to Clean (${queueItems.size})",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          items(queueItems, key = { it.id }) { item ->
            CleanupRowCard(
              item = item,
              badgeText =
                if (item.pendingGalleryDeleteUri != null) "Vault + Gallery"
                else "Expired",
              badgeBg = extra.roseSoft,
              badgeFg = IosRose,
              loadThumbnail = loadThumbnail,
              onRescueForever = { onKeepForever(item.id) },
              onShredNow = { onShredSingle(item) },
            )
          }
        }

        if (upcomingTimedItems.isNotEmpty()) {
          item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Active Timers (${upcomingTimedItems.size})",
              style = MaterialTheme.typography.titleMedium,
            )
          }
          items(upcomingTimedItems, key = { "upcoming_${it.id}" }) { item ->
            CleanupRowCard(
              item = item,
              badgeText = formatRemainingTime(item.expiresAtMillis, nowMillis),
              badgeBg = extra.amberSoft,
              badgeFg = IosAmber,
              loadThumbnail = loadThumbnail,
              onRescueForever = { onKeepForever(item.id) },
              onShredNow = { onShredSingle(item) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CleanupRowCard(
  item: ScreenshotEntity,
  badgeText: String,
  badgeBg: Color,
  badgeFg: Color,
  loadThumbnail: suspend (ScreenshotEntity) -> Bitmap?,
  onRescueForever: () -> Unit,
  onShredNow: () -> Unit,
) {
  val thumb by
    produceState<Bitmap?>(initialValue = null, key1 = item.id) {
      value = loadThumbnail(item)
    }

  FrostedCard(
    contentPadding = PaddingValues(14.dp),
    modifier = Modifier.fillMaxWidth().testTag("cleanup_row_${item.id}"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val bmp = thumb
      if (bmp != null) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier
              .size(width = 64.dp, height = 84.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
        )
      } else {
        Spacer(
          modifier =
            Modifier
              .size(width = 64.dp, height = 84.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        StatusPill(
          text = badgeText,
          containerColor = badgeBg,
          contentColor = badgeFg,
          icon = Icons.Filled.Timer,
          monoText = item.status == ScreenshotStatus.ACTIVE.name,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onRescueForever,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(48.dp).testTag("cleanup_rescue_${item.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.AllInclusive,
              contentDescription = null,
              tint = IosMint,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = " Keep",
              color = IosMint,
              style = MaterialTheme.typography.labelMedium,
            )
          }
          IconButton(
            onClick = onShredNow,
            modifier = Modifier.size(48.dp).testTag("cleanup_shred_${item.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteForever,
              contentDescription = "Shred screenshot",
              tint = IosRose,
            )
          }
        }
      }
    }
  }
}
