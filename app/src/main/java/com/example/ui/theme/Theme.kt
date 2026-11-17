package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.preferences.ThemePreference

@Immutable
data class SnapKeepExtraColors(
  val isDark: Boolean,
  val frostedCard: Color,
  val frostedBorder: Color,
  val subtleCanvasTop: Color,
  val subtleCanvasBottom: Color,
  val blueSoft: Color,
  val mintSoft: Color,
  val amberSoft: Color,
  val roseSoft: Color,
  val indigoSoft: Color,
)

val LocalSnapKeepColors =
  staticCompositionLocalOf {
    SnapKeepExtraColors(
      isDark = false,
      frostedCard = LightSurface,
      frostedBorder = LightBorder,
      subtleCanvasTop = Color(0xFFEAF2FF),
      subtleCanvasBottom = LightCanvas,
      blueSoft = IosBlueSoft,
      mintSoft = IosMintSoft,
      amberSoft = IosAmberSoft,
      roseSoft = IosRoseSoft,
      indigoSoft = IosIndigoSoft,
    )
  }

private val LightColors =
  lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = IosBlueSoft,
    onPrimaryContainer = Color(0xFF003B75),
    secondary = IosMint,
    onSecondary = Color.White,
    secondaryContainer = IosMintSoft,
    onSecondaryContainer = Color(0xFF0C4A1D),
    tertiary = IosIndigo,
    onTertiary = Color.White,
    tertiaryContainer = IosIndigoSoft,
    onTertiaryContainer = Color(0xFF211F6B),
    error = IosRose,
    onError = Color.White,
    errorContainer = IosRoseSoft,
    onErrorContainer = Color(0xFF7A1510),
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurfaceElevated,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFE9EFF7),
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
  )

private val DarkColors =
  darkColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = IosBlueDarkSoft,
    onPrimaryContainer = Color(0xFFBBDFFF),
    secondary = IosMint,
    onSecondary = Color(0xFF04260E),
    secondaryContainer = IosMintDarkSoft,
    onSecondaryContainer = Color(0xFFB9F6CA),
    tertiary = IosIndigo,
    onTertiary = Color.White,
    tertiaryContainer = IosIndigoDarkSoft,
    onTertiaryContainer = Color(0xFFD5D4FF),
    error = IosRose,
    onError = Color.White,
    errorContainer = IosRoseDarkSoft,
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurfaceElevated,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF1F2B44),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
  )

private val SnapKeepShapes =
  Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun SnapKeepTheme(
  themePreference: ThemePreference = ThemePreference.SYSTEM,
  content: @Composable () -> Unit,
) {
  val isDark =
    when (themePreference) {
      ThemePreference.SYSTEM -> isSystemInDarkTheme()
      ThemePreference.LIGHT -> false
      ThemePreference.DARK -> true
    }

  val extra =
    if (isDark) {
      SnapKeepExtraColors(
        isDark = true,
        frostedCard = DarkSurface,
        frostedBorder = DarkBorder,
        subtleCanvasTop = Color(0xFF111C33),
        subtleCanvasBottom = DarkCanvas,
        blueSoft = IosBlueDarkSoft,
        mintSoft = IosMintDarkSoft,
        amberSoft = IosAmberDarkSoft,
        roseSoft = IosRoseDarkSoft,
        indigoSoft = IosIndigoDarkSoft,
      )
    } else {
      SnapKeepExtraColors(
        isDark = false,
        frostedCard = LightSurface,
        frostedBorder = LightBorder,
        subtleCanvasTop = Color(0xFFE7F1FF),
        subtleCanvasBottom = LightCanvas,
        blueSoft = IosBlueSoft,
        mintSoft = IosMintSoft,
        amberSoft = IosAmberSoft,
        roseSoft = IosRoseSoft,
        indigoSoft = IosIndigoSoft,
      )
    }

  CompositionLocalProvider(LocalSnapKeepColors provides extra) {
    MaterialTheme(
      colorScheme = if (isDark) DarkColors else LightColors,
      typography = SnapKeepTypography,
      shapes = SnapKeepShapes,
      content = content,
    )
  }
}
