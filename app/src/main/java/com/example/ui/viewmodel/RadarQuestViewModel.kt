package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalThemePreferences
import com.example.data.local.ThemePrefsSnapshot
import com.example.data.location.LiveLocationPoint
import com.example.data.model.AppNotificationItem
import com.example.data.model.Challenge
import com.example.data.model.Mission
import com.example.data.model.MissionCompletion
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyMissionPopup
import com.example.data.model.NearbyPlayerItem
import com.example.data.model.PlayerInteraction
import com.example.data.model.PlayerProfile
import com.example.data.model.UserSettings
import com.example.data.repository.RadarQuestRepository
import com.example.util.GeoUtils
import com.example.util.PhotoUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RadarQuestUiState(
  val myProfile: PlayerProfile? = null,
  val settings: UserSettings = UserSettings(),
  val showOnboardingQuest: Boolean = false,
  val currentLocation: LiveLocationPoint? = null,
  val hasLocationPermission: Boolean = false,
  val hasNotificationPermission: Boolean = true,
  val isBroadcastingRadar: Boolean = true,
  val nearbyPlayers: List<NearbyPlayerItem> = emptyList(),
  val leaderboardPlayers: List<PlayerProfile> = emptyList(),
  val missions: List<MissionWithDistance> = emptyList(),
  val activeAcceptedMissionId: String? = null,
  val activeAcceptedMission: MissionWithDistance? = null,
  val waitingMissionQueue: List<String> = emptyList(),
  val rejectedMissionIds: Set<String> = emptySet(),
  val queuedPromptMission: MissionWithDistance? = null,
  val myCompletions: List<MissionCompletion> = emptyList(),
  val publicCompletions: List<MissionCompletion> = emptyList(),
  val openChallenges: List<Challenge> = emptyList(),
  val myChallenges: List<Challenge> = emptyList(),
  val interactions: List<PlayerInteraction> = emptyList(),
  val notifications: List<AppNotificationItem> = emptyList(),
  val unreadNotificationsCount: Int = 0,
  val nearbyMissionPopups: List<NearbyMissionPopup> = emptyList(),
  val snackbarMessage: String? = null,
  val isRefreshing: Boolean = false,
  val isSubmitting: Boolean = false,
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RadarQuestViewModel(
  private val repository: RadarQuestRepository,
  private val localPrefs: LocalThemePreferences,
  private val currentUserId: String,
  private val defaultDisplayName: String,
  private val onSystemNotify: (id: Int, title: String, message: String, isProximity: Boolean) -> Unit = { _, _, _, _ -> },
) : ViewModel() {

  private val _currentLocation = MutableStateFlow<LiveLocationPoint?>(null)
  private val _hasLocationPermission = MutableStateFlow(false)
  private val _hasNotificationPermission = MutableStateFlow(true)
  private val _isBroadcastingRadar = MutableStateFlow(true)
  private val _snackbarMessage = MutableStateFlow<String?>(null)
  private val _isRefreshing = MutableStateFlow(false)
  private val _isSubmitting = MutableStateFlow(false)
  private val _errorMessage = MutableStateFlow<String?>(null)
  private val _hasResolvedRemoteSettings = MutableStateFlow(false)
  private val _forceShowOnboarding = MutableStateFlow(false)
  private val _notifications = MutableStateFlow<List<AppNotificationItem>>(emptyList())
  private val _dismissedPopupMissionIds = MutableStateFlow<Set<String>>(emptySet())
  private val _refreshTrigger = MutableStateFlow(0)
  private val _refreshedMissions = MutableStateFlow<List<Mission>>(emptyList())

  private val _missionSeed = MutableStateFlow(System.currentTimeMillis())
  private val _missionAnchorCoords = MutableStateFlow<Pair<Double, Double>?>(null)
  private val _activeAcceptedMissionId = MutableStateFlow<String?>(null)
  private val _waitingMissionQueue = MutableStateFlow<List<String>>(emptyList())
  private val _rejectedMissionIds = MutableStateFlow<Set<String>>(emptySet())
  private val _queuedPromptMissionId = MutableStateFlow<String?>(null)

  private var respawnJob: Job? = null
  private var hasInitializedProfile = false
  private var seededInitialInteractions = false
  private val seenInteractionIds = mutableSetOf<String>()
  private var seededInitialChallenges = false
  private val seenChallengeStates = mutableMapOf<String, String>()
  private val notifiedProximityMissionIds = mutableSetOf<String>()
  private val notifiedCloseSpotIds = mutableSetOf<String>()

  private fun unwrapError(error: Throwable, contextLabel: String): String {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val root = firestoreError ?: error
    Log.w(TAG, "Stream error ($contextLabel)", root)
    return root.message ?: "Error de conexión"
  }

  private fun pushNotification(
    id: String,
    title: String,
    message: String,
    type: String,
    isProximity: Boolean = false,
  ) {
    val currentList = _notifications.value
    if (currentList.any { it.id == id }) return
    val item = AppNotificationItem(
      id = id,
      title = title,
      message = message,
      type = type,
      timestampMs = System.currentTimeMillis(),
      isRead = false,
    )
    _notifications.value = (listOf(item) + currentList).take(30)
    if (uiState.value.settings.notificationsEnabled) {
      onSystemNotify(id.hashCode(), title, message, isProximity)
    }
  }

  private val myProfileFlow = _refreshTrigger.flatMapLatest {
    repository.observeMyProfile(currentUserId)
      .catch { e -> _errorMessage.value = unwrapError(e, "profile"); emit(null) }
  }

  private val settingsFlow = _refreshTrigger.flatMapLatest {
    repository.observeUserSettings(currentUserId)
      .onEach { _hasResolvedRemoteSettings.value = true }
      .catch { e ->
        _hasResolvedRemoteSettings.value = true
        _errorMessage.value = unwrapError(e, "settings")
        emit(null)
      }
  }

  private val onlinePlayersFlow = _refreshTrigger.flatMapLatest {
    repository.observeOnlinePlayers(currentUserId)
      .catch { e -> _errorMessage.value = unwrapError(e, "onlinePlayers"); emit(emptyList()) }
  }

  private val activeMissionsFlow = _refreshTrigger.flatMapLatest {
    repository.observeActiveMissions(currentUserId)
      .catch { e -> _errorMessage.value = unwrapError(e, "missions"); emit(emptyList()) }
  }

  private val myCompletionsFlow = _refreshTrigger.flatMapLatest {
    repository.observeMyCompletions(currentUserId)
      .catch { e -> _errorMessage.value = unwrapError(e, "myCompletions"); emit(emptyList()) }
  }

  private val publicCompletionsFlow = _refreshTrigger.flatMapLatest {
    repository.observePublicCompletions(currentUserId)
      .catch { e -> _errorMessage.value = unwrapError(e, "publicCompletions"); emit(emptyList()) }
  }

  private val openChallengesFlow = _refreshTrigger.flatMapLatest {
    repository.observeOpenChallenges(currentUserId)
      .catch { e -> _errorMessage.value = unwrapError(e, "openChallenges"); emit(emptyList()) }
  }

  private val myChallengesFlow = _refreshTrigger.flatMapLatest {
    repository.observeMyChallenges(currentUserId)
      .onEach { list ->
        if (!seededInitialChallenges) {
          seededInitialChallenges = true
          list.forEach { seenChallengeStates[it.id] = it.status }
        } else {
          list.forEach { ch ->
            val prev = seenChallengeStates[ch.id]
            if (prev != ch.status) {
              seenChallengeStates[ch.id] = ch.status
              if (ch.status == "active" && ch.creatorId == currentUserId && ch.opponentName.isNotBlank()) {
                pushNotification(
                  id = "ch_join_${ch.id}",
                  title = "¡Reto aceptado!",
                  message = "${ch.opponentName} se unió a '${ch.title}'",
                  type = "challenge",
                )
              } else if (ch.status == "completed" && ch.winnerId.isNotBlank() && ch.winnerId != currentUserId) {
                pushNotification(
                  id = "ch_done_${ch.id}",
                  title = "Actualización de reto",
                  message = "'${ch.title}' finalizó. Revisa el marcador en Arena.",
                  type = "challenge",
                )
              }
            }
          }
        }
      }
      .catch { e -> _errorMessage.value = unwrapError(e, "myChallenges"); emit(emptyList()) }
  }

  private val interactionsFlow = _refreshTrigger.flatMapLatest {
    repository.observeMyInteractions(currentUserId)
      .onEach { list ->
        val incomingForMe = list.filter { it.toUserId == currentUserId }
        if (!seededInitialInteractions) {
          seededInitialInteractions = true
          incomingForMe.forEach { seenInteractionIds.add(it.id) }
        } else {
          incomingForMe.forEach { inter ->
            if (seenInteractionIds.add(inter.id)) {
              val title = when (inter.type) {
                "help" -> "${inter.fromUserName} te envió ayuda"
                "pulse" -> "Pulso de radar de ${inter.fromUserName}"
                "cheer" -> "¡Aplauso de ${inter.fromUserName}!"
                else -> "Mensaje de ${inter.fromUserName}"
              }
              pushNotification(
                id = "int_${inter.id}",
                title = title,
                message = inter.message,
                type = inter.type,
              )
            }
          }
        }
      }
      .catch { e -> _errorMessage.value = unwrapError(e, "interactions"); emit(emptyList()) }
  }

  private val localOnboardingDoneFlow = localPrefs.observeOnboardingCompleted(currentUserId)

  private val coreDataFlow = combine(
    myProfileFlow,
    settingsFlow,
    onlinePlayersFlow,
    activeMissionsFlow,
    myCompletionsFlow,
  ) { profile, remoteSettings, onlinePlayers, missions, myComps ->
    CoreBundle(profile, remoteSettings, onlinePlayers, missions, myComps)
  }

  private val socialDataFlow = combine(
    publicCompletionsFlow,
    openChallengesFlow,
    myChallengesFlow,
    interactionsFlow,
    localPrefs.prefsFlow,
  ) { publicComps, openChals, myChals, inters, localTheme ->
    SocialBundle(publicComps, openChals, myChals, inters, localTheme)
  }

  private val localControlFlow = combine(
    _currentLocation,
    _hasLocationPermission,
    _hasNotificationPermission,
    _isBroadcastingRadar,
    _snackbarMessage,
    _isSubmitting,
    _dismissedPopupMissionIds,
    _isRefreshing,
    _refreshedMissions,
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    LocalControlBundle(
      location = values[0] as LiveLocationPoint?,
      hasPermission = values[1] as Boolean,
      hasNotifPermission = values[2] as Boolean,
      broadcast = values[3] as Boolean,
      snackbar = values[4] as String?,
      isSubmitting = values[5] as Boolean,
      dismissedPopupIds = values[6] as Set<String>,
      isRefreshing = values[7] as Boolean,
      refreshedMissions = values[8] as List<Mission>,
    )
  }

  private val missionFlowState = combine(
    _missionSeed,
    _missionAnchorCoords,
    _activeAcceptedMissionId,
    _waitingMissionQueue,
    _rejectedMissionIds,
    _queuedPromptMissionId,
  ) { values ->
    @Suppress("UNCHECKED_CAST")
    MissionWorkflowBundle(
      missionSeed = values[0] as Long,
      anchorCoords = values[1] as Pair<Double, Double>?,
      activeAcceptedId = values[2] as String?,
      waitingQueue = values[3] as List<String>,
      rejectedIds = values[4] as Set<String>,
      queuedPromptId = values[5] as String?,
    )
  }

  private val onboardingStateFlow = combine(
    _hasResolvedRemoteSettings,
    localOnboardingDoneFlow,
    _forceShowOnboarding,
    _notifications,
    _errorMessage,
  ) { resolved, localDone, forceShow, notifs, err ->
    OnboardingGateBundle(resolved, localDone, forceShow, notifs, err)
  }

  val uiState: StateFlow<RadarQuestUiState> = combine(
    coreDataFlow,
    socialDataFlow,
    localControlFlow,
    onboardingStateFlow,
    missionFlowState,
  ) { core, social, local, gate, workflow ->
    val remote = core.remoteSettings
    val effectiveSettings = remote ?: UserSettings(
      userId = currentUserId,
      themeMode = social.localTheme.themeMode,
      paletteKey = social.localTheme.paletteKey,
      radarStyle = social.localTheme.radarStyle,
      cornerStyle = social.localTheme.cornerStyle,
      searchRadiusMeters = social.localTheme.searchRadiusMeters,
      animationTier = social.localTheme.animationTier,
      notificationsEnabled = social.localTheme.notificationsEnabled,
      onboardingCompleted = gate.localDone,
      playStyle = social.localTheme.playStyle,
      favoriteCategories = social.localTheme.favoriteCategories,
      privacyComfort = social.localTheme.privacyComfort,
      missionPace = social.localTheme.missionPace,
      gestureNavEnabled = social.localTheme.gestureNavEnabled,
    )

    val isOnboardingFinished = gate.localDone || (remote?.onboardingCompleted == true)
    val shouldShowOnboarding = gate.forceShow || (gate.resolved && !isOnboardingFinished)

    val refLat = local.location?.latitude ?: core.profile?.latitude ?: 0.0
    val refLon = local.location?.longitude ?: core.profile?.longitude ?: 0.0
    val hasValidRef = (refLat != 0.0 || refLon != 0.0)
    val radiusMeters = effectiveSettings.searchRadiusMeters.toFloat()

    // Lock anchor to first valid GPS fix so random mission coordinates remain stable as player walks
    val anchor = workflow.anchorCoords ?: if (hasValidRef) {
      (refLat to refLon).also { _missionAnchorCoords.value = it }
    } else {
      19.4326 to -99.1332
    }

    val nearby = core.onlinePlayers
      .filter { it.userId != currentUserId }
      .map { player ->
        val dist = if (hasValidRef && (player.latitude != 0.0 || player.longitude != 0.0)) {
          GeoUtils.distanceMeters(refLat, refLon, player.latitude, player.longitude)
        } else {
          0f
        }
        val bearing = if (hasValidRef && (player.latitude != 0.0 || player.longitude != 0.0)) {
          GeoUtils.bearingDegrees(refLat, refLon, player.latitude, player.longitude)
        } else {
          0f
        }
        NearbyPlayerItem(player, dist, bearing)
      }
      .filter { it.distanceMeters <= radiusMeters || !hasValidRef }
      .sortedBy { it.distanceMeters }

    val completedMissionIds = core.myCompletions.map { it.missionId }.toSet()
    val randomGeneratedMissions = GeoUtils.generateRandomMissions(
      baseLat = anchor.first,
      baseLon = anchor.second,
      seed = workflow.missionSeed,
      maxRadiusMeters = radiusMeters,
    )

    val allMissions = (core.missions + local.refreshedMissions + randomGeneratedMissions)
      .distinctBy { it.id }
      .filterNot { it.id in workflow.rejectedIds }

    val favCats = effectiveSettings.favoriteCategories.toSet()

    val missionsWithDist = allMissions.map { mission ->
      val dist = if (hasValidRef) {
        GeoUtils.distanceMeters(refLat, refLon, mission.latitude, mission.longitude)
      } else {
        180f
      }
      val bearing = if (hasValidRef) {
        GeoUtils.bearingDegrees(refLat, refLon, mission.latitude, mission.longitude)
      } else {
        45f
      }
      val withinProx = hasValidRef && local.hasPermission && dist <= GeoUtils.PROXIMITY_RADIUS_METERS
      MissionWithDistance(
        mission = mission,
        distanceMeters = dist,
        bearingDegrees = bearing,
        isCompletedByMe = mission.id in completedMissionIds,
        isWithinProximity = withinProx,
      )
    }.sortedWith(
      compareByDescending<MissionWithDistance> { it.mission.id == workflow.activeAcceptedId }
        .thenBy { it.isCompletedByMe }
        .thenByDescending { it.isWithinProximity }
        .thenByDescending { it.mission.category in favCats }
        .thenBy { it.distanceMeters }
    )

    val activeAccepted = workflow.activeAcceptedId?.let { id ->
      missionsWithDist.firstOrNull { it.mission.id == id }
    }
    val queuedPromptMission = workflow.queuedPromptId?.let { id ->
      missionsWithDist.firstOrNull { it.mission.id == id }
    }

    // Trigger proximity (120m) and close-spot directional guidance (<= 35m) alerts
    if (hasValidRef && local.hasPermission) {
      missionsWithDist.forEach { item ->
        if (!item.isCompletedByMe && item.distanceMeters <= GeoUtils.CLOSE_GUIDANCE_RADIUS_METERS) {
          val isActive = item.mission.id == workflow.activeAcceptedId
          if ((isActive || item.isWithinProximity) && notifiedCloseSpotIds.add(item.mission.id)) {
            val arrow = GeoUtils.compassArrowForBearing(item.bearingDegrees)
            val dir = GeoUtils.cardinalDirectionEs(item.bearingDegrees)
            pushNotification(
              id = "close_${item.mission.id}",
              title = "¡Punto final a ${GeoUtils.formatDistance(item.distanceMeters)}!",
              message = "Sigue $arrow rumbo al $dir para completar '${item.mission.title}'.",
              type = "proximity",
              isProximity = true,
            )
          }
        } else if (item.isWithinProximity && !item.isCompletedByMe && notifiedProximityMissionIds.add(item.mission.id)) {
          pushNotification(
            id = "prox_${item.mission.id}",
            title = "¡Misión desbloqueada cerca de ti!",
            message = "Estás a ${GeoUtils.formatDistance(item.distanceMeters)} de '${item.mission.title}'. Ya puedes completarla.",
            type = "proximity",
            isProximity = true,
          )
        }
      }
    }

    val unreadCount = gate.notifications.count { !it.isRead }
    val maxSuggestDist = effectiveSettings.searchRadiusMeters.toFloat().coerceAtMost(1200f)
    val popupSuggestions = if (effectiveSettings.notificationsEnabled && !shouldShowOnboarding) {
      missionsWithDist
        .filter { !it.isCompletedByMe && it.distanceMeters <= maxSuggestDist && it.mission.id !in local.dismissedPopupIds }
        .take(3)
        .map { item ->
          NearbyMissionPopup(
            missionId = item.mission.id,
            title = item.mission.title,
            category = item.mission.category,
            distanceMeters = item.distanceMeters,
            xpReward = item.mission.xpReward,
          )
        }
    } else {
      emptyList()
    }

    RadarQuestUiState(
      myProfile = core.profile,
      settings = effectiveSettings,
      showOnboardingQuest = shouldShowOnboarding,
      currentLocation = local.location,
      hasLocationPermission = local.hasPermission,
      hasNotificationPermission = local.hasNotifPermission,
      isBroadcastingRadar = local.broadcast,
      nearbyPlayers = nearby,
      leaderboardPlayers = core.onlinePlayers.sortedByDescending { it.xp },
      missions = missionsWithDist,
      activeAcceptedMissionId = workflow.activeAcceptedId,
      activeAcceptedMission = activeAccepted,
      waitingMissionQueue = workflow.waitingQueue,
      rejectedMissionIds = workflow.rejectedIds,
      queuedPromptMission = queuedPromptMission,
      myCompletions = core.myCompletions.sortedByDescending { it.createdAt ?: Timestamp(0, 0) },
      publicCompletions = social.publicCompletions.sortedByDescending { it.createdAt ?: Timestamp(0, 0) },
      openChallenges = social.openChallenges
        .filter { it.creatorId != currentUserId }
        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) },
      myChallenges = social.myChallenges.sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) },
      interactions = social.interactions.sortedByDescending { it.createdAt ?: Timestamp(0, 0) },
      notifications = gate.notifications,
      unreadNotificationsCount = unreadCount,
      nearbyMissionPopups = popupSuggestions,
      snackbarMessage = local.snackbar,
      isRefreshing = local.isRefreshing,
      isSubmitting = local.isSubmitting,
      isLoading = false,
      errorMessage = gate.errorMessage,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000L),
    initialValue = RadarQuestUiState(),
  )

  init {
    ensureInitialProfileAndSettings()
  }

  private fun ensureInitialProfileAndSettings() {
    if (hasInitializedProfile) return
    hasInitializedProfile = true
    viewModelScope.launch {
      val existing = repository.observeMyProfile(currentUserId)
      existing.catch { }.collect { prof ->
        if (prof == null) {
          val cleanName = defaultDisplayName.ifBlank { "Explorador" }.take(24)
          val defaultHandle = cleanName.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifBlank { "explorador" }.take(18)
          repository.ensureOrUpdatePlayerProfile(
            username = defaultHandle,
            callsign = cleanName,
            avatarIcon = "explore",
            avatarColorHex = "#00BFA5",
            avatarPhotoBase64 = "",
            bannerPhotoBase64 = "",
            bannerPreset = "topo",
            statusMessage = "Listo para misiones",
            seekingHelp = false,
            isOnline = _isBroadcastingRadar.value,
            latitude = _currentLocation.value?.latitude ?: 0.0,
            longitude = _currentLocation.value?.longitude ?: 0.0,
          )
        }
        return@collect
      }
    }
  }

  fun acceptMission(missionId: String) {
    val target = uiState.value.missions.firstOrNull { it.mission.id == missionId }
    _activeAcceptedMissionId.value = missionId
    _waitingMissionQueue.value = _waitingMissionQueue.value - missionId
    if (_queuedPromptMissionId.value == missionId) {
      _queuedPromptMissionId.value = null
    }
    _snackbarMessage.value = if (target != null) {
      "Misión iniciada: ${target.mission.title} (${GeoUtils.formatDistance(target.distanceMeters)})"
    } else {
      "Misión iniciada"
    }
  }

  fun waitMission(missionId: String) {
    if (_activeAcceptedMissionId.value == missionId) {
      _activeAcceptedMissionId.value = null
    }
    val curQueue = _waitingMissionQueue.value
    if (missionId !in curQueue) {
      _waitingMissionQueue.value = curQueue + missionId
    }
    if (_queuedPromptMissionId.value == missionId) {
      _queuedPromptMissionId.value = null
    }
    val pos = _waitingMissionQueue.value.indexOf(missionId) + 1
    _snackbarMessage.value = "Misión en espera (cola #$pos)"
  }

  fun rejectMission(missionId: String) {
    _rejectedMissionIds.value = _rejectedMissionIds.value + missionId
    if (_activeAcceptedMissionId.value == missionId) {
      _activeAcceptedMissionId.value = null
    }
    _waitingMissionQueue.value = _waitingMissionQueue.value - missionId
    if (_queuedPromptMissionId.value == missionId) {
      _queuedPromptMissionId.value = null
    }
    _snackbarMessage.value = "Misión rechazada. Reaparecerá en otro punto aleatorio."
    scheduleRejectedRespawn()
  }

  fun cancelActiveMission() {
    _activeAcceptedMissionId.value = null
    _snackbarMessage.value = "Guía de misión pausada"
  }

  fun dismissQueuedPromptKeepWaiting() {
    val id = _queuedPromptMissionId.value
    if (id != null && id !in _waitingMissionQueue.value) {
      _waitingMissionQueue.value = listOf(id) + _waitingMissionQueue.value
    }
    _queuedPromptMissionId.value = null
  }

  fun respawnRandomMissions() {
    respawnJob?.cancel()
    val loc = _currentLocation.value
    val prof = uiState.value.myProfile
    val lat = loc?.latitude ?: prof?.latitude ?: 0.0
    val lon = loc?.longitude ?: prof?.longitude ?: 0.0
    if (lat != 0.0 || lon != 0.0) {
      _missionAnchorCoords.value = lat to lon
    }
    _rejectedMissionIds.value = emptySet()
    notifiedProximityMissionIds.clear()
    notifiedCloseSpotIds.clear()
    _missionSeed.value = System.currentTimeMillis()
    _snackbarMessage.value = "Misiones reubicadas de forma aleatoria en el mapa"
  }

  private fun scheduleRejectedRespawn() {
    respawnJob?.cancel()
    respawnJob = viewModelScope.launch {
      delay(25_000L)
      if (_rejectedMissionIds.value.isNotEmpty()) {
        val loc = _currentLocation.value
        val prof = uiState.value.myProfile
        val lat = loc?.latitude ?: prof?.latitude ?: 0.0
        val lon = loc?.longitude ?: prof?.longitude ?: 0.0
        if (lat != 0.0 || lon != 0.0) {
          _missionAnchorCoords.value = lat to lon
        }
        _rejectedMissionIds.value = emptySet()
        _missionSeed.value = System.currentTimeMillis()
        pushNotification(
          id = "respawn_${_missionSeed.value}",
          title = "Nuevas misiones aleatorias",
          message = "Aparecieron nuevas ubicaciones aleatorias de misiones cerca de tu radar.",
          type = "proximity",
          isProximity = false,
        )
      }
    }
  }

  private fun handlePostCompletionQueue(completedMissionId: String) {
    if (_activeAcceptedMissionId.value == completedMissionId) {
      _activeAcceptedMissionId.value = null
    }
    val remainingQueue = _waitingMissionQueue.value.filter { it != completedMissionId }
    val validIds = uiState.value.missions.map { it.mission.id }.toSet()
    val nextId = remainingQueue.firstOrNull { it in validIds && it !in _rejectedMissionIds.value }
    if (nextId != null) {
      _waitingMissionQueue.value = remainingQueue.filterNot { it == nextId }
      _queuedPromptMissionId.value = nextId
    } else {
      _waitingMissionQueue.value = remainingQueue
    }
  }

  fun completeOnboardingQuest(
    playStyle: String,
    favoriteCategories: List<String>,
    privacyComfort: String,
    missionPace: String,
    paletteKey: String,
  ) {
    val current = uiState.value.settings
    val targetRadius = when (privacyComfort) {
      "close_only" -> 500L
      else -> current.searchRadiusMeters
    }
    val broadcastActive = privacyComfort != "ghost"
    _isBroadcastingRadar.value = broadcastActive
    _forceShowOnboarding.value = false

    val updated = current.copy(
      userId = currentUserId,
      paletteKey = paletteKey,
      searchRadiusMeters = targetRadius,
      onboardingCompleted = true,
      playStyle = playStyle,
      favoriteCategories = favoriteCategories.distinct().take(6),
      privacyComfort = privacyComfort,
      missionPace = missionPace,
    )
    viewModelScope.launch {
      localPrefs.setOnboardingCompleted(currentUserId, true)
      localPrefs.save(
        ThemePrefsSnapshot(
          themeMode = updated.themeMode,
          paletteKey = updated.paletteKey,
          radarStyle = updated.radarStyle,
          cornerStyle = updated.cornerStyle,
          searchRadiusMeters = updated.searchRadiusMeters,
          animationTier = updated.animationTier,
          notificationsEnabled = updated.notificationsEnabled,
          playStyle = updated.playStyle,
          favoriteCategories = updated.favoriteCategories,
          privacyComfort = updated.privacyComfort,
          missionPace = updated.missionPace,
          gestureNavEnabled = updated.gestureNavEnabled,
        )
      )
      repository.saveUserSettings(updated)
      val loc = _currentLocation.value
      val prof = uiState.value.myProfile
      repository.updateLiveLocation(
        latitude = loc?.latitude ?: prof?.latitude ?: 0.0,
        longitude = loc?.longitude ?: prof?.longitude ?: 0.0,
        isOnline = broadcastActive,
      )
      _snackbarMessage.value = "¡Quest inicial completado! Tus gustos privados están guardados"
    }
  }

  fun reopenOnboardingQuest() {
    _forceShowOnboarding.value = true
  }

  fun setLocationPermission(granted: Boolean) {
    _hasLocationPermission.value = granted
  }

  fun setNotificationPermission(granted: Boolean) {
    _hasNotificationPermission.value = granted
  }

  fun markAllNotificationsRead() {
    _notifications.value = _notifications.value.map { it.copy(isRead = true) }
  }

  fun dismissNearbyMissionPopup(missionId: String) {
    _dismissedPopupMissionIds.value = _dismissedPopupMissionIds.value + missionId
  }

  fun refreshAll() {
    if (_isRefreshing.value) return
    viewModelScope.launch {
      _isRefreshing.value = true
      _errorMessage.value = null
      _dismissedPopupMissionIds.value = emptySet()
      val loc = _currentLocation.value
      val prof = uiState.value.myProfile
      val lat = loc?.latitude ?: prof?.latitude ?: 0.0
      val lon = loc?.longitude ?: prof?.longitude ?: 0.0
      if (lat != 0.0 || lon != 0.0) {
        _missionAnchorCoords.value = lat to lon
      }
      _rejectedMissionIds.value = emptySet()
      _missionSeed.value = System.currentTimeMillis()
      _refreshTrigger.value += 1
      val fetchResult = repository.fetchActiveMissionsOnce()
      fetchResult.onSuccess { fetched ->
        if (fetched.isNotEmpty()) {
          _refreshedMissions.value = fetched
        }
      }
      if (uiState.value.myProfile == null) {
        hasInitializedProfile = false
        ensureInitialProfileAndSettings()
      }
      delay(450L)
      _isRefreshing.value = false
      _snackbarMessage.value = "Radar y misiones aleatorias actualizados"
    }
  }

  fun onLocationUpdated(point: LiveLocationPoint) {
    val previous = _currentLocation.value
    _currentLocation.value = point
    val movedEnough = previous == null ||
      GeoUtils.distanceMeters(previous.latitude, previous.longitude, point.latitude, point.longitude) > 8f
    if (movedEnough) {
      viewModelScope.launch {
        repository.updateLiveLocation(point.latitude, point.longitude, _isBroadcastingRadar.value)
      }
    }
  }

  fun toggleBroadcast(enabled: Boolean) {
    _isBroadcastingRadar.value = enabled
    viewModelScope.launch {
      val loc = _currentLocation.value
      val lat = loc?.latitude ?: uiState.value.myProfile?.latitude ?: 0.0
      val lon = loc?.longitude ?: uiState.value.myProfile?.longitude ?: 0.0
      repository.updateLiveLocation(lat, lon, enabled)
      _snackbarMessage.value = if (enabled) "Señal de radar activa" else "Modo oculto activado"
    }
  }

  fun saveThemeCustomization(
    themeMode: String = uiState.value.settings.themeMode,
    paletteKey: String = uiState.value.settings.paletteKey,
    radarStyle: String = uiState.value.settings.radarStyle,
    cornerStyle: String = uiState.value.settings.cornerStyle,
    searchRadiusMeters: Long = uiState.value.settings.searchRadiusMeters,
    animationTier: String = uiState.value.settings.animationTier,
    notificationsEnabled: Boolean = uiState.value.settings.notificationsEnabled,
    gestureNavEnabled: Boolean = uiState.value.settings.gestureNavEnabled,
  ) {
    val cur = uiState.value.settings
    val updated = cur.copy(
      userId = currentUserId,
      themeMode = themeMode,
      paletteKey = paletteKey,
      radarStyle = radarStyle,
      cornerStyle = cornerStyle,
      searchRadiusMeters = searchRadiusMeters,
      animationTier = animationTier,
      notificationsEnabled = notificationsEnabled,
      gestureNavEnabled = gestureNavEnabled,
    )
    viewModelScope.launch {
      localPrefs.save(
        ThemePrefsSnapshot(
          themeMode = themeMode,
          paletteKey = paletteKey,
          radarStyle = radarStyle,
          cornerStyle = cornerStyle,
          searchRadiusMeters = searchRadiusMeters,
          animationTier = animationTier,
          notificationsEnabled = notificationsEnabled,
          playStyle = cur.playStyle,
          favoriteCategories = cur.favoriteCategories,
          privacyComfort = cur.privacyComfort,
          missionPace = cur.missionPace,
          gestureNavEnabled = gestureNavEnabled,
        )
      )
      repository.saveUserSettings(updated)
    }
  }

  fun updatePlayerProfile(
    username: String = uiState.value.myProfile?.username ?: "",
    callsign: String,
    avatarIcon: String,
    avatarColorHex: String,
    avatarPhotoBase64: String = uiState.value.myProfile?.avatarPhotoBase64 ?: "",
    bannerPhotoBase64: String = uiState.value.myProfile?.bannerPhotoBase64 ?: "",
    bannerPreset: String = uiState.value.myProfile?.bannerPreset ?: "topo",
    statusMessage: String,
    seekingHelp: Boolean,
  ) {
    viewModelScope.launch {
      val loc = _currentLocation.value
      val prof = uiState.value.myProfile
      val res = repository.ensureOrUpdatePlayerProfile(
        username = username,
        callsign = callsign,
        avatarIcon = avatarIcon,
        avatarColorHex = avatarColorHex,
        avatarPhotoBase64 = avatarPhotoBase64,
        bannerPhotoBase64 = bannerPhotoBase64,
        bannerPreset = bannerPreset,
        statusMessage = statusMessage,
        seekingHelp = seekingHelp,
        isOnline = _isBroadcastingRadar.value,
        latitude = loc?.latitude ?: prof?.latitude ?: 0.0,
        longitude = loc?.longitude ?: prof?.longitude ?: 0.0,
      )
      _snackbarMessage.value = if (res.isSuccess) "Perfil guardado" else "Error al guardar perfil"
    }
  }

  fun toggleSeekingHelp() {
    val prof = uiState.value.myProfile ?: return
    updatePlayerProfile(
      username = prof.username,
      callsign = prof.callsign,
      avatarIcon = prof.avatarIcon,
      avatarColorHex = prof.avatarColorHex,
      avatarPhotoBase64 = prof.avatarPhotoBase64,
      bannerPhotoBase64 = prof.bannerPhotoBase64,
      bannerPreset = prof.bannerPreset,
      statusMessage = if (!prof.seekingHelp) "¡Necesito ayuda con una misión!" else "Explorando cerca",
      seekingHelp = !prof.seekingHelp,
    )
  }

  fun sendHelpToPlayer(target: PlayerProfile) {
    val myName = uiState.value.myProfile?.callsign ?: defaultDisplayName
    viewModelScope.launch {
      val res = repository.sendInteraction(
        fromUserName = myName,
        toUserId = target.userId,
        toUserName = target.callsign,
        type = "help",
        message = "¡Te envío +30 XP de energía y ayuda en tu misión!",
        xpBonus = 30L,
      )
      if (res.isSuccess) {
        repository.awardPlayerStats(addXp = 30L, addHelpsGiven = 1L)
        _snackbarMessage.value = "Ayuda enviada a ${target.callsign} (+30 XP)"
      } else {
        _snackbarMessage.value = "No se pudo enviar la ayuda"
      }
    }
  }

  fun sendPlayPulse(target: PlayerProfile) {
    val myName = uiState.value.myProfile?.callsign ?: defaultDisplayName
    viewModelScope.launch {
      val res = repository.sendInteraction(
        fromUserName = myName,
        toUserId = target.userId,
        toUserName = target.callsign,
        type = "pulse",
        message = "¡Pulso de radar! ¿Jugamos una misión cerca?",
        xpBonus = 15L,
      )
      if (res.isSuccess) {
        repository.awardPlayerStats(addXp = 15L)
        _snackbarMessage.value = "Pulso enviado a ${target.callsign} (+15 XP)"
      }
    }
  }

  fun cheerCompletion(completion: MissionCompletion) {
    if (completion.userId == currentUserId || completion.userId.isBlank()) return
    val myName = uiState.value.myProfile?.callsign ?: defaultDisplayName
    viewModelScope.launch {
      val res = repository.sendInteraction(
        fromUserName = myName,
        toUserId = completion.userId,
        toUserName = completion.userName,
        type = "cheer",
        message = "¡Genial logro en '${completion.missionTitle}'!",
        xpBonus = 15L,
      )
      if (res.isSuccess) {
        repository.awardPlayerStats(addXp = 15L)
        _snackbarMessage.value = "Aplauso enviado a ${completion.userName} (+15 XP)"
      }
    }
  }

  fun createChallenge(
    mode: String,
    title: String,
    missionId: String,
    xpStake: Long,
  ) {
    val myName = uiState.value.myProfile?.callsign ?: defaultDisplayName
    viewModelScope.launch {
      _isSubmitting.value = true
      val res = repository.createChallenge(
        creatorName = myName,
        mode = mode,
        title = title,
        missionId = missionId,
        xpStake = xpStake,
      )
      _isSubmitting.value = false
      _snackbarMessage.value = if (res.isSuccess) {
        if (mode == "duel") "Duelo publicado para jugadores cercanos" else "Sala cooperativa creada"
      } else {
        "No se pudo crear el reto"
      }
    }
  }

  fun joinChallenge(challenge: Challenge) {
    val myName = uiState.value.myProfile?.callsign ?: defaultDisplayName
    viewModelScope.launch {
      val res = repository.joinChallenge(challenge, myName)
      _snackbarMessage.value = if (res.isSuccess) "¡Te uniste a ${challenge.title}!" else "No se pudo unir"
    }
  }

  fun completeChallengeStep(challenge: Challenge) {
    viewModelScope.launch {
      val currentProg = if (challenge.creatorId == currentUserId) challenge.creatorProgress else challenge.opponentProgress
      val nextProg = (currentProg + 50L).coerceAtMost(100L)
      val res = repository.updateChallengeProgress(challenge, nextProg)
      if (res.isSuccess && nextProg >= 100L) {
        repository.awardPlayerStats(
          addXp = challenge.xpStake,
          addDuelsWon = if (challenge.mode == "duel") 1L else 0L,
        )
        _snackbarMessage.value = "¡Reto completado! +${challenge.xpStake} XP"
      } else if (res.isSuccess) {
        _snackbarMessage.value = "Progreso actualizado (${nextProg}%)"
      }
    }
  }

  fun createCustomMission(
    title: String,
    description: String,
    category: String,
    xpReward: Long,
    missionType: String = "photo",
    iconKey: String = "explore",
  ) {
    val myName = uiState.value.myProfile?.callsign ?: defaultDisplayName
    val loc = _currentLocation.value
    val prof = uiState.value.myProfile
    val baseLat = loc?.latitude ?: prof?.latitude ?: 19.4326
    val baseLon = loc?.longitude ?: prof?.longitude ?: -99.1332
    val (lat, lon) = GeoUtils.destinationPoint(
      baseLat = baseLat,
      baseLon = baseLon,
      distanceMeters = (45..260).random().toDouble(),
      bearingDegrees = (0..359).random().toDouble(),
    )
    viewModelScope.launch {
      _isSubmitting.value = true
      val res = repository.createMission(
        creatorName = myName,
        title = title,
        description = description,
        category = category,
        xpReward = xpReward,
        latitude = lat,
        longitude = lon,
        missionType = missionType,
        iconKey = iconKey,
      )
      _isSubmitting.value = false
      _snackbarMessage.value = if (res.isSuccess) "Misión creada en punto aleatorio del radar" else "Error al crear misión"
    }
  }

  fun confirmMissionWithPhoto(
    mission: Mission,
    caption: String,
    photoThumbnailBase64: String,
    isPublic: Boolean,
  ) {
    val myName = uiState.value.myProfile?.callsign ?: defaultDisplayName
    val loc = _currentLocation.value
    val lat = loc?.latitude ?: mission.latitude
    val lon = loc?.longitude ?: mission.longitude
    viewModelScope.launch {
      _isSubmitting.value = true
      val res = repository.submitMissionCompletion(
        userName = myName,
        missionId = mission.id,
        missionTitle = mission.title,
        category = mission.category,
        xpEarned = mission.xpReward,
        caption = caption.ifBlank { "Misión verificada" },
        photoThumbnailBase64 = photoThumbnailBase64,
        latitude = lat,
        longitude = lon,
        isPublic = isPublic,
      )
      if (res.isSuccess) {
        repository.awardPlayerStats(addXp = mission.xpReward, addMissionsCompleted = 1L)
        uiState.value.myChallenges
          .filter { it.status == "active" && it.missionId == mission.id }
          .forEach { challenge ->
            repository.updateChallengeProgress(challenge, 100L)
          }
        handlePostCompletionQueue(mission.id)
        _snackbarMessage.value = "¡Misión completada! +${mission.xpReward} XP"
      } else {
        _snackbarMessage.value = "Error al guardar la confirmación"
      }
      _isSubmitting.value = false
    }
  }

  fun completeMiniGameMission(
    mission: Mission,
    caption: String,
    isPublic: Boolean,
  ) {
    val badgeThumb = PhotoUtils.createMiniGameBadgeBase64(mission.title, mission.missionType)
    confirmMissionWithPhoto(
      mission = mission,
      caption = caption,
      photoThumbnailBase64 = badgeThumb,
      isPublic = isPublic,
    )
  }

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  private data class CoreBundle(
    val profile: PlayerProfile?,
    val remoteSettings: UserSettings?,
    val onlinePlayers: List<PlayerProfile>,
    val missions: List<Mission>,
    val myCompletions: List<MissionCompletion>,
  )

  private data class SocialBundle(
    val publicCompletions: List<MissionCompletion>,
    val openChallenges: List<Challenge>,
    val myChallenges: List<Challenge>,
    val interactions: List<PlayerInteraction>,
    val localTheme: ThemePrefsSnapshot,
  )

  private data class LocalControlBundle(
    val location: LiveLocationPoint?,
    val hasPermission: Boolean,
    val hasNotifPermission: Boolean,
    val broadcast: Boolean,
    val snackbar: String?,
    val isSubmitting: Boolean,
    val dismissedPopupIds: Set<String>,
    val isRefreshing: Boolean,
    val refreshedMissions: List<Mission>,
  )

  private data class MissionWorkflowBundle(
    val missionSeed: Long,
    val anchorCoords: Pair<Double, Double>?,
    val activeAcceptedId: String?,
    val waitingQueue: List<String>,
    val rejectedIds: Set<String>,
    val queuedPromptId: String?,
  )

  private data class OnboardingGateBundle(
    val resolved: Boolean,
    val localDone: Boolean,
    val forceShow: Boolean,
    val notifications: List<AppNotificationItem>,
    val errorMessage: String?,
  )

  private companion object {
    const val TAG = "RadarQuestVM"
  }
}
