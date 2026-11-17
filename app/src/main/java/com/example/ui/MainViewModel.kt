package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SnapKeepApplication
import com.example.capture.CaptureSessionBus
import com.example.capture.CaptureSessionEvent
import com.example.data.local.CleanupStatsEntity
import com.example.data.local.ScreenshotEntity
import com.example.data.local.ScreenshotStatus
import com.example.data.local.SourceType
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.ThemePreference
import com.example.stitch.LongScreenshotStitcher
import com.example.stitch.StitchPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PendingMarkDraft(
  val bitmap: Bitmap,
  val sourceType: SourceType,
  val defaultTitle: String,
  val sourceUri: Uri? = null,
  val frameCount: Int = 1,
)

sealed interface StitchUiState {
  data object Idle : StitchUiState
  data class Processing(val frameCount: Int) : StitchUiState
  data class Ready(
    val plan: StitchPlan,
    val stitchedBitmap: Bitmap,
    val sourceType: SourceType,
  ) : StitchUiState
  data class Error(val message: String) : StitchUiState
}

sealed interface UiEffect {
  data class ShowMessage(val message: String) : UiEffect
  data class LaunchShareIntent(val uri: Uri, val chooserTitle: String) : UiEffect
  data class RequestMediaDelete(val uris: List<Uri>, val itemIds: List<String>) : UiEffect
  data object OpenStitchPreview : UiEffect
  data object CloseStitchPreview : UiEffect
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as SnapKeepApplication
  private val repository = app.vaultRepository
  private val prefsRepo = app.preferencesRepository
  private val crypto = app.cryptoManager

  private val thumbCache =
    object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
      override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

