package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HostHardwareReport
import com.example.data.model.VmProfile
import com.example.service.RfbVncClient
import com.example.service.VncConnectionState
import com.example.ui.components.TelemetryPill
import com.example.ui.components.WorkstationCard
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ConsoleViewModel
import kotlin.math.roundToInt

@Composable
fun VncConsoleScreen(
    vm: VmProfile,
    hostReport: HostHardwareReport,
    consoleViewModel: ConsoleViewModel,
    onBack: () -> Unit
) {
    val connectionState by consoleViewModel.connectionState.collectAsStateWithLifecycle()
    val framebuffer by consoleViewModel.framebuffer.collectAsStateWithLifecycle()
    val frameCount by consoleViewModel.frameCount.collectAsStateWithLifecycle()

    var hostOverride by remember(vm.id) { mutableStateOf(vm.vncHost) }
    var portOverride by remember(vm.id) { mutableStateOf(vm.vncPort.toString()) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textToSend by remember { mutableStateOf("") }
    var copiedSpec by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    BackHandler {
        consoleViewModel.disconnect()
        onBack()
    }

    LaunchedEffect(vm.id) {
        consoleViewModel.connectToVm(vm)
    }

    DisposableEffect(Unit) {
        onDispose {
            consoleViewModel.disconnect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Telemetry Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface)
                .border(1.dp, HairlineBorder)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        consoleViewModel.disconnect()
                        onBack()
                    },
                    modifier = Modifier.testTag("console_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit VM console")
                }
                Column {
                    Text(vm.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${vm.osVersionLabel} • ${vm.parsedFormFactor.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            when (val st = connectionState) {
                is VncConnectionState.Connected -> {
                    TelemetryPill(
                        label = "${st.width}×${st.height} • ${st.latencyMs}ms • F$frameCount",
                        accent = AndroidEmerald,
                        leadingDot = true
                    )
                }
                is VncConnectionState.Connecting -> {
                    TelemetryPill(label = "Connecting", accent = TelemetryCyan, leadingDot = true)
                }
                is VncConnectionState.Error -> {
                    TelemetryPill(label = "Bridge Offline", accent = AmberWarn, leadingDot = true)
                }
                VncConnectionState.Disconnected -> {
                    TelemetryPill(label = "Disconnected", accent = TextSecondary)
                }
            }

            IconButton(
                onClick = {
                    val port = portOverride.toIntOrNull()?.coerceIn(1, 65535) ?: vm.vncPort
                    consoleViewModel.connectToVm(vm.copy(vncHost = hostOverride, vncPort = port))
                },
                modifier = Modifier.testTag("console_reconnect_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reconnect VNC", tint = TelemetryCyan)
            }
        }

        // Main Framebuffer / Bridge Diagnostics Viewport
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val bmp = framebuffer
            if (bmp != null && connectionState is VncConnectionState.Connected) {
                val fbW = bmp.width.toFloat().coerceAtLeast(1f)
                val fbH = bmp.height.toFloat().coerceAtLeast(1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(bmp.width, bmp.height) {
                            detectTapGestures { offset ->
                                val scale = minOf(size.width / fbW, size.height / fbH)
                                val drawW = fbW * scale
                                val drawH = fbH * scale
                                val left = (size.width - drawW) / 2f
                                val top = (size.height - drawH) / 2f
                                val gx = (((offset.x - left) / scale).roundToInt()).coerceIn(0, bmp.width - 1)
                                val gy = (((offset.y - top) / scale).roundToInt()).coerceIn(0, bmp.height - 1)
                                consoleViewModel.sendPointer(gx, gy, pressed = true)
                                consoleViewModel.sendPointer(gx, gy, pressed = false)
                            }
                        }
                ) {
                    val scale = minOf(size.width / fbW, size.height / fbH)
                    val drawW = (fbW * scale).roundToInt()
                    val drawH = (fbH * scale).roundToInt()
                    val left = ((size.width - drawW) / 2f).roundToInt()
                    val top = ((size.height - drawH) / 2f).roundToInt()
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(drawW, drawH)
                    )
                }
            } else {
                val launchCmd = remember(vm, hostReport) {
                    consoleViewModel.launchCommandFor(vm, hostReport)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (connectionState is VncConnectionState.Connecting) {
                        CircularProgressIndicator(color = TelemetryCyan)
                        Text(
                            "Connecting RFB/VNC to $hostOverride:$portOverride...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        WorkstationCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = AmberWarn.copy(alpha = 0.5f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = AmberWarn)
                                Text("Hypervisor VNC Bridge Standby", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (connectionState is VncConnectionState.Error) {
                                Text(
                                    text = (connectionState as VncConnectionState.Error).message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoseAlert
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(
                                text = "Host Status: ${hostReport.avfStatusLabel}. Start your local AVF/QEMU instance (e.g. via Termux or LAN workstation) listening on VNC without auth, or point this console to your host IP:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = hostOverride,
                                    onValueChange = { hostOverride = it },
                                    label = { Text("VNC Host") },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f)
                                )
                                OutlinedTextField(
                                    value = portOverride,
                                    onValueChange = { portOverride = it.filter(Char::isDigit).take(5) },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val port = portOverride.toIntOrNull()?.coerceIn(1, 65535) ?: vm.vncPort
                                    consoleViewModel.connectToVm(vm.copy(vncHost = hostOverride, vncPort = port))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("console_connect_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TelemetryCyan,
                                    contentColor = ObsidianCanvas
                                )
                            ) {
                                Text("Connect RFB / VNC Console")
                            }
                        }

                        WorkstationCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Generated QEMU / AVF Command", style = MaterialTheme.typography.titleSmall)
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(launchCmd))
                                        copiedSpec = true
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy command", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (copiedSpec) "Copied" else "Copy")
                                }
                            }
                            Text(
                                text = launchCmd,
                                style = MaterialTheme.typography.labelSmall,
                                color = AndroidEmerald,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(ObsidianCanvas)
                                    .border(1.dp, HairlineBorder, MaterialTheme.shapes.small)
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Power-User Keybar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SlateSurface)
                .border(1.dp, HairlineBorder)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConsoleKeyChip("Esc") { consoleViewModel.sendSpecialKey(RfbVncClient.KeySyms.ESCAPE) }
            ConsoleKeyChip("Ctrl+Alt+Del") { consoleViewModel.sendCtrlAltDel() }
            ConsoleKeyChip("Win / Home") { consoleViewModel.sendSpecialKey(RfbVncClient.KeySyms.SUPER_LEFT) }
            ConsoleKeyChip("Tab") { consoleViewModel.sendSpecialKey(RfbVncClient.KeySyms.TAB) }
            ConsoleKeyChip("Enter") { consoleViewModel.sendSpecialKey(RfbVncClient.KeySyms.RETURN) }
            OutlinedButton(
                onClick = { showTextDialog = true },
                modifier = Modifier.heightIn(min = 48.dp),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send Text", color = TelemetryCyan)
            }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            containerColor = SlateElevated,
            title = { Text("Send Keystrokes to VM") },
            text = {
                OutlinedTextField(
                    value = textToSend,
                    onValueChange = { textToSend = it },
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        consoleViewModel.sendText(textToSend)
                        textToSend = ""
                        showTextDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianCanvas)
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConsoleKeyChip(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
