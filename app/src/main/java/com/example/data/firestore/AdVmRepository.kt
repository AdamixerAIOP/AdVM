package com.example.data.firestore

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.WorkstationRepository
import com.example.data.model.CpuArchitecture
import com.example.data.model.FormFactor
import com.example.data.model.GpuAcceleration
import com.example.data.model.ImageDownloadStatus
import com.example.data.model.ImageFormat
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.UserSyncProfile
import com.example.data.model.VmProfile
import com.example.data.model.VmSnapshot
import com.example.data.model.VmStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AdVmRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : WorkstationRepository {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing cloud sync.")
        if (expectedUserId != null && uid != expectedUserId) {
            throw IllegalStateException("Active user changed; refusing cross-user Firestore operation.")
        }
        return uid
    }

    private fun sanitizeDeviceModel(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }.take(100)

    private fun normalizeProviderId(user: FirebaseUser): String {
        val raw = user.providerData
            .firstOrNull { it.providerId != "firebase" }
            ?.providerId ?: "google.com"
        return when (raw) {
            "google.com", "apple.com", "microsoft.com", "firebase" -> raw
            else -> "firebase"
        }
    }

    override fun observeUserProfile(userId: String): Flow<UserSyncProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(UserSyncProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override fun observeVms(userId: String): Flow<List<VmProfile>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/vms"
        emitAll(
            db.collection("users").document(uid).collection("vms")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(VmProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeImages(userId: String): Flow<List<OsImageEntry>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/images"
        emitAll(
            db.collection("users").document(uid).collection("images")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(OsImageEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeSnapshots(userId: String): Flow<List<VmSnapshot>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/snapshots"
        emitAll(
            db.collection("users").document(uid).collection("snapshots")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(VmSnapshot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncUserSession(user: FirebaseUser): Result<Unit> {
        val path = "users/${user.uid}"
        return try {
            val uid = requireUserId(user.uid)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val displayName = (user.displayName ?: user.email?.substringBefore("@") ?: "AdVm Operator")
                .trim().ifBlank { "AdVm Operator" }.take(80)
            val email = (user.email ?: "").take(120)
            val providerId = normalizeProviderId(user)
            val device = sanitizeDeviceModel()

            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "providerId" to providerId,
                    "lastDeviceModel" to device,
                    "preferredFormFactor" to FormFactor.PHONE.name,
                    "cloudSyncEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "providerId" to providerId,
                        "lastDeviceModel" to device,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            seedStarterVmsIfEmpty(uid, device)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedStarterVmsIfEmpty(uid: String, deviceModel: String) {
        val vmsCollection = db.collection("users").document(uid).collection("vms")
        val existing = vmsCollection.limit(1).get().await()
        if (!existing.isEmpty) return

        val android17Starter = VmProfile(
            userId = uid,
            name = "Android 17 GSI Phone",
            osFamily = OsFamily.ANDROID.name,
            osVersionLabel = "Android 17",
            osMajorVersion = 17,
            formFactor = FormFactor.PHONE.name,
            architecture = CpuArchitecture.ARM64.name,
            cpuCores = 4,
            ramMb = 4096,
            storageGb = 32,
            gpuAcceleration = GpuAcceleration.VIRTIO_VULKAN.name,
            uefiEnabled = true,
            tpmEnabled = false,
            displayWidth = 1080,
            displayHeight = 2400,
            displayDpi = 420,
            imageId = "gsi_aosp_17_arm64",
            imageTitle = "AOSP 17 ARM64 GSI",
            vncHost = "127.0.0.1",
            vncPort = 5900,
            status = VmStatus.STOPPED.name,
            lastSyncedDevice = deviceModel
        )
        val win11Starter = VmProfile(
            userId = uid,
            name = "Windows 11 Desktop",
            osFamily = OsFamily.WINDOWS.name,
            osVersionLabel = "Windows 11",
            osMajorVersion = 11,
            formFactor = FormFactor.PC.name,
            architecture = CpuArchitecture.X86_64.name,
            cpuCores = 6,
            ramMb = 8192,
            storageGb = 64,
            gpuAcceleration = GpuAcceleration.VIRTIO_VULKAN.name,
            uefiEnabled = true,
            tpmEnabled = true,
            displayWidth = 1920,
            displayHeight = 1080,
            displayDpi = 160,
            imageId = "win11_arm64_virtio",
            imageTitle = "Windows 11 VirtIO Image",
            vncHost = "127.0.0.1",
            vncPort = 5901,
            status = VmStatus.STOPPED.name,
            lastSyncedDevice = deviceModel
        )

        vmsCollection.document("starter_android_17").set(buildVmCreateMap(uid, android17Starter)).await()
        vmsCollection.document("starter_windows_11").set(buildVmCreateMap(uid, win11Starter)).await()
    }

    override suspend fun updatePreferredFormFactor(userId: String, formFactor: FormFactor): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid).update(
                mapOf(
                    "preferredFormFactor" to formFactor.name,
                    "lastDeviceModel" to sanitizeDeviceModel(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun saveVm(userId: String, vm: VmProfile): Result<String> {
        val isCreate = vm.id.isBlank()
        val vmsCollection = db.collection("users").document(userId).collection("vms")
        val docRef = if (isCreate) vmsCollection.document() else vmsCollection.document(vm.id)
        val path = docRef.path
        return try {
            val uid = requireUserId(userId)
            val validated = clampVmToRules(uid, vm)
            if (isCreate) {
                docRef.set(buildVmCreateMap(uid, validated)).await()
            } else {
                docRef.update(buildVmUpdateMap(validated)).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun updateVmStatus(userId: String, vmId: String, status: VmStatus): Result<Unit> {
        val path = "users/$userId/vms/$vmId"
        return try {
            val uid = requireUserId(userId)
            require(vmId.isNotBlank()) { "VM ID must not be blank" }
            db.collection("users").document(uid).collection("vms").document(vmId).update(
                mapOf(
                    "status" to status.name,
                    "lastSyncedDevice" to sanitizeDeviceModel(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteVm(userId: String, vmId: String): Result<Unit> {
        val path = "users/$userId/vms/$vmId"
        return try {
            val uid = requireUserId(userId)
            require(vmId.isNotBlank()) { "VM ID must not be blank" }
            db.collection("users").document(uid).collection("vms").document(vmId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun saveImageEntry(userId: String, entry: OsImageEntry): Result<String> {
        val isCreate = entry.id.isBlank()
        val collection = db.collection("users").document(userId).collection("images")
        val docRef = if (isCreate) collection.document() else collection.document(entry.id)
        val path = docRef.path
        return try {
            val uid = requireUserId(userId)
            val safeTitle = entry.title.trim().ifBlank { "OS Image" }.take(100)
            val safeVersion = entry.osVersionLabel.trim().ifBlank { "Android 15" }.take(40)
            val safeMajor = entry.osMajorVersion.coerceIn(4, 17)
            if (isCreate) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "osFamily" to entry.osFamily,
                    "osVersionLabel" to safeVersion,
                    "osMajorVersion" to safeMajor,
                    "format" to entry.format,
                    "architecture" to entry.architecture,
                    "sourceUrl" to entry.sourceUrl.take(500),
                    "localPath" to entry.localPath.take(500),
                    "sizeBytes" to entry.sizeBytes.coerceAtLeast(0L),
                    "sha256" to entry.sha256.take(128),
                    "downloadStatus" to entry.downloadStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to safeTitle,
                        "localPath" to entry.localPath.take(500),
                        "sizeBytes" to entry.sizeBytes.coerceAtLeast(0L),
                        "sha256" to entry.sha256.take(128),
                        "downloadStatus" to entry.downloadStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteImageEntry(userId: String, imageId: String): Result<Unit> {
        val path = "users/$userId/images/$imageId"
        return try {
            val uid = requireUserId(userId)
            require(imageId.isNotBlank()) { "Image ID must not be blank" }
            db.collection("users").document(uid).collection("images").document(imageId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    override suspend fun createSnapshot(userId: String, vm: VmProfile, label: String): Result<String> {
        val collection = db.collection("users").document(userId).collection("snapshots")
        val docRef = collection.document()
        val path = docRef.path
        return try {
            val uid = requireUserId(userId)
            require(vm.id.isNotBlank()) { "VM ID must not be blank for snapshot" }
            val safeLabel = label.trim().ifBlank { "Checkpoint" }.take(80)
            val payload = mapOf(
                "userId" to uid,
                "vmId" to vm.id,
                "vmName" to vm.name.trim().ifBlank { "VM" }.take(60),
                "label" to safeLabel,
                "osVersionLabel" to vm.osVersionLabel.take(40),
                "formFactor" to vm.formFactor,
                "ramMb" to vm.ramMb.coerceIn(512, 32768),
                "deviceOrigin" to sanitizeDeviceModel(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteSnapshot(userId: String, snapshotId: String): Result<Unit> {
        val path = "users/$userId/snapshots/$snapshotId"
        return try {
            val uid = requireUserId(userId)
            require(snapshotId.isNotBlank()) { "Snapshot ID must not be blank" }
            db.collection("users").document(uid).collection("snapshots").document(snapshotId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun clampVmToRules(uid: String, vm: VmProfile): VmProfile = vm.copy(
        userId = uid,
        name = vm.name.trim().ifBlank { "New VM" }.take(60),
        osVersionLabel = vm.osVersionLabel.trim().ifBlank { "Android 15" }.take(40),
        osMajorVersion = vm.osMajorVersion.coerceIn(4, 17),
        cpuCores = vm.cpuCores.coerceIn(1, 16),
        ramMb = vm.ramMb.coerceIn(512, 32768),
        storageGb = vm.storageGb.coerceIn(8, 512),
        displayWidth = vm.displayWidth.coerceIn(480, 3840),
        displayHeight = vm.displayHeight.coerceIn(640, 2400),
        displayDpi = vm.displayDpi.coerceIn(96, 640),
        imageId = vm.imageId.take(128),
        imageTitle = vm.imageTitle.take(120),
        vncHost = vm.vncHost.trim().ifBlank { "127.0.0.1" }.take(120),
        vncPort = vm.vncPort.coerceIn(1, 65535),
        lastSyncedDevice = sanitizeDeviceModel()
    )

    private fun buildVmCreateMap(uid: String, vm: VmProfile): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to vm.name,
        "osFamily" to vm.osFamily,
        "osVersionLabel" to vm.osVersionLabel,
        "osMajorVersion" to vm.osMajorVersion,
        "formFactor" to vm.formFactor,
        "architecture" to vm.architecture,
        "cpuCores" to vm.cpuCores,
        "ramMb" to vm.ramMb,
        "storageGb" to vm.storageGb,
        "gpuAcceleration" to vm.gpuAcceleration,
        "uefiEnabled" to vm.uefiEnabled,
        "tpmEnabled" to vm.tpmEnabled,
        "displayWidth" to vm.displayWidth,
        "displayHeight" to vm.displayHeight,
        "displayDpi" to vm.displayDpi,
        "imageId" to vm.imageId,
        "imageTitle" to vm.imageTitle,
        "vncHost" to vm.vncHost,
        "vncPort" to vm.vncPort,
        "status" to vm.status,
        "lastSyncedDevice" to vm.lastSyncedDevice,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun buildVmUpdateMap(vm: VmProfile): Map<String, Any> = mapOf(
        "name" to vm.name,
        "osFamily" to vm.osFamily,
        "osVersionLabel" to vm.osVersionLabel,
        "osMajorVersion" to vm.osMajorVersion,
        "formFactor" to vm.formFactor,
        "architecture" to vm.architecture,
        "cpuCores" to vm.cpuCores,
        "ramMb" to vm.ramMb,
        "storageGb" to vm.storageGb,
        "gpuAcceleration" to vm.gpuAcceleration,
        "uefiEnabled" to vm.uefiEnabled,
        "tpmEnabled" to vm.tpmEnabled,
        "displayWidth" to vm.displayWidth,
        "displayHeight" to vm.displayHeight,
        "displayDpi" to vm.displayDpi,
        "imageId" to vm.imageId,
        "imageTitle" to vm.imageTitle,
        "vncHost" to vm.vncHost,
        "vncPort" to vm.vncPort,
        "status" to vm.status,
        "lastSyncedDevice" to vm.lastSyncedDevice,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
