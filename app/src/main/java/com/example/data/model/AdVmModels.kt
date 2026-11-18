package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class OsFamily(val displayName: String) {
    ANDROID("Android"),
    WINDOWS("Windows");

    companion object {
        fun fromRaw(raw: String): OsFamily =
            entries.firstOrNull { it.name == raw } ?: ANDROID
    }
}

enum class FormFactor(val label: String) {
    PHONE("Phone Mode"),
    PC("PC Mode");

    companion object {
        fun fromRaw(raw: String): FormFactor =
            entries.firstOrNull { it.name == raw } ?: PHONE
    }
}

enum class CpuArchitecture(val label: String) {
    ARM64("ARM64 (aarch64)"),
    X86_64("x86_64 (amd64)");

    companion object {
        fun fromRaw(raw: String): CpuArchitecture =
            entries.firstOrNull { it.name == raw } ?: ARM64
    }
}

enum class GpuAcceleration(val label: String) {
    VIRTIO_VULKAN("VirtIO-GPU (Vulkan)"),
    VIRGL("VirGL OpenGL ES"),
    SWIFTSHADER("SwiftShader CPU");

    companion object {
        fun fromRaw(raw: String): GpuAcceleration =
            entries.firstOrNull { it.name == raw } ?: VIRTIO_VULKAN
    }
}

enum class VmStatus(val label: String) {
    STOPPED("Stopped"),
    RUNNING("Running"),
    PAUSED("Paused");

    companion object {
        fun fromRaw(raw: String): VmStatus =
            entries.firstOrNull { it.name == raw } ?: STOPPED
    }
}

enum class ImageFormat(val label: String, val extension: String) {
    GSI_IMG("GSI System Image (.img)", "img"),
    QCOW2("QEMU Disk (.qcow2)", "qcow2"),
    ISO("Bootable ISO (.iso)", "iso");

    companion object {
        fun fromRaw(raw: String): ImageFormat =
            entries.firstOrNull { it.name == raw } ?: GSI_IMG
    }
}

enum class ImageDownloadStatus(val label: String) {
    AVAILABLE("Remote Catalog"),
    DOWNLOADING("Downloading"),
    VERIFIED("Verified Local"),
    LOCAL_IMPORT("Local Import"),
    FAILED("Download Failed");

    companion object {
        fun fromRaw(raw: String): ImageDownloadStatus =
            entries.firstOrNull { it.name == raw } ?: AVAILABLE
    }
}

data class OsVersionOption(
    val osFamily: OsFamily,
    val majorVersion: Int,
    val label: String,
    val recommendedArch: CpuArchitecture,
    val recommendedRamMb: Int,
    val requiresTpmUefi: Boolean = false
)

object OsCatalog {
    val androidVersions: List<OsVersionOption> = listOf(
        OsVersionOption(OsFamily.ANDROID, 4, "Android 4.4 KitKat", CpuArchitecture.ARM64, 1024),
        OsVersionOption(OsFamily.ANDROID, 5, "Android 5.1 Lollipop", CpuArchitecture.ARM64, 1536),
        OsVersionOption(OsFamily.ANDROID, 6, "Android 6.0 Marshmallow", CpuArchitecture.ARM64, 2048),
        OsVersionOption(OsFamily.ANDROID, 7, "Android 7.1 Nougat", CpuArchitecture.ARM64, 2048),
        OsVersionOption(OsFamily.ANDROID, 8, "Android 8.1 Oreo", CpuArchitecture.ARM64, 2048),
        OsVersionOption(OsFamily.ANDROID, 9, "Android 9 Pie", CpuArchitecture.ARM64, 3072),
        OsVersionOption(OsFamily.ANDROID, 10, "Android 10", CpuArchitecture.ARM64, 3072),
        OsVersionOption(OsFamily.ANDROID, 11, "Android 11", CpuArchitecture.ARM64, 4096),
        OsVersionOption(OsFamily.ANDROID, 12, "Android 12L", CpuArchitecture.ARM64, 4096),
        OsVersionOption(OsFamily.ANDROID, 13, "Android 13", CpuArchitecture.ARM64, 4096),
        OsVersionOption(OsFamily.ANDROID, 14, "Android 14", CpuArchitecture.ARM64, 4096),
        OsVersionOption(OsFamily.ANDROID, 15, "Android 15", CpuArchitecture.ARM64, 6144),
        OsVersionOption(OsFamily.ANDROID, 16, "Android 16", CpuArchitecture.ARM64, 6144),
        OsVersionOption(OsFamily.ANDROID, 17, "Android 17", CpuArchitecture.ARM64, 6144),
    )

