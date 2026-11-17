package com.example.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.theme.IosBlue
import com.example.ui.theme.IosRose
import com.example.ui.theme.LocalSnapKeepColors

fun canUseBiometrics(activity: FragmentActivity): Boolean {
  val manager = BiometricManager.from(activity)
  val res =
    manager.canAuthenticate(
      BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK,
    )
  return res == BiometricManager.BIOMETRIC_SUCCESS
}

fun launchBiometricPrompt(
  activity: FragmentActivity,
  onSuccess: () -> Unit,
  onFallbackToPin: () -> Unit,
) {
  val executor = ContextCompat.getMainExecutor(activity)
  val prompt =
    BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          onFallbackToPin()
        }
      },
    )
  val info =
    BiometricPrompt.PromptInfo.Builder()
      .setTitle(activity.getString(R.string.biometric_title))
      .setSubtitle(activity.getString(R.string.biometric_subtitle))
      .setNegativeButtonText(activity.getString(R.string.biometric_cancel))
      .build()
  prompt.authenticate(info)
}

@Composable
fun VaultLockGate(
  preferBiometric: Boolean,
  onVerifyPin: (String) -> Boolean,
  onBiometricUnlocked: () -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  val haptics = LocalHapticFeedback.current
  val extra = LocalSnapKeepColors.current

  var enteredPin by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val biometricAvailable = remember(activity) { activity != null && canUseBiometrics(activity) }

  LaunchedEffect(preferBiometric, biometricAvailable) {
    if (preferBiometric && biometricAvailable && activity != null) {
      launchBiometricPrompt(
        activity = activity,
        onSuccess = onBiometricUnlocked,
        onFallbackToPin = {},
      )
    }
  }

  FrostedAtmosphereBackground {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .safeDrawingPadding()
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        shape = CircleShape,
        color = extra.blueSoft,
        modifier = Modifier.size(76.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Vault Locked",
            tint = IosBlue,
            modifier = Modifier.size(36.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Text(
        text = "SnapKeep Vault Locked",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Enter your 4-digit PIN to view your private screenshots",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(24.dp))

      // 4 PIN dots
      Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) { index ->
          val filled = index < enteredPin.length
          Box(
            modifier =
              Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (filled) IosBlue else Color.Transparent)
                .border(2.dp, if (filled) IosBlue else MaterialTheme.colorScheme.outline, CircleShape),
          )
        }
      }

      AnimatedVisibility(visible = errorText != null) {
        Text(
          text = errorText.orEmpty(),
          color = IosRose,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(top = 12.dp),
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      PinKeypad(
        showBiometricButton = biometricAvailable,
        onDigit = { digit ->
          if (enteredPin.length < 4) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            errorText = null
            val next = enteredPin + digit
            enteredPin = next
            if (next.length == 4) {
              if (!onVerifyPin(next)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                errorText = "Incorrect PIN. Please try again."
                enteredPin = ""
              }
            }
          }
        },
        onBackspace = {
          if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorText = null
          }
        },
        onBiometricClick = {
          if (activity != null) {
            launchBiometricPrompt(
              activity = activity,
              onSuccess = onBiometricUnlocked,
              onFallbackToPin = {},
            )
          }
        },
      )
    }
  }
}

@Composable
fun SetupPinDialog(
  onDismiss: () -> Unit,
  onPinConfirmed: (String) -> Unit,
) {
  var firstPin by remember { mutableStateOf("") }
  var confirmPin by remember { mutableStateOf("") }
  var isConfirmStep by remember { mutableStateOf(false) }
  var mismatchError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(28.dp),
    title = {
      Text(
        text = if (isConfirmStep) "Confirm Your 4-Digit PIN" else "Create a 4-Digit PIN",
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
          text =
            if (isConfirmStep) "Enter the same 4 numbers again so you won't forget."
            else "Pick an easy 4-digit PIN for your private screenshots.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        val activeLen = if (isConfirmStep) confirmPin.length else firstPin.length
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          repeat(4) { i ->
            val filled = i < activeLen
            Box(
              modifier =
                Modifier
                  .size(16.dp)
                  .clip(CircleShape)
                  .background(if (filled) IosBlue else Color.Transparent)
                  .border(2.dp, if (filled) IosBlue else MaterialTheme.colorScheme.outline, CircleShape),
            )
          }
        }
        if (mismatchError) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "PINs did not match. Try again.",
            color = IosRose,
            style = MaterialTheme.typography.labelMedium,
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PinKeypad(
          showBiometricButton = false,
          compact = true,
          onDigit = { d ->
            mismatchError = false
            if (!isConfirmStep) {
              if (firstPin.length < 4) {
                firstPin += d
                if (firstPin.length == 4) {
                  isConfirmStep = true
                }
              }
            } else if (confirmPin.length < 4) {
              confirmPin += d
              if (confirmPin.length == 4) {
                if (confirmPin == firstPin) {
                  onPinConfirmed(confirmPin)
                } else {
                  mismatchError = true
                  confirmPin = ""
                }
              }
            }
          },
          onBackspace = {
            if (isConfirmStep) {
              if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
              else isConfirmStep = false
            } else if (firstPin.isNotEmpty()) {
              firstPin = firstPin.dropLast(1)
            }
          },
          onBiometricClick = {},
        )
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("pin_setup_cancel")) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun PinKeypad(
  showBiometricButton: Boolean,
  compact: Boolean = false,
  onDigit: (String) -> Unit,
  onBackspace: () -> Unit,
  onBiometricClick: () -> Unit,
) {
  val keySize = if (compact) 58.dp else 72.dp
  val spacing = if (compact) 12.dp else 16.dp
  val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))

  Column(
    modifier = Modifier.widthIn(max = 300.dp),
    verticalArrangement = Arrangement.spacedBy(spacing),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    rows.forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        row.forEach { digit ->
          PinKeyButton(
            label = digit,
            size = keySize,
            onClick = { onDigit(digit) },
            testTag = "pin_key_$digit",
          )
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
      if (showBiometricButton) {
        Surface(
          shape = CircleShape,
          color = LocalSnapKeepColors.current.blueSoft,
          modifier =
            Modifier
              .size(keySize)
              .clip(CircleShape)
              .clickable(onClick = onBiometricClick)
              .testTag("pin_key_biometric"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Fingerprint,
              contentDescription = "Unlock with Fingerprint",
              tint = IosBlue,
              modifier = Modifier.size(30.dp),
            )
          }
        }
      } else {
        Spacer(modifier = Modifier.size(keySize))
      }

      PinKeyButton(
        label = "0",
        size = keySize,
        onClick = { onDigit("0") },
        testTag = "pin_key_0",
      )

      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
          Modifier
            .size(keySize)
            .clip(CircleShape)
            .clickable(onClick = onBackspace)
            .testTag("pin_key_backspace"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Delete digit",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun PinKeyButton(
  label: String,
  size: androidx.compose.ui.unit.Dp,
  onClick: () -> Unit,
  testTag: String,
) {
  val extra = LocalSnapKeepColors.current
  Surface(
    shape = CircleShape,
    color = extra.frostedCard,
    border = androidx.compose.foundation.BorderStroke(1.dp, extra.frostedBorder),
    shadowElevation = 1.dp,
    modifier =
      Modifier
        .size(size)
        .clip(CircleShape)
        .clickable(onClick = onClick)
        .testTag(testTag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
