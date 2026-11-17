package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferencesRepository
import com.example.data.repository.VaultRepository
import com.example.security.VaultCryptoManager
import com.example.worker.AutoShredWorker
import com.example.worker.NotificationHelper

class SnapKeepApplication : Application() {

  lateinit var database: AppDatabase
    private set

  lateinit var cryptoManager: VaultCryptoManager
    private set

  lateinit var preferencesRepository: PreferencesRepository
    private set

  lateinit var vaultRepository: VaultRepository
    private set

  override fun onCreate() {
    super.onCreate()
    database = AppDatabase.getInstance(this)
    cryptoManager = VaultCryptoManager(this)
    preferencesRepository = PreferencesRepository(this)
    vaultRepository = VaultRepository(this, database.screenshotDao(), cryptoManager)
    NotificationHelper.ensureChannels(this)

    runCatching {
      if (!WorkManager.isInitialized()) {
        WorkManager.initialize(this, Configuration.Builder().build())
      }
      AutoShredWorker.schedulePeriodic(this)
    }
  }
}
