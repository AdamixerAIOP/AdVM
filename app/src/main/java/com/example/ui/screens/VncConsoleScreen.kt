package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HostHardwareReport
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.VmProfile
import com.example.service.RfbVncClient
import com.example.service.VncConnectionState
import com.example.ui.components.HumanCaptchaDialog
import com.example.ui.components.TelemetryPill
import com.example.ui.components.WorkstationCard
import com.example.ui.components.formatBytes
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WindowsCobalt
import com.example.ui.viewmodel.ConsoleViewModel
import kotlin.math.roundToInt

@Composable
fun VncConsoleScreen(
    vm: VmProfile,
    images: List<OsImageEntry>,
    hostReport: HostHardwareReport,
    consoleViewModel: ConsoleViewModel,
    autoBootVerified: Boolean = false,
    onConsumeAutoBoot: () -> Unit = {},
    onAttachImage: (OsImageEntry) -> Unit,
    onImportLocalFile: (Uri) -> Unit,
    onBrowseImages: () -> Unit,
    onCreateSnapshot: (VmProfile, String) -> Unit,
    onPowerOff: () -> Unit,
    onMinimize: () -> Unit
) {
    val context = LocalContext.current

    val connectionState by consoleViewModel.connectionState.collectAsStateWithLifecycle()
    val framebuffer by consoleViewModel.framebuffer.collectAsStateWithLifecycle()
    val frameCount by consoleViewModel.frameCount.collectAsStateWithLifecycle()
    val qmpStatus by consoleViewModel.qmpStatus.collectAsStateWithLifecycle()
    val isQmpBusy by consoleViewModel.isQmpBusy.collectAsStateWithLifecycle()
    val isLaunchingEngine by consoleViewModel.isLaunchingEngine.collectAsStateWithLifecycle()
    val engineNotice by consoleViewModel.engineNotice.collectAsStateWithLifecycle()
    val missingNativeBinary by consoleViewModel.missingNativeBinary.collectAsStateWithLifecycle()

    val attachedImage = remember(vm.imageId, vm.imageTitle, images) {
        consoleViewModel.findAttachedImage(vm, images)
    }

    var rightClickMode by remember { mutableStateOf(false) }
    var showTypeTextDialog by remember { mutableStateOf(false) }
    var typedText by remember { mutableStateOf("") }
    var appendEnterKey by remember { mutableStateOf(true) }
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var snapshotLabel by remember(vm.id) { mutableStateOf("${vm.osVersionLabel}_live") }
    var showFilePickerSheet by remember { mutableStateOf(false) }
    var showBootCaptcha by remember { mutableStateOf(false) }

    val pickOsFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalFile(uri)
            showFilePickerSheet = false
        }
    }

    LaunchedEffect(vm.id) {
        consoleViewModel.startConsoleForVm(vm)
    }

    LaunchedEffect(vm.id, autoBootVerified, attachedImage?.id) {
        if (autoBootVerified && attachedImage != null) {
            onConsumeAutoBoot()
            consoleViewModel.launchEmbeddedEngine(
                context = context,
                vm = vm,
                host = hostReport,
                attachedImage = attachedImage
            )
        }
    }

    BackHandler {
        consoleViewModel.disconnect()
        onMinimize()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ConsoleTopBar(
            vm = vm,
            hasFile = attachedImage != null,
            connectionState = connectionState,
            frameCount = frameCount,
            onBack = {
                consoleViewModel.disconnect()
                onMinimize()
            },
            onReconnect = {
                consoleViewModel.connectToEndpoint(vm.vncHost, vm.vncPort)
            }
        )

        AnimatedVisibility(visible = !qmpStatus.isNullOrBlank()) {
            Surface(
                color = SlateElevated,
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = qmpStatus.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TelemetryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { consoleViewModel.clearQmpStatus() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss status", tint = TextSecondary)
                    }
                }
            }
        }

        val isConnected = framebuffer != null && connectionState is VncConnectionState.Connected
        if (isConnected) {
            LiveVncCanvas(
                framebuffer = framebuffer!!,
                rightClickMode = rightClickMode,
                onTap = { x, y, rightClick -> consoleViewModel.sendTap(x, y, rightClick) },
                onPointerMove = { x, y, buttonMask -> consoleViewModel.sendPointer(x, y, buttonMask) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
                    .testTag("live_vnc_canvas")
            )

            HypervisorQmpAndKeyBar(
                isQmpBusy = isQmpBusy,
                rightClickMode = rightClickMode,
                onToggleRightClick = { rightClickMode = !rightClickMode },
                onPause = { consoleViewModel.pauseGuest(vm.vncHost, vm.vncPort) },
                onResume = { consoleViewModel.resumeGuest(vm.vncHost, vm.vncPort) },
                onHardReset = { consoleViewModel.hardReset(vm.vncHost, vm.vncPort) },
                onOpenSnapshot = { showSnapshotDialog = true },
                onAcpiShutdown = {
                    consoleViewModel.acpiShutdown(vm.vncHost, vm.vncPort) {
                        onPowerOff()
                    }
                },
                onSpecialKey = { consoleViewModel.sendSpecialKey(it) },
                onCtrlAltDel = { consoleViewModel.sendCtrlAltDel() },
                onOpenTypeText = { showTypeTextDialog = true }
            )
        } else {
            val isWindows = vm.parsedOsFamily == OsFamily.WINDOWS
            val accent = if (isWindows) WindowsCobalt else AndroidEmerald

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWindows) Icons.Rounded.Computer else Icons.Rounded.Android,
                        contentDescription = vm.osVersionLabel,
                        tint = accent,
                        modifier = Modifier.size(44.dp)
                    )
                }

                if (attachedImage == null || showFilePickerSheet) {
                    Text(
                        text = "Insert an OS File to Boot",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Download or select a real .img, .iso, or .qcow2 file to run ${vm.osVersionLabel} directly inside AdVm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = { pickOsFileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("pick_os_file_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = ObsidianCanvas
                        )
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Insert OS File from Phone", fontWeight = FontWeight.SemiBold)
                    }

                    if (images.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Or pick a downloaded image from your Library:",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            images.forEach { img ->
                                WorkstationCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAttachImage(img)
                                            showFilePickerSheet = false
                                        }
                                        .testTag("attach_image_${img.id}"),
                                    borderColor = if (img.id == vm.imageId) accent else HairlineBorder
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = accent)
                                            Column {
                                                Text(img.title, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    text = "${img.parsedFormat.label}${if (img.sizeBytes > 0) " • ${formatBytes(img.sizeBytes)}" else ""}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        TelemetryPill(label = "SELECT", accent = accent)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBrowseImages,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download OS File")
                        }
                        if (attachedImage != null && showFilePickerSheet) {
                            OutlinedButton(
                                onClick = { showFilePickerSheet = false },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                } else {
                    Text(
                        text = vm.name,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )

                    WorkstationCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = accent.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null, tint = accent)
                                Column {
                                    Text(
                                        text = attachedImage.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${attachedImage.parsedFormat.label} • ${vm.cpuCores} vCPU • ${vm.ramMb} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showFilePickerSheet = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change", color = TelemetryCyan)
                            }
                        }
                    }

                    Button(
                        onClick = { showBootCaptcha = true },
                        enabled = !isLaunchingEngine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("boot_os_file_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = ObsidianCanvas
                        )
                    ) {
                        if (isLaunchingEngine || connectionState is VncConnectionState.Connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ObsidianCanvas,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Booting ${vm.osVersionLabel}...", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Boot OS", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!engineNotice.isNullOrBlank()) {
                        WorkstationCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = TelemetryCyan.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = engineNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TelemetryCyan
                            )
                        }
                    }

                    missingNativeBinary?.let { missing ->
                        WorkstationCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = AmberWarn.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = "OS File Verified — Native Hypervisor Binary Needed",
                                style = MaterialTheme.typography.titleSmall,
                                color = AmberWarn
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Human verification passed and your OS file is staged (${missing.verifiedDiskPath}). To execute real guest OS binaries directly inside this APK with no external app or terminal, package Android NDK-compiled `${missing.expectedLibName}` in `app/src/main/jniLibs/${missing.abi}/` (not present in this Kotlin-only build container).",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            consoleViewModel.stopAndDisconnect()
                            onPowerOff()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("console_power_off_button"),
                        border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f))
                    ) {
                        Text("Power Off VM", color = RoseAlert)
                    }
                }
            }
        }
    }

    if (showBootCaptcha) {
        HumanCaptchaDialog(
            vmName = vm.name,
            onVerified = {
                showBootCaptcha = false
                consoleViewModel.launchEmbeddedEngine(
                    context = context,
                    vm = vm,
                    host = hostReport,
                    attachedImage = attachedImage
                )
            },
            onDismiss = { showBootCaptcha = false }
        )
    }

    if (showTypeTextDialog) {
        AlertDialog(
            onDismissRequest = { showTypeTextDialog = false },
            containerColor = SlateElevated,
            title = { Text("Send Keystrokes to Guest") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = { typedText = it },
                        label = { Text("Command or Text (ASCII)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = appendEnterKey, onCheckedChange = { appendEnterKey = it })
                        Text("Press Enter after typing", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        consoleViewModel.sendText(if (appendEnterKey) "$typedText\n" else typedText)
                        typedText = ""
                        showTypeTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianCanvas)
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTypeTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSnapshotDialog) {
        AlertDialog(
            onDismissRequest = { showSnapshotDialog = false },
            containerColor = SlateElevated,
            title = { Text("Save Live VM Snapshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Saves a live checkpoint (`savevm`) on QCOW2 disks and records it in AdVm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = snapshotLabel,
                        onValueChange = { snapshotLabel = it.take(60) },
                        label = { Text("Snapshot Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val label = snapshotLabel.ifBlank { "snap" }
                        consoleViewModel.saveLiveVmSnapshot(vm.vncHost, vm.vncPort, label) {
                            onCreateSnapshot(vm, label)
                        }
                        showSnapshotDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianCanvas)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSnapshotDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConsoleTopBar(
    vm: VmProfile,
    hasFile: Boolean,
    connectionState: VncConnectionState,
    frameCount: Long,
    onBack: () -> Unit,
    onReconnect: () -> Unit
) {
    Surface(
        color = SlateSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("console_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to VMs",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vm.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${vm.osVersionLabel} • ${vm.cpuCores} vCPU • ${vm.ramMb} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                connectionState is VncConnectionState.Connected -> {
                    TelemetryPill(
                        label = "${connectionState.width}×${connectionState.height} • F$frameCount",
                        accent = AndroidEmerald,
                        leadingDot = true
                    )
                }
                !hasFile -> {
                    TelemetryPill(label = "NEEDS FILE", accent = AmberWarn, leadingDot = true)
                }
                connectionState is VncConnectionState.Connecting -> {
                    TelemetryPill(label = "BOOTING", accent = TelemetryCyan, leadingDot = true)
                }
                else -> {
                    TelemetryPill(label = "READY", accent = TelemetryCyan, leadingDot = true)
                }
            }

            IconButton(
                onClick = onReconnect,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("console_reconnect_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh display connection",
                    tint = TelemetryCyan
                )
            }
        }
    }
}

@Composable
private fun LiveVncCanvas(
    framebuffer: ImageBitmap,
    rightClickMode: Boolean,
    onTap: (Int, Int, Boolean) -> Unit,
    onPointerMove: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fbW = framebuffer.width.toFloat().coerceAtLeast(1f)
    val fbH = framebuffer.height.toFloat().coerceAtLeast(1f)

    fun mapOffset(offset: Offset, viewW: Int, viewH: Int): Pair<Int, Int> {
        val scale = minOf(viewW / fbW, viewH / fbH).coerceAtLeast(0.001f)
        val drawW = fbW * scale
        val drawH = fbH * scale
        val left = (viewW - drawW) / 2f
        val top = (viewH - drawH) / 2f
        val gx = (((offset.x - left) / scale).roundToInt()).coerceIn(0, framebuffer.width - 1)
        val gy = (((offset.y - top) / scale).roundToInt()).coerceIn(0, framebuffer.height - 1)
        return gx to gy
    }

    Canvas(
        modifier = modifier
            .pointerInput(framebuffer.width, framebuffer.height, rightClickMode) {
                detectTapGestures { offset ->
                    val (gx, gy) = mapOffset(offset, size.width, size.height)
                    onTap(gx, gy, rightClickMode)
                }
            }
            .pointerInput(framebuffer.width, framebuffer.height) {
                var lastX = 0
                var lastY = 0
                detectDragGestures(
                    onDragStart = { offset ->
                        val (gx, gy) = mapOffset(offset, size.width, size.height)
                        lastX = gx
                        lastY = gy
                        onPointerMove(gx, gy, 1)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val (gx, gy) = mapOffset(change.position, size.width, size.height)
                        lastX = gx
                        lastY = gy
                        onPointerMove(gx, gy, 1)
                    },
                    onDragEnd = { onPointerMove(lastX, lastY, 0) },
                    onDragCancel = { onPointerMove(lastX, lastY, 0) }
                )
            }
    ) {
        val scale = minOf(size.width / fbW, size.height / fbH)
        val drawW = (fbW * scale).roundToInt()
        val drawH = (fbH * scale).roundToInt()
        val left = ((size.width - drawW) / 2f).roundToInt()
        val top = ((size.height - drawH) / 2f).roundToInt()
        drawImage(
            image = framebuffer,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(drawW, drawH)
        )
    }
}

@Composable
private fun HypervisorQmpAndKeyBar(
    isQmpBusy: Boolean,
    rightClickMode: Boolean,
    onToggleRightClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onHardReset: () -> Unit,
    onOpenSnapshot: () -> Unit,
    onAcpiShutdown: () -> Unit,
    onSpecialKey: (Int) -> Unit,
    onCtrlAltDel: () -> Unit,
    onOpenTypeText: () -> Unit
) {
    Surface(
        color = SlateSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onPause, enabled = !isQmpBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause")
                }
                OutlinedButton(onClick = onResume, enabled = !isQmpBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume")
                }
                OutlinedButton(onClick = onHardReset, enabled = !isQmpBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
                OutlinedButton(onClick = onOpenSnapshot, enabled = !isQmpBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Snapshot")
                }
                OutlinedButton(
                    onClick = onAcpiShutdown,
                    enabled = !isQmpBusy,
                    border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.5f)),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = RoseAlert, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Power Off", color = RoseAlert)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = rightClickMode,
                    onClick = onToggleRightClick,
                    label = { Text("Right-Click") }
                )
                TextButton(onClick = onOpenTypeText, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Type")
                }
                TextButton(onClick = { onSpecialKey(RfbVncClient.KeySyms.ESCAPE) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Esc") }
                TextButton(onClick = { onSpecialKey(RfbVncClient.KeySyms.TAB) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tab") }
                TextButton(onClick = { onSpecialKey(RfbVncClient.KeySyms.SUPER_LEFT) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Win/Home") }
                TextButton(onClick = onCtrlAltDel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ctrl+Alt+Del") }
                TextButton(onClick = { onSpecialKey(RfbVncClient.KeySyms.RETURN) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Enter") }
                TextButton(onClick = { onSpecialKey(RfbVncClient.KeySyms.BACKSPACE) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Bksp") }
            }
        }
    }
}
