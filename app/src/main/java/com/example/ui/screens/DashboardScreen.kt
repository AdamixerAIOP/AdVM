package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FormFactor
import com.example.data.model.HostHardwareReport
import com.example.data.model.OsFamily
import com.example.data.model.UiState
import com.example.data.model.VmProfile
import com.example.data.model.VmStatus
import com.example.service.QemuAvfSpecGenerator
import com.example.ui.components.TelemetryPill
import com.example.ui.components.WorkstationCard
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WindowsCobalt

@Composable
fun DashboardScreen(
    vmsState: UiState<List<VmProfile>>,
    hostReport: HostHardwareReport,
    selectedFilter: FormFactor?,
    selectedVmId: String?,
    statusBanner: String?,
    isMutating: Boolean,
    isExpandedWidth: Boolean,
    onRefreshProbe: () -> Unit,
    onFilterChange: (FormFactor?) -> Unit,
    onSelectVm: (String?) -> Unit,
    onSaveVm: (VmProfile, () -> Unit) -> Unit,
    onTogglePower: (VmProfile) -> Unit,
    onStopVm: (VmProfile) -> Unit,
    onDeleteVm: (VmProfile) -> Unit,
    onCreateSnapshot: (VmProfile, String) -> Unit,
    onLaunchConsole: (VmProfile) -> Unit,
    onClearBanner: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var editorTargetVm by remember { mutableStateOf<VmProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var snapshotTargetVm by remember { mutableStateOf<VmProfile?>(null) }
    var snapshotLabel by remember { mutableStateOf("Clean Checkpoint") }
    var deleteConfirmVm by remember { mutableStateOf<VmProfile?>(null) }

    LaunchedEffect(statusBanner) {
        if (!statusBanner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusBanner)
            onClearBanner()
        }
    }

    Scaffold(
        containerColor = ObsidianCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editorTargetVm = null
                    showEditor = true
                },
                containerColor = TelemetryCyan,
                contentColor = ObsidianCanvas,
                modifier = Modifier.testTag("create_vm_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create new virtual machine")
            }
        }
    ) { innerPadding ->
        val vmList = (vmsState as? UiState.Success)?.data.orEmpty()
        val filteredVms = remember(vmList, selectedFilter) {
            if (selectedFilter == null) vmList else vmList.filter { it.parsedFormFactor == selectedFilter }
        }
        val activeInspectorVm = remember(filteredVms, selectedVmId) {
            filteredVms.firstOrNull { it.id == selectedVmId } ?: filteredVms.firstOrNull()
        }

        if (isExpandedWidth) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    item {
                        HostTelemetryCard(
                            report = hostReport,
                            onRefresh = onRefreshProbe
                        )
                    }
                    item {
                        FormFactorFilterBar(
                            selectedFilter = selectedFilter,
                            totalCount = vmList.size,
                            onSelectFilter = onFilterChange
                        )
                    }
                    when (vmsState) {
                        UiState.Loading -> item { LoadingVmCard() }
                        is UiState.Error -> item { ErrorVmCard(vmsState.message) }
                        is UiState.Success -> {
                            if (filteredVms.isEmpty()) {
                                item {
                                    EmptyVmStateCard(
                                        onCreateNew = {
                                            editorTargetVm = null
                                            showEditor = true
                                        }
                                    )
                                }
                            } else {
                                items(filteredVms, key = { it.id }) { vm ->
                                    VmEnvironmentCard(
                                        vm = vm,
                                        isSelected = vm.id == activeInspectorVm?.id,
                                        onClick = { onSelectVm(vm.id) },
                                        onLaunchConsole = { onLaunchConsole(vm) },
                                        onTogglePower = { onTogglePower(vm) },
                                        onStop = { onStopVm(vm) },
                                        onEdit = {
                                            editorTargetVm = vm
                                            showEditor = true
                                        },
                                        onSnapshot = {
                                            snapshotTargetVm = vm
                                            snapshotLabel = "${vm.osVersionLabel} Snapshot"
                                        },
                                        onDelete = { deleteConfirmVm = vm }
                                    )
                                }
                            }
                        }
                    }
                }

                // Detail Inspector Pane on Tablets / Foldables
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                ) {
                    if (activeInspectorVm != null) {
                        VmInspectorPane(
                            vm = activeInspectorVm,
                            hostReport = hostReport,
                            onLaunchConsole = { onLaunchConsole(activeInspectorVm) },
                            onEdit = {
                                editorTargetVm = activeInspectorVm
                                showEditor = true
                            },
                            onSnapshot = {
                                snapshotTargetVm = activeInspectorVm
                                snapshotLabel = "${activeInspectorVm.osVersionLabel} Snapshot"
                            }
                        )
                    } else {
                        WorkstationCard(modifier = Modifier.fillMaxWidth()) {
                            Text("VM Hardware Inspector", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Select or create an Android (4–17) or Windows (7–11) environment to inspect its VirtIO GPU, memory allocation, and AVF/QEMU launch spec.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp)
            ) {
                item {
                    HostTelemetryCard(
                        report = hostReport,
                        onRefresh = onRefreshProbe
                    )
                }
                item {
                    FormFactorFilterBar(
                        selectedFilter = selectedFilter,
                        totalCount = vmList.size,
                        onSelectFilter = onFilterChange
                    )
                }
                when (vmsState) {
                    UiState.Loading -> item { LoadingVmCard() }
                    is UiState.Error -> item { ErrorVmCard(vmsState.message) }
                    is UiState.Success -> {
                        if (filteredVms.isEmpty()) {
                            item {
                                EmptyVmStateCard(
                                    onCreateNew = {
                                        editorTargetVm = null
                                        showEditor = true
                                    }
                                )
                            }
                        } else {
                            items(filteredVms, key = { it.id }) { vm ->
                                VmEnvironmentCard(
                                    vm = vm,
                                    isSelected = vm.id == selectedVmId,
                                    onClick = {
                                        onSelectVm(if (selectedVmId == vm.id) null else vm.id)
                                    },
                                    onLaunchConsole = { onLaunchConsole(vm) },
                                    onTogglePower = { onTogglePower(vm) },
                                    onStop = { onStopVm(vm) },
                                    onEdit = {
                                        editorTargetVm = vm
                                        showEditor = true
                                    },
                                    onSnapshot = {
                                        snapshotTargetVm = vm
                                        snapshotLabel = "${vm.osVersionLabel} Snapshot"
                                    },
                                    onDelete = { deleteConfirmVm = vm }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        VmEditorSheet(
            initialVm = editorTargetVm,
            hostReport = hostReport,
            isSaving = isMutating,
            onSave = { updated ->
                onSaveVm(updated) { showEditor = false }
            },
            onDismiss = { showEditor = false }
        )
    }

    if (snapshotTargetVm != null) {
        AlertDialog(
            onDismissRequest = { snapshotTargetVm = null },
            containerColor = SlateElevated,
            title = { Text("Create Cloud Snapshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Save a cloud-synchronized checkpoint for ${snapshotTargetVm?.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = snapshotLabel,
                        onValueChange = { snapshotLabel = it.take(80) },
                        label = { Text("Snapshot Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        snapshotTargetVm?.let { onCreateSnapshot(it, snapshotLabel) }
                        snapshotTargetVm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianCanvas)
                ) {
                    Text("Save Snapshot")
                }
            },
            dismissButton = {
                TextButton(onClick = { snapshotTargetVm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deleteConfirmVm != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmVm = null },
            containerColor = SlateElevated,
            title = { Text("Delete Virtual Machine?") },
            text = {
                Text("Remove \"${deleteConfirmVm?.name}\" from your cloud-synced AdVm workstation? Local GSI image files remain untouched.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmVm?.let(onDeleteVm)
                        deleteConfirmVm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseAlert, contentColor = ObsidianCanvas)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmVm = null }) {
                    Text("Keep")
                }
            }
        )
    }
}

@Composable
private fun HostTelemetryCard(
    report: HostHardwareReport,
    onRefresh: () -> Unit
) {
    val usedRamRatio = ((report.totalRamMb - report.availableRamMb).toFloat() / report.totalRamMb.toFloat())
        .coerceIn(0f, 1f)
    val avfColor = when {
        report.avfServiceAvailable && report.kvmAccessible -> AndroidEmerald
        report.avfFeaturePresent || report.avfServiceAvailable -> TelemetryCyan
        else -> AmberWarn
    }

    WorkstationCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = avfColor.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.deviceModel,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "API ${report.androidApiLevel} (${report.androidRelease}) • ${report.supportedAbis.firstOrNull() ?: "arm64"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TelemetryPill(label = report.avfStatusLabel, accent = avfColor, leadingDot = true)
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_hardware_probe_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh hardware probe", tint = TelemetryCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryPill(label = "${report.cpuCoreCount} Cores", accent = TelemetryCyan)
            TelemetryPill(label = report.vulkanVersionLabel, accent = AndroidEmerald)
            TelemetryPill(
                label = if (report.kvmAccessible) "/dev/kvm RW" else if (report.kvmDevicePresent) "/dev/kvm Locked" else "No /dev/kvm",
                accent = if (report.kvmAccessible) AndroidEmerald else AmberWarn
            )
            TelemetryPill(
                label = if (report.avfFeaturePresent) "AVF Feature" else "Standard Host",
                accent = if (report.avfFeaturePresent) TelemetryCyan else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Host Memory Headroom", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(
                "${report.availableRamMb} MB free / ${report.totalRamMb} MB",
                style = MaterialTheme.typography.labelSmall,
                color = TelemetryCyan
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { usedRamRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = TelemetryCyan,
            trackColor = SlateElevated
        )
    }
}

@Composable
private fun FormFactorFilterBar(
    selectedFilter: FormFactor?,
    totalCount: Int,
    onSelectFilter: (FormFactor?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onSelectFilter(null) },
            label = { Text("All VMs ($totalCount)") },
            modifier = Modifier.testTag("filter_all_vms")
        )
        FilterChip(
            selected = selectedFilter == FormFactor.PHONE,
            onClick = { onSelectFilter(FormFactor.PHONE) },
            label = { Text("Android Phone Mode") },
            leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = AndroidEmerald) },
            modifier = Modifier.testTag("filter_phone_vms")
        )
        FilterChip(
            selected = selectedFilter == FormFactor.PC,
            onClick = { onSelectFilter(FormFactor.PC) },
            label = { Text("Windows PC Mode") },
            leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null, tint = WindowsCobalt) },
            modifier = Modifier.testTag("filter_pc_vms")
        )
    }
}

@Composable
private fun VmEnvironmentCard(
    vm: VmProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLaunchConsole: () -> Unit,
    onTogglePower: () -> Unit,
    onStop: () -> Unit,
    onEdit: () -> Unit,
    onSnapshot: () -> Unit,
    onDelete: () -> Unit
) {
    val isWindows = vm.parsedOsFamily == OsFamily.WINDOWS
    val accent = if (isWindows) WindowsCobalt else AndroidEmerald
    val statusColor = when (vm.parsedStatus) {
        VmStatus.RUNNING -> AndroidEmerald
        VmStatus.PAUSED -> AmberWarn
        VmStatus.STOPPED -> TextMuted
    }

    WorkstationCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("vm_card_${vm.id}"),
        borderColor = if (isSelected) TelemetryCyan else HairlineBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWindows) Icons.Rounded.Computer else Icons.Rounded.Android,
                        contentDescription = vm.osFamily,
                        tint = accent
                    )
                }
                Column {
                    Text(
                        text = vm.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${vm.osVersionLabel} • ${vm.parsedFormFactor.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            TelemetryPill(label = vm.parsedStatus.label, accent = statusColor, leadingDot = true)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryPill(label = "${vm.cpuCores} vCPU", accent = TelemetryCyan)
            TelemetryPill(label = "${vm.ramMb} MB RAM", accent = accent)
            TelemetryPill(label = vm.architecture, accent = TextSecondary)
            TelemetryPill(label = "${vm.displayWidth}×${vm.displayHeight}", accent = TextSecondary)
            if (vm.tpmEnabled) TelemetryPill(label = "vTPM 2.0", accent = WindowsCobalt)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onLaunchConsole,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("launch_console_${vm.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TelemetryCyan,
                    contentColor = ObsidianCanvas
                )
            ) {
                Icon(Icons.Rounded.CastConnected, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Console")
            }

            OutlinedButton(
                onClick = onTogglePower,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("power_toggle_${vm.id}"),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = if (vm.parsedStatus == VmStatus.RUNNING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (vm.parsedStatus == VmStatus.RUNNING) "Pause VM" else "Start VM",
                    tint = statusColor
                )
            }

            if (vm.parsedStatus != VmStatus.STOPPED) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Power off VM", tint = RoseAlert)
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("edit_vm_${vm.id}")
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Configure VM", tint = TextSecondary)
            }
        }

        AnimatedVisibility(visible = isSelected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "VNC Bridge: ${vm.vncHost}:${vm.vncPort} • GPU: ${vm.parsedGpu.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Image: ${vm.imageTitle.ifBlank { "No disk attached yet (attach in GSI Images)" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnapshot,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Snapshot")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete VM", tint = RoseAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun VmInspectorPane(
    vm: VmProfile,
    hostReport: HostHardwareReport,
    onLaunchConsole: () -> Unit,
    onEdit: () -> Unit,
    onSnapshot: () -> Unit
) {
    val launchSpec = remember(vm, hostReport) {
        QemuAvfSpecGenerator.buildLaunchSpec(vm, hostReport)
    }
    WorkstationCard(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        borderColor = TelemetryCyan.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(vm.name, style = MaterialTheme.typography.headlineSmall)
            TelemetryPill(label = vm.osVersionLabel, accent = TelemetryCyan)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Last synced from ${vm.lastSyncedDevice}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TelemetryPill(label = vm.parsedGpu.label, accent = AndroidEmerald)
            TelemetryPill(label = if (vm.uefiEnabled) "UEFI" else "BIOS", accent = WindowsCobalt)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("AVF / QEMU Launch Specification", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = launchSpec,
            style = MaterialTheme.typography.labelSmall,
            color = AndroidEmerald,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(ObsidianCanvas)
                .border(1.dp, HairlineBorder, MaterialTheme.shapes.small)
                .padding(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onLaunchConsole,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianCanvas)
            ) {
                Text("Open VNC Console")
            }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Edit")
            }
            OutlinedButton(
                onClick = onSnapshot,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Snapshot")
            }
        }
    }
}

@Composable
private fun LoadingVmCard() {
    WorkstationCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = TelemetryCyan, strokeWidth = 2.dp)
            Text("Synchronizing VM environments from cloud...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorVmCard(message: String) {
    WorkstationCard(modifier = Modifier.fillMaxWidth(), borderColor = RoseAlert) {
        Text("Cloud Sync Error", style = MaterialTheme.typography.titleMedium, color = RoseAlert)
        Spacer(modifier = Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun EmptyVmStateCard(onCreateNew: () -> Unit) {
    WorkstationCard(modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("No VMs Match This Mode", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Provision an Android (4–17) Phone or Windows (7–11) PC environment with custom CPU, RAM, and VirtIO GPU allocation.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onCreateNew,
            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianCanvas),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Rounded.Memory, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Provision New VM")
        }
    }
}
