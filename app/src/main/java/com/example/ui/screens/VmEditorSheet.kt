package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CpuArchitecture
import com.example.data.model.FormFactor
import com.example.data.model.GpuAcceleration
import com.example.data.model.HostHardwareReport
import com.example.data.model.OsCatalog
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.VmProfile
import com.example.service.QemuAvfSpecGenerator
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WindowsCobalt
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmEditorSheet(
    initialVm: VmProfile?,
    prefilledImage: OsImageEntry? = null,
    hostReport: HostHardwareReport,
    isSaving: Boolean,
    onSave: (VmProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember {
        mutableStateOf(
            initialVm?.name ?: prefilledImage?.let { "${it.osVersionLabel} VM" } ?: "Android 17 Lab"
        )
    }
    var osFamily by remember {
        mutableStateOf(
            initialVm?.parsedOsFamily ?: prefilledImage?.parsedOsFamily ?: OsFamily.ANDROID
        )
    }
    var osVersionLabel by remember {
        mutableStateOf(
            initialVm?.osVersionLabel ?: prefilledImage?.osVersionLabel ?: "Android 17"
        )
    }
    var osMajorVersion by remember {
        mutableIntStateOf(
            initialVm?.osMajorVersion ?: prefilledImage?.osMajorVersion ?: 17
        )
    }
    var formFactor by remember {
        mutableStateOf(
            initialVm?.parsedFormFactor ?: if (osFamily == OsFamily.WINDOWS) FormFactor.PC else FormFactor.PHONE
        )
    }
    var architecture by remember {
        mutableStateOf(
            initialVm?.parsedArch ?: prefilledImage?.parsedArch ?: if (osFamily == OsFamily.WINDOWS) CpuArchitecture.X86_64 else CpuArchitecture.ARM64
        )
    }
    var cpuCores by remember {
        mutableIntStateOf(initialVm?.cpuCores ?: hostReport.cpuCoreCount.coerceIn(2, 6))
    }
    var ramMb by remember {
        mutableFloatStateOf((initialVm?.ramMb ?: 4096).toFloat())
    }
    var storageGb by remember {
        mutableFloatStateOf((initialVm?.storageGb ?: 32).toFloat())
    }
    var gpuAcceleration by remember {
        mutableStateOf(initialVm?.parsedGpu ?: GpuAcceleration.VIRTIO_VULKAN)
    }
    var uefiEnabled by remember { mutableStateOf(initialVm?.uefiEnabled ?: true) }
    var tpmEnabled by remember { mutableStateOf(initialVm?.tpmEnabled ?: (osFamily == OsFamily.WINDOWS && osMajorVersion >= 10)) }
    var vncHost by remember { mutableStateOf(initialVm?.vncHost ?: "127.0.0.1") }
    var vncPortText by remember { mutableStateOf((initialVm?.vncPort ?: 5900).toString()) }
    var showLaunchSpec by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val displayDims = remember(formFactor) {
        if (formFactor == FormFactor.PHONE) Triple(1080, 2400, 420) else Triple(1920, 1080, 160)
    }

    val previewVm = VmProfile(
        id = initialVm?.id.orEmpty(),
        userId = initialVm?.userId.orEmpty(),
        name = name.ifBlank { "VM Preview" },
        osFamily = osFamily.name,
        osVersionLabel = osVersionLabel,
        osMajorVersion = osMajorVersion,
        formFactor = formFactor.name,
        architecture = architecture.name,
        cpuCores = cpuCores,
        ramMb = ramMb.roundToInt(),
        storageGb = storageGb.roundToInt(),
        gpuAcceleration = gpuAcceleration.name,
        uefiEnabled = uefiEnabled,
        tpmEnabled = tpmEnabled,
        displayWidth = displayDims.first,
        displayHeight = displayDims.second,
        displayDpi = displayDims.third,
        imageId = initialVm?.imageId ?: prefilledImage?.id.orEmpty(),
        imageTitle = initialVm?.imageTitle ?: prefilledImage?.title.orEmpty(),
        vncHost = vncHost.ifBlank { "127.0.0.1" },
        vncPort = vncPortText.toIntOrNull()?.coerceIn(1, 65535) ?: 5900
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialVm == null) "Provision New VM" else "Configure ${initialVm.name}",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_vm_editor_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close VM editor")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(60)
                    validationError = null
                },
                label = { Text("Environment Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vm_name_input")
            )

            // OS Family Toggle
            Text("Guest Operating System", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = osFamily == OsFamily.ANDROID,
                    onClick = {
                        osFamily = OsFamily.ANDROID
                        val first = OsCatalog.androidVersions.last()
                        osVersionLabel = first.label
                        osMajorVersion = first.majorVersion
                        architecture = first.recommendedArch
                        formFactor = FormFactor.PHONE
                        tpmEnabled = false
                    },
                    label = { Text("Android (4–17)") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Android, contentDescription = null, tint = AndroidEmerald)
                    },
                    modifier = Modifier.testTag("os_family_android_chip")
                )
                FilterChip(
                    selected = osFamily == OsFamily.WINDOWS,
                    onClick = {
                        osFamily = OsFamily.WINDOWS
                        val win11 = OsCatalog.windowsVersions.last()
                        osVersionLabel = win11.label
                        osMajorVersion = win11.majorVersion
                        architecture = win11.recommendedArch
                        formFactor = FormFactor.PC
                        tpmEnabled = true
                        uefiEnabled = true
                    },
                    label = { Text("Windows (7–11)") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Computer, contentDescription = null, tint = WindowsCobalt)
                    },
                    modifier = Modifier.testTag("os_family_windows_chip")
                )
            }

            // OS Version Picker (Android 4..17 or Windows 7..11)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OsCatalog.optionsFor(osFamily).forEach { option ->
                    FilterChip(
                        selected = osVersionLabel == option.label,
                        onClick = {
                            osVersionLabel = option.label
                            osMajorVersion = option.majorVersion
                            architecture = option.recommendedArch
                            tpmEnabled = option.requiresTpmUefi
                        },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("os_ver_${option.majorVersion}")
                    )
                }
            }

            // Form Factor Mode (Android Phone vs Windows PC)
            Text("Viewport & Input Mode", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = formFactor == FormFactor.PHONE,
                    onClick = { formFactor = FormFactor.PHONE },
                    label = { Text("Phone (1080×2400)") },
                    leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                    modifier = Modifier.testTag("form_factor_phone_chip")
                )
                FilterChip(
                    selected = formFactor == FormFactor.PC,
                    onClick = { formFactor = FormFactor.PC },
                    label = { Text("PC Desktop (1080p)") },
                    leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null) },
                    modifier = Modifier.testTag("form_factor_pc_chip")
                )
            }

            // CPU & Architecture
            Text("CPU & Architecture", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CpuArchitecture.entries.forEach { arch ->
                    FilterChip(
                        selected = architecture == arch,
                        onClick = { architecture = arch },
                        label = { Text(arch.name) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(SlateElevated)
                    .border(1.dp, HairlineBorder, MaterialTheme.shapes.small)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("vCPU Cores", style = MaterialTheme.typography.bodyMedium)
                    Text("$cpuCores / ${hostReport.cpuCoreCount} host", style = MaterialTheme.typography.labelMedium, color = TelemetryCyan)
                }
                Slider(
                    value = cpuCores.toFloat(),
                    onValueChange = { cpuCores = it.roundToInt().coerceIn(1, 16) },
                    valueRange = 1f..12f,
                    steps = 10
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Allocated Memory (RAM)", style = MaterialTheme.typography.bodyMedium)
                    val ramVal = ramMb.roundToInt()
                    Text(
                        text = "$ramVal MB",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ramVal > hostReport.safeMaxVmRamMb) AmberWarn else AndroidEmerald
                    )
                }
                Slider(
                    value = ramMb,
                    onValueChange = { ramMb = ((it / 512f).roundToInt() * 512).toFloat().coerceIn(512f, 16384f) },
                    valueRange = 512f..16384f
                )
                if (ramMb.roundToInt() > hostReport.safeMaxVmRamMb) {
                    Text(
                        text = "Exceeds 65% of device RAM (${hostReport.totalRamMb} MB). Host memory pressure may occur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarn
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Virtual Disk Size", style = MaterialTheme.typography.bodyMedium)
                    Text("${storageGb.roundToInt()} GB", style = MaterialTheme.typography.labelMedium, color = TelemetryCyan)
                }
                Slider(
                    value = storageGb,
                    onValueChange = { storageGb = ((it / 8f).roundToInt() * 8).toFloat().coerceIn(8f, 256f) },
                    valueRange = 8f..256f
                )
            }

            // Hardware Acceleration & Firmware
            Text("GPU & Firmware Acceleration", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GpuAcceleration.entries.forEach { gpu ->
                    FilterChip(
                        selected = gpuAcceleration == gpu,
                        onClick = { gpuAcceleration = gpu },
                        label = { Text(gpu.label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("UEFI Firmware (AAVMF / OVMF)", style = MaterialTheme.typography.bodyMedium)
                    Text("Recommended for Android 11+ & Windows 10/11", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(checked = uefiEnabled, onCheckedChange = { uefiEnabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("vTPM 2.0 + Secure Boot", style = MaterialTheme.typography.bodyMedium)
                    Text("Required for Windows 11 hardware compliance", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(checked = tpmEnabled, onCheckedChange = { tpmEnabled = it })
            }

            // VNC Endpoint
            Text("VNC / RFB Display Bridge", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vncHost,
                    onValueChange = { vncHost = it.take(120) },
                    label = { Text("Host IP") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = vncPortText,
                    onValueChange = { vncPortText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            TextButton(
                onClick = { showLaunchSpec = !showLaunchSpec },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = TelemetryCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showLaunchSpec) "Hide AVF / QEMU Spec" else "Preview AVF / QEMU Launch Spec", color = TelemetryCyan)
            }

            AnimatedVisibility(visible = showLaunchSpec) {
                Text(
                    text = QemuAvfSpecGenerator.buildLaunchSpec(previewVm, hostReport),
                    style = MaterialTheme.typography.labelSmall,
                    color = AndroidEmerald,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(ObsidianCanvas)
                        .border(1.dp, HairlineBorder, MaterialTheme.shapes.small)
                        .padding(12.dp)
                )
            }

            if (validationError != null) {
                TelemetryPill(label = validationError!!, accent = AmberWarn)
            }

            Button(
                onClick = {
                    val port = vncPortText.toIntOrNull()
                    if (name.isBlank()) {
                        validationError = "Please enter an environment name."
                        return@Button
                    }
                    if (port == null || port !in 1..65535) {
                        validationError = "VNC port must be between 1 and 65535."
                        return@Button
                    }
                    onSave(previewVm.copy(status = initialVm?.status ?: "STOPPED"))
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("save_vm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TelemetryCyan,
                    contentColor = ObsidianCanvas
                )
            ) {
                Text(if (isSaving) "Syncing to Cloud..." else "Save & Sync VM Profile")
            }
        }
    }
}
