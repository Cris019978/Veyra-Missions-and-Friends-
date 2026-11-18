package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.LocalAccountStore
import com.example.data.local.LocalAuthManager
import com.example.data.local.LocalMissionFeedRepository
import com.example.data.local.LocalThemePreferences
import com.example.data.local.ThemePrefsSnapshot
import com.example.data.local.db.VeyraLocalDatabase
import com.example.data.location.LocationTracker
import com.example.data.repository.RadarQuestRepository
import com.example.service.ProximityGuidanceService
import com.example.ui.auth.AuthScreen
import com.example.ui.components.FloatingBottomNavBar
import com.example.ui.components.NearbyMissionPopupOverlay
import com.example.ui.components.NotificationsSheet
import com.example.ui.components.OnboardingQuestOverlay
import com.example.ui.components.QueuedMissionPromptDialog
import com.example.ui.screens.CustomizeStyleScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RadarHomeScreen
import com.example.ui.screens.SocialArenaScreen
import com.example.ui.screens.SocialFeedScreen
import com.example.ui.theme.RadarQuestTheme
import com.example.ui.viewmodel.RadarQuestViewModel
import com.example.util.SafeRuntimeBootstrap
import com.example.util.VeyraNotificationManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // 1. Register global UncaughtExceptionHandler before super.onCreate / setContent
    SafeRuntimeBootstrap.installGlobalExceptionHandler(applicationContext)
    // 2. Initialize osmdroid safely into internal cacheDir without external storage permissions
    SafeRuntimeBootstrap.initOsmdroidSafely(applicationContext)

    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AppNavigation()
    }
  }
}

@Composable
fun AppNavigation() {
  val context = LocalContext.current
  val navScope = rememberCoroutineScope()
  val authManager = remember(context) { LocalAuthManager(context.applicationContext) }
  val localPrefs = remember(context) { LocalThemePreferences(context.applicationContext) }
  val accountStore = remember(context) { LocalAccountStore(context.applicationContext) }
  val notificationManager = remember(context) { VeyraNotificationManager(context.applicationContext) }
  val localThemeSnapshot by localPrefs.prefsFlow.collectAsStateWithLifecycle(initialValue = ThemePrefsSnapshot())
  val authSession by authManager.sessionFlow.collectAsStateWithLifecycle()

  val handleSignOut: () -> Unit = {
    navScope.launch {
      withContext(NonCancellable) {
        ProximityGuidanceService.stop(context.applicationContext)
        authManager.logout()
        localPrefs.clearAll()
        accountStore.clearSavedAccount(markExplicitlySignedOut = false)
      }
    }
  }

  val activeSession = authSession
  if (activeSession != null && activeSession.isLoggedIn) {
    val localUid = activeSession.username
    val sessionKey = "local_${localUid}"
    key(sessionKey) {
      val viewModel: RadarQuestViewModel = viewModel(
        key = sessionKey,
        factory = viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY])
            val localDb = VeyraLocalDatabase.getInstance(app)
            val feedRepo = LocalMissionFeedRepository(
              dao = localDb.localMissionPostDao(),
              contributionDao = localDb.authorContributionDao(),
            )
            RadarQuestViewModel(
              repository = RadarQuestRepository(
                db = null,
                localUserId = localUid,
                localAccountStore = LocalAccountStore(app),
              ),
              localPrefs = LocalThemePreferences(app),
              currentUserId = localUid,
              defaultDisplayName = localUid,
              isLocalOfflineMode = true,
              savedAccountEmail = localUid,
              accountRole = activeSession.role,
              accountRoleTitle = activeSession.roleTitle,
              initialAccountXp = activeSession.xp,
              initialAccountLevel = activeSession.level,
              localMissionFeedRepository = feedRepo,
              onSystemNotify = notificationManager::postNotification,
            )
          }
        },
      )
      MainAppShell(
        viewModel = viewModel,
        notificationManager = notificationManager,
        currentUserId = localUid,
        onSyncSessionStats = { xp, level ->
          authManager.updateActiveStats(xp, level)
        },
        onSignOutRequested = handleSignOut,
      )
    }
  } else {
    RadarQuestTheme(
      themeMode = "dark",
      paletteKey = localThemeSnapshot.paletteKey,
      cornerStyle = localThemeSnapshot.cornerStyle,
    ) {
      AuthScreen(
        onLogin = { username, pin -> authManager.login(username, pin) },
        onRegister = { username, pin, inviteCode ->
          authManager.registerAccount(username, pin, inviteCode)
        },
      )
    }
  }
}

