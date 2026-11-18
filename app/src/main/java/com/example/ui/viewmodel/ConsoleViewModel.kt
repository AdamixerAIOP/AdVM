package com.example.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.HostHardwareReport
import com.example.data.model.OsImageEntry
import com.example.data.model.VmProfile
import com.example.service.EmbeddedQemuEngine
import com.example.service.EngineLaunchResult
import com.example.service.QemuAvfSpecGenerator
import com.example.service.QmpClient
import com.example.service.RfbVncClient
import com.example.service.VncConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConsoleViewModel : ViewModel() {
    private val vncClient = RfbVncClient()
    private var pollJob: Job? = null
    private var activeEndpoint: Pair<String, Int>? = null

    private val _connectionState = MutableStateFlow<VncConnectionState>(VncConnectionState.Disconnected)
    val connectionState: StateFlow<VncConnectionState> = _connectionState.asStateFlow()

    private val _framebuffer = MutableStateFlow<ImageBitmap?>(null)
    val framebuffer: StateFlow<ImageBitmap?> = _framebuffer.asStateFlow()

    private val _frameCount = MutableStateFlow(0L)
    val frameCount: StateFlow<Long> = _frameCount.asStateFlow()

    private val _autoPollEnabled = MutableStateFlow(true)
    val autoPollEnabled: StateFlow<Boolean> = _autoPollEnabled.asStateFlow()

    private val _qmpStatus = MutableStateFlow<String?>(null)
    val qmpStatus: StateFlow<String?> = _qmpStatus.asStateFlow()

    private val _isQmpBusy = MutableStateFlow(false)
    val isQmpBusy: StateFlow<Boolean> = _isQmpBusy.asStateFlow()

    private val _isLaunchingEngine = MutableStateFlow(false)
    val isLaunchingEngine: StateFlow<Boolean> = _isLaunchingEngine.asStateFlow()

    private val _engineNotice = MutableStateFlow<String?>(null)
    val engineNotice: StateFlow<String?> = _engineNotice.asStateFlow()

    private val _missingNativeBinary = MutableStateFlow<EngineLaunchResult.MissingNativeBinary?>(null)
    val missingNativeBinary: StateFlow<EngineLaunchResult.MissingNativeBinary?> = _missingNativeBinary.asStateFlow()

    fun startConsoleForVm(vm: VmProfile) {
        val target = vm.vncHost to vm.vncPort
        if (activeEndpoint == target && _connectionState.value is VncConnectionState.Connected) {
            return
        }
        _qmpStatus.value = null
        _engineNotice.value = null
        _missingNativeBinary.value = null
        connectToEndpoint(vm.vncHost, vm.vncPort)
    }

    fun launchEmbeddedEngine(
        context: Context,
        vm: VmProfile,
        host: HostHardwareReport?,
        attachedImage: OsImageEntry?
    ) {
        if (attachedImage == null) {
            _engineNotice.value = "Select an OS file (.img, .iso, or .qcow2) first."
            return
        }
        viewModelScope.launch {
            _isLaunchingEngine.value = true
            _missingNativeBinary.value = null
            _engineNotice.value = "Preparing ${attachedImage.title}..."

            when (val res = EmbeddedQemuEngine.launchVm(context.applicationContext, vm, host, attachedImage)) {
                is EngineLaunchResult.Started -> {
                    _engineNotice.value = "Hypervisor running — attaching display..."
                    connectToEndpoint(vm.vncHost, vm.vncPort)
                }
                is EngineLaunchResult.MissingNativeBinary -> {
                    _missingNativeBinary.value = res
                    _engineNotice.value = null
                }
                is EngineLaunchResult.Failed -> {
                    _engineNotice.value = res.reason
                }
            }
            _isLaunchingEngine.value = false
        }
    }

    fun connectToEndpoint(host: String, port: Int) {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val cleanPort = port.coerceIn(1, 65535)
        activeEndpoint = cleanHost to cleanPort
        pollJob?.cancel()

        attemptConnect(cleanHost, cleanPort)

        if (_autoPollEnabled.value) {
            startAutoPollLoop(cleanHost, cleanPort)
        }
    }

    fun setAutoPoll(enabled: Boolean, host: String, port: Int) {
        _autoPollEnabled.value = enabled
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val cleanPort = port.coerceIn(1, 65535)
        if (!enabled) {
            pollJob?.cancel()
            pollJob = null
        } else {
            val state = _connectionState.value
            if (state !is VncConnectionState.Connected) {
                startAutoPollLoop(cleanHost, cleanPort)
            }
        }
    }

    private fun startAutoPollLoop(host: String, port: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && _autoPollEnabled.value) {
                delay(POLL_INTERVAL_MS)
                val current = _connectionState.value
                if (current is VncConnectionState.Error || current is VncConnectionState.Disconnected) {
                    attemptConnect(host, port)
                } else if (current is VncConnectionState.Connected) {
                    _missingNativeBinary.value = null
                    break
                }
            }
        }
    }

    private fun attemptConnect(host: String, port: Int) {
        _framebuffer.value = null
        _frameCount.value = 0L
        vncClient.connect(
            host = host,
            port = port,
            scope = viewModelScope,
            onState = { state ->
                _connectionState.value = state
                if (state is VncConnectionState.Connected) {
                    _missingNativeBinary.value = null
                    _engineNotice.value = null
                }
            },
            onFrame = { bitmap ->
                _framebuffer.value = bitmap
                _frameCount.value = _frameCount.value + 1L
            }
        )
    }

    fun findAttachedImage(vm: VmProfile, images: List<OsImageEntry>): OsImageEntry? {
        if (vm.imageId.isNotBlank()) {
            images.firstOrNull { it.id == vm.imageId }?.let { return it }
        }
        if (vm.imageTitle.isNotBlank()) {
            images.firstOrNull { it.title.equals(vm.imageTitle, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    fun sendPointer(x: Int, y: Int, buttonMask: Int) {
        vncClient.sendPointerEvent(viewModelScope, x, y, buttonMask)
    }

    fun sendTap(x: Int, y: Int, rightClick: Boolean = false) {
        val mask = if (rightClick) 4 else 1
        vncClient.sendPointerEvent(viewModelScope, x, y, mask)
        vncClient.sendPointerEvent(viewModelScope, x, y, 0)
    }

    fun sendSpecialKey(keysym: Int) {
        vncClient.sendKeyPress(viewModelScope, keysym)
    }

    fun sendCtrlAltDel() {
        vncClient.sendCombo(
            viewModelScope,
            RfbVncClient.KeySyms.CTRL_LEFT,
            RfbVncClient.KeySyms.ALT_LEFT,
            RfbVncClient.KeySyms.DELETE
        )
    }

    fun sendText(text: String) {
        text.forEach { ch ->
            when (ch) {
                '\n' -> vncClient.sendKeyPress(viewModelScope, RfbVncClient.KeySyms.RETURN)
                '\t' -> vncClient.sendKeyPress(viewModelScope, RfbVncClient.KeySyms.TAB)
                else -> if (ch.code in 0x20..0x7E) {
                    vncClient.sendKeyPress(viewModelScope, ch.code)
                }
            }
        }
    }

    fun pauseGuest(host: String, vncPort: Int) = runQmpAction("Pause VM") {
        QmpClient.pauseVm(host, vncPort).map { "Guest CPU paused." }
    }

    fun resumeGuest(host: String, vncPort: Int) = runQmpAction("Resume VM") {
        QmpClient.resumeVm(host, vncPort).map { "Guest CPU resumed." }
    }

    fun acpiShutdown(host: String, vncPort: Int, onIssued: () -> Unit = {}) = runQmpAction("Power Off") {
        QmpClient.acpiPowerDown(host, vncPort).map {
            EmbeddedQemuEngine.stopActiveProcess()
            onIssued()
            "ACPI power-down sent."
        }
    }

    fun hardReset(host: String, vncPort: Int) = runQmpAction("Reset") {
        QmpClient.hardReset(host, vncPort).map { "Hardware reset issued." }
    }

    fun saveLiveVmSnapshot(
        host: String,
        vncPort: Int,
        label: String,
        onMetadataRecord: () -> Unit
    ) {
        runQmpAction("SaveVM") {
            QmpClient.saveLiveSnapshot(host, vncPort, label).map {
                onMetadataRecord()
                "Live snapshot \"$label\" saved."
            }
        }
    }

    private fun runQmpAction(actionName: String, block: suspend () -> Result<String>) {
        if (_isQmpBusy.value) return
        viewModelScope.launch {
            _isQmpBusy.value = true
            val result = block()
            _isQmpBusy.value = false
            _qmpStatus.value = result.getOrElse { err ->
                "$actionName failed: ${err.localizedMessage ?: "Engine unreachable"}"
            }
        }
    }

    fun clearQmpStatus() {
        _qmpStatus.value = null
    }

    fun launchCommandFor(
        vm: VmProfile,
        host: HostHardwareReport?,
        images: List<OsImageEntry> = emptyList()
    ): String {
        val attached = findAttachedImage(vm, images)
        return QemuAvfSpecGenerator.buildLaunchSpec(vm, host, attached)
    }

    fun stopAndDisconnect() {
        pollJob?.cancel()
        pollJob = null
        activeEndpoint = null
        vncClient.disconnect()
        EmbeddedQemuEngine.stopActiveProcess()
        _framebuffer.value = null
        _connectionState.value = VncConnectionState.Disconnected
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        activeEndpoint = null
        vncClient.disconnect()
        _framebuffer.value = null
        _connectionState.value = VncConnectionState.Disconnected
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        vncClient.disconnect()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2500L
    }
}
