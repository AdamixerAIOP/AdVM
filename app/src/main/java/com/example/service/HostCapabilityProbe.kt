package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.model.HostHardwareReport
import java.io.File

object HostCapabilityProbe {

    fun inspect(context: Context): HostHardwareReport {
        val pm = context.packageManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(1024)
        val availableRamMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceAtLeast(256)

        val avfFeaturePresent = pm.hasSystemFeature("android.software.virtualization") ||
            pm.hasSystemFeature("android.hardware.virtualization")

        val avfServiceAvailable = if (Build.VERSION.SDK_INT >= 33) {
            try {
                context.getSystemService("virtualmachine") != null
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }

        val kvmFile = File("/dev/kvm")
        val kvmPresent = try {
            kvmFile.exists()
        } catch (_: SecurityException) {
            false
        }
        val kvmAccessible = try {
            kvmPresent && kvmFile.canRead() && kvmFile.canWrite()
        } catch (_: SecurityException) {
            false
        }

        val vulkanSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } else {
            false
        }
        val vulkanVersionLabel = if (vulkanSupported) {
            "Vulkan 1.1+ HW"
        } else {
            "GLES Fallback"
        }

        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Host" }
        return HostHardwareReport(
            deviceModel = model,
            androidApiLevel = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: listOf("arm64-v8a"),
            cpuCoreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            avfFeaturePresent = avfFeaturePresent,
            avfServiceAvailable = avfServiceAvailable,
            kvmDevicePresent = kvmPresent,
            kvmAccessible = kvmAccessible,
            vulkanSupported = vulkanSupported,
            vulkanVersionLabel = vulkanVersionLabel
        )
    }
}
