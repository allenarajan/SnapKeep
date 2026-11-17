package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.capture.CaptureSessionBus
import com.example.capture.CaptureSessionEvent
import com.example.capture.FloatingCaptureService
import com.example.capture.PendingMediaProjectionToken
import com.example.data.local.ScreenshotStatus
import com.example.ui.components.FrostedAtmosphereBackground
import com.example.ui.components.MarkDecisionSheet
import com.example.ui.components.SetupPinDialog
import com.example.ui.components.VaultLockGate
import com.example.ui.screens.CleanupScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StitchPreviewScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.LocalSnapKeepColors
import com.example.ui.theme.SnapKeepTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object VaultRoute
@Serializable data object CleanupRoute
@Serializable data object SettingsRoute
@Serializable data object StitchPreviewRoute
@Serializable data class DetailRoute(val itemId: String)

private data class TopTabItem(
  val route: Any,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

@Composable
fun SnapKeepApp(
  viewModel: MainViewModel,
  openCleanupSignal: Int,
  onSuppressNextBackgroundLock: () -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? Activity
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val preferences by viewModel.preferences.collectAsStateWithLifecycle()
  val screenshots by viewModel.allScreenshots.collectAsStateWithLifecycle()
  val cleanupQueue by viewModel.cleanupQueueItems.collectAsStateWithLifecycle()
  val stats by viewModel.stats.collectAsStateWithLifecycle()
  val nowMillis by viewModel.nowMillis.collectAsStateWithLifecycle()
  val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
  val isPreferencesLoaded by viewModel.isPreferencesLoaded.collectAsStateWithLifecycle()
  val pendingMarkDraft by viewModel.pendingMarkDraft.collectAsStateWithLifecycle()
  val stitchState by viewModel.stitchState.collectAsStateWithLifecycle()
  val captureEvent by viewModel.captureSessionEvent.collectAsStateWithLifecycle()

  var showPinSetupDialog by remember { mutableStateOf(false) }

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination

  val pickSingleLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        viewModel.importSingleUriForMark(uri)
      }
    }

  val pickMultipleLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.handleMultipleUris(uris, fromShare = false)
      }
    }

  val mediaProjectionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null) {
        CaptureSessionBus.pendingToken = PendingMediaProjectionToken(result.resultCode, data)
        val svcIntent = Intent(context, FloatingCaptureService::class.java)
        ContextCompat.startForegroundService(context, svcIntent)
        activity?.moveTaskToBack(true)
      } else {
        scope.launch {
          snackbarHostState.showSnackbar("Screen capture cancelled. You can also stitch picked photos!")
        }
      }
    }

  val galleryDeleteLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
      if (res.resultCode == Activity.RESULT_OK) {
        viewModel.onGalleryDeleteCompleted()
      }
    }

  LaunchedEffect(openCleanupSignal) {
    if (openCleanupSignal > 0 && preferences.hasCompletedOnboarding) {
      navController.navigate(CleanupRoute) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
    }
  }

  LaunchedEffect(Unit) {
    viewModel.effects.collect { effect ->
      when (effect) {
        is UiEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        is UiEffect.LaunchShareIntent -> {
          onSuppressNextBackgroundLock()
          val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
              type = "image/jpeg"
              putExtra(Intent.EXTRA_STREAM, effect.uri)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
          context.startActivity(Intent.createChooser(shareIntent, effect.chooserTitle))
        }
        is UiEffect.RequestMediaDelete -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            onSuppressNextBackgroundLock()
            val pi = MediaStore.createDeleteRequest(context.contentResolver, effect.uris)
            galleryDeleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
          } else {
            withContext(Dispatchers.IO) {
              effect.uris.forEach { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
            }
            viewModel.onGalleryDeleteCompleted()
          }
        }
        is UiEffect.OpenStitchPreview -> {
          navController.navigate(StitchPreviewRoute) { launchSingleTop = true }
        }
        is UiEffect.CloseStitchPreview -> {
          if (currentDest?.hasRoute(StitchPreviewRoute::class) == true) {
            navController.popBackStack()
          }
        }
      }
    }
  }

  SnapKeepTheme(themePreference = preferences.themePreference) {
    if (!isPreferencesLoaded) {
      FrostedAtmosphereBackground {}
      return@SnapKeepTheme
    }

    if (preferences.isVaultLockEnabled && !isUnlocked) {
      VaultLockGate(
        preferBiometric = preferences.isBiometricPreferred,
        onVerifyPin = viewModel::tryUnlockWithPin,
        onBiometricUnlocked = viewModel::unlockWithBiometricSuccess,
      )
      return@SnapKeepTheme
    }

    if (!preferences.hasCompletedOnboarding) {
      OnboardingScreen(
        onSetupPinLock = { showPinSetupDialog = true },
        onSkipToHome = { viewModel.completeOnboarding() },
      )
      if (showPinSetupDialog) {
        SetupPinDialog(
          onDismiss = { showPinSetupDialog = false },
          onPinConfirmed = { pin ->
            showPinSetupDialog = false
            viewModel.enablePinLock(pin, preferBiometric = true)
            viewModel.completeOnboarding()
          },
        )
      }
      return@SnapKeepTheme
    }

    val tabs =
      listOf(
        TopTabItem(
          route = HomeRoute,
          labelRes = R.string.nav_home,
          selectedIcon = Icons.Filled.Home,
          unselectedIcon = Icons.Outlined.Home,
          testTag = "nav_home",
        ),
        TopTabItem(
          route = VaultRoute,
          labelRes = R.string.nav_vault,
          selectedIcon = Icons.Filled.Lock,
          unselectedIcon = Icons.Outlined.Lock,
          testTag = "nav_vault",
        ),
        TopTabItem(
          route = CleanupRoute,
          labelRes = R.string.nav_cleanup,
          selectedIcon = Icons.Filled.CleaningServices,
          unselectedIcon = Icons.Outlined.CleaningServices,
          testTag = "nav_cleanup",
        ),
        TopTabItem(
          route = SettingsRoute,
          labelRes = R.string.nav_settings,
          selectedIcon = Icons.Filled.Settings,
          unselectedIcon = Icons.Outlined.Settings,
          testTag = "nav_settings",
        ),
      )

    val showTopLevelNav =
      currentDest?.hasRoute(HomeRoute::class) == true ||
        currentDest?.hasRoute(VaultRoute::class) == true ||
        currentDest?.hasRoute(CleanupRoute::class) == true ||
        currentDest?.hasRoute(SettingsRoute::class) == true ||
        currentDest == null

    val activeTimerCount =
      screenshots.count {
        it.status == ScreenshotStatus.ACTIVE.name &&
          it.expiresAtMillis != null &&
          it.expiresAtMillis > nowMillis
      }
    val upcomingTimed =
      screenshots.filter {
        it.status == ScreenshotStatus.ACTIVE.name &&
          it.expiresAtMillis != null &&
          it.expiresAtMillis > nowMillis
      }

    fun navigateToTab(route: Any) {
      navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
    }

    FrostedAtmosphereBackground {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        val extra = LocalSnapKeepColors.current

        Scaffold(
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
          bottomBar = {
            if (showTopLevelNav && !useRail) {
              NavigationBar(
                containerColor = extra.frostedCard,
                tonalElevation = 4.dp,
                modifier = Modifier.navigationBarsPadding(),
              ) {
                tabs.forEach { tab ->
                  val selected = currentDest?.hasRoute(tab.route::class) == true
                  val badgeCount = if (tab.route is CleanupRoute) cleanupQueue.size else 0
                  NavigationBarItem(
                    selected = selected,
                    onClick = { navigateToTab(tab.route) },
                    icon = {
                      BadgedBox(
                        badge = {
                          if (badgeCount > 0) {
                            Badge { Text("$badgeCount") }
                          }
                        },
                      ) {
                        Icon(
                          imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                          contentDescription = stringResource(tab.labelRes),
                        )
                      }
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    modifier = Modifier.testTag(tab.testTag),
                  )
                }
              }
            }
          },
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (showTopLevelNav && useRail) {
              NavigationRail(containerColor = extra.frostedCard) {
                tabs.forEach { tab ->
                  val selected = currentDest?.hasRoute(tab.route::class) == true
                  val badgeCount = if (tab.route is CleanupRoute) cleanupQueue.size else 0
                  NavigationRailItem(
                    selected = selected,
                    onClick = { navigateToTab(tab.route) },
                    icon = {
                      BadgedBox(
                        badge = {
                          if (badgeCount > 0) {
                            Badge { Text("$badgeCount") }
                          }
                        },
                      ) {
                        Icon(
                          imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                          contentDescription = stringResource(tab.labelRes),
                        )
                      }
                    },
                    label = { Text(stringResource(tab.labelRes)) },
                    modifier = Modifier.testTag("${tab.testTag}_rail"),
                  )
                }
              }
            }

            NavHost(
              navController = navController,
              startDestination = HomeRoute,
              modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
              composable<HomeRoute> {
                HomeScreen(
                  preferences = preferences,
                  stats = stats,
                  activeTimerCount = activeTimerCount,
                  cleanupReadyCount = cleanupQueue.size,
                  isCapturingLive = captureEvent is CaptureSessionEvent.Capturing,
                  onStartFloatingBubble = {
                    onSuppressNextBackgroundLock()
                    val mpm =
                      context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
                    mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
                  },
                  onPickMultipleToStitch = {
                    onSuppressNextBackgroundLock()
                    pickMultipleLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                  },
                  onPickSingleToProtect = {
                    onSuppressNextBackgroundLock()
                    pickSingleLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                  },
                  onOpenCleanupTab = { navigateToTab(CleanupRoute) },
                  onLockNow = { viewModel.lockVaultNow() },
                )
              }

              composable<VaultRoute> {
                VaultScreen(
                  screenshots = screenshots,
                  nowMillis = nowMillis,
                  loadThumbnail = viewModel::loadThumbnail,
                  onOpenDetail = { id -> navController.navigate(DetailRoute(id)) },
                  onPickToImport = {
                    onSuppressNextBackgroundLock()
                    pickSingleLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                  },
                )
              }

              composable<CleanupRoute> {
                CleanupScreen(
                  queueItems = cleanupQueue,
                  upcomingTimedItems = upcomingTimed,
                  nowMillis = nowMillis,
                  loadThumbnail = viewModel::loadThumbnail,
                  onCleanAllExpired = viewModel::cleanAllExpired,
                  onKeepForever = { id -> viewModel.updateScreenshotTimer(id, null) },
                  onShredSingle = viewModel::shredScreenshotNow,
                )
              }

              composable<SettingsRoute> {
                SettingsScreen(
                  preferences = preferences,
                  onRequestEnablePin = { showPinSetupDialog = true },
                  onDisableLock = viewModel::disableVaultLock,
                  onSetBiometricPreferred = viewModel::setBiometricPreferred,
                  onSetScreenShield = viewModel::setScreenShieldEnabled,
                  onSetTimerAlerts = viewModel::setTimerAlertsEnabled,
                  onSetSilentAutoShred = viewModel::setSilentAutoShred,
                  onUpdateTimerPresets = viewModel::updateTimerPresets,
                  onSetTheme = viewModel::setThemePreference,
                )
              }

              composable<StitchPreviewRoute> {
                StitchPreviewScreen(
                  stitchState = stitchState,
                  defaultShortTimerMinutes = preferences.timerPreset1Minutes,
                  onAdjustSeamOverlap = viewModel::adjustSeamOverlap,
                  onToggleTrimSystemBars = viewModel::toggleTrimSystemBars,
                  onOpenTimerPicker = viewModel::sendStitchToMarkDecision,
                  onSaveForever = { viewModel.saveStitchDirectly(timerMinutes = null) },
                  onShareAndTimer = {
                    viewModel.saveStitchDirectly(
                      timerMinutes = preferences.timerPreset1Minutes,
                      shareAfter = true,
                    )
                  },
                  onDiscard = viewModel::discardStitchPreview,
                )
              }

              composable<DetailRoute> { entry ->
                val route = entry.toRoute<DetailRoute>()
                val item by viewModel.observeScreenshot(route.itemId).collectAsStateWithLifecycle(null)
                DetailScreen(
                  item = item,
                  preferences = preferences,
                  nowMillis = nowMillis,
                  loadFullImage = viewModel::loadFullImage,
                  onBack = { navController.popBackStack() },
                  onUpdateTimer = viewModel::updateScreenshotTimer,
                  onShareAndDelete = { entity ->
                    viewModel.shareScreenshot(entity, preferences.timerPreset1Minutes)
                  },
                  onExportToGallery = viewModel::exportCopyToGallery,
                  onShredNow = viewModel::shredScreenshotNow,
                )
              }
            }
          }
        }
      }
    }

    val draft = pendingMarkDraft
    if (draft != null) {
      MarkDecisionSheet(
        draft = draft,
        preferences = preferences,
        onDismiss = viewModel::dismissMarkDraft,
        onCommit = viewModel::commitMarkDraft,
      )
    }

    if (showPinSetupDialog) {
      SetupPinDialog(
        onDismiss = { showPinSetupDialog = false },
        onPinConfirmed = { pin ->
          showPinSetupDialog = false
          viewModel.enablePinLock(pin, preferBiometric = true)
        },
      )
    }
  }
}
