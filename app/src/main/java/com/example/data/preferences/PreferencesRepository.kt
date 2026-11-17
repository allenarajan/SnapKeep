package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.snapKeepDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "snapkeep_settings")

enum class ThemePreference {
  SYSTEM,
  LIGHT,
  DARK,
}

data class AppPreferences(
  val timerPreset1Minutes: Int = 15,
  val timerPreset2Minutes: Int = 60,
  val timerPreset3Minutes: Int = 1440,
  val isVaultLockEnabled: Boolean = false,
  val isBiometricPreferred: Boolean = true,
  val pinHashHex: String? = null,
  val pinSaltHex: String? = null,
  val isScreenShieldEnabled: Boolean = false,
  val areTimerAlertsEnabled: Boolean = true,
  val silentAutoShred: Boolean = true,
  val hasCompletedOnboarding: Boolean = false,
  val themePreference: ThemePreference = ThemePreference.SYSTEM,
)

class PreferencesRepository(private val context: Context) {

  private object Keys {
    val TIMER_1 = intPreferencesKey("timer_preset_1_minutes")
    val TIMER_2 = intPreferencesKey("timer_preset_2_minutes")
    val TIMER_3 = intPreferencesKey("timer_preset_3_minutes")
    val VAULT_LOCK = booleanPreferencesKey("is_vault_lock_enabled")
    val BIOMETRIC_PREF = booleanPreferencesKey("is_biometric_preferred")
    val PIN_HASH = stringPreferencesKey("pin_hash_hex")
    val PIN_SALT = stringPreferencesKey("pin_salt_hex")
    val SCREEN_SHIELD = booleanPreferencesKey("is_screen_shield_enabled")
    val TIMER_ALERTS = booleanPreferencesKey("are_timer_alerts_enabled")
    val SILENT_SHRED = booleanPreferencesKey("silent_auto_shred")
    val ONBOARDING_DONE = booleanPreferencesKey("has_completed_onboarding")
    val THEME_PREF = stringPreferencesKey("theme_preference")
  }

  val preferences: Flow<AppPreferences> =
    context.snapKeepDataStore.data.map { prefs ->
      AppPreferences(
        timerPreset1Minutes = prefs[Keys.TIMER_1] ?: 15,
        timerPreset2Minutes = prefs[Keys.TIMER_2] ?: 60,
        timerPreset3Minutes = prefs[Keys.TIMER_3] ?: 1440,
        isVaultLockEnabled = prefs[Keys.VAULT_LOCK] ?: false,
        isBiometricPreferred = prefs[Keys.BIOMETRIC_PREF] ?: true,
        pinHashHex = prefs[Keys.PIN_HASH],
        pinSaltHex = prefs[Keys.PIN_SALT],
        isScreenShieldEnabled = prefs[Keys.SCREEN_SHIELD] ?: false,
        areTimerAlertsEnabled = prefs[Keys.TIMER_ALERTS] ?: true,
        silentAutoShred = prefs[Keys.SILENT_SHRED] ?: true,
        hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false,
        themePreference =
          runCatching {
            ThemePreference.valueOf(prefs[Keys.THEME_PREF] ?: ThemePreference.SYSTEM.name)
          }.getOrDefault(ThemePreference.SYSTEM),
      )
    }

  suspend fun updateTimerPresets(p1: Int, p2: Int, p3: Int) {
    context.snapKeepDataStore.edit { prefs ->
      prefs[Keys.TIMER_1] = p1
      prefs[Keys.TIMER_2] = p2
      prefs[Keys.TIMER_3] = p3
    }
  }

  suspend fun setVaultLock(enabled: Boolean, biometricPreferred: Boolean? = null) {
    context.snapKeepDataStore.edit { prefs ->
      prefs[Keys.VAULT_LOCK] = enabled
      if (biometricPreferred != null) {
        prefs[Keys.BIOMETRIC_PREF] = biometricPreferred
      }
    }
  }

  suspend fun savePinCredentials(hashHex: String, saltHex: String) {
    context.snapKeepDataStore.edit { prefs ->
      prefs[Keys.PIN_HASH] = hashHex
      prefs[Keys.PIN_SALT] = saltHex
      prefs[Keys.VAULT_LOCK] = true
    }
  }

  suspend fun clearPinCredentials() {
    context.snapKeepDataStore.edit { prefs ->
      prefs.remove(Keys.PIN_HASH)
      prefs.remove(Keys.PIN_SALT)
      prefs[Keys.VAULT_LOCK] = false
    }
  }

  suspend fun setScreenShieldEnabled(enabled: Boolean) {
    context.snapKeepDataStore.edit { it[Keys.SCREEN_SHIELD] = enabled }
  }

  suspend fun setTimerAlertsEnabled(enabled: Boolean) {
    context.snapKeepDataStore.edit { it[Keys.TIMER_ALERTS] = enabled }
  }

  suspend fun setSilentAutoShred(enabled: Boolean) {
    context.snapKeepDataStore.edit { it[Keys.SILENT_SHRED] = enabled }
  }

  suspend fun setOnboardingCompleted(done: Boolean) {
    context.snapKeepDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
  }

  suspend fun setThemePreference(theme: ThemePreference) {
    context.snapKeepDataStore.edit { it[Keys.THEME_PREF] = theme.name }
  }
}
