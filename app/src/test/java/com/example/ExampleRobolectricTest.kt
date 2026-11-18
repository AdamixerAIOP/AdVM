package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalGuestRepository
import com.example.data.TutorialPreferences
import com.example.data.model.CpuArchitecture
import com.example.data.model.FormFactor
import com.example.data.model.ImageFormat
import com.example.data.model.OsCatalog
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.VmProfile
import com.example.service.HostCapabilityProbe
import com.example.service.QemuAvfSpecGenerator
import com.example.service.QmpClient
import com.example.ui.screens.AdVmTutorialCatalog
import com.example.ui.viewmodel.ConsoleViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app_name matches AdVm and HostCapabilityProbe succeeds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AdVm", appName)

        val report = HostCapabilityProbe.inspect(context)
        assertTrue(report.cpuCoreCount >= 1)
        assertTrue(report.totalRamMb >= 1024)
    }

    @Test
    fun `os catalog spans android 4 to 17 and windows 7 to 11`() {
        val androidVersions = OsCatalog.androidVersions.map { it.majorVersion }
        assertEquals((4..17).toList(), androidVersions)

        val windowsVersions = OsCatalog.windowsVersions.map { it.majorVersion }
        assertTrue(windowsVersions.containsAll(listOf(7, 8, 10, 11)))
    }

    @Test
    fun `qemu launch spec includes tpm, uefi, qmp and vnc for windows 11`() {
        val vm = VmProfile(
            id = "win11_pro",
            name = "Win11 Pro",
            osFamily = OsFamily.WINDOWS.name,
            osVersionLabel = "Windows 11",
            osMajorVersion = 11,
            formFactor = FormFactor.PC.name,
            architecture = CpuArchitecture.X86_64.name,
            cpuCores = 6,
            ramMb = 8192,
            uefiEnabled = true,
            tpmEnabled = true,
            vncPort = 5901
        )
        val iso = OsImageEntry(
            id = "iso1",
            title = "Windows 11 ARM64",
            osFamily = OsFamily.WINDOWS.name,
            osVersionLabel = "Windows 11",
            format = ImageFormat.ISO.name,
            localPath = "/sdcard/Download/win11.iso"
        )
        val spec = QemuAvfSpecGenerator.buildLaunchSpec(vm, null, iso)
        assertTrue(spec.contains("qemu-system-x86_64"))
        assertTrue(spec.contains("edk2-x86_64-code.fd"))
        assertTrue(spec.contains("tpm-tis"))
        assertTrue(spec.contains("-qmp tcp:127.0.0.1:4445,server,nowait"))
        assertTrue(spec.contains("-vnc :1 -display none"))
        assertTrue(spec.contains("qemu-img create -f qcow2"))
        assertTrue(spec.contains("-cdrom \"/sdcard/Download/win11.iso\""))
    }

    @Test
    fun `qmpPortForVncPort maps standard VNC ports deterministically`() {
        assertEquals(4444, QmpClient.qmpPortForVncPort(5900))
        assertEquals(4445, QmpClient.qmpPortForVncPort(5901))
        assertEquals(4449, QmpClient.qmpPortForVncPort(5905))
    }

    @Test
    fun `guest repository disables cloud sync and persists vms locally`() = runTest {
        val repo = LocalGuestRepository()
        val profile = repo.observeUserProfile("guest").first()
        assertNotNull(profile)
        assertFalse(profile!!.cloudSyncEnabled)

        val initialVms = repo.observeVms("guest").first()
        assertEquals(2, initialVms.size)

        val saveResult = repo.saveVm(
            "guest",
            VmProfile(
                name = "Android 4.4 KitKat",
                osFamily = OsFamily.ANDROID.name,
                osVersionLabel = "Android 4.4",
                osMajorVersion = 4,
                formFactor = FormFactor.PHONE.name
            )
        )
        assertTrue(saveResult.isSuccess)
        val updatedVms = repo.observeVms("guest").first()
        assertEquals(3, updatedVms.size)
        assertTrue(updatedVms.any { it.name == "Android 4.4 KitKat" })
    }

    @Test
    fun `tutorial catalog and preferences work cleanly`() = runTest {
        assertEquals(5, AdVmTutorialCatalog.steps.size)
        assertTrue(AdVmTutorialCatalog.steps.all { it.title.isNotBlank() && it.checklist.isNotEmpty() })

        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = TutorialPreferences(context)
        assertFalse(prefs.hasSeenTutorial.first())
        prefs.setTutorialCompleted(true)
        assertTrue(prefs.hasSeenTutorial.first())
    }

    @Test
    fun `console viewmodel resolves attached image path and toggles autopoll`() {
        val consoleVm = ConsoleViewModel()
        assertTrue(consoleVm.autoPollEnabled.value)
        consoleVm.setAutoPoll(false, "127.0.0.1", 5900)
        assertFalse(consoleVm.autoPollEnabled.value)

        val vm = VmProfile(
            id = "a14",
            name = "Android 14",
            osFamily = OsFamily.ANDROID.name,
            osVersionLabel = "Android 14",
            osMajorVersion = 14,
            imageTitle = "AOSP 14 GSI",
            vncPort = 5902
        )
        val image = OsImageEntry(
            id = "img14",
            title = "AOSP 14 GSI",
            osVersionLabel = "Android 14",
            format = ImageFormat.GSI_IMG.name,
            localPath = "/data/user/0/com.example/files/aosp14.img"
        )
        val cmd = consoleVm.launchCommandFor(vm, null, listOf(image))
        assertTrue(cmd.contains("/data/user/0/com.example/files/aosp14.img"))
        assertTrue(cmd.contains("-qmp tcp:127.0.0.1:4446,server,nowait"))
        assertTrue(cmd.contains("-vnc :2 -display none"))

        val oneLine = QemuAvfSpecGenerator.buildOneLineLaunchCommand(vm, null, image)
        assertFalse(oneLine.contains("\n"))
        assertTrue(oneLine.contains("qemu-system-aarch64"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dlMgr = com.example.service.GsiDownloadManager(context)
        val uri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fwin11.iso")
        assertEquals("/sdcard/Download/win11.iso", dlMgr.resolveSafFilesystemPath(uri))
    }

    @Test
    fun `add vm presets convert cleanly to vm profiles and include android and windows`() {
        val host = HostCapabilityProbe.inspect(ApplicationProvider.getApplicationContext())
        assertTrue(com.example.ui.screens.quickVmPresets.any { it.osFamily == OsFamily.ANDROID })
        assertTrue(com.example.ui.screens.quickVmPresets.any { it.osFamily == OsFamily.WINDOWS })

        val profiles = com.example.ui.screens.quickVmPresets.map { it.toVmProfile(host) }
        assertTrue(profiles.all { it.name.isNotBlank() && it.ramMb >= 1536 && it.cpuCores >= 2 })
        assertEquals(2, com.example.ui.screens.AddVmSheetMode.entries.size)
    }

    @Test
    fun `captcha generator and embedded qemu engine binary resolution work cleanly`() {
        val code = com.example.ui.components.CaptchaGenerator.nextCode()
        assertEquals(6, code.length)
        assertTrue(com.example.ui.components.CaptchaGenerator.matches(code, code.lowercase()))
        assertFalse(com.example.ui.components.CaptchaGenerator.matches(code, "WRONG1"))

        assertEquals(
            "libqemu-system-aarch64.so",
            com.example.service.EmbeddedQemuEngine.expectedBinaryName(CpuArchitecture.ARM64)
        )
        assertEquals(
            "libqemu-system-x86_64.so",
            com.example.service.EmbeddedQemuEngine.expectedBinaryName(CpuArchitecture.X86_64)
        )
    }
}
