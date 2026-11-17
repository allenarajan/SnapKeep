package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ScreenshotEntity
import com.example.data.local.ScreenshotStatus
import com.example.data.local.SourceType
import com.example.ui.components.FrostedCard
import com.example.ui.components.SnapPillButton
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatRemainingTime
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosMint
import com.example.ui.theme.IosRose
import com.example.ui.theme.LocalSnapKeepColors

enum class VaultFilter(val label: String) {
  ALL("All"),
  TIMED("Timed"),
  FOREVER("Forever"),
  LONG("Long Scrolls"),
}

@Composable
fun VaultScreen(
  screenshots: List<ScreenshotEntity>,
  nowMillis: Long,
  loadThumbnail: suspend (ScreenshotEntity) -> Bitmap?,
  onOpenDetail: (String) -> Unit,
  onPickToImport: () -> Unit,
) {
  var selectedFilter by remember { mutableStateOf(VaultFilter.ALL) }
  val extra = LocalSnapKeepColors.current

  val filtered =
    remember(screenshots, selectedFilter) {
      when (selectedFilter) {
        VaultFilter.ALL -> screenshots
        VaultFilter.TIMED ->
          screenshots.filter { it.status == ScreenshotStatus.ACTIVE.name && it.expiresAtMillis != null }
        VaultFilter.FOREVER ->
          screenshots.filter { it.status == ScreenshotStatus.KEPT_FOREVER.name }
        VaultFilter.LONG ->
          screenshots.filter {
            it.sourceType == SourceType.SCROLL_STITCH.name ||
              it.sourceType == SourceType.PICK_STITCH.name
          }
      }
    }

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()) {
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Private Vault",
          style = MaterialTheme.typography.headlineLarge,
        )
        StatusPill(
          text = "${screenshots.size} Locked",
          containerColor = extra.blueSoft,
          contentColor = IosBlue,
          icon = Icons.Filled.Lock,
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VaultFilter.entries.forEach { filter ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { selectedFilter = filter },
            label = {
              Text(
                text = filter.label,
                style = MaterialTheme.typography.labelLarge,
              )
            },
            shape = RoundedCornerShape(20.dp),
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = IosBlue,
                selectedLabelColor = Color.White,
              ),
            modifier = Modifier.height(48.dp).testTag("vault_filter_${filter.name.lowercase()}"),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (filtered.isEmpty()) {
        FrostedCard(
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_empty_vault),
              contentDescription = "Empty vault illustration",
              contentScale = ContentScale.Crop,
              modifier =
                Modifier
                  .size(width = 210.dp, height = 150.dp)
                  .clip(RoundedCornerShape(22.dp)),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = stringResource(R.string.empty_vault_title),
              style = MaterialTheme.typography.titleLarge,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = stringResource(R.string.empty_vault_body),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            SnapPillButton(
              text = "Pick Screenshot",
              onClick = onPickToImport,
              containerColor = IosBlue,
              testTag = "vault_empty_import_button",
            )
          }
        }
      } else {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 156.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(filtered, key = { it.id }) { item ->
            VaultGridCard(
              item = item,
              nowMillis = nowMillis,
              loadThumbnail = loadThumbnail,
              onClick = { onOpenDetail(item.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun VaultGridCard(
  item: ScreenshotEntity,
  nowMillis: Long,
  loadThumbnail: suspend (ScreenshotEntity) -> Bitmap?,
  onClick: () -> Unit,
) {
  val extra = LocalSnapKeepColors.current
  val thumb by
    produceState<Bitmap?>(initialValue = null, key1 = item.id) {
      value = loadThumbnail(item)
    }

  val isForever = item.status == ScreenshotStatus.KEPT_FOREVER.name
  val isCleanupReady =
    item.status == ScreenshotStatus.CLEANUP_READY.name ||
      (item.expiresAtMillis != null && item.expiresAtMillis <= nowMillis)
  val isLong =
    item.sourceType == SourceType.SCROLL_STITCH.name ||
      item.sourceType == SourceType.PICK_STITCH.name

  FrostedCard(
    onClick = onClick,
    contentPadding = PaddingValues(10.dp),
    cornerRadius = 22.dp,
    modifier = Modifier.fillMaxWidth().testTag("vault_item_${item.id}"),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(0.78f)
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
      val bmp = thumb
      if (bmp != null) {
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }

      if (isLong) {
        StatusPill(
          text = "Long ${item.frameCount}x",
          containerColor = IosBlue.copy(alpha = 0.92f),
          contentColor = Color.White,
          icon = Icons.Filled.ViewAgenda,
          modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )
      }

      val (badgeColor, badgeTextColor, badgeIcon, badgeText) =
        when {
          isForever ->
            QuadBadge(extra.mintSoft, IosMint, Icons.Filled.AllInclusive, "Forever")
          isCleanupReady ->
            QuadBadge(extra.roseSoft, IosRose, Icons.Filled.Timer, "Expired")
          else ->
            QuadBadge(
              extra.amberSoft,
              IosAmber,
              Icons.Filled.Timer,
              formatRemainingTime(item.expiresAtMillis, nowMillis),
            )
        }

      StatusPill(
        text = badgeText,
        containerColor = badgeColor,
        contentColor = badgeTextColor,
        icon = badgeIcon,
        monoText = !isForever && !isCleanupReady,
        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
      )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = item.title,
      style = MaterialTheme.typography.titleMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = "${item.width}×${item.height} • ${formatFileSize(item.fileSizeBytes)}",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.Normal,
      maxLines = 1,
    )
  }
}

private data class QuadBadge(
  val bg: Color,
  val fg: Color,
  val icon: androidx.compose.ui.graphics.vector.ImageVector,
  val text: String,
)
