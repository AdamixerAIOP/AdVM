const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function buildVmPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    name: "Pixel Android 17 Lab",
    osFamily: "ANDROID",
    osVersionLabel: "Android 17",
    osMajorVersion: 17,
    formFactor: "PHONE",
    architecture: "ARM64",
    cpuCores: 4,
    ramMb: 4096,
    storageGb: 32,
    gpuAcceleration: "VIRTIO_VULKAN",
    uefiEnabled: true,
    tpmEnabled: false,
    displayWidth: 1080,
    displayHeight: 2400,
    displayDpi: 420,
    imageId: "img_android_17",
    imageTitle: "AOSP 17 ARM64 GSI",
    vncHost: "127.0.0.1",
    vncPort: 5900,
    status: "STOPPED",
    lastSyncedDevice: "Pixel 9 Pro",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildImagePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    title: "Android 15 GSI ARM64",
    osFamily: "ANDROID",
    osVersionLabel: "Android 15",
    osMajorVersion: 15,
    format: "GSI_IMG",
    architecture: "ARM64",
    sourceUrl: "https://dl.google.com/developers/android/gsi/aosp_arm64-img.zip",
    localPath: "",
    sizeBytes: 0,
    sha256: "",
    downloadStatus: "AVAILABLE",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildSnapshotPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    vmId: "vm_1",
    vmName: "Windows 11 Workstation",
    label: "Clean Desktop Checkpoint",
    osVersionLabel: "Windows 11",
    formFactor: "PC",
    ramMb: 8192,
    deviceOrigin: "Pixel Tablet",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated users are blocked from all VM, image, snapshot, and profile paths", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vms").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("vms").doc("vm_1").set(buildVmPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("images").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("snapshots").get());
});

test("Owner can create, list, partially update, and delete VM profiles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const vmRef = aliceDb.collection("users").doc(ALICE_UID).collection("vms").doc("vm_1");

  await assertSucceeds(vmRef.set(buildVmPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("vms").get());

  // Partial status update
  await assertSucceeds(
    vmRef.update({
      status: "RUNNING",
      lastSyncedDevice: "Galaxy Fold",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Full hardware config update
  await assertSucceeds(
    vmRef.update({
      osFamily: "WINDOWS",
      osVersionLabel: "Windows 11",
      osMajorVersion: 11,
      formFactor: "PC",
      architecture: "X86_64",
      cpuCores: 8,
      ramMb: 8192,
      tpmEnabled: true,
      displayWidth: 1920,
      displayHeight: 1080,
      displayDpi: 160,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject ghost @DocumentId 'id' field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vms").doc("vm_2").set(
      buildVmPayload(ALICE_UID, { id: "vm_2" })
    )
  );

  // Reject immutable createdAt modification on update
  await assertFails(
    vmRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject out-of-range RAM or future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vms").doc("vm_bad").set(
      buildVmPayload(ALICE_UID, { ramMb: 128 })
    )
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("vms").doc("vm_future").set(
      buildVmPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  await assertSucceeds(vmRef.delete());
});

test("Cross-user access to VMs, profiles, images, and snapshots is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).collection("vms").doc("bob_vm").set(buildVmPayload(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("images").doc("bob_img").set(buildImagePayload(BOB_UID));
    await db.collection("users").doc(BOB_UID).collection("snapshots").doc("bob_snap").set(buildSnapshotPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("vms").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("vms").doc("bob_vm").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("vms").doc("bob_vm").update({ status: "RUNNING" }));
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("images").doc("bob_img").delete());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("snapshots").get());
});

test("Owner can manage user sync profile, OS images, and immutable snapshots", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Ada Lovelace",
      email: "ada@example.com",
      providerId: "google.com",
      lastDeviceModel: "Pixel 9",
      preferredFormFactor: "PC",
      cloudSyncEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    profileRef.update({
      preferredFormFactor: "PHONE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  const imgRef = aliceDb.collection("users").doc(ALICE_UID).collection("images").doc("img_1");
  await assertSucceeds(imgRef.set(buildImagePayload(ALICE_UID)));
  await assertSucceeds(
    imgRef.update({
      localPath: "/data/user/0/app/files/images/android15.img",
      sizeBytes: 2147483648,
      sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      downloadStatus: "VERIFIED",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  const snapRef = aliceDb.collection("users").doc(ALICE_UID).collection("snapshots").doc("snap_1");
  await assertSucceeds(snapRef.set(buildSnapshotPayload(ALICE_UID)));
  // Snapshots are immutable
  await assertFails(snapRef.update({ label: "Tampered" }));
  await assertSucceeds(snapRef.delete());
});
