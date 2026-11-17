const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function getAuthedDb(uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

function getUnauthDb() {
  return testEnv.unauthenticatedContext().firestore();
}

function validTs() {
  return new Date(Date.now() - 2000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. Unauthenticated access ---
test("Unauthenticated user cannot read or write any collection", async () => {
  const db = getUnauthDb();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("players").where("isOnline", "==", true).get());
  await assertFails(db.collection("missions").where("isActive", "==", true).get());
  await assertFails(db.collection("challenges").where("status", "==", "open").get());
  await assertFails(db.collection("interactions").where("participantIds", "array-contains", ALICE_UID).get());
});

// --- 2. Private UserSettings ---
test("UserSettings: owner can create and update own settings; other user cannot read/write", async () => {
  const aliceDb = getAuthedDb(ALICE_UID);
  const bobDb = getAuthedDb(BOB_UID);
  const ts = validTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      themeMode: "system",
      paletteKey: "emerald",
      radarStyle: "neon",
      cornerStyle: "soft",
      searchRadiusMeters: 2000,
      animationTier: "medium",
      notificationsEnabled: true,
      onboardingCompleted: true,
      playStyle: "helper",
      favoriteCategories: ["eco", "fitness", "creative"],
      privacyComfort: "radius",
      missionPace: "daily",
      gestureNavEnabled: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").where("playStyle", "==", "helper").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ themeMode: "dark", updatedAt: validTs() }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ isAdmin: true, updatedAt: validTs() }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ playStyle: "hacker", updatedAt: validTs() }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ animationTier: "ultra", updatedAt: validTs() }));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).update({ paletteKey: "coral", animationTier: "low", notificationsEnabled: false, missionPace: "intense", gestureNavEnabled: true, updatedAt: validTs() }));
});

// --- 3. Players (Online Radar Presence) ---
test("Players: owner creates profile, online players queryable, offline hidden from others", async () => {
  const aliceDb = getAuthedDb(ALICE_UID);
  const bobDb = getAuthedDb(BOB_UID);
  const ts = validTs();

  await assertSucceeds(
    aliceDb.collection("players").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_radar",
      callsign: "AliceExplorer",
      avatarIcon: "explore",
      avatarColorHex: "#00BFA5",
      avatarPhotoBase64: "sample_avatar_b64",
      bannerPhotoBase64: "sample_banner_b64",
      bannerPreset: "topo",
      statusMessage: "Buscando misiones",
      seekingHelp: false,
      isOnline: true,
      latitude: 37.422,
      longitude: -122.084,
      xp: 100,
      level: 2,
      missionsCompleted: 1,
      helpsGiven: 0,
      duelsWon: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(aliceDb.collection("players").doc(ALICE_UID).update({ username: "bad user!", updatedAt: validTs() }));
  await assertSucceeds(aliceDb.collection("players").doc(ALICE_UID).update({ bannerPreset: "sunset", updatedAt: validTs() }));
  await assertFails(bobDb.collection("players").get());
  await assertSucceeds(bobDb.collection("players").where("isOnline", "==", true).get());
  await assertFails(bobDb.collection("players").doc(ALICE_UID).update({ xp: 9999, updatedAt: validTs() }));

  await assertSucceeds(aliceDb.collection("players").doc(ALICE_UID).update({ isOnline: false, updatedAt: validTs() }));
  await assertFails(bobDb.collection("players").doc(ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("players").doc(ALICE_UID).get());
});

// --- 4. Missions & Photo Completions ---
test("Missions & Completions: create, query active, submit photo proof, enforce ownership", async () => {
  const aliceDb = getAuthedDb(ALICE_UID);
  const bobDb = getAuthedDb(BOB_UID);
  const ts = validTs();

  await assertSucceeds(
    aliceDb.collection("missions").doc("mission_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      title: "Foto en el parque",
      description: "Toma una foto de un árbol cercano",
      category: "creative",
      xpReward: 120,
      latitude: 37.422,
      longitude: -122.084,
      isActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(bobDb.collection("missions").get());
  await assertSucceeds(bobDb.collection("missions").where("isActive", "==", true).get());
  await assertFails(bobDb.collection("missions").doc("mission_1").update({ title: "Hacked", updatedAt: validTs() }));

  await assertSucceeds(
    bobDb.collection("mission_completions").doc("comp_1").set({
      userId: BOB_UID,
      userName: "Bob",
      missionId: "mission_1",
      missionTitle: "Foto en el parque",
      category: "eco",
      xpEarned: 120,
      caption: "Gran roble!",
      photoThumbnail: "base64_fake_jpeg_data_valid_length",
      latitude: 37.4221,
      longitude: -122.0841,
      isPublic: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("mission_completions").where("isPublic", "==", true).get());
  await assertFails(aliceDb.collection("mission_completions").doc("comp_1").delete());
});

// --- 5. Challenges (Join, Progress, Terminal Lock) ---
test("Challenges: open -> join -> active -> completed (terminal state locked)", async () => {
  const aliceDb = getAuthedDb(ALICE_UID);
  const bobDb = getAuthedDb(BOB_UID);
  const charlieDb = getAuthedDb(CHARLIE_UID);
  const ts = validTs();

  await assertSucceeds(
    aliceDb.collection("challenges").doc("chal_1").set({
      creatorId: ALICE_UID,
      creatorName: "Alice",
      mode: "duel",
      title: "Duelo fotográfico rápido",
      missionId: "mission_1",
      xpStake: 150,
      status: "open",
      participantIds: [ALICE_UID],
      creatorProgress: 0,
      opponentProgress: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("challenges").where("status", "==", "open").get());

  await assertSucceeds(
    bobDb.collection("challenges").doc("chal_1").update({
      participantIds: [ALICE_UID, BOB_UID],
      opponentId: BOB_UID,
      opponentName: "Bob",
      status: "active",
      updatedAt: validTs(),
    })
  );

  await assertFails(
    charlieDb.collection("challenges").doc("chal_1").update({
      opponentProgress: 100,
      updatedAt: validTs(),
    })
  );

  await assertSucceeds(
    bobDb.collection("challenges").doc("chal_1").update({
      opponentProgress: 100,
      status: "completed",
      winnerId: BOB_UID,
      updatedAt: validTs(),
    })
  );

  await assertFails(
    aliceDb.collection("challenges").doc("chal_1").update({
      creatorProgress: 100,
      winnerId: ALICE_UID,
      updatedAt: validTs(),
    })
  );
});

// --- 6. Interactions (Immutable Help & Pulses) ---
test("Interactions: help/pulse creation and participant-only read; immutable", async () => {
  const aliceDb = getAuthedDb(ALICE_UID);
  const bobDb = getAuthedDb(BOB_UID);
  const charlieDb = getAuthedDb(CHARLIE_UID);
  const ts = validTs();

  await assertSucceeds(
    aliceDb.collection("interactions").doc("int_1").set({
      fromUserId: ALICE_UID,
      fromUserName: "Alice",
      toUserId: BOB_UID,
      toUserName: "Bob",
      type: "help",
      message: "¡Te envío energía para tu misión!",
      xpBonus: 25,
      participantIds: [ALICE_UID, BOB_UID],
      createdAt: ts,
    })
  );

  await assertFails(
    aliceDb.collection("interactions").doc("int_self").set({
      fromUserId: ALICE_UID,
      fromUserName: "Alice",
      toUserId: ALICE_UID,
      toUserName: "Alice",
      type: "help",
      message: "Self boost",
      xpBonus: 25,
      participantIds: [ALICE_UID, ALICE_UID],
      createdAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("interactions").where("participantIds", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("interactions").doc("int_1").get());
  await assertFails(aliceDb.collection("interactions").doc("int_1").delete());

  await assertSucceeds(
    bobDb.collection("interactions").doc("int_cheer_1").set({
      fromUserId: BOB_UID,
      fromUserName: "Bob",
      toUserId: ALICE_UID,
      toUserName: "Alice",
      type: "cheer",
      message: "¡Genial foto en 'Foto en el parque'!",
      xpBonus: 15,
      participantIds: [BOB_UID, ALICE_UID],
      createdAt: validTs(),
    })
  );
});
