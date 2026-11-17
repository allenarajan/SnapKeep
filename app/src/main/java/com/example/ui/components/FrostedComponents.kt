package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalSnapKeepColors
import java.util.Locale

@Composable
fun FrostedAtmosphereBackground(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val extra = LocalSnapKeepColors.current
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(extra.subtleCanvasTop, extra.subtleCanvasBottom),
          ),
        ),
  ) {
    content()
  }
}

@Composable
fun FrostedCard(
  modifier: Modifier = Modifier,
  containerColor: Color = LocalSnapKeepColors.current.frostedCard,
  borderColor: Color = LocalSnapKeepColors.current.frostedBorder,
  cornerRadius: Dp = 26.dp,
  onClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(20.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = RoundedCornerShape(cornerRadius)
  Surface(
    modifier =
      modifier
        .clip(shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    shape = shape,
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    tonalElevation = 2.dp,
    shadowElevation = 2.dp,
  ) {
    Column(modifier = Modifier.padding(contentPadding), content = content)
  }
}

@Composable
fun SnapPillButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String,
  icon: ImageVector? = null,
  containerColor: Color = MaterialTheme.colorScheme.primary,
  contentColor: Color = Color.White,
  enabled: Boolean = true,
  height: Dp = 56.dp,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(28.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = containerColor.copy(alpha = 0.4f),
        disabledContentColor = contentColor.copy(alpha = 0.7f),
      ),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    modifier = modifier.height(height).testTag(testTag),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
    }
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  monoText: Boolean = false,
) {
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = containerColor,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(15.dp),
        )
      }
      Text(
        text = text,
        color = contentColor,
        style =
          if (monoText) {
            MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily)
          } else {
            MaterialTheme.typography.labelMedium
          },
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

fun formatPresetDuration(minutes: Int): String {
  return when {
    minutes < 60 -> "${minutes}m"
    minutes % 1440 == 0 -> "${minutes / 1440}d"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes}m"
  }
}

fun formatPresetReadable(minutes: Int): String {
  return when {
    minutes < 60 -> "$minutes Min"
    minutes == 60 -> "1 Hour"
    minutes < 1440 && minutes % 60 == 0 -> "${minutes / 60} Hours"
    minutes == 1440 -> "24 Hours"
    minutes % 1440 == 0 -> "${minutes / 1440} Days"
    else -> "$minutes Min"
  }
}

fun formatRemainingTime(expiresAtMillis: Long?, nowMillis: Long): String {
  if (expiresAtMillis == null) return "Kept Forever"
  val diff = expiresAtMillis - nowMillis
  if (diff <= 0L) return "Expired"
  val totalSeconds = diff / 1000L
  val days = totalSeconds / 86400L
  val hours = (totalSeconds % 86400L) / 3600L
  val mins = (totalSeconds % 3600L) / 60L
  val secs = totalSeconds % 60L
  return when {
    days > 0 -> "${days}d ${hours}h"
    hours > 0 -> "${hours}h ${mins}m"
    mins > 0 -> "${mins}m ${secs}s"
    else -> "${secs}s"
  }
}

fun formatFileSize(bytes: Long): String {
  return when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
  }
}
