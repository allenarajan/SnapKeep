package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.StitchUiState
import com.example.ui.components.FrostedCard
import com.example.ui.components.SnapPillButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosIndigo
import com.example.ui.theme.IosMint
import com.example.ui.theme.LocalSnapKeepColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchPreviewScreen(
  stitchState: StitchUiState,
  defaultShortTimerMinutes: Int,
  onAdjustSeamOverlap: (seamIndex: Int, newOverlapPx: Int) -> Unit,
  onToggleTrimSystemBars: () -> Unit,
  onOpenTimerPicker: () -> Unit,
  onSaveForever: () -> Unit,
  onShareAndTimer: () -> Unit,
  onDiscard: () -> Unit,
) {
  BackHandler(onBack = onDiscard)
  val extra = LocalSnapKeepColors.current
  var showSeamAdjuster by remember { mutableStateOf(false) }
  var selectedSeamIndex by remember { mutableIntStateOf(0) }

  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text("Long Screenshot Preview", style = MaterialTheme.typography.titleLarge) },
      navigationIcon = {
        IconButton(
          onClick = onDiscard,
          modifier = Modifier.size(48.dp).testTag("stitch_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Discard and go back",
          )
        }
      },
      actions = {
        if (stitchState is StitchUiState.Ready && stitchState.plan.overlapsPx.isNotEmpty()) {
          IconButton(
            onClick = { showSeamAdjuster = !showSeamAdjuster },
            modifier = Modifier.size(48.dp).testTag("stitch_tune_seams_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Tune,
              contentDescription = "Adjust Seam Overlap",
              tint = if (showSeamAdjuster) IosBlue else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )

    when (stitchState) {
      is StitchUiState.Idle -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("No stitched screenshot active.")
        }
      }

      is StitchUiState.Processing -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = IosBlue, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Aligning & stitching ${stitchState.frameCount} screens…",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Everything stays 100% on your phone",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      is StitchUiState.Error -> {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
          FrostedCard(modifier = Modifier.widthIn(max = 480.dp)) {
            Text(text = "Could Not Stitch", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = stitchState.message, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            SnapPillButton(
              text = "Go Back",
              onClick = onDiscard,
              modifier = Modifier.fillMaxWidth(),
              testTag = "stitch_error_back_button",
            )
          }
        }
      }

      is StitchUiState.Ready -> {
        val bmp = stitchState.stitchedBitmap
        val overlaps = stitchState.plan.overlapsPx
        val seamCount = overlaps.size

        Column(
          modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Row(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            StatusPill(
              text = "${stitchState.plan.preparedFrames.size} Pages Stitched",
              containerColor = extra.blueSoft,
              contentColor = IosBlue,
            )
            StatusPill(
              text = "${bmp.width} × ${bmp.height} px",
              containerColor = extra.mintSoft,
              contentColor = IosMint,
              monoText = true,
            )
          }

          if (showSeamAdjuster && seamCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            FrostedCard(
              modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Trim Status & Nav Bars", style = MaterialTheme.typography.titleMedium)
                Switch(
                  checked = stitchState.plan.trimSystemBars,
                  onCheckedChange = { onToggleTrimSystemBars() },
                  modifier = Modifier.testTag("stitch_trim_bars_switch"),
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              val safeSeamIdx = selectedSeamIndex.coerceIn(0, seamCount - 1)
              val nextFrameHeight = stitchState.plan.preparedFrames[safeSeamIdx + 1].height
              val currentOverlap = overlaps[safeSeamIdx]

              if (seamCount > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  (0 until seamCount).forEach { idx ->
                    val selected = idx == safeSeamIdx
                    SnapPillButton(
                      text = "Join ${idx + 1}",
                      containerColor = if (selected) IosBlue else MaterialTheme.colorScheme.surfaceVariant,
                      contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                      onClick = { selectedSeamIndex = idx },
                      height = 42.dp,
                      testTag = "stitch_seam_tab_$idx",
                    )
                  }
                }
                Spacer(modifier = Modifier.height(6.dp))
              }

              Text(
                text = "Seam ${safeSeamIdx + 1} Overlap: ${currentOverlap}px",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
              )
              Slider(
                value = currentOverlap.toFloat(),
                onValueChange = { onAdjustSeamOverlap(safeSeamIdx, it.toInt()) },
                valueRange = 0f..(nextFrameHeight * 0.65f).coerceAtLeast(100f),
                modifier = Modifier.testTag("stitch_seam_slider"),
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Smooth scrollable long image viewport
          Box(
            modifier =
              Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .verticalScroll(rememberScrollState()),
          ) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "Stitched long screenshot",
              contentScale = ContentScale.FillWidth,
              modifier = Modifier.fillMaxWidth(),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Bottom big action bar
          Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SnapPillButton(
                text = "Set Timer",
                icon = Icons.Filled.Timer,
                containerColor = IosAmber,
                contentColor = Color.White,
                onClick = onOpenTimerPicker,
                modifier = Modifier.weight(1f),
                testTag = "stitch_set_timer_button",
              )
              SnapPillButton(
                text = "Keep Forever",
                icon = Icons.Filled.AllInclusive,
                containerColor = IosMint,
                contentColor = Color.White,
                onClick = onSaveForever,
                modifier = Modifier.weight(1f),
                testTag = "stitch_keep_forever_button",
              )
            }
            SnapPillButton(
              text = "Share & Auto-Delete (${defaultShortTimerMinutes}m)",
              icon = Icons.Filled.Share,
              containerColor = IosIndigo,
              contentColor = Color.White,
              onClick = onShareAndTimer,
              modifier = Modifier.fillMaxWidth(),
              testTag = "stitch_share_delete_button",
            )
          }
        }
      }
    }
  }
}