    val windowsVersions: List<OsVersionOption> = listOf(
        OsVersionOption(OsFamily.WINDOWS, 7, "Windows 7 SP1", CpuArchitecture.X86_64, 4096, requiresTpmUefi = false),
        OsVersionOption(OsFamily.WINDOWS, 8, "Windows 8.1 Pro", CpuArchitecture.X86_64, 4096, requiresTpmUefi = false),
        OsVersionOption(OsFamily.WINDOWS, 10, "Windows 10", CpuArchitecture.X86_64, 6144, requiresTpmUefi = true),
        OsVersionOption(OsFamily.WINDOWS, 11, "Windows 11", CpuArchitecture.X86_64, 8192, requiresTpmUefi = true),
    )

    fun optionsFor(family: OsFamily): List<OsVersionOption> =
        if (family == OsFamily.ANDROID) androidVersions else windowsVersions
}

data class CuratedGsiTemplate(
    val id: String,
    val title: String,
    val osFamily: OsFamily,
    val osVersionLabel: String,
    val osMajorVersion: Int,
    val format: ImageFormat,
    val architecture: CpuArchitecture,
    val sourceUrl: String,
    val notes: String
)

object CuratedGsiCatalog {
    val templates: List<CuratedGsiTemplate> = listOf(
        CuratedGsiTemplate(
            id = "gsi_aosp_17_arm64",
            title = "AOSP 17 ARM64 GSI (Developer Preview)",
            osFamily = OsFamily.ANDROID,
            osVersionLabel = "Android 17",
            osMajorVersion = 17,
            format = ImageFormat.GSI_IMG,
            architecture = CpuArchitecture.ARM64,
            sourceUrl = "https://developer.android.com/topic/generic-system-image/releases",
            notes = "Treble VNDK v36+ compatible ARM64 Generic System Image target."
        ),
        CuratedGsiTemplate(
            id = "gsi_aosp_15_arm64",
            title = "AOSP 15 ARM64 GSI (VNDK Lite)",
            osFamily = OsFamily.ANDROID,
            osVersionLabel = "Android 15",
            osMajorVersion = 15,
            format = ImageFormat.GSI_IMG,
            architecture = CpuArchitecture.ARM64,
            sourceUrl = "https://dl.google.com/developers/android/vic/images/gsi/aosp_arm64-exp-AP31.240617.015-12082952-c4986d61.zip",
            notes = "Official Android 15 ARM64 GSI archive from Google GSI distribution."
        ),
        CuratedGsiTemplate(
            id = "gsi_aosp_14_arm64",
            title = "AOSP 14 ARM64 GSI",
            osFamily = OsFamily.ANDROID,
            osVersionLabel = "Android 14",
            osMajorVersion = 14,
            format = ImageFormat.GSI_IMG,
            architecture = CpuArchitecture.ARM64,
            sourceUrl = "https://dl.google.com/developers/android/udc/images/gsi/aosp_arm64-exp-UP1A.231005.007-10912714-35455d17.zip",
            notes = "Android 14 Treble-compliant ARM64 GSI."
        ),
        CuratedGsiTemplate(
            id = "gsi_legacy_kitkat_arm",
            title = "Android 4.4 KitKat Legacy Rootfs",
            osFamily = OsFamily.ANDROID,
            osVersionLabel = "Android 4.4 KitKat",
            osMajorVersion = 4,
            format = ImageFormat.GSI_IMG,
            architecture = CpuArchitecture.ARM64,
            sourceUrl = "https://source.android.com/docs/core/architecture/bootloader/generic-system-images",
            notes = "Legacy Dalvik runtime profile for Android 4.x compatibility."
        ),
        CuratedGsiTemplate(
            id = "win11_arm64_virtio",
            title = "Windows 11 ARM64 VirtIO Profile",
            osFamily = OsFamily.WINDOWS,
            osVersionLabel = "Windows 11",
            osMajorVersion = 11,
            format = ImageFormat.ISO,
            architecture = CpuArchitecture.ARM64,
            sourceUrl = "https://fedorapeople.org/groups/virt/virtio-win/direct-downloads/stable-virtio/virtio-win.iso",
            notes = "VirtIO drivers ISO for Windows 11 UEFI + vTPM 2.0 virtual machines."
        ),
        CuratedGsiTemplate(
            id = "win7_sp1_x86_64",
            title = "Windows 7 SP1 x86_64 Legacy PC",
            osFamily = OsFamily.WINDOWS,
            osVersionLabel = "Windows 7 SP1",
            osMajorVersion = 7,
            format = ImageFormat.QCOW2,
            architecture = CpuArchitecture.X86_64,
            sourceUrl = "https://fedorapeople.org/groups/virt/virtio-win/direct-downloads/archive-virtio/",
            notes = "Legacy BIOS + VirtIO storage profile for Windows 7 SP1."
        )
    )
}

