package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CleanupStatsEntity
import com.example.data.preferences.AppPreferences
import com.example.ui.components.FrostedCard
import com.example.ui.components.SnapPillButton
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosMint
import com.example.ui.theme.IosRose
import com.example.ui.theme.LocalSnapKeepColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  preferences: AppPreferences,
  stats: CleanupStatsEntity,
  activeTimerCount: Int,
  cleanupReadyCount: Int,
  isCapturingLive: Boolean,
  onStartFloatingBubble: () -> Unit,
  onPickMultipleToStitch: () -> Unit,
  onPickSingleToProtect: () -> Unit,
  onOpenCleanupTab: () -> Unit,
  onLockNow: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val extra = LocalSnapKeepColors.current
  var showBubbleGuideSheet by remember { mutableStateOf(false) }
  var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Top greeting + privacy badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "SnapKeep",
            style = MaterialTheme.typography.headlineLarge,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
              text = stringResource(R.string.privacy_badge),
              containerColor = extra.mintSoft,
              contentColor = IosMint,
              icon = Icons.Filled.Shield,
            )
          }
        }
        if (preferences.isVaultLockEnabled) {
          Surface(
            shape = CircleShape,
            color = extra.blueSoft,
          ) {
            IconButton(
              onClick = onLockNow,
              modifier = Modifier.size(48.dp).testTag("home_lock_now_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Lock Vault Now",
                tint = IosBlue,
              )
            }
          }
        }
      }

      // Cleanup Ready Alert Banner (if items waiting)
      if (cleanupReadyCount > 0) {
        FrostedCard(
          containerColor = extra.roseSoft,
          borderColor = IosRose.copy(alpha = 0.4f),
          onClick = onOpenCleanupTab,
          modifier = Modifier.fillMaxWidth().testTag("home_cleanup_banner"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.CleaningServices,
              contentDescription = null,
              tint = IosRose,
              modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "$cleanupReadyCount Expired Screenshot(s) Ready",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "Tap here for 1-Tap Cleanup",
                style = MaterialTheme.typography.bodyMedium,
                color = IosRose,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }

      // Hero Card 1: Very Long Screenshot Floating Bubble
      FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = IosBlue.copy(alpha = 0.3f),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_privacy_hero),
            contentDescription = "Long screenshot illustration",
            contentScale = ContentScale.Crop,
            modifier =
              Modifier
                .size(width = 86.dp, height = 64.dp)
                .clip(RoundedCornerShape(16.dp)),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.hero_bubble_title),
              style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = stringResource(R.string.hero_bubble_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        SnapPillButton(
          text =
            if (isCapturingLive) "Bubble Active — Scroll Any App"
            else stringResource(R.string.btn_start_bubble),
          icon = Icons.Filled.PhotoCameraFront,
          containerColor = IosBlue,
          contentColor = Color.White,
          onClick = {
            if (hasOverlayPermission) {
              onStartFloatingBubble()
            } else {
              showBubbleGuideSheet = true
            }
          },
          modifier = Modifier.fillMaxWidth(),
          height = 60.dp,
          testTag = "home_start_bubble_button",
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
          onClick = onPickMultipleToStitch,
          shape = RoundedCornerShape(26.dp),
          modifier =
            Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("home_pick_stitch_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Collections,
            contentDescription = null,
            tint = IosBlue,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.btn_pick_stitch),
            style = MaterialTheme.typography.labelLarge,
            color = IosBlue,
          )
        }
      }

      // Hero Card 2: Mark-Style Screenshot Vault & Auto-Delete Timer
      FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = IosAmber.copy(alpha = 0.35f),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = extra.amberSoft,
            modifier = Modifier.size(48.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = IosAmber,
                modifier = Modifier.size(26.dp),
              )
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.hero_mark_title),
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = stringResource(R.string.hero_mark_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SnapPillButton(
          text = stringResource(R.string.btn_import_screenshot),
          icon = Icons.Filled.AddPhotoAlternate,
          containerColor = IosMint,
          contentColor = Color.White,
          onClick = onPickSingleToProtect,
          modifier = Modifier.fillMaxWidth(),
          height = 58.dp,
          testTag = "home_import_screenshot_button",
        )
      }

      // Friendly 3-Metric Summary Strip
      FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Your Phone Cleanup Impact",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetricTile(
            value = "${stats.totalShreddedCount}",
            label = "Cleaned",
            accent = IosMint,
            modifier = Modifier.weight(1f),
          )
          MetricTile(
            value = formatFileSize(stats.totalBytesReclaimed),
            label = "Space Freed",
            accent = IosBlue,
            modifier = Modifier.weight(1f),
          )
          MetricTile(
            value = "$activeTimerCount",
            label = "Active Timers",
            accent = IosAmber,
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }

  // Plain-English Guided 2-Step Floating Bubble Permission Sheet
  if (showBubbleGuideSheet) {
    ModalBottomSheet(
      onDismissRequest = { showBubbleGuideSheet = false },
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text(
          text = "2 Easy Steps for Long Screenshots",
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = "To float a small Snap button over other apps, Android needs one quick permission.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val step1Done = hasOverlayPermission
        GuidedStepRow(
          done = step1Done,
          title = "Step 1: Allow Floating Bubble",
          subtitle = "Turn on 'Allow display over other apps' for SnapKeep, then press Back.",
        )
        GuidedStepRow(
          done = false,
          title = "Step 2: Tap Start Now",
          subtitle = "Scroll any chat or webpage and tap + Snap or Auto 2s on the bubble.",
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (!step1Done) {
          SnapPillButton(
            text = "Open Floating Bubble Setting",
            containerColor = IosBlue,
            onClick = {
              val intent =
                Intent(
                  Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:${context.packageName}"),
                )
              context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            height = 58.dp,
            testTag = "guide_open_overlay_settings",
          )
        } else {
          SnapPillButton(
            text = "Launch Floating Bubble Now",
            containerColor = IosMint,
            onClick = {
              showBubbleGuideSheet = false
              onStartFloatingBubble()
            },
            modifier = Modifier.fillMaxWidth(),
            height = 58.dp,
            testTag = "guide_launch_bubble_now",
          )
        }
      }
    }
  }
}

@Composable
private fun MetricTile(
  value: String,
  label: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = accent,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun GuidedStepRow(
  done: Boolean,
  title: String,
  subtitle: String,
) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
      contentDescription = null,
      tint = if (done) IosMint else IosBlue,
      modifier = Modifier.size(26.dp),
    )
    Column {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
