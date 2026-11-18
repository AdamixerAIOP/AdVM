package com.example.service

import android.content.Context
import android.net.Uri
import android.os.Build
import com.example.data.model.CpuArchitecture
import com.example.data.model.GpuAcceleration
import com.example.data.model.HostHardwareReport
import com.example.data.model.ImageFormat
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.VmProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface EngineLaunchResult {
    data class Started(val diskPath: String, val binaryPath: String) : EngineLaunchResult
    data class MissingNativeBinary(
        val expectedLibName: String,
        val abi: String,
        val verifiedDiskPath: String
    ) : EngineLaunchResult
    data class Failed(val reason: String) : EngineLaunchResult
}

/**
 * In-process QEMU hypervisor launcher.
 * Stages the selected .img/.iso/.qcow2 file and executes bundled native QEMU libraries
 * (`libqemu-system-aarch64.so` / `libqemu-system-x86_64.so`) from `nativeLibraryDir`
 * with zero external apps or terminals required.
 */
object EmbeddedQemuEngine {

    private var activeProcess: Process? = null

    fun expectedBinaryName(arch: CpuArchitecture): String =
        if (arch == CpuArchitecture.ARM64) "libqemu-system-aarch64.so" else "libqemu-system-x86_64.so"

    fun findBundledBinary(context: Context, arch: CpuArchitecture): File? {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val binary = File(libDir, expectedBinaryName(arch))
        return binary.takeIf { it.exists() && it.canExecute() }
    }

    suspend fun stageOsImage(context: Context, image: OsImageEntry): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val rawPath = image.localPath.trim()
            if (rawPath.isBlank()) {
                throw IllegalStateException("Selected OS entry \"${image.title}\" has no local file yet. Download or pick a file first.")
            }
            if (!rawPath.startsWith("content://")) {
                val directFile = File(rawPath)
                if (!directFile.exists() || !directFile.canRead()) {
                    throw IllegalStateException("OS image file not found or unreadable at $rawPath")
                }
                if (directFile.length() <= 0L) {
                    throw IllegalStateException("OS image file is empty (0 bytes).")
                }
                return@runCatching directFile
            }

