package com.example.data.repository

import com.example.data.model.Challenge
import com.example.data.model.Mission
import com.example.data.model.MissionCompletion
import com.example.data.model.PlayerInteraction
import com.example.data.model.PlayerProfile
import com.example.data.model.UserSettings
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Dual-mode repository:
 * - When [db] and [auth] are non-null, reads and writes directly against Firebase Firestore.
 * - When [db] or [auth] is null (e.g. google-services.json absent or Google Services unavailable),
 *   transparently operates in local exploration mode backed by in-memory reactive flows without
 *   requiring authentication or throwing Firebase exceptions.
 */
class RadarQuestRepository(
  private val db: FirebaseFirestore? = null,
  private val auth: FirebaseAuth? = null,
) {

  val isCloudAvailable: Boolean
    get() = db != null && auth != null

  private val localSettingsFlow = MutableStateFlow<UserSettings?>(null)
  private val localProfileFlow = MutableStateFlow<PlayerProfile?>(null)
  private val localMissionsFlow = MutableStateFlow<List<Mission>>(emptyList())
  private val localMyCompletionsFlow = MutableStateFlow<List<MissionCompletion>>(emptyList())
  private val localPublicCompletionsFlow = MutableStateFlow<List<MissionCompletion>>(emptyList())
  private val localOpenChallengesFlow = MutableStateFlow<List<Challenge>>(emptyList())
  private val localMyChallengesFlow = MutableStateFlow<List<Challenge>>(emptyList())
  private val localInteractionsFlow = MutableStateFlow<List<PlayerInteraction>>(emptyList())

  private fun requireUserId(): String =
    auth?.currentUser?.uid
      ?: throw IllegalStateException("El usuario debe iniciar sesión con Google.")

  private fun <T> Flow<T>.logFirestoreErrors(op: OperationType, path: String, uid: String): Flow<T> =
    catch { error ->
      val firestoreError =
        generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val exceptionToLog = firestoreError ?: (error as? Exception)
      if (exceptionToLog != null && auth?.currentUser?.uid == uid) {
        handleFirestoreError(exceptionToLog, op, path)
      }
      throw error
    }

  // --- User Settings ---
  fun observeUserSettings(uid: String): Flow<UserSettings?> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localSettingsFlow
    }
    val path = "users/$uid"
    return firestore.collection("users").document(uid)
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .logFirestoreErrors(OperationType.GET, path, uid)
  }

  suspend fun saveUserSettings(settings: UserSettings): Result<Unit> {
    val safePlayStyle = if (settings.playStyle in listOf("helper", "competitor", "explorer", "balanced")) {
      settings.playStyle
    } else {
      "balanced"
    }
    val safeCategories = settings.favoriteCategories
      .filter { it in VALID_CATEGORIES }
      .distinct()
      .take(6)
    val safePrivacy = if (settings.privacyComfort in listOf("radius", "close_only", "ghost")) {
      settings.privacyComfort
    } else {
      "radius"
    }
    val safePace = if (settings.missionPace in listOf("casual", "daily", "intense")) {
      settings.missionPace
    } else {
      "casual"
    }
    val safeAnimTier = if (settings.animationTier in listOf("low", "medium", "high")) {
      settings.animationTier
    } else {
      "high"
    }

    val firestore = db
    if (firestore == null || auth == null) {
      localSettingsFlow.value = settings.copy(
        userId = settings.userId.ifBlank { LOCAL_USER_ID },
        playStyle = safePlayStyle,
        favoriteCategories = safeCategories,
        privacyComfort = safePrivacy,
        missionPace = safePace,
        animationTier = safeAnimTier,
      )
      return Result.success(Unit)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("users").document(uid)
      val existing = docRef.get().await()
      if (existing.exists()) {
        val updates = mapOf(
          "themeMode" to settings.themeMode,
          "paletteKey" to settings.paletteKey,
          "radarStyle" to settings.radarStyle,
          "cornerStyle" to settings.cornerStyle,
          "searchRadiusMeters" to settings.searchRadiusMeters,
          "animationTier" to safeAnimTier,
          "notificationsEnabled" to settings.notificationsEnabled,
          "onboardingCompleted" to settings.onboardingCompleted,
          "playStyle" to safePlayStyle,
          "favoriteCategories" to safeCategories,
          "privacyComfort" to safePrivacy,
          "missionPace" to safePace,
          "gestureNavEnabled" to settings.gestureNavEnabled,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(updates).await()
      } else {
        val payload = mapOf(
          "userId" to uid,
          "themeMode" to settings.themeMode,
          "paletteKey" to settings.paletteKey,
          "radarStyle" to settings.radarStyle,
          "cornerStyle" to settings.cornerStyle,
          "searchRadiusMeters" to settings.searchRadiusMeters,
          "animationTier" to safeAnimTier,
          "notificationsEnabled" to settings.notificationsEnabled,
          "onboardingCompleted" to settings.onboardingCompleted,
          "playStyle" to safePlayStyle,
          "favoriteCategories" to safeCategories,
          "privacyComfort" to safePrivacy,
          "missionPace" to safePace,
          "gestureNavEnabled" to settings.gestureNavEnabled,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      }
      Unit
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }
  }

  // --- Players & Real-Time Location ---
  fun observeMyProfile(uid: String): Flow<PlayerProfile?> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localProfileFlow
    }
    val path = "players/$uid"
    return firestore.collection("players").document(uid)
      .snapshots()
      .map { snap ->
        if (snap.exists()) {
          snap.toObject(PlayerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }
      .logFirestoreErrors(OperationType.GET, path, uid)
  }

  fun observeOnlinePlayers(uid: String): Flow<List<PlayerProfile>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localProfileFlow.map { prof -> listOfNotNull(prof) }
    }
    val path = "players"
    return firestore.collection("players")
      .whereEqualTo("isOnline", true)
      .snapshots()
      .map { snap ->
        snap.toObjects(PlayerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  suspend fun ensureOrUpdatePlayerProfile(
    username: String = "",
    callsign: String,
    avatarIcon: String,
    avatarColorHex: String,
    avatarPhotoBase64: String = "",
    bannerPhotoBase64: String = "",
    bannerPreset: String = "topo",
    statusMessage: String,
    seekingHelp: Boolean,
    isOnline: Boolean,
    latitude: Double,
    longitude: Double,
  ): Result<Unit> {
    val safeUsername = username.trim().replace(Regex("[^a-zA-Z0-9_]"), "").take(24)
    val safeCallsign = callsign.trim().ifBlank { "Explorador" }.take(32).padEnd(2, 'X')
    val safeStatus = statusMessage.trim().take(140)
    val safePreset = if (bannerPreset in listOf("topo", "sunset", "cosmos", "emerald", "custom")) bannerPreset else "topo"

    val firestore = db
    if (firestore == null || auth == null) {
      val current = localProfileFlow.value
      localProfileFlow.value = PlayerProfile(
        id = current?.id ?: LOCAL_USER_ID,
        userId = current?.userId ?: LOCAL_USER_ID,
        username = safeUsername,
        callsign = safeCallsign,
        avatarIcon = avatarIcon.take(32),
        avatarColorHex = avatarColorHex.take(12),
        avatarPhotoBase64 = avatarPhotoBase64.take(80000),
        bannerPhotoBase64 = bannerPhotoBase64.take(80000),
        bannerPreset = safePreset,
        statusMessage = safeStatus,
        seekingHelp = seekingHelp,
        isOnline = isOnline,
        latitude = latitude.coerceIn(-90.0, 90.0),
        longitude = longitude.coerceIn(-180.0, 180.0),
        xp = current?.xp ?: 0L,
        level = current?.level ?: 1L,
        missionsCompleted = current?.missionsCompleted ?: 0L,
        helpsGiven = current?.helpsGiven ?: 0L,
        duelsWon = current?.duelsWon ?: 0L,
      )
      return Result.success(Unit)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("players").document(uid)
      val existing = docRef.get().await()
      if (existing.exists()) {
        val updates = mapOf(
          "username" to safeUsername,
          "callsign" to safeCallsign,
          "avatarIcon" to avatarIcon.take(32),
          "avatarColorHex" to avatarColorHex.take(12),
          "avatarPhotoBase64" to avatarPhotoBase64.take(80000),
          "bannerPhotoBase64" to bannerPhotoBase64.take(80000),
          "bannerPreset" to safePreset,
          "statusMessage" to safeStatus,
          "seekingHelp" to seekingHelp,
          "isOnline" to isOnline,
          "latitude" to latitude.coerceIn(-90.0, 90.0),
          "longitude" to longitude.coerceIn(-180.0, 180.0),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(updates).await()
      } else {
        val payload = mapOf(
          "userId" to uid,
          "username" to safeUsername,
          "callsign" to safeCallsign,
          "avatarIcon" to avatarIcon.take(32),
          "avatarColorHex" to avatarColorHex.take(12),
          "avatarPhotoBase64" to avatarPhotoBase64.take(80000),
          "bannerPhotoBase64" to bannerPhotoBase64.take(80000),
          "bannerPreset" to safePreset,
          "statusMessage" to safeStatus,
          "seekingHelp" to seekingHelp,
          "isOnline" to isOnline,
          "latitude" to latitude.coerceIn(-90.0, 90.0),
          "longitude" to longitude.coerceIn(-180.0, 180.0),
          "xp" to 0L,
          "level" to 1L,
          "missionsCompleted" to 0L,
          "helpsGiven" to 0L,
          "duelsWon" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      }
      Unit
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.WRITE, "players/${auth.currentUser?.uid}")
    }
  }

  suspend fun updateLiveLocation(latitude: Double, longitude: Double, isOnline: Boolean): Result<Unit> {
    val firestore = db
    if (firestore == null || auth == null) {
      val current = localProfileFlow.value ?: PlayerProfile(
        id = LOCAL_USER_ID,
        userId = LOCAL_USER_ID,
        callsign = "Explorador",
      )
      localProfileFlow.value = current.copy(
        latitude = latitude.coerceIn(-90.0, 90.0),
        longitude = longitude.coerceIn(-180.0, 180.0),
        isOnline = isOnline,
      )
      return Result.success(Unit)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("players").document(uid)
      val existing = docRef.get().await()
      if (existing.exists()) {
        docRef.update(
          mapOf(
            "latitude" to latitude.coerceIn(-90.0, 90.0),
            "longitude" to longitude.coerceIn(-180.0, 180.0),
            "isOnline" to isOnline,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Unit
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.UPDATE, "players/${auth.currentUser?.uid}")
    }
  }

  suspend fun awardPlayerStats(
    addXp: Long,
    addMissionsCompleted: Long = 0L,
    addHelpsGiven: Long = 0L,
    addDuelsWon: Long = 0L,
  ): Result<Unit> {
    val firestore = db
    if (firestore == null || auth == null) {
      val current = localProfileFlow.value ?: PlayerProfile(
        id = LOCAL_USER_ID,
        userId = LOCAL_USER_ID,
        callsign = "Explorador",
      )
      val newXp = (current.xp + addXp).coerceAtLeast(0L)
      val newLevel = (newXp / 250L) + 1L
      localProfileFlow.value = current.copy(
        xp = newXp,
        level = newLevel,
        missionsCompleted = current.missionsCompleted + addMissionsCompleted,
        helpsGiven = current.helpsGiven + addHelpsGiven,
        duelsWon = current.duelsWon + addDuelsWon,
      )
      return Result.success(Unit)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("players").document(uid)
      val snap = docRef.get().await()
      val currentXp = snap.getLong("xp") ?: 0L
      val newXp = (currentXp + addXp).coerceAtLeast(0L)
      val newLevel = (newXp / 250L) + 1L
      val currentMissions = snap.getLong("missionsCompleted") ?: 0L
      val currentHelps = snap.getLong("helpsGiven") ?: 0L
      val currentDuels = snap.getLong("duelsWon") ?: 0L
      docRef.update(
        mapOf(
          "xp" to newXp,
          "level" to newLevel,
          "missionsCompleted" to currentMissions + addMissionsCompleted,
          "helpsGiven" to currentHelps + addHelpsGiven,
          "duelsWon" to currentDuels + addDuelsWon,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Unit
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.UPDATE, "players/${auth.currentUser?.uid}")
    }
  }

  // --- Real-Life Missions ---
  fun observeActiveMissions(uid: String): Flow<List<Mission>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localMissionsFlow
    }
    val path = "missions"
    return firestore.collection("missions")
      .whereEqualTo("isActive", true)
      .snapshots()
      .map { snap ->
        snap.toObjects(Mission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  suspend fun fetchActiveMissionsOnce(): Result<List<Mission>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return Result.success(localMissionsFlow.value)
    }
    return runCatching {
      requireUserId()
      val snap = firestore.collection("missions")
        .whereEqualTo("isActive", true)
        .get()
        .await()
      snap.toObjects(Mission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.LIST, "missions")
    }
  }

  suspend fun createMission(
    creatorName: String,
    title: String,
    description: String,
    category: String,
    xpReward: Long,
    latitude: Double,
    longitude: Double,
    missionType: String = "photo",
    iconKey: String = "explore",
    timeLimitSeconds: Int = 0,
    placeLabel: String = "Zona pública segura",
  ): Result<String> {
    val id = "m_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val safeCategory = if (category in VALID_CATEGORIES) category else "explore"
    val safeType = if (missionType in listOf("photo", "wires", "puzzle", "memory")) missionType else "photo"
    val safeIcon = iconKey.trim().ifBlank { "explore" }.take(32)
    val safeTimeLimit = timeLimitSeconds.coerceIn(0, 600)
    val safePlace = placeLabel.trim().ifBlank { "Zona pública segura" }.take(100)

    val firestore = db
    if (firestore == null || auth == null) {
      val localMission = Mission(
        id = id,
        creatorId = LOCAL_USER_ID,
        creatorName = creatorName.trim().ifBlank { "Explorador" }.take(40),
        title = title.trim().take(80),
        description = description.trim().take(280),
        category = safeCategory,
        missionType = safeType,
        iconKey = safeIcon,
        timeLimitSeconds = safeTimeLimit,
        placeLabel = safePlace,
        xpReward = xpReward.coerceIn(25L, 1000L),
        latitude = latitude.coerceIn(-90.0, 90.0),
        longitude = longitude.coerceIn(-180.0, 180.0),
        isActive = true,
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now(),
      )
      localMissionsFlow.value = listOf(localMission) + localMissionsFlow.value
      return Result.success(id)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("missions").document(id)
      val payload = mapOf(
        "creatorId" to uid,
        "creatorName" to creatorName.trim().ifBlank { "Explorador" }.take(40),
        "title" to title.trim().take(80),
        "description" to description.trim().take(280),
        "category" to safeCategory,
        "missionType" to safeType,
        "iconKey" to safeIcon,
        "timeLimitSeconds" to safeTimeLimit,
        "placeLabel" to safePlace,
        "xpReward" to xpReward.coerceIn(25L, 1000L),
        "latitude" to latitude.coerceIn(-90.0, 90.0),
        "longitude" to longitude.coerceIn(-180.0, 180.0),
        "isActive" to true,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      id
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.CREATE, "missions")
    }
  }

  // --- Photo-Confirmed Mission Completions ---
  fun observeMyCompletions(uid: String): Flow<List<MissionCompletion>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localMyCompletionsFlow
    }
    val path = "mission_completions"
    return firestore.collection("mission_completions")
      .whereEqualTo("userId", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(MissionCompletion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  fun observePublicCompletions(uid: String): Flow<List<MissionCompletion>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localPublicCompletionsFlow
    }
    val path = "mission_completions"
    return firestore.collection("mission_completions")
      .whereEqualTo("isPublic", true)
      .snapshots()
      .map { snap ->
        snap.toObjects(MissionCompletion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  suspend fun submitMissionCompletion(
    userName: String,
    missionId: String,
    missionTitle: String,
    category: String,
    xpEarned: Long,
    caption: String,
    photoThumbnailBase64: String,
    latitude: Double,
    longitude: Double,
    isPublic: Boolean = true,
  ): Result<String> {
    val id = "c_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val safeCategory = if (category in VALID_CATEGORIES) category else "explore"

    val firestore = db
    if (firestore == null || auth == null) {
      val completion = MissionCompletion(
        id = id,
        userId = LOCAL_USER_ID,
        userName = userName.trim().ifBlank { "Explorador" }.take(40),
        missionId = missionId.take(128),
        missionTitle = missionTitle.trim().take(80),
        category = safeCategory,
        xpEarned = xpEarned.coerceIn(10L, 2000L),
        caption = caption.trim().take(200),
        photoThumbnail = photoThumbnailBase64.take(80000),
        latitude = latitude.coerceIn(-90.0, 90.0),
        longitude = longitude.coerceIn(-180.0, 180.0),
        isPublic = isPublic,
        createdAt = Timestamp.now(),
      )
      localMyCompletionsFlow.value = listOf(completion) + localMyCompletionsFlow.value
      if (isPublic) {
        localPublicCompletionsFlow.value = listOf(completion) + localPublicCompletionsFlow.value
      }
      return Result.success(id)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("mission_completions").document(id)
      val payload = mapOf(
        "userId" to uid,
        "userName" to userName.trim().ifBlank { "Explorador" }.take(40),
        "missionId" to missionId.take(128),
        "missionTitle" to missionTitle.trim().take(80),
        "category" to safeCategory,
        "xpEarned" to xpEarned.coerceIn(10L, 2000L),
        "caption" to caption.trim().take(200),
        "photoThumbnail" to photoThumbnailBase64.take(80000),
        "latitude" to latitude.coerceIn(-90.0, 90.0),
        "longitude" to longitude.coerceIn(-180.0, 180.0),
        "isPublic" to isPublic,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      id
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.CREATE, "mission_completions")
    }
  }

  // --- Challenges (Compete & Play) ---
  fun observeOpenChallenges(uid: String): Flow<List<Challenge>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localOpenChallengesFlow
    }
    val path = "challenges"
    return firestore.collection("challenges")
      .whereEqualTo("status", "open")
      .snapshots()
      .map { snap ->
        snap.toObjects(Challenge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  fun observeMyChallenges(uid: String): Flow<List<Challenge>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localMyChallengesFlow
    }
    val path = "challenges"
    return firestore.collection("challenges")
      .whereArrayContains("participantIds", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(Challenge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  suspend fun createChallenge(
    creatorName: String,
    mode: String,
    title: String,
    missionId: String,
    xpStake: Long,
  ): Result<String> {
    val id = "ch_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val firestore = db
    if (firestore == null || auth == null) {
      val localChallenge = Challenge(
        id = id,
        creatorId = LOCAL_USER_ID,
        creatorName = creatorName.trim().ifBlank { "Explorador" }.take(40),
        mode = mode,
        title = title.trim().take(80),
        missionId = missionId.take(128),
        xpStake = xpStake.coerceIn(25L, 1000L),
        status = "open",
        participantIds = listOf(LOCAL_USER_ID),
        creatorProgress = 0L,
        opponentProgress = 0L,
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now(),
      )
      localMyChallengesFlow.value = listOf(localChallenge) + localMyChallengesFlow.value
      localOpenChallengesFlow.value = listOf(localChallenge) + localOpenChallengesFlow.value
      return Result.success(id)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("challenges").document(id)
      val payload = mapOf(
        "creatorId" to uid,
        "creatorName" to creatorName.trim().ifBlank { "Explorador" }.take(40),
        "mode" to mode,
        "title" to title.trim().take(80),
        "missionId" to missionId.take(128),
        "xpStake" to xpStake.coerceIn(25L, 1000L),
        "status" to "open",
        "participantIds" to listOf(uid),
        "creatorProgress" to 0L,
        "opponentProgress" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      docRef.set(payload).await()
      id
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.CREATE, "challenges")
    }
  }

  suspend fun joinChallenge(challenge: Challenge, opponentName: String): Result<Unit> {
    val firestore = db
    if (firestore == null || auth == null) {
      val updated = challenge.copy(
        participantIds = listOf(challenge.creatorId, LOCAL_USER_ID),
        opponentId = LOCAL_USER_ID,
        opponentName = opponentName.trim().ifBlank { "Rival" }.take(40),
        status = "active",
        updatedAt = Timestamp.now(),
      )
      localMyChallengesFlow.value = listOf(updated) + localMyChallengesFlow.value.filterNot { it.id == challenge.id }
      localOpenChallengesFlow.value = localOpenChallengesFlow.value.filterNot { it.id == challenge.id }
      return Result.success(Unit)
    }

    return runCatching {
      val uid = requireUserId()
      val docRef = firestore.collection("challenges").document(challenge.id)
      docRef.update(
        mapOf(
          "participantIds" to listOf(challenge.creatorId, uid),
          "opponentId" to uid,
          "opponentName" to opponentName.trim().ifBlank { "Rival" }.take(40),
          "status" to "active",
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Unit
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.UPDATE, "challenges/${challenge.id}")
    }
  }

  suspend fun updateChallengeProgress(
    challenge: Challenge,
    newProgress: Long,
  ): Result<Unit> {
    val firestore = db
    if (firestore == null || auth == null) {
      val clamped = newProgress.coerceIn(0L, 100L)
      val isCreator = LOCAL_USER_ID == challenge.creatorId
      val newCreatorProg = if (isCreator) clamped else challenge.creatorProgress
      val newOpponentProg = if (!isCreator) clamped else challenge.opponentProgress
      val completed =
        if (challenge.mode == "coop") {
          newCreatorProg >= 100L && newOpponentProg >= 100L
        } else {
          clamped >= 100L
        }
      val updated = challenge.copy(
        creatorProgress = newCreatorProg,
        opponentProgress = newOpponentProg,
        status = if (completed) "completed" else "active",
        winnerId = if (completed) LOCAL_USER_ID else challenge.winnerId,
        updatedAt = Timestamp.now(),
      )
      localMyChallengesFlow.value = localMyChallengesFlow.value.map { if (it.id == challenge.id) updated else it }
      return Result.success(Unit)
    }

    return runCatching {
      val uid = requireUserId()
      val clamped = newProgress.coerceIn(0L, 100L)
      val isCreator = uid == challenge.creatorId
      val newCreatorProg = if (isCreator) clamped else challenge.creatorProgress
      val newOpponentProg = if (!isCreator) clamped else challenge.opponentProgress
      val completed =
        if (challenge.mode == "coop") {
          newCreatorProg >= 100L && newOpponentProg >= 100L
        } else {
          clamped >= 100L
        }
      val updates = mutableMapOf<String, Any>(
        "creatorProgress" to newCreatorProg,
        "opponentProgress" to newOpponentProg,
        "status" to if (completed) "completed" else "active",
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (completed) {
        updates["winnerId"] = uid
      }
      firestore.collection("challenges").document(challenge.id).update(updates).await()
      Unit
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.UPDATE, "challenges/${challenge.id}")
    }
  }

  // --- Interactions (Help, Radar Pulse, Cheer) ---
  fun observeMyInteractions(uid: String): Flow<List<PlayerInteraction>> {
    val firestore = db
    if (firestore == null || auth == null) {
      return localInteractionsFlow
    }
    val path = "interactions"
    return firestore.collection("interactions")
      .whereArrayContains("participantIds", uid)
      .snapshots()
      .map { snap ->
        snap.toObjects(PlayerInteraction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .logFirestoreErrors(OperationType.LIST, path, uid)
  }

  suspend fun sendInteraction(
    fromUserName: String,
    toUserId: String,
    toUserName: String,
    type: String,
    message: String,
    xpBonus: Long,
  ): Result<String> {
    val id = "in_" + UUID.randomUUID().toString().replace("-", "").take(16)
    val firestore = db
    if (firestore == null || auth == null) {
      val localInteraction = PlayerInteraction(
        id = id,
        fromUserId = LOCAL_USER_ID,
        fromUserName = fromUserName.trim().ifBlank { "Explorador" }.take(40),
        toUserId = toUserId.take(128),
        toUserName = toUserName.trim().ifBlank { "Jugador" }.take(40),
        type = type,
        message = message.trim().take(160),
        xpBonus = xpBonus.coerceIn(0L, 200L),
        participantIds = listOf(LOCAL_USER_ID, toUserId),
        createdAt = Timestamp.now(),
      )
      localInteractionsFlow.value = listOf(localInteraction) + localInteractionsFlow.value
      return Result.success(id)
    }

    return runCatching {
      val uid = requireUserId()
      require(uid != toUserId) { "No puedes enviarte una interacción a ti mismo." }
      val payload = mapOf(
        "fromUserId" to uid,
        "fromUserName" to fromUserName.trim().ifBlank { "Explorador" }.take(40),
        "toUserId" to toUserId.take(128),
        "toUserName" to toUserName.trim().ifBlank { "Jugador" }.take(40),
        "type" to type,
        "message" to message.trim().take(160),
        "xpBonus" to xpBonus.coerceIn(0L, 200L),
        "participantIds" to listOf(uid, toUserId),
        "createdAt" to FieldValue.serverTimestamp(),
      )
      firestore.collection("interactions").document(id).set(payload).await()
      id
    }.onFailure { e ->
      if (e is CancellationException) throw e
      handleFirestoreError(e, OperationType.CREATE, "interactions")
    }
  }

  companion object {
    const val LOCAL_USER_ID = "local_explorer"
    private val VALID_CATEGORIES = setOf("explore", "eco", "social", "challenge", "fitness", "creative", "puzzle")
  }
}