@Composable
private fun MainAppShell(
  viewModel: RadarQuestViewModel,
  notificationManager: VeyraNotificationManager,
  currentUserId: String,
  onSyncSessionStats: (xp: Long, level: Long) -> Unit,
  onSignOutRequested: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val locationTracker = remember(context) { LocationTracker(context) }
  val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
  var showStyleInProfile by remember { mutableStateOf(false) }
  var showNotificationsSheet by remember { mutableStateOf(false) }
  val disableAnimations = uiState.settings.animationTier == "low"
  val gestureNavEnabled = uiState.settings.gestureNavEnabled

  LaunchedEffect(uiState.myProfile?.xp, uiState.myProfile?.level) {
    val prof = uiState.myProfile
    if (prof != null) {
      onSyncSessionStats(prof.xp, prof.level)
    }
  }

  val selectTab: (Int) -> Unit = { targetPage ->
    showStyleInProfile = false
    scope.launch {
      if (disableAnimations) {
        pagerState.scrollToPage(targetPage)
      } else {
        pagerState.animateScrollToPage(targetPage)
      }
    }
  }

  val checkLocationPermission: () -> Boolean = {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    viewModel.setNotificationPermission(granted)
  }

  val requestNotificationPermissionIfNeeded: () -> Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      viewModel.setNotificationPermission(notificationManager.hasPermission())
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val granted = grants.values.any { it }
    viewModel.setLocationPermission(granted)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationManager.hasPermission()) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(Unit) {
    val locGranted = checkLocationPermission()
    val notifGranted = notificationManager.hasPermission()
    viewModel.setLocationPermission(locGranted)
    viewModel.setNotificationPermission(notifGranted)
    if (!locGranted) {
      locationPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(uiState.hasLocationPermission) {
    if (uiState.hasLocationPermission) {
      locationTracker.observeLocationUpdates().collect { point ->
        viewModel.onLocationUpdated(point)
      }
    }
  }

  LaunchedEffect(uiState.snackbarMessage) {
    val msg = uiState.snackbarMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearSnackbar()
    }
  }

  LaunchedEffect(
    uiState.activeAcceptedMission,
    uiState.hasLocationPermission,
    uiState.settings.notificationsEnabled,
  ) {
    val active = uiState.activeAcceptedMission
    if (active != null && uiState.hasLocationPermission && uiState.settings.notificationsEnabled) {
      ProximityGuidanceService.startForMission(
        context = context,
        missionId = active.mission.id,
        missionTitle = active.mission.title,
        latitude = active.mission.latitude,
        longitude = active.mission.longitude,
      )
    } else {
      ProximityGuidanceService.stop(context)
    }
  }

  val isExpandedWidth = LocalConfiguration.current.screenWidthDp >= 720
  val radiusPresets = listOf(500L, 2000L, 5000L, 15000L)

  RadarQuestTheme(
    themeMode = uiState.settings.themeMode,
    paletteKey = uiState.settings.paletteKey,
    cornerStyle = uiState.settings.cornerStyle,
  ) {
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (isExpandedWidth && !gestureNavEnabled) {
          NavigationRail {
            NavigationRailItem(
              selected = pagerState.currentPage == 0,
              onClick = { selectTab(0) },
              icon = { Icon(if (pagerState.currentPage == 0) Icons.Rounded.Radar else Icons.Outlined.Radar, null) },
              label = { Text(stringResource(id = R.string.nav_radar)) },
            )
            NavigationRailItem(
              selected = pagerState.currentPage == 1,
              onClick = { selectTab(1) },
              icon = { Icon(if (pagerState.currentPage == 1) Icons.Rounded.AccountCircle else Icons.Outlined.AccountCircle, null) },
              label = { Text(stringResource(id = R.string.nav_profile)) },
            )
            NavigationRailItem(
              selected = pagerState.currentPage == 2,
              onClick = { selectTab(2) },
              icon = { Icon(if (pagerState.currentPage == 2) Icons.Rounded.PhotoLibrary else Icons.Outlined.PhotoLibrary, null) },
              label = { Text(stringResource(id = R.string.nav_social_feed)) },
            )
            NavigationRailItem(
              selected = pagerState.currentPage == 3,
              onClick = { selectTab(3) },
              icon = { Icon(if (pagerState.currentPage == 3) Icons.Rounded.Groups else Icons.Outlined.Groups, null) },
              label = { Text(stringResource(id = R.string.nav_social)) },
            )
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          BackHandler(enabled = pagerState.currentPage != 0 || showStyleInProfile) {
            if (showStyleInProfile) {
              showStyleInProfile = false
            } else {
              selectTab(0)
            }
          }

          HorizontalPager(
            state = pagerState,
            userScrollEnabled = gestureNavEnabled,
            modifier = Modifier.fillMaxSize(),
          ) { page ->
            when (page) {
              0 -> RadarHomeScreen(
                uiState = uiState,
                onRequestLocationPermission = {
                  locationPermissionLauncher.launch(
                    arrayOf(
                      Manifest.permission.ACCESS_FINE_LOCATION,
                      Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                  )
                },
                onOpenNotifications = { showNotificationsSheet = true },
                onRefresh = { viewModel.refreshAll() },
                onToggleBroadcast = { viewModel.toggleBroadcast(it) },
                onToggleSeekingHelp = { viewModel.toggleSeekingHelp() },
                onCycleRadius = {
                  val cur = uiState.settings.searchRadiusMeters
                  val nextIdx = (radiusPresets.indexOf(cur) + 1) % radiusPresets.size
                  viewModel.saveThemeCustomization(searchRadiusMeters = radiusPresets[nextIdx])
                },
                onHelpPlayer = { viewModel.sendHelpToPlayer(it.profile) },
                onSendPulse = { viewModel.sendPlayPulse(it.profile) },
                onCompeteDuel = { target, mission ->
                  viewModel.createChallenge(
                    mode = "duel",
                    title = "Duelo vs ${target.profile.callsign}: ${mission.title}",
                    missionId = mission.id,
                    xpStake = mission.xpReward + 50L,
                  )
                },
                onPlayCoop = { target, mission ->
                  viewModel.createChallenge(
                    mode = "coop",
                    title = "Co-op con ${target.profile.callsign}: ${mission.title}",
                    missionId = mission.id,
                    xpStake = mission.xpReward + 40L,
                  )
                },
                onCreateMission = { title, desc, cat, xp, type, icon ->
                  viewModel.createCustomMission(title, desc, cat, xp, type, icon, 0)
                },
                onCreateTimedMission = { title, desc, cat, xp, type, icon, timeLimit ->
                  viewModel.createCustomMission(title, desc, cat, xp, type, icon, timeLimit)
                },
                onConfirmMissionPhoto = { mission, caption, base64, isPublic ->
                  viewModel.confirmMissionWithPhoto(mission, caption, base64, isPublic)
                },
                onCompleteMiniGame = { mission, caption, isPublic ->
                  viewModel.completeMiniGameMission(mission, caption, isPublic)
                },
                onAcceptMission = { viewModel.acceptMission(it) },
                onWaitMission = { viewModel.waitMission(it) },
                onRejectMission = { viewModel.rejectMission(it) },
                onCancelActiveMission = { viewModel.cancelActiveMission() },
                onRespawnRandomMissions = { viewModel.respawnRandomMissions() },
              )

              1 -> if (showStyleInProfile) {
                CustomizeStyleScreen(
                  uiState = uiState,
                  onSaveTheme = { mode, palette, radar, corner ->
                    viewModel.saveThemeCustomization(
                      themeMode = mode,
                      paletteKey = palette,
                      radarStyle = radar,
                      cornerStyle = corner,
                    )
                  },
                  onSaveAnimationTier = { tier ->
                    viewModel.saveThemeCustomization(animationTier = tier)
                  },
                  onToggleNotifications = { enabled ->
                    viewModel.saveThemeCustomization(notificationsEnabled = enabled)
                    if (enabled && !uiState.hasNotificationPermission) {
                      requestNotificationPermissionIfNeeded()
                    }
                  },
                  onToggleGestureNav = { enabled ->
                    viewModel.saveThemeCustomization(gestureNavEnabled = enabled)
                  },
                  onRequestNotificationPermission = requestNotificationPermissionIfNeeded,
                  onSaveProfile = { callsign, icon, colorHex, status, help ->
                    viewModel.updatePlayerProfile(
                      callsign = callsign,
                      avatarIcon = icon,
                      avatarColorHex = colorHex,
                      statusMessage = status,
                      seekingHelp = help,
                    )
                  },
                  onReopenOnboardingQuest = { viewModel.reopenOnboardingQuest() },
                  onSignOut = {
                    showStyleInProfile = false
                    onSignOutRequested()
                  },
                  onBack = { showStyleInProfile = false },
                )
              } else {
                ProfileScreen(
                  uiState = uiState,
                  onSaveProfile = { username, callsign, icon, colorHex, avatarB64, bannerB64, bannerPreset, status, help ->
                    viewModel.updatePlayerProfile(
                      username = username,
                      callsign = callsign,
                      avatarIcon = icon,
                      avatarColorHex = colorHex,
                      avatarPhotoBase64 = avatarB64,
                      bannerPhotoBase64 = bannerB64,
                      bannerPreset = bannerPreset,
                      statusMessage = status,
                      seekingHelp = help,
                    )
                  },
                  onQuickSelectPalette = { palette ->
                    viewModel.saveThemeCustomization(paletteKey = palette)
                  },
                  onOpenStyleCustomization = { showStyleInProfile = true },
                )
              }

              2 -> SocialFeedScreen(
                uiState = uiState,
                currentUserId = currentUserId,
                onRefresh = { viewModel.generateFreshLocalFeedMissions() },
                onCheerCompletion = { viewModel.cheerCompletion(it) },
                onPublishLocalMission = { title, desc, cat, xp, place ->
                  viewModel.publishLocalFeedMission(title, desc, cat, xp, place)
                },
                onGenerateLocalMissions = { viewModel.generateFreshLocalFeedMissions() },
                onOpenFeedMissionOnRadar = { post ->
                  viewModel.acceptMission(post.id)
                  selectTab(0)
                },
              )

              3 -> SocialArenaScreen(
                uiState = uiState,
                currentUserId = currentUserId,
                onRefresh = { viewModel.refreshAll() },
                onCreateChallenge = { mode, title, missionId, stake ->
                  viewModel.createChallenge(mode, title, missionId, stake)
                },
                onJoinChallenge = { viewModel.joinChallenge(it) },
                onAdvanceChallenge = { viewModel.completeChallengeStep(it) },
              )
            }
          }

          NearbyMissionPopupOverlay(
            popups = uiState.nearbyMissionPopups,
            disableAnimations = disableAnimations,
            onDismissPopup = { missionId -> viewModel.dismissNearbyMissionPopup(missionId) },
            modifier = Modifier.align(Alignment.TopCenter),
          )

          if (!isExpandedWidth) {
            if (disableAnimations) {
              if (!gestureNavEnabled) {
                FloatingBottomNavBar(
                  selectedTab = pagerState.currentPage,
                  disableAnimations = true,
                  onSelectTab = selectTab,
                  modifier = Modifier.align(Alignment.BottomCenter),
                )
              }
            } else {
              androidx.compose.animation.AnimatedVisibility(
                visible = !gestureNavEnabled,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
              ) {
                FloatingBottomNavBar(
                  selectedTab = pagerState.currentPage,
                  disableAnimations = false,
                  onSelectTab = selectTab,
                )
              }
            }
          }
        }
      }
    }

    if (showNotificationsSheet) {
      NotificationsSheet(
        notifications = uiState.notifications,
        hasSystemPermission = uiState.hasNotificationPermission,
        onDismiss = { showNotificationsSheet = false },
        onMarkAllRead = { viewModel.markAllNotificationsRead() },
        onRequestSystemPermission = requestNotificationPermissionIfNeeded,
      )
    }

    if (uiState.showOnboardingQuest) {
      OnboardingQuestOverlay(
        initialSettings = uiState.settings,
        onCompleteQuest = { playStyle, favCats, privacyComfort, pace, palette ->
          viewModel.completeOnboardingQuest(
            playStyle = playStyle,
            favoriteCategories = favCats,
            privacyComfort = privacyComfort,
            missionPace = pace,
            paletteKey = palette,
          )
        },
        onPreviewPalette = { palette ->
          viewModel.saveThemeCustomization(paletteKey = palette)
        },
      )
    }

    uiState.queuedPromptMission?.let { queued ->
      QueuedMissionPromptDialog(
        queuedMission = queued,
        remainingQueueCount = uiState.waitingMissionQueue.size,
        onAcceptNow = {
          viewModel.acceptMission(queued.mission.id)
          selectTab(0)
        },
        onReject = { viewModel.rejectMission(queued.mission.id) },
        onKeepWaiting = { viewModel.dismissQueuedPromptKeepWaiting() },
      )
    }
  }
}