  val preferences: StateFlow<AppPreferences> =
    prefsRepo.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = AppPreferences(),
    )

  val allScreenshots: StateFlow<List<ScreenshotEntity>> =
    repository.allScreenshots.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val stats: StateFlow<CleanupStatsEntity> =
    repository.stats.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = CleanupStatsEntity(),
    )

  private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
  val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

  private val _isUnlocked = MutableStateFlow(false)
  val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

  private val _isPreferencesLoaded = MutableStateFlow(false)
  val isPreferencesLoaded: StateFlow<Boolean> = _isPreferencesLoaded.asStateFlow()

  private val _pendingMarkDraft = MutableStateFlow<PendingMarkDraft?>(null)
  val pendingMarkDraft: StateFlow<PendingMarkDraft?> = _pendingMarkDraft.asStateFlow()

  private val _stitchState = MutableStateFlow<StitchUiState>(StitchUiState.Idle)
  val stitchState: StateFlow<StitchUiState> = _stitchState.asStateFlow()

  val captureSessionEvent: StateFlow<CaptureSessionEvent> = CaptureSessionBus.event

  private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
  val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

  val cleanupQueueItems: StateFlow<List<ScreenshotEntity>> =
    combine(allScreenshots, nowMillis) { items, now ->
      items.filter { item ->
        item.status == ScreenshotStatus.CLEANUP_READY.name ||
          (item.status == ScreenshotStatus.ACTIVE.name &&
            item.expiresAtMillis != null &&
            item.expiresAtMillis <= now)
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  init {
    viewModelScope.launch {
      val initialPrefs = prefsRepo.preferences.first()
      _isUnlocked.value = !initialPrefs.isVaultLockEnabled
      _isPreferencesLoaded.value = true
    }

    // Live countdown ticker & automatic expiration check
    viewModelScope.launch {
      while (isActive) {
        val now = System.currentTimeMillis()
        _nowMillis.value = now
        val currentPrefs = preferences.value
        val hasExpired =
          allScreenshots.value.any {
            it.status == ScreenshotStatus.ACTIVE.name &&
              it.expiresAtMillis != null &&
              it.expiresAtMillis <= now
          }
        if (hasExpired) {
          repository.markExpiredReadyOrShred(currentPrefs.silentAutoShred)
        }
        delay(1000L)
      }
    }

    // Listen for live frames coming back from FloatingCaptureService
    viewModelScope.launch {
      CaptureSessionBus.event.collect { event ->
        if (event is CaptureSessionEvent.ReadyToStitch) {
          processFramesToStitch(event.frames, SourceType.SCROLL_STITCH)
          CaptureSessionBus.clearEvent()
        } else if (event is CaptureSessionEvent.Error) {
          _effects.tryEmit(UiEffect.ShowMessage(event.message))
          CaptureSessionBus.clearEvent()
        }
      }
    }
  }

  fun observeScreenshot(id: String) = repository.observeById(id)

  suspend fun loadThumbnail(entity: ScreenshotEntity): Bitmap? {
    thumbCache.get(entity.id)?.let { return it }
    val decoded = repository.decryptThumbnail(entity) ?: return null
    thumbCache.put(entity.id, decoded)
    return decoded
  }

  suspend fun loadFullImage(entity: ScreenshotEntity): Bitmap? = repository.decryptFullImage(entity)

  fun onAppBackgrounded() {
    if (preferences.value.isVaultLockEnabled) {
      _isUnlocked.value = false
    }
  }

  fun lockVaultNow() {
    if (preferences.value.isVaultLockEnabled) {
      _isUnlocked.value = false
    }
  }

  fun unlockWithBiometricSuccess() {
    _isUnlocked.value = true
  }

  fun tryUnlockWithPin(pin: String): Boolean {
    val prefs = preferences.value
    val ok = crypto.verifyPin(pin, prefs.pinSaltHex, prefs.pinHashHex)
    if (ok) {
      _isUnlocked.value = true
    }
    return ok
  }

  fun enablePinLock(pin: String, preferBiometric: Boolean) {
    viewModelScope.launch {
      val (hash, salt) = crypto.createSaltedPinHash(pin)
      prefsRepo.savePinCredentials(hash, salt)
      prefsRepo.setVaultLock(enabled = true, biometricPreferred = preferBiometric)
      _isUnlocked.value = true
      _effects.tryEmit(UiEffect.ShowMessage("Vault Lock enabled on this phone"))
    }
  }

  fun disableVaultLock() {
    viewModelScope.launch {
      prefsRepo.clearPinCredentials()
      _isUnlocked.value = true
      _effects.tryEmit(UiEffect.ShowMessage("Vault Lock turned off"))
    }
  }

  fun setBiometricPreferred(enabled: Boolean) {
    viewModelScope.launch {
      prefsRepo.setVaultLock(enabled = preferences.value.isVaultLockEnabled, biometricPreferred = enabled)
    }
  }

  fun importSingleUriForMark(uri: Uri, sourceType: SourceType = SourceType.IMPORTED) {
    viewModelScope.launch {
      val bmp = repository.decodeUriToBitmap(uri)
      if (bmp == null) {
        _effects.tryEmit(UiEffect.ShowMessage("Could not read that image."))
        return@launch
      }
      _pendingMarkDraft.value =
        PendingMarkDraft(
          bitmap = bmp,
          sourceType = sourceType,
          defaultTitle = if (sourceType == SourceType.SHARED) "Shared Screenshot" else "Protected Shot",
          sourceUri = uri,
          frameCount = 1,
        )
    }
  }

  fun handleMultipleUris(uris: List<Uri>, fromShare: Boolean = false) {
    if (uris.isEmpty()) return
    if (uris.size == 1) {
      importSingleUriForMark(
        uris.first(),
        if (fromShare) SourceType.SHARED else SourceType.IMPORTED,
      )
      return
    }
    viewModelScope.launch {
      _stitchState.value = StitchUiState.Processing(uris.size)
      _effects.tryEmit(UiEffect.OpenStitchPreview)
      val bitmaps = uris.mapNotNull { repository.decodeUriToBitmap(it) }
      if (bitmaps.isEmpty()) {
        _stitchState.value = StitchUiState.Error("Could not load selected screenshots.")
        return@launch
      }
      processFramesToStitch(bitmaps, SourceType.PICK_STITCH)
    }
  }

  private fun processFramesToStitch(frames: List<Bitmap>, sourceType: SourceType) {
    viewModelScope.launch {
      _stitchState.value = StitchUiState.Processing(frames.size)
      _effects.tryEmit(UiEffect.OpenStitchPreview)
      runCatching {
        val plan = LongScreenshotStitcher.createStitchPlan(frames, trimSystemBars = true)
        val rendered = LongScreenshotStitcher.renderBitmap(plan)
        StitchUiState.Ready(plan = plan, stitchedBitmap = rendered, sourceType = sourceType)
      }.onSuccess { ready ->
        _stitchState.value = ready
      }.onFailure { err ->
        _stitchState.value = StitchUiState.Error(err.message ?: "Failed to stitch frames")
      }
    }
  }

  fun adjustSeamOverlap(seamIndex: Int, newOverlapPx: Int) {
    val current = _stitchState.value as? StitchUiState.Ready ?: return
    if (seamIndex !in current.plan.overlapsPx.indices) return
    val updatedOverlaps =
      current.plan.overlapsPx.toMutableList().apply { this[seamIndex] = newOverlapPx }
    val updatedPlan = current.plan.copy(overlapsPx = updatedOverlaps)
    viewModelScope.launch {
      val rendered = LongScreenshotStitcher.renderBitmap(updatedPlan)
      _stitchState.value = current.copy(plan = updatedPlan, stitchedBitmap = rendered)
    }
  }

  fun toggleTrimSystemBars() {
    val current = _stitchState.value as? StitchUiState.Ready ?: return
    val nextTrim = !current.plan.trimSystemBars
    viewModelScope.launch {
      val newPlan = LongScreenshotStitcher.createStitchPlan(current.plan.rawFrames, trimSystemBars = nextTrim)
      val rendered = LongScreenshotStitcher.renderBitmap(newPlan)
      _stitchState.value = current.copy(plan = newPlan, stitchedBitmap = rendered)
    }
  }

  fun sendStitchToMarkDecision() {
    val ready = _stitchState.value as? StitchUiState.Ready ?: return
    _pendingMarkDraft.value =
      PendingMarkDraft(
        bitmap = ready.stitchedBitmap,
        sourceType = ready.sourceType,
        defaultTitle = "Long Scroll • ${ready.plan.preparedFrames.size} pages",
        sourceUri = null,
        frameCount = ready.plan.preparedFrames.size,
      )
  }

  fun saveStitchDirectly(timerMinutes: Int?, shareAfter: Boolean = false) {
    val ready = _stitchState.value as? StitchUiState.Ready ?: return
    viewModelScope.launch {
      val entity =
        repository.saveBitmapToVault(
          bitmap = ready.stitchedBitmap,
          title = "Long Scroll • ${ready.plan.preparedFrames.size} pages",
          sourceType = ready.sourceType,
          timerMinutes = timerMinutes,
          frameCount = ready.plan.preparedFrames.size,
        )
      _stitchState.value = StitchUiState.Idle
      _effects.tryEmit(UiEffect.CloseStitchPreview)
      if (shareAfter) {
        val shareUri = repository.createShareUri(entity)
        if (shareUri != null) {
          _effects.tryEmit(UiEffect.LaunchShareIntent(shareUri, "Share Long Screenshot"))
        }
      } else {
        val msg =
          if (timerMinutes == null) "Saved Long Screenshot forever in Private Vault"
          else "Saved Long Screenshot • Auto-deletes in $timerMinutes min"
        _effects.tryEmit(UiEffect.ShowMessage(msg))
      }
    }
  }

  fun discardStitchPreview() {
    _stitchState.value = StitchUiState.Idle
    _effects.tryEmit(UiEffect.CloseStitchPreview)
  }

  fun commitMarkDraft(
    title: String,
    timerMinutes: Int?,
    queueOriginalGalleryUri: Boolean,
    shareNow: Boolean,
  ) {
    val draft = _pendingMarkDraft.value ?: return
    _pendingMarkDraft.value = null
    viewModelScope.launch {
      val galleryUriStr =
        if (queueOriginalGalleryUri && draft.sourceUri != null) draft.sourceUri.toString() else null
      val entity =
        repository.saveBitmapToVault(
          bitmap = draft.bitmap,
          title = title.ifBlank { draft.defaultTitle },
          sourceType = draft.sourceType,
          timerMinutes = timerMinutes,
          frameCount = draft.frameCount,
          pendingGalleryDeleteUri = galleryUriStr,
        )
      if (_stitchState.value is StitchUiState.Ready) {
        _stitchState.value = StitchUiState.Idle
        _effects.tryEmit(UiEffect.CloseStitchPreview)
      }

      if (shareNow) {
        val uri = repository.createShareUri(entity)
        if (uri != null) {
          _effects.tryEmit(UiEffect.LaunchShareIntent(uri, "Share Screenshot"))
        }
      } else {
        val label =
          if (timerMinutes == null) "Kept forever in Private Vault"
          else "Protected! Timer set for $timerMinutes min"
        _effects.tryEmit(UiEffect.ShowMessage(label))
      }
    }
  }

  fun dismissMarkDraft() {
    _pendingMarkDraft.value = null
  }

  fun updateScreenshotTimer(id: String, timerMinutes: Int?) {
    viewModelScope.launch {
      repository.updateTimer(id, timerMinutes)
      val msg =
        if (timerMinutes == null) "Kept forever in Private Vault"
        else "Timer updated to $timerMinutes min"
      _effects.tryEmit(UiEffect.ShowMessage(msg))
    }
  }

  fun shredScreenshotNow(item: ScreenshotEntity) {
    viewModelScope.launch {
      thumbCache.remove(item.id)
      val galleryUri = item.pendingGalleryDeleteUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
      repository.shredItems(listOf(item))
      if (galleryUri != null) {
        _effects.tryEmit(UiEffect.RequestMediaDelete(listOf(galleryUri), emptyList()))
      } else {
        _effects.tryEmit(UiEffect.ShowMessage("Screenshot shredded permanently"))
      }
    }
  }

  fun shareScreenshot(item: ScreenshotEntity, setShortDeleteMinutes: Int? = null) {
    viewModelScope.launch {
      if (setShortDeleteMinutes != null) {
        repository.updateTimer(item.id, setShortDeleteMinutes)
      }
      val uri = repository.createShareUri(item)
      if (uri != null) {
        _effects.tryEmit(UiEffect.LaunchShareIntent(uri, "Share Screenshot"))
      } else {
        _effects.tryEmit(UiEffect.ShowMessage("Could not prepare screenshot for sharing"))
      }
    }
  }

  fun exportCopyToGallery(item: ScreenshotEntity) {
    viewModelScope.launch {
      val ok = repository.exportCopyToGallery(item)
      _effects.tryEmit(
        UiEffect.ShowMessage(
          if (ok) "Saved a copy to Pictures/SnapKeep" else "Could not export image",
        ),
      )
    }
  }

  fun cleanAllExpired() {
    viewModelScope.launch {
      val items = cleanupQueueItems.value
      if (items.isEmpty()) return@launch
      items.forEach { thumbCache.remove(it.id) }
      val galleryUris =
        items.mapNotNull { it.pendingGalleryDeleteUri?.let { u -> runCatching { Uri.parse(u) }.getOrNull() } }
      repository.shredItems(items)
      if (galleryUris.isNotEmpty()) {
        _effects.tryEmit(UiEffect.RequestMediaDelete(galleryUris, emptyList()))
      } else {
        _effects.tryEmit(UiEffect.ShowMessage("Cleaned ${items.size} expired screenshot(s)"))
      }
    }
  }

  fun onGalleryDeleteCompleted() {
    _effects.tryEmit(UiEffect.ShowMessage("Original gallery copies cleaned up!"))
  }

  fun updateTimerPresets(p1: Int, p2: Int, p3: Int) {
    viewModelScope.launch {
      prefsRepo.updateTimerPresets(p1, p2, p3)
      _effects.tryEmit(UiEffect.ShowMessage("Timer presets updated"))
    }
  }

  fun setScreenShieldEnabled(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setScreenShieldEnabled(enabled) }
  }

  fun setTimerAlertsEnabled(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setTimerAlertsEnabled(enabled) }
  }

  fun setSilentAutoShred(enabled: Boolean) {
    viewModelScope.launch { prefsRepo.setSilentAutoShred(enabled) }
  }

  fun setThemePreference(pref: ThemePreference) {
    viewModelScope.launch { prefsRepo.setThemePreference(pref) }
  }

  fun completeOnboarding() {
    viewModelScope.launch { prefsRepo.setOnboardingCompleted(true) }
  }
}