            // Copy SAF content:// URI into app-private disk directory for native hypervisor access
            val disksDir = File(context.filesDir, "disks").apply { if (!exists()) mkdirs() }
            val safeId = image.id.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "guest" }
            val stagedFile = File(disksDir, "$safeId.${image.parsedFormat.extension}")
            if (stagedFile.exists() && stagedFile.length() > 0L) {
                return@runCatching stagedFile
            }
            val uri = Uri.parse(rawPath)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open selected OS file URI.")
            input.use { src ->
                FileOutputStream(stagedFile).use { dst ->
                    src.copyTo(dst, bufferSize = 64 * 1024)
                    dst.flush()
                }
            }
            if (stagedFile.length() <= 0L) {
                stagedFile.delete()
                throw IllegalStateException("Copied OS file was empty.")
            }
            stagedFile
        }
    }

    suspend fun launchVm(
        context: Context,
        vm: VmProfile,
        host: HostHardwareReport?,
        image: OsImageEntry
    ): EngineLaunchResult = withContext(Dispatchers.IO) {
        val stagedResult = stageOsImage(context, image)
        val diskFile = stagedResult.getOrElse { err ->
            return@withContext EngineLaunchResult.Failed(err.localizedMessage ?: "Failed to prepare OS image file.")
        }

        val arch = vm.parsedArch
        val binary = findBundledBinary(context, arch)
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        if (binary == null) {
            return@withContext EngineLaunchResult.MissingNativeBinary(
                expectedLibName = expectedBinaryName(arch),
                abi = primaryAbi,
                verifiedDiskPath = diskFile.absolutePath
            )
        }

        runCatching {
            stopActiveProcess()

            val disksDir = File(context.filesDir, "disks").apply { if (!exists()) mkdirs() }
            val vmDisk = File(disksDir, "vm_${vm.id.ifBlank { "disk" }}.qcow2")
            val isIso = image.parsedFormat == ImageFormat.ISO || diskFile.extension.equals("iso", ignoreCase = true)
            val isQcow2 = image.parsedFormat == ImageFormat.QCOW2 || diskFile.extension.equals("qcow2", ignoreCase = true)

            if (isIso && !vmDisk.exists()) {
                val qemuImg = File(context.applicationInfo.nativeLibraryDir, "libqemu-img.so")
                if (qemuImg.exists() && qemuImg.canExecute()) {
                    ProcessBuilder(
                        qemuImg.absolutePath,
                        "create", "-f", "qcow2",
                        vmDisk.absolutePath, "${vm.storageGb}G"
                    ).apply {
                        environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
                    }.start().waitFor()
                }
            }

            val vncDisplay = (vm.vncPort - 5900).coerceAtLeast(0)
            val qmpPort = QmpClient.qmpPortForVncPort(vm.vncPort)
            val args = mutableListOf(binary.absolutePath)

            if (arch == CpuArchitecture.ARM64) {
                args += listOf("-machine", "virt,gic-version=3")
            } else {
                args += listOf("-machine", "q35")
            }

            val hostMatchesGuest = host?.supportedAbis?.any {
                (arch == CpuArchitecture.ARM64 && it.contains("arm64")) ||
                    (arch == CpuArchitecture.X86_64 && it.contains("x86_64"))
            } ?: false
            if (host?.kvmAccessible == true && hostMatchesGuest) {
                args += listOf("-enable-kvm", "-cpu", "host")
            } else if (arch == CpuArchitecture.ARM64) {
                args += listOf("-cpu", "cortex-a76")
            } else {
                args += listOf("-cpu", "qemu64,+ssse3,+sse4.1,+sse4.2,+avx")
            }

            args += listOf("-smp", vm.cpuCores.toString(), "-m", "${vm.ramMb}M")

            if (isIso) {
                if (vmDisk.exists()) {
                    args += listOf("-drive", "file=${vmDisk.absolutePath},if=virtio,format=qcow2")
                }
                args += listOf("-cdrom", diskFile.absolutePath, "-boot", "d")
            } else {
                val fmt = if (isQcow2) "qcow2" else "raw"
                args += listOf("-drive", "file=${diskFile.absolutePath},if=virtio,format=$fmt")
            }

            val gpuDevice = when (vm.parsedGpu) {
                GpuAcceleration.VIRTIO_VULKAN -> "virtio-gpu-gl-pci,venus=true"
                GpuAcceleration.VIRGL -> "virtio-gpu-gl-pci"
                GpuAcceleration.SWIFTSHADER -> "virtio-vga"
            }
            args += listOf("-device", "$gpuDevice,xres=${vm.displayWidth},yres=${vm.displayHeight}")
            args += listOf("-device", "qemu-xhci", "-device", "usb-kbd", "-device", "usb-tablet")
            args += listOf("-netdev", "user,id=net0", "-device", "virtio-net-pci,netdev=net0")

            if (vm.osFamily == OsFamily.WINDOWS.name && vm.tpmEnabled) {
                val tpmSock = File(context.cacheDir, "swtpm-sock")
                args += listOf(
                    "-chardev", "socket,id=chrtpm,path=${tpmSock.absolutePath}",
                    "-tpmdev", "emulator,id=tpm0,chardev=chrtpm",
                    "-device", "tpm-tis,tpmdev=tpm0"
                )
            }

            args += listOf("-qmp", "tcp:127.0.0.1:$qmpPort,server,nowait")
            args += listOf("-vnc", ":$vncDisplay", "-display", "none")

            val proc = ProcessBuilder(args).apply {
                directory(disksDir)
                environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
                environment()["HOME"] = disksDir.absolutePath
                environment()["TMPDIR"] = context.cacheDir.absolutePath
                redirectErrorStream(true)
            }.start()
            activeProcess = proc

            EngineLaunchResult.Started(
                diskPath = diskFile.absolutePath,
                binaryPath = binary.absolutePath
            )
        }.getOrElse { err ->
            EngineLaunchResult.Failed(err.localizedMessage ?: "Failed to start in-app QEMU engine.")
        }
    }

    fun stopActiveProcess() {
        runCatching {
            activeProcess?.destroy()
        }
        activeProcess = null
    }
}
