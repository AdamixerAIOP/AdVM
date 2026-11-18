package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CpuArchitecture
import com.example.data.model.FormFactor
import com.example.data.model.GpuAcceleration
import com.example.data.model.HostHardwareReport
import com.example.data.model.OsCatalog
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.VmProfile
import com.example.ui.components.TelemetryPill
import com.example.ui.components.WorkstationCard
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

enum class AddVmSheetMode {
    PRESETS,
    CUSTOM
}

data class QuickVmPreset(
    val label: String,
    val osFamily: OsFamily,
    val majorVersion: Int,
    val versionLabel: String,
    val formFactor: FormFactor,
    val ramMb: Int,
    val arch: CpuArchitecture
) {
    fun toVmProfile(hostReport: HostHardwareReport): VmProfile {
        val safeRam = ramMb.coerceAtMost(hostReport.safeMaxVmRamMb.coerceAtLeast(2048))
        val dims = if (formFactor == FormFactor.PHONE) Triple(1080, 2400, 420) else Triple(1920, 1080, 160)
        return VmProfile(
            name = "$label ${if (formFactor == FormFactor.PC) "PC" else "Phone"}",
            osFamily = osFamily.name,
            osVersionLabel = versionLabel,
            osMajorVersion = majorVersion,
            formFactor = formFactor.name,
            architecture = arch.name,
            cpuCores = hostReport.cpuCoreCount.coerceIn(2, 6),
            ramMb = safeRam,
            storageGb = if (osFamily == OsFamily.WINDOWS) 64 else 32,
            gpuAcceleration = GpuAcceleration.VIRTIO_VULKAN.name,
            uefiEnabled = true,
            tpmEnabled = osFamily == OsFamily.WINDOWS && majorVersion >= 10,
            displayWidth = dims.first,
            displayHeight = dims.second,
            displayDpi = dims.third
        )
    }
}

