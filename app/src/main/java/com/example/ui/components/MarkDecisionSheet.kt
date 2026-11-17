package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppPreferences
import com.example.ui.PendingMarkDraft
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosIndigo
import com.example.ui.theme.IosMint
import com.example.ui.theme.LocalSnapKeepColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkDecisionSheet(
  draft: PendingMarkDraft,
  preferences: AppPreferences,
  onDismiss: () -> Unit,
  onCommit: (title: String, timerMinutes: Int?, queueGalleryDelete: Boolean, shareNow: Boolean) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val extra = LocalSnapKeepColors.current

  var title by remember(draft) { mutableStateOf(draft.defaultTitle) }
  var queueGalleryDelete by remember(draft) { mutableStateOf(draft.sourceUri != null) }

  val presets =
    listOf(
      preferences.timerPreset1Minutes,
      preferences.timerPreset2Minutes,
      preferences.timerPreset3Minutes,
    )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Image(
          bitmap = draft.bitmap.asImageBitmap(),
          contentDescription = "Captured screenshot preview",
          contentScale = ContentScale.Crop,
          modifier =
            Modifier
              .size(width = 76.dp, height = 104.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "What should SnapKeep do?",
            style = MaterialTheme.typography.titleLarge,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Pick an auto-delete timer so it never clogs your phone, or keep it locked safely forever.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Friendly Label (optional)") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("mark_label_input"),
      )

      if (draft.sourceUri != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable { queueGalleryDelete = !queueGalleryDelete }
              .padding(vertical = 4.dp),
        ) {
          Checkbox(
            checked = queueGalleryDelete,
            onCheckedChange = { queueGalleryDelete = it },
            modifier = Modifier.testTag("mark_queue_gallery_checkbox"),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Add original gallery photo to 1-Tap Cleanup Queue",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "Auto-Delete After",
        style = MaterialTheme.typography.titleMedium,
      )
      Spacer(modifier = Modifier.height(10.dp))

      // 3 Mark-style large timer buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        presets.forEachIndexed { index, mins ->
          Surface(
            onClick = { onCommit(title, mins, queueGalleryDelete, false) },
            shape = RoundedCornerShape(20.dp),
            color = extra.amberSoft,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, IosAmber.copy(alpha = 0.45f)),
            modifier =
              Modifier
                .weight(1f)
                .height(74.dp)
                .testTag("mark_timer_preset_$index"),
          ) {
            Column(
              modifier = Modifier.padding(8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.Timer,
                contentDescription = null,
                tint = IosAmber,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = formatPresetReadable(mins),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SnapPillButton(
          text = "Keep Forever",
          icon = Icons.Filled.AllInclusive,
          containerColor = IosMint,
          contentColor = Color.White,
          onClick = { onCommit(title, null, queueGalleryDelete, false) },
          modifier = Modifier.weight(1f),
          testTag = "mark_keep_forever_button",
        )

        SnapPillButton(
          text = "Share & Shred",
          icon = Icons.Filled.Share,
          containerColor = IosIndigo,
          contentColor = Color.White,
          onClick = {
            onCommit(title, preferences.timerPreset1Minutes, queueGalleryDelete, true)
          },
          modifier = Modifier.weight(1f),
          testTag = "mark_share_delete_button",
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("mark_cancel_button"),
      ) {
        Text("Cancel", color = IosBlue)
      }
    }
  }
}
