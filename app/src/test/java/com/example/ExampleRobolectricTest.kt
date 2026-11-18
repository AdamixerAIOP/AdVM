package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalGuestRepository
import com.example.data.TutorialPreferences
import com.example.data.model.CpuArchitecture
import com.example.data.model.FormFactor
import com.example.data.model.OsCatalog
import com.example.data.model.OsFamily
import com.example.data.model.VmProfile
import com.example.service.HostCapabilityProbe
import com.example.service.QemuAvfSpecGenerator
import com.example.ui.screens.AdVmTutorialCatalog
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
    fun `qemu launch spec includes tpm and uefi for windows 11`() {
        val vm = VmProfile(
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
        val spec = QemuAvfSpecGenerator.buildLaunchSpec(vm, null)
        assertTrue(spec.contains("qemu-system-x86_64"))
        assertTrue(spec.contains("OVMF_CODE.fd"))
        assertTrue(spec.contains("tpm-tis"))
        assertTrue(spec.contains("-vnc :1"))
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
}