val quickVmPresets = listOf(
    QuickVmPreset("Android 17", OsFamily.ANDROID, 17, "Android 17", FormFactor.PHONE, 4096, CpuArchitecture.ARM64),
    QuickVmPreset("Android 14", OsFamily.ANDROID, 14, "Android 14", FormFactor.PHONE, 4096, CpuArchitecture.ARM64),
    QuickVmPreset("Android 4.4", OsFamily.ANDROID, 4, "Android 4.4 KitKat", FormFactor.PHONE, 1536, CpuArchitecture.ARM64),
    QuickVmPreset("Windows 11", OsFamily.WINDOWS, 11, "Windows 11", FormFactor.PC, 8192, CpuArchitecture.X86_64),
    QuickVmPreset("Windows 10", OsFamily.WINDOWS, 10, "Windows 10", FormFactor.PC, 6144, CpuArchitecture.X86_64),
    QuickVmPreset("Windows 7", OsFamily.WINDOWS, 7, "Windows 7 SP1", FormFactor.PC, 4096, CpuArchitecture.X86_64)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmEditorSheet(
    initialVm: VmProfile?,
    prefilledImage: OsImageEntry? = null,
    availableImages: List<OsImageEntry> = emptyList(),
    hostReport: HostHardwareReport,
    isSaving: Boolean,
    onImportLocalFile: ((Uri, OsFamily, String, Int, (OsImageEntry) -> Unit) -> Unit)? = null,
    onSave: (VmProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSwitchToPresets = initialVm == null && prefilledImage == null
    var sheetMode by remember {
        mutableStateOf(if (canSwitchToPresets) AddVmSheetMode.PRESETS else AddVmSheetMode.CUSTOM)
    }

    var name by remember {
        mutableStateOf(
            initialVm?.name ?: prefilledImage?.let { "${it.osVersionLabel} VM" } ?: "Custom VM"
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
    var attachedImageId by remember {
        mutableStateOf(initialVm?.imageId ?: prefilledImage?.id.orEmpty())
    }
    var attachedImageTitle by remember {
        mutableStateOf(initialVm?.imageTitle ?: prefilledImage?.title.orEmpty())
    }
    var importedImageCache by remember { mutableStateOf<OsImageEntry?>(null) }
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
    var showAdvancedFirmware by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val customFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && onImportLocalFile != null) {
            onImportLocalFile(uri, osFamily, osVersionLabel, osMajorVersion) { imported ->
                importedImageCache = imported
                attachedImageId = imported.id
                attachedImageTitle = imported.title
                if (name == "Custom VM" || name.isBlank()) {
                    name = imported.title.substringBeforeLast('.').take(40).ifBlank { "Custom VM" }
                }
            }
        }
    }

    BackHandler(enabled = canSwitchToPresets && sheetMode == AddVmSheetMode.CUSTOM) {
        sheetMode = AddVmSheetMode.PRESETS
    }

    val displayDims = remember(formFactor) {
        if (formFactor == FormFactor.PHONE) Triple(1080, 2400, 420) else Triple(1920, 1080, 160)
    }

    val previewVm = VmProfile(
        id = initialVm?.id.orEmpty(),
        userId = initialVm?.userId.orEmpty(),
        name = name.ifBlank { "Custom VM" },
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
        imageId = attachedImageId,
        imageTitle = attachedImageTitle,
        vncHost = initialVm?.vncHost ?: "127.0.0.1",
        vncPort = initialVm?.vncPort ?: 5900
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        AnimatedContent(
            targetState = sheetMode,
            label = "AddVmSheetModeTransition"
        ) { mode ->
            if (mode == AddVmSheetMode.PRESETS) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add VM", style = MaterialTheme.typography.headlineSmall)
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp).testTag("close_vm_editor_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Add VM sheet")
                        }
                    }

                    Text(
                        text = "Tap an OS preset for 1-tap creation, or configure your own OS file and specs below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    quickVmPresets.forEach { preset ->
                        val isWindows = preset.osFamily == OsFamily.WINDOWS
                        val accent = if (isWindows) WindowsCobalt else AndroidEmerald
                        val presetRam = preset.ramMb.coerceAtMost(hostReport.safeMaxVmRamMb.coerceAtLeast(2048))
                        WorkstationCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSaving) {
                                    onSave(preset.toVmProfile(hostReport))
                                }
                                .testTag("quick_preset_${preset.label.lowercase().replace(" ", "_")}"),
                            borderColor = accent.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(accent.copy(alpha = 0.16f))
                                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isWindows) Icons.Rounded.Computer else Icons.Rounded.Android,
                                            contentDescription = preset.label,
                                            tint = accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${preset.formFactor.label} • ${preset.arch.name} • $presetRam MB RAM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                TelemetryPill(label = "1-Tap Add", accent = accent)
                            }
                        }
                    }

                    // Prominent "Add a custom VM" button directly below OS presets
                    OutlinedButton(
                        onClick = { sheetMode = AddVmSheetMode.CUSTOM },
                        enabled = !isSaving,
                        border = BorderStroke(1.5.dp, TelemetryCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("add_custom_vm_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = TelemetryCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add a custom VM",
                            color = TelemetryCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (canSwitchToPresets) {
                                IconButton(
                                    onClick = { sheetMode = AddVmSheetMode.PRESETS },
                                    modifier = Modifier.size(48.dp).testTag("custom_vm_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back to presets"
                                    )
                                }
                            }
                            Text(
                                text = when {
                                    initialVm != null -> "Edit ${initialVm.name}"
                                    prefilledImage != null -> "New VM from Image"
                                    else -> "Add a custom VM"
                                },
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(48.dp).testTag("close_vm_editor_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close VM editor")
                        }
                    }

                    // 1. OS File Picker (.img / .iso / .qcow2)
                    Text("OS Image File (.img / .iso / .qcow2)", style = MaterialTheme.typography.titleSmall)
                    if (onImportLocalFile != null) {
                        OutlinedButton(
                            onClick = { customFilePickerLauncher.launch(arrayOf("*/*")) },
                            border = BorderStroke(1.dp, AndroidEmerald.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("custom_vm_pick_file_button")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = AndroidEmerald)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (attachedImageTitle.isNotBlank()) "Pick Different File from Phone" else "Choose OS File from Phone",
                                color = AndroidEmerald
                            )
                        }
                    }

                    val allSelectableImages = remember(availableImages, importedImageCache) {
                        val extra = importedImageCache?.let { cached ->
                            if (availableImages.none { it.id == cached.id }) listOf(cached) else emptyList()
                        }.orEmpty()
                        extra + availableImages
                    }

                    if (attachedImageTitle.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(SlateElevated)
                                .border(1.dp, AndroidEmerald.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                                .padding(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = AndroidEmerald)
                            Text(
                                text = "Selected File: $attachedImageTitle",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (allSelectableImages.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = attachedImageId.isBlank(),
                                onClick = {
                                    attachedImageId = ""
                                    attachedImageTitle = ""
                                },
                                label = { Text("Pick File Later") }
                            )
                            allSelectableImages.forEach { img ->
                                FilterChip(
                                    selected = attachedImageId == img.id,
                                    onClick = {
                                        attachedImageId = img.id
                                        attachedImageTitle = img.title
                                    },
                                    label = { Text(img.title) }
                                )
                            }
                        }
                    }

                    // 2. VM Name & Operating System
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(60)
                            validationError = null
                        },
                        label = { Text("VM Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vm_name_input")
                    )

                    Text("Operating System", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = osFamily == OsFamily.ANDROID,
                            onClick = {
                                osFamily = OsFamily.ANDROID
                                val latest = OsCatalog.androidVersions.last()
                                osVersionLabel = latest.label
                                osMajorVersion = latest.majorVersion
                                architecture = latest.recommendedArch
                                formFactor = FormFactor.PHONE
                                tpmEnabled = false
                            },
                            label = { Text("Android (4–17)") },
                            leadingIcon = { Icon(Icons.Rounded.Android, contentDescription = null, tint = AndroidEmerald) },
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
                            leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null, tint = WindowsCobalt) },
                            modifier = Modifier.testTag("os_family_windows_chip")
                        )
                    }

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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CpuArchitecture.entries.forEach { arch ->
                            FilterChip(
                                selected = architecture == arch,
                                onClick = { architecture = arch },
                                label = { Text(arch.name) }
                            )
                        }
                    }

                    Text("Display Mode", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = formFactor == FormFactor.PHONE,
                            onClick = { formFactor = FormFactor.PHONE },
                            label = { Text("Phone Mode") },
                            leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                            modifier = Modifier.testTag("form_factor_phone_chip")
                        )
                        FilterChip(
                            selected = formFactor == FormFactor.PC,
                            onClick = { formFactor = FormFactor.PC },
                            label = { Text("PC Desktop") },
                            leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null) },
                            modifier = Modifier.testTag("form_factor_pc_chip")
                        )
                    }

                    // 3. Custom CPU, RAM & Disk Hardware Controls
                    Text("Custom CPU, RAM & Storage", style = MaterialTheme.typography.titleSmall)
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
                            Text("Memory (RAM)", style = MaterialTheme.typography.bodyMedium)
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

                    // 4. Optional Firmware & Network Options
                    TextButton(
                        onClick = { showAdvancedFirmware = !showAdvancedFirmware },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("toggle_advanced_vm_settings")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = TelemetryCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Advanced GPU & Firmware", color = TelemetryCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (showAdvancedFirmware) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = TelemetryCyan
                        )
                    }

                    AnimatedVisibility(visible = showAdvancedFirmware) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    Text("UEFI Firmware", style = MaterialTheme.typography.bodyMedium)
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
                                    Text("Required for Windows 11 compliance", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Switch(checked = tpmEnabled, onCheckedChange = { tpmEnabled = it })
                            }
                        }
                    }

                    if (validationError != null) {
                        TelemetryPill(label = validationError!!, accent = AmberWarn)
                    }

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                validationError = "Please enter a VM name."
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
                        Text(if (isSaving) "Saving..." else if (initialVm == null) "Create Custom VM" else "Save Changes")
                    }
                }
            }
        }
    }
}
