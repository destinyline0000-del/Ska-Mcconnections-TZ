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
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "destinyline0000@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("movies").where("published", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("chat_groups").where("active", "==", true).get());
});

test("Email spoofing attack without email_verified=true is rejected for Admin operations", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(
    spoofDb.collection("movies").doc("m1").set({
      title: "Spoof Movie",
      description: "Should fail",
      tier: "FREE",
      videoUrl: "https://example.com/a.mp4",
      linkType: "DIRECT_MP4",
      genre: "Drama",
      durationMinutes: 90,
      driveAccountSlot: 0,
      published: true,
      addedByUid: "spoof_uid",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(spoofDb.collection("private_chats").get());
});

test("User profile isolation: owner can create FREE profile and edit safe fields, cannot self-escalate role/tier or read others PII", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: "bob@example.com",
    email_verified: true,
  }).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice TZ",
      email: "alice@example.com",
      role: "FREE",
      subscriptionTier: "FREE",
      preferredLanguage: "SW",
      banned: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Self-escalation to VIP or ADMIN must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      subscriptionTier: "VIP",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      role: "ADMIN",
      subscriptionTier: "PREMIUM",
      preferredLanguage: "EN",
      banned: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Safe profile edit succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Dar",
      preferredLanguage: "EN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user PII read fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Movies: admin can publish, regular users can query published movies but cannot create/modify", async () => {
  const admDb = adminContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    admDb.collection("movies").doc("bongo_1").set({
      title: "Dar City Lights",
      description: "Tanzanian thriller",
      tier: "VIP",
      videoUrl: "https://drive.google.com/file/d/abc123/view",
      resolvedStreamUrl: "https://drive.google.com/uc?export=download&id=abc123",
      linkType: "GOOGLE_DRIVE",
      genre: "Thriller",
      durationMinutes: 105,
      driveAccountSlot: 1,
      driveFileId: "abc123",
      published: true,
      addedByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("movies").where("published", "==", true).get());
  await assertFails(aliceDb.collection("movies").get());
  await assertFails(
    aliceDb.collection("movies").doc("bongo_2").set({
      title: "Unauthorized",
      description: "No",
      tier: "FREE",
      videoUrl: "https://example.com/v.mp4",
      linkType: "DIRECT_MP4",
      genre: "Action",
      durationMinutes: 60,
      driveAccountSlot: 0,
      published: true,
      addedByUid: ALICE_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Private chats: strict participant isolation + Admin oversight access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext("eve_999").firestore();
  const admDb = adminContext().firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("private_chats").doc(chatId).set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessage: "Mambo Bob!",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("private_chats").doc(chatId).collection("messages").doc("msg1").set({
      chatId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Mambo Bob!",
      mediaType: "NONE",
      createdAt: now,
    })
  );

  // Participants can query their private chats & messages
  await assertSucceeds(
    bobDb.collection("private_chats").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb
      .collection("private_chats")
      .doc(chatId)
      .collection("messages")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );

  // Outsider Eve cannot read chat or messages
  await assertFails(eveDb.collection("private_chats").doc(chatId).get());
  await assertFails(
    eveDb
      .collection("private_chats")
      .doc(chatId)
      .collection("messages")
      .where("participantIds", "array-contains", BOB_UID)
      .get()
  );

  // Admin oversight can list ALL private chats & messages
  await assertSucceeds(admDb.collection("private_chats").get());
  await assertSucceeds(
    admDb.collection("private_chats").doc(chatId).collection("messages").get()
  );
});

test("Drive 5-account limit and GitHub config are strictly Admin-only", async () => {
  const admDb = adminContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    admDb.collection("drive_accounts").doc("slot_1").set({
      slotNumber: 1,
      accountLabel: "Ska Drive Vault 1",
      accountEmail: "vault1@gmail.com",
      status: "ACTIVE",
      usedBytes: 1024,
      totalBytes: 15000000000,
      updatedAt: now,
    })
  );

  // 6th slot is rejected by rules
  await assertFails(
    admDb.collection("drive_accounts").doc("slot_6").set({
      slotNumber: 6,
      accountLabel: "Slot 6",
      accountEmail: "v6@gmail.com",
      status: "ACTIVE",
      usedBytes: 0,
      totalBytes: 1000,
      updatedAt: now,
    })
  );

  // Regular user cannot read/write drive_accounts or github_config
  await assertFails(aliceDb.collection("drive_accounts").doc("slot_1").get());
  await assertFails(aliceDb.collection("github_config").doc("main").get());
});

test("Payment requests: user creates PENDING request, admin approves", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const admDb = adminContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("payment_requests").doc("pay_1").set({
      userId: ALICE_UID,
      userName: "Alice",
      provider: "MPESA",
      phoneNumber: "0754123456",
      targetTier: "VIP",
      amountTzs: 10000,
      referenceCode: "TZ882199",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );

  // User cannot self-approve
  await assertFails(
    aliceDb.collection("payment_requests").doc("pay_1").update({
      status: "APPROVED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Admin can approve
  await assertSucceeds(
    admDb.collection("payment_requests").doc("pay_1").update({
      status: "APPROVED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
