package com.example.service

import com.example.data.model.CpuArchitecture
import com.example.data.model.GpuAcceleration
import com.example.data.model.HostHardwareReport
import com.example.data.model.OsFamily
import com.example.data.model.VmProfile

object QemuAvfSpecGenerator {

    fun buildLaunchSpec(vm: VmProfile, host: HostHardwareReport?): String {
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

        val diskPath = vm.imageTitle.ifBlank { "system_gsi.img" }
        val vncDisplay = (vm.vncPort - 5900).coerceAtLeast(0)

        val lines = mutableListOf(
            "$binary \\",
            "  $machineFlag $accelFlag \\",
            "  -smp ${vm.cpuCores} -m ${vm.ramMb}M \\",
            "  -drive file=\"$diskPath\",if=virtio,format=raw \\",
            "  -device $gpuDevice,xres=${vm.displayWidth},yres=${vm.displayHeight} \\",
            "  -netdev user,id=net0 -device virtio-net-pci,netdev=net0 \\"
        )

        if (vm.uefiEnabled) {
            val fw = if (arch == CpuArchitecture.ARM64) "AAVMF_CODE.fd" else "OVMF_CODE.fd"
            lines += "  -bios /usr/share/edk2/$fw \\"
        }
        if (osFamily == OsFamily.WINDOWS && vm.tpmEnabled) {
            lines += "  -chardev socket,id=chrtpm,path=/tmp/swtpm-sock -tpmdev emulator,id=tpm0,chardev=chrtpm -device tpm-tis,tpmdev=tpm0 \\"
        }
        lines += "  -vnc :$vncDisplay -display none"
        return lines.joinToString("\n")
    }
}
