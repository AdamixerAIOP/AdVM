package com.example.service

import com.example.data.model.CpuArchitecture
import com.example.data.model.GpuAcceleration
import com.example.data.model.HostHardwareReport
import com.example.data.model.ImageFormat
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.VmProfile

object QemuAvfSpecGenerator {

    fun buildOneLineLaunchCommand(
        vm: VmProfile,
        host: HostHardwareReport?,
        attachedImage: OsImageEntry? = null
    ): String {
        val multiline = buildLaunchSpec(vm, host, attachedImage)
        return multiline
            .replace("\\\n", " ")
            .replace("\n", " && ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun buildLaunchSpec(
        vm: VmProfile,
        host: HostHardwareReport?,
        attachedImage: OsImageEntry? = null
    ): String {
        val arch = vm.parsedArch
        val osFamily = vm.parsedOsFamily
        val binary = if (arch == CpuArchitecture.ARM64) "qemu-system-aarch64" else "qemu-system-x86_64"
        val hostMatchesGuest = host?.supportedAbis?.any {
            (arch == CpuArchitecture.ARM64 && it.contains("arm64")) ||
                (arch == CpuArchitecture.X86_64 && it.contains("x86_64"))
        } ?: false
        val accelFlag = when {
            host?.kvmAccessible == true && hostMatchesGuest -> "-enable-kvm -cpu host"
            arch == CpuArchitecture.ARM64 -> "-cpu cortex-a76"
            else -> "-cpu qemu64,+ssse3,+sse4.1,+sse4.2,+avx"
        }
        val machineFlag = if (arch == CpuArchitecture.ARM64) {
            "-machine virt,gic-version=3"
        } else {
            "-machine q35"
        }

        val gpuDevice = when (vm.parsedGpu) {
            GpuAcceleration.VIRTIO_VULKAN -> "virtio-gpu-gl-pci,venus=true"
            GpuAcceleration.VIRGL -> "virtio-gpu-gl-pci"
            GpuAcceleration.SWIFTSHADER -> "virtio-vga"
        }

        val resolvedPath = attachedImage?.localPath?.takeIf { it.isNotBlank() && !it.startsWith("content://") }
            ?: "/sdcard/Download/${attachedImage?.title?.replace(" ", "_") ?: "guest_os.${attachedImage?.parsedFormat?.extension ?: "img"}"}"

        val isIso = attachedImage?.parsedFormat == ImageFormat.ISO || resolvedPath.endsWith(".iso", ignoreCase = true)
        val isQcow2 = attachedImage?.parsedFormat == ImageFormat.QCOW2 || resolvedPath.endsWith(".qcow2", ignoreCase = true)
        val vncDisplay = (vm.vncPort - 5900).coerceAtLeast(0)
        val qmpPort = QmpClient.qmpPortForVncPort(vm.vncPort)
        val vmDisk = "advm_${vm.id.ifBlank { "disk" }}.qcow2"

        val lines = mutableListOf<String>()
        if (isIso) {
            lines += "qemu-img create -f qcow2 -o preallocation=off \"$vmDisk\" ${vm.storageGb}G 2>/dev/null || true"
        }
        lines += "$binary \\"
        lines += "  $machineFlag $accelFlag \\"
        lines += "  -smp ${vm.cpuCores} -m ${vm.ramMb}M \\"
        if (isIso) {
            lines += "  -drive file=\"$vmDisk\",if=virtio,format=qcow2 \\"
            lines += "  -cdrom \"$resolvedPath\" -boot d \\"
        } else {
            val fmt = if (isQcow2) "qcow2" else "raw"
            lines += "  -drive file=\"$resolvedPath\",if=virtio,format=$fmt \\"
        }
        lines += "  -device $gpuDevice,xres=${vm.displayWidth},yres=${vm.displayHeight} \\"
        lines += "  -device qemu-xhci -device usb-kbd -device usb-tablet \\"
        lines += "  -netdev user,id=net0 -device virtio-net-pci,netdev=net0 \\"

        if (vm.uefiEnabled) {
            val fw = if (arch == CpuArchitecture.ARM64) {
                "edk2-aarch64-code.fd"
            } else {
                "edk2-x86_64-code.fd"
            }
            lines += "  -bios \"$fw\" \\"
        }
        if (osFamily == OsFamily.WINDOWS && vm.tpmEnabled) {
            lines += "  -chardev socket,id=chrtpm,path=/tmp/swtpm-sock -tpmdev emulator,id=tpm0,chardev=chrtpm -device tpm-tis,tpmdev=tpm0 \\"
        }
        lines += "  -qmp tcp:127.0.0.1:$qmpPort,server,nowait \\"
        lines += "  -vnc :$vncDisplay -display none"
        return lines.joinToString("\n")
    }
}
