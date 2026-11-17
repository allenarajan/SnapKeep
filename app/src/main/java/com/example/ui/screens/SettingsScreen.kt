package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ThemePreference
import com.example.ui.components.FrostedCard
import com.example.ui.components.SnapPillButton
import com.example.ui.components.formatPresetReadable
import com.example.ui.theme.IosAmber
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosIndigo
import com.example.ui.theme.IosMint

private val TIMER_CHOICES_MINUTES = listOf(5, 15, 30, 60, 180, 720, 1440, 4320, 10080)

@Composable
fun SettingsScreen(
  preferences: AppPreferences,
  onRequestEnablePin: () -> Unit,
  onDisableLock: () -> Unit,
  onSetBiometricPreferred: (Boolean) -> Unit,
  onSetScreenShield: (Boolean) -> Unit,
  onSetTimerAlerts: (Boolean) -> Unit,
  onSetSilentAutoShred: (Boolean) -> Unit,
  onUpdateTimerPresets: (Int, Int, Int) -> Unit,
  onSetTheme: (ThemePreference) -> Unit,
) {
  val context = LocalContext.current
  var editingPresetSlot by remember { mutableIntStateOf(-1) }
  var showDisableLockDialog by remember { mutableStateOf(false) }

  val notifPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { granted ->
        onSetTimerAlerts(granted)
      },
    )

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Settings & Security",
        style = MaterialTheme.typography.headlineLarge,
      )

      // 1. Security & Vault Lock
      FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Privacy & Vault Lock", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        SettingSwitchRow(
          icon = Icons.Filled.Lock,
          tint = IosBlue,
          title = "PIN & Fingerprint Lock",
          subtitle = "Require your 4-digit PIN or fingerprint to open SnapKeep.",
          checked = preferences.isVaultLockEnabled,
          onCheckedChange = { wantOn ->
            if (wantOn) onRequestEnablePin() else showDisableLockDialog = true
          },
          testTag = "settings_vault_lock_switch",
        )

        if (preferences.isVaultLockEnabled) {
          Spacer(modifier = Modifier.height(10.dp))
          SettingSwitchRow(
            icon = Icons.Filled.Fingerprint,
            tint = IosIndigo,
            title = "Unlock with Fingerprint / Face",
            subtitle = "Ask for biometrics first, with PIN as backup.",
            checked = preferences.isBiometricPreferred,
            onCheckedChange = onSetBiometricPreferred,
            testTag = "settings_biometric_switch",
          )
        }

        Spacer(modifier = Modifier.height(10.dp))
        SettingSwitchRow(
          icon = Icons.Filled.Security,
          tint = IosMint,
          title = "Screen Capture Shield (Anti-Record)",
          subtitle = "Blacks out SnapKeep in screen recorders and remote casting. Keep off for screen mirroring.",
          checked = preferences.isScreenShieldEnabled,
          onCheckedChange = onSetScreenShield,
          testTag = "settings_screen_shield_switch",
        )
      }

      // 2. Mark-Style Timer Presets
      FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(imageVector = Icons.Filled.Timer, contentDescription = null, tint = IosAmber)
          Text(text = "Quick Auto-Delete Buttons", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Tap any preset to change the 3 quick buttons on your decision sheet.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        val presets =
          listOf(
            preferences.timerPreset1Minutes,
            preferences.timerPreset2Minutes,
            preferences.timerPreset3Minutes,
          )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          presets.forEachIndexed { idx, mins ->
            SnapPillButton(
              text = formatPresetReadable(mins),
              containerColor = IosAmber,
              onClick = { editingPresetSlot = idx },
              modifier = Modifier.weight(1f),
              height = 50.dp,
              testTag = "settings_preset_slot_$idx",
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SettingSwitchRow(
          icon = Icons.Filled.Shield,
          tint = IosAmber,
          title = "Silent Auto-Shred",
          subtitle =
            "Automatically shred expired vault screenshots. Turn off to review everything in 1-Tap Cleanup first.",
          checked = preferences.silentAutoShred,
          onCheckedChange = onSetSilentAutoShred,
          testTag = "settings_silent_shred_switch",
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingSwitchRow(
          icon = Icons.Filled.Notifications,
          tint = IosBlue,
          title = "Auto-Delete Timer Alerts",
          subtitle = "Notify you when temporary screenshots expire or finish cleaning.",
          checked = preferences.areTimerAlertsEnabled,
          onCheckedChange = { enable ->
            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                  PackageManager.PERMISSION_GRANTED
              if (granted) {
                onSetTimerAlerts(true)
              } else {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            } else {
              onSetTimerAlerts(enable)
            }
          },
          testTag = "settings_timer_alerts_switch",
        )
      }

      // 3. Appearance
      FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Appearance", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          ThemePreference.entries.forEach { option ->
            val label =
              when (option) {
                ThemePreference.SYSTEM -> "Auto"
                ThemePreference.LIGHT -> "Light"
                ThemePreference.DARK -> "Dark"
              }
            FilterChip(
              selected = preferences.themePreference == option,
              onClick = { onSetTheme(option) },
              label = { Text(label, style = MaterialTheme.typography.labelLarge) },
              shape = RoundedCornerShape(18.dp),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = IosBlue,
                  selectedLabelColor = Color.White,
                ),
              modifier = Modifier.weight(1f).height(48.dp).testTag("settings_theme_${option.name.lowercase()}"),
            )
          }
        }
      }

      // 4. Privacy Guarantee Card
      FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(imageVector = Icons.Filled.Shield, contentDescription = null, tint = IosMint)
          Text(text = "100% Free & Local Guarantee", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "SnapKeep has no login, no internet permission, no ads, and no cloud servers. Your screenshots are encrypted with Android Keystore AES-256 right inside this phone and never leave it unless you tap Share.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(14.dp))
    }
  }

  if (editingPresetSlot in 0..2) {
    AlertDialog(
      onDismissRequest = { editingPresetSlot = -1 },
      shape = RoundedCornerShape(26.dp),
      title = {
        Text("Choose Time for Button ${editingPresetSlot + 1}", style = MaterialTheme.typography.titleLarge)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          TIMER_CHOICES_MINUTES.chunked(3).forEach { rowChoices ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              rowChoices.forEach { mins ->
                SnapPillButton(
                  text = formatPresetReadable(mins),
                  containerColor = IosBlue,
                  height = 46.dp,
                  onClick = {
                    val p1 = if (editingPresetSlot == 0) mins else preferences.timerPreset1Minutes
                    val p2 = if (editingPresetSlot == 1) mins else preferences.timerPreset2Minutes
                    val p3 = if (editingPresetSlot == 2) mins else preferences.timerPreset3Minutes
                    onUpdateTimerPresets(p1, p2, p3)
                    editingPresetSlot = -1
                  },
                  modifier = Modifier.weight(1f),
                  testTag = "preset_choice_$mins",
                )
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { editingPresetSlot = -1 }) { Text("Cancel") }
      },
    )
  }

  if (showDisableLockDialog) {
    AlertDialog(
      onDismissRequest = { showDisableLockDialog = false },
      shape = RoundedCornerShape(26.dp),
      title = { Text("Turn Off Vault Lock?", style = MaterialTheme.typography.titleLarge) },
      text = {
        Text(
          "Anyone holding your unlocked phone will be able to open SnapKeep without a PIN or fingerprint.",
          style = MaterialTheme.typography.bodyLarge,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDisableLockDialog = false
            onDisableLock()
          },
          modifier = Modifier.testTag("settings_confirm_disable_lock"),
        ) {
          Text("Turn Off")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDisableLockDialog = false }) { Text("Keep On") }
      },
    )
  }
}

@Composable
private fun SettingSwitchRow(
  icon: ImageVector,
  tint: Color,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(26.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
    )
  }
}