@Keep
@IgnoreExtraProperties
data class UserSyncProfile(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val providerId: String = "google.com",
    val lastDeviceModel: String = "",
    val preferredFormFactor: String = FormFactor.PHONE.name,
    val cloudSyncEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VmProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val osFamily: String = OsFamily.ANDROID.name,
    val osVersionLabel: String = "Android 17",
    val osMajorVersion: Int = 17,
    val formFactor: String = FormFactor.PHONE.name,
    val architecture: String = CpuArchitecture.ARM64.name,
    val cpuCores: Int = 4,
    val ramMb: Int = 4096,
    val storageGb: Int = 32,
    val gpuAcceleration: String = GpuAcceleration.VIRTIO_VULKAN.name,
    val uefiEnabled: Boolean = true,
    val tpmEnabled: Boolean = false,
    val displayWidth: Int = 1080,
    val displayHeight: Int = 2400,
    val displayDpi: Int = 420,
    val imageId: String = "",
    val imageTitle: String = "",
    val vncHost: String = "127.0.0.1",
    val vncPort: Int = 5900,
    val status: String = VmStatus.STOPPED.name,
    val lastSyncedDevice: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedOsFamily: OsFamily get() = OsFamily.fromRaw(osFamily)
    val parsedFormFactor: FormFactor get() = FormFactor.fromRaw(formFactor)
    val parsedArch: CpuArchitecture get() = CpuArchitecture.fromRaw(architecture)
    val parsedGpu: GpuAcceleration get() = GpuAcceleration.fromRaw(gpuAcceleration)
    val parsedStatus: VmStatus get() = VmStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class OsImageEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val osFamily: String = OsFamily.ANDROID.name,
    val osVersionLabel: String = "Android 15",
    val osMajorVersion: Int = 15,
    val format: String = ImageFormat.GSI_IMG.name,
    val architecture: String = CpuArchitecture.ARM64.name,
    val sourceUrl: String = "",
    val localPath: String = "",
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val downloadStatus: String = ImageDownloadStatus.AVAILABLE.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedOsFamily: OsFamily get() = OsFamily.fromRaw(osFamily)
    val parsedFormat: ImageFormat get() = ImageFormat.fromRaw(format)
    val parsedArch: CpuArchitecture get() = CpuArchitecture.fromRaw(architecture)
    val parsedStatus: ImageDownloadStatus get() = ImageDownloadStatus.fromRaw(downloadStatus)
}

@Keep
@IgnoreExtraProperties
data class VmSnapshot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vmId: String = "",
    val vmName: String = "",
    val label: String = "",
    val osVersionLabel: String = "",
    val formFactor: String = FormFactor.PHONE.name,
    val ramMb: Int = 4096,
    val deviceOrigin: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class HostHardwareReport(
    val deviceModel: String,
    val androidApiLevel: Int,
    val androidRelease: String,
    val supportedAbis: List<String>,
    val cpuCoreCount: Int,
    val totalRamMb: Int,
    val availableRamMb: Int,
    val avfFeaturePresent: Boolean,
    val avfServiceAvailable: Boolean,
    val kvmDevicePresent: Boolean,
    val kvmAccessible: Boolean,
    val vulkanSupported: Boolean,
    val vulkanVersionLabel: String
) {
    val avfStatusLabel: String
        get() = when {
            avfServiceAvailable && kvmAccessible -> "AVF + KVM Direct"
            avfFeaturePresent || avfServiceAvailable -> "AVF Ready (Restricted Privilege)"
            kvmAccessible -> "KVM Node Accessible"
            else -> "Standard Emulation"
        }

    val safeMaxVmRamMb: Int
        get() = (totalRamMb * 0.65f).toInt().coerceIn(1024, 16384)
}
