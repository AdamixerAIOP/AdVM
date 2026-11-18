package com.example.data

import android.os.Build
import com.example.data.model.CpuArchitecture
import com.example.data.model.FormFactor
import com.example.data.model.GpuAcceleration
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.UserSyncProfile
import com.example.data.model.VmProfile
import com.example.data.model.VmSnapshot
import com.example.data.model.VmStatus
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

interface WorkstationRepository {
    fun observeUserProfile(userId: String): Flow<UserSyncProfile?>
    fun observeVms(userId: String): Flow<List<VmProfile>>
    fun observeImages(userId: String): Flow<List<OsImageEntry>>
    fun observeSnapshots(userId: String): Flow<List<VmSnapshot>>
    suspend fun updatePreferredFormFactor(userId: String, formFactor: FormFactor): Result<Unit>
    suspend fun saveVm(userId: String, vm: VmProfile): Result<String>
    suspend fun updateVmStatus(userId: String, vmId: String, status: VmStatus): Result<Unit>
    suspend fun deleteVm(userId: String, vmId: String): Result<Unit>
    suspend fun saveImageEntry(userId: String, entry: OsImageEntry): Result<String>
    suspend fun deleteImageEntry(userId: String, imageId: String): Result<Unit>
    suspend fun createSnapshot(userId: String, vm: VmProfile, label: String): Result<String>
    suspend fun deleteSnapshot(userId: String, snapshotId: String): Result<Unit>
}

/**
 * Device-local in-memory repository used when the user selects "Continue as a guest".
 * Cloud synchronization and Firebase queries are completely deactivated.
 */
class LocalGuestRepository : WorkstationRepository {
    private val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }

    private val profileFlow = MutableStateFlow(
        UserSyncProfile(
            userId = "guest",
            displayName = "Guest Operator",
            email = "",
            providerId = "firebase",
            lastDeviceModel = deviceModel,
            preferredFormFactor = FormFactor.PHONE.name,
            cloudSyncEnabled = false
        )
    )

    private val vmsFlow = MutableStateFlow(
        listOf(
            VmProfile(
                id = "guest_android_17",
                userId = "guest",
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
                lastSyncedDevice = "$deviceModel (Local Only)"
            ),
            VmProfile(
                id = "guest_windows_11",
                userId = "guest",
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
                lastSyncedDevice = "$deviceModel (Local Only)"
            )
        )
    )

    private val imagesFlow = MutableStateFlow<List<OsImageEntry>>(emptyList())
    private val snapshotsFlow = MutableStateFlow<List<VmSnapshot>>(emptyList())

    override fun observeUserProfile(userId: String): Flow<UserSyncProfile?> = profileFlow
    override fun observeVms(userId: String): Flow<List<VmProfile>> = vmsFlow
    override fun observeImages(userId: String): Flow<List<OsImageEntry>> = imagesFlow
    override fun observeSnapshots(userId: String): Flow<List<VmSnapshot>> =
        snapshotsFlow.map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }

    override suspend fun updatePreferredFormFactor(userId: String, formFactor: FormFactor): Result<Unit> {
        profileFlow.update { it.copy(preferredFormFactor = formFactor.name) }
        return Result.success(Unit)
    }

    override suspend fun saveVm(userId: String, vm: VmProfile): Result<String> {
        val id = vm.id.ifBlank { "guest_vm_${UUID.randomUUID().toString().take(8)}" }
        val now = Timestamp.now()
        val updatedVm = vm.copy(
            id = id,
            userId = "guest",
            lastSyncedDevice = "$deviceModel (Local Only)",
            createdAt = vm.createdAt ?: now,
            updatedAt = now
        )
        vmsFlow.update { list ->
            val exists = list.any { it.id == id }
            if (exists) list.map { if (it.id == id) updatedVm else it }
            else listOf(updatedVm) + list
        }
        return Result.success(id)
    }

    override suspend fun updateVmStatus(userId: String, vmId: String, status: VmStatus): Result<Unit> {
        val now = Timestamp.now()
        vmsFlow.update { list ->
            list.map { if (it.id == vmId) it.copy(status = status.name, updatedAt = now) else it }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteVm(userId: String, vmId: String): Result<Unit> {
        vmsFlow.update { list -> list.filterNot { it.id == vmId } }
        return Result.success(Unit)
    }

    override suspend fun saveImageEntry(userId: String, entry: OsImageEntry): Result<String> {
        val id = entry.id.ifBlank { "guest_img_${UUID.randomUUID().toString().take(8)}" }
        val now = Timestamp.now()
        val updated = entry.copy(id = id, userId = "guest", createdAt = entry.createdAt ?: now, updatedAt = now)
        imagesFlow.update { list ->
            val exists = list.any { it.id == id }
            if (exists) list.map { if (it.id == id) updated else it }
            else listOf(updated) + list
        }
        return Result.success(id)
    }

    override suspend fun deleteImageEntry(userId: String, imageId: String): Result<Unit> {
        imagesFlow.update { list -> list.filterNot { it.id == imageId } }
        return Result.success(Unit)
    }

    override suspend fun createSnapshot(userId: String, vm: VmProfile, label: String): Result<String> {
        val id = "guest_snap_${UUID.randomUUID().toString().take(8)}"
        val now = Timestamp.now()
        val snap = VmSnapshot(
            id = id,
            userId = "guest",
            vmId = vm.id,
            vmName = vm.name,
            label = label.ifBlank { "Local Checkpoint" },
            osVersionLabel = vm.osVersionLabel,
            formFactor = vm.formFactor,
            ramMb = vm.ramMb,
            deviceOrigin = "$deviceModel (Local Only)",
            createdAt = now,
            updatedAt = now
        )
        snapshotsFlow.update { list -> listOf(snap) + list }
        return Result.success(id)
    }

    override suspend fun deleteSnapshot(userId: String, snapshotId: String): Result<Unit> {
        snapshotsFlow.update { list -> list.filterNot { it.id == snapshotId } }
        return Result.success(Unit)
    }
}
