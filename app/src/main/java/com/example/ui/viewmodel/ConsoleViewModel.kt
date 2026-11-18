package com.example.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.HostHardwareReport
import com.example.data.model.VmProfile
import com.example.service.QemuAvfSpecGenerator
import com.example.service.RfbVncClient
import com.example.service.VncConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConsoleViewModel : ViewModel() {
    private val vncClient = RfbVncClient()

    private val _connectionState = MutableStateFlow<VncConnectionState>(VncConnectionState.Disconnected)
    val connectionState: StateFlow<VncConnectionState> = _connectionState.asStateFlow()

    private val _framebuffer = MutableStateFlow<ImageBitmap?>(null)
    val framebuffer: StateFlow<ImageBitmap?> = _framebuffer.asStateFlow()

    private val _frameCount = MutableStateFlow(0L)
    val frameCount: StateFlow<Long> = _frameCount.asStateFlow()

    fun connectToVm(vm: VmProfile) {
        _framebuffer.value = null
        _frameCount.value = 0L
        vncClient.connect(
            host = vm.vncHost,
            port = vm.vncPort,
            scope = viewModelScope,
            onState = { state -> _connectionState.value = state },
            onFrame = { bitmap ->
                _framebuffer.value = bitmap
                _frameCount.value = _frameCount.value + 1L
            }
        )
    }

    fun sendPointer(x: Int, y: Int, pressed: Boolean) {
        vncClient.sendPointerEvent(viewModelScope, x, y, if (pressed) 1 else 0)
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
            vncClient.sendKeyPress(viewModelScope, ch.code)
        }
    }

    fun launchCommandFor(vm: VmProfile, host: HostHardwareReport?): String =
        QemuAvfSpecGenerator.buildLaunchSpec(vm, host)

    fun disconnect() {
        vncClient.disconnect()
        _connectionState.value = VncConnectionState.Disconnected
    }

    override fun onCleared() {
        super.onCleared()
        vncClient.disconnect()
    }
}
