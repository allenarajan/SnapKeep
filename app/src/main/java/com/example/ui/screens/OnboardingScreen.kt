package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.FrostedAtmosphereBackground
import com.example.ui.components.FrostedCard
import com.example.ui.components.SnapPillButton
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosMint

@Composable
fun OnboardingScreen(
  onSetupPinLock: () -> Unit,
  onSkipToHome: () -> Unit,
) {
  FrostedAtmosphereBackground {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_privacy_hero),
          contentDescription = "SnapKeep privacy shield and long scroll illustration",
          contentScale = ContentScale.Crop,
          modifier =
            Modifier
              .fillMaxWidth()
              .height(200.dp)
              .clip(RoundedCornerShape(28.dp)),
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
          text = "Welcome to SnapKeep",
          style = MaterialTheme.typography.headlineLarge,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Simple, free, and 100% private on your phone. No accounts, no cloud, ever.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        FrostedCard(modifier = Modifier.fillMaxWidth()) {
          OnboardingFeatureRow(
            icon = Icons.Filled.ViewAgenda,
            tint = IosBlue,
            title = "Very Long Screenshots",
            subtitle = "Use the easy floating bubble while scrolling chats or recipes.",
          )
          Spacer(modifier = Modifier.height(14.dp))
          OnboardingFeatureRow(
            icon = Icons.Filled.Timer,
            tint = IosAmber,
            title = "Auto-Delete Timers",
            subtitle = "Temporary receipts and codes erase themselves when time is up.",
          )
          Spacer(modifier = Modifier.height(14.dp))
          OnboardingFeatureRow(
            icon = Icons.Filled.Shield,
            tint = IosMint,
            title = "Encrypted Local Vault",
            subtitle = "No other app or person can read or steal your protected shots.",
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SnapPillButton(
          text = "Turn On PIN / Fingerprint Lock",
          icon = Icons.Filled.Lock,
          containerColor = IosBlue,
          contentColor = Color.White,
          onClick = onSetupPinLock,
          modifier = Modifier.fillMaxWidth(),
          height = 60.dp,
          testTag = "onboarding_enable_lock_button",
        )

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
          onClick = onSkipToHome,
          modifier = Modifier.height(52.dp).testTag("onboarding_skip_button"),
        ) {
          Text(
            text = "Continue Without Lock (Can add later)",
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun OnboardingFeatureRow(
  icon: ImageVector,
  tint: Color,
  title: String,
  subtitle: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(30.dp),
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
