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

test("Unauthenticated user cannot read or write any user collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("tasks")
      .doc("task_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized task",
        priority: "HIGH",
        done: false,
        createdAt: past,
        updatedAt: past,
      })
  );
});

test("Conversations and messages: owner create, partial update, parent-exists check, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc("conv_1");

  // Message write before parent conversation exists must fail
  await assertFails(
    convRef.collection("messages").doc("msg_0").set({
      userId: ALICE_UID,
      role: "user",
      text: "Orphaned message",
      createdAt: past,
    })
  );

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Morning Briefing",
      modelName: "gemini-3.8-flash",
      lastMessagePreview: "Hello Jarvis",
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Hello Jarvis, summarize my schedule.",
      createdAt: past,
    })
  );

  await assertSucceeds(
    convRef.update({
      lastMessagePreview: "Here is your schedule...",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / @DocumentId id field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_2")
      .set({
        id: "conv_2",
        userId: ALICE_UID,
        title: "Ghost ID",
        modelName: "gemini-3.8-flash",
        lastMessagePreview: "",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Bob cannot read or write Alice's conversation
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .get()
  );
});

test("Tasks, Notes, Schedules, Memories, and Preferences enforce schema and ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Tasks
  const taskRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("tasks")
    .doc("task_1");
  await assertSucceeds(
    taskRef.set({
      userId: ALICE_UID,
      title: "Build Jarvis APK",
      priority: "HIGH",
      done: false,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    taskRef.update({
      done: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Future timestamp rejected
  await assertFails(
    taskRef.update({
      done: false,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Notes
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Physics Revision",
      content: "Relativity and quantum notes",
      tag: "Study",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Schedules
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("schedules")
      .doc("sched_1")
      .set({
        userId: ALICE_UID,
        title: "Deep Work",
        timeSlot: "09:00 AM",
        dateLabel: "Today",
        category: "Focus",
        createdAt: past,
        updatedAt: past,
      })
  );

  // Memories
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("memories")
      .doc("mem_1")
      .set({
        userId: ALICE_UID,
        fact: "Prefers Roman Urdu for casual chat",
        active: true,
        createdAt: past,
        updatedAt: past,
      })
  );

  // Preferences (only 'settings' doc allowed)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("settings")
      .set({
        userId: ALICE_UID,
        memoryEnabled: true,
        voiceAutoSpeak: false,
        preferredLanguage: "Auto",
        preferredModel: "gemini-3.8-flash",
        updatedAt: past,
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("preferences")
      .doc("other_doc")
      .set({
        userId: ALICE_UID,
        memoryEnabled: true,
        voiceAutoSpeak: false,
        preferredLanguage: "Auto",
        preferredModel: "gemini-3.8-flash",
        updatedAt: past,
      })
  );

  // Cross-user isolation check
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
});
