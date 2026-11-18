package com.example.service

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import android.graphics.Bitmap

sealed interface VncConnectionState {
    data object Disconnected : VncConnectionState
    data class Connecting(val endpoint: String) : VncConnectionState
    data class Connected(
        val desktopName: String,
        val width: Int,
        val height: Int,
        val latencyMs: Long
    ) : VncConnectionState
    data class Error(val message: String) : VncConnectionState
}

/**
 * Pure-Kotlin RFB 3.3/3.8 VNC client for connecting to local AVF/QEMU or remote LAN hosts.
 * Supports Raw and CopyRect encodings over TCP with interactive pointer & X11 key events.
 */
class RfbVncClient {
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var readerJob: Job? = null
    private var fbWidth = 0
    private var fbHeight = 0
    private var pixelBuffer = IntArray(0)

    fun connect(
        host: String,
        port: Int,
        scope: CoroutineScope,
        onState: (VncConnectionState) -> Unit,
        onFrame: (ImageBitmap) -> Unit
    ) {
        disconnect()
        val endpoint = "$host:$port"
        onState(VncConnectionState.Connecting(endpoint))

        readerJob = scope.launch(Dispatchers.IO) {
            try {
                val startMs = System.currentTimeMillis()
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), 5000)
                sock.soTimeout = 15000
                socket = sock

                val input = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(sock.getOutputStream())
                output = out

                // 1. ProtocolVersion handshake (12 bytes: "RFB 003.008\n")
                val serverVersionBytes = ByteArray(12)
                input.readFully(serverVersionBytes)
                val serverVersion = String(serverVersionBytes, Charsets.US_ASCII)
                if (!serverVersion.startsWith("RFB ")) {
                    throw IllegalStateException("Endpoint is not an RFB/VNC server ($serverVersion)")
                }
                val use38 = serverVersion.contains("003.008") || serverVersion.contains("003.007")
                val clientVer = if (use38) "RFB 003.008\n" else "RFB 003.003\n"
                out.write(clientVer.toByteArray(Charsets.US_ASCII))
                out.flush()

                // 2. Security negotiation
                if (use38) {
                    val count = input.readUnsignedByte()
                    if (count == 0) {
                        val reasonLen = input.readInt().coerceIn(0, 4096)
                        val reason = ByteArray(reasonLen).also { input.readFully(it) }
                        throw IllegalStateException("VNC rejected handshake: ${String(reason)}")
                    }
                    val types = ByteArray(count).also { input.readFully(it) }
                    if (types.none { it.toInt() == 1 }) {
                        throw IllegalStateException("VNC server requires authentication (only SecurityType None is enabled for this console).")
                    }
                    out.writeByte(1) // None
                    out.flush()
                    val secResult = input.readInt()
                    if (secResult != 0) {
                        throw IllegalStateException("VNC security handshake failed (code $secResult)")
                    }
                } else {
                    val secType = input.readInt()
                    if (secType == 0) {
                        throw IllegalStateException("VNC connection failed in RFB 3.3")
                    }
                    if (secType != 1) {
                        throw IllegalStateException("VNC server requires password auth ($secType)")
                    }
                }

                // 3. ClientInit (shared flag = 1)
                out.writeByte(1)
                out.flush()

                // 4. ServerInit
                fbWidth = input.readUnsignedShort().coerceIn(1, 4096)
                fbHeight = input.readUnsignedShort().coerceIn(1, 4096)
                val pixelFormat = ByteArray(16).also { input.readFully(it) }
                val nameLen = input.readInt().coerceIn(0, 1024)
                val nameBytes = ByteArray(nameLen).also { input.readFully(it) }
                val desktopName = String(nameBytes, Charsets.UTF_8).ifBlank { endpoint }
                pixelBuffer = IntArray(fbWidth * fbHeight)

                // Request 32-bpp little-endian RGBX pixel format for fast ARGB_8888 rendering
                sendSetPixelFormat32bpp(out)
                // Supported encodings: CopyRect (1), Raw (0)
                sendSetEncodings(out)

                val latency = System.currentTimeMillis() - startMs
                withContext(Dispatchers.Main) {
                    onState(VncConnectionState.Connected(desktopName, fbWidth, fbHeight, latency))
                }

                // Request initial full framebuffer
                sendFramebufferUpdateRequest(out, incremental = false, 0, 0, fbWidth, fbHeight)

                // Read server messages
                sock.soTimeout = 0
                while (isActive && !sock.isClosed) {
                    val msgType = input.readUnsignedByte()
                    when (msgType) {
                        0 -> { // FramebufferUpdate
                            input.readUnsignedByte() // padding
                            val numRects = input.readUnsignedShort()
                            for (r in 0 until numRects) {
                                val rx = input.readUnsignedShort()
                                val ry = input.readUnsignedShort()
                                val rw = input.readUnsignedShort()
                                val rh = input.readUnsignedShort()
                                val encoding = input.readInt()
                                when (encoding) {
                                    0 -> readRawRect(input, rx, ry, rw, rh)
                                    1 -> readCopyRect(input, rx, ry, rw, rh)
                                    else -> throw IllegalStateException("Unsupported encoding $encoding")
                                }
                            }
                            val bmp = Bitmap.createBitmap(pixelBuffer, fbWidth, fbHeight, Bitmap.Config.ARGB_8888)
                            val imageBitmap = bmp.asImageBitmap()
                            withContext(Dispatchers.Main) {
                                onFrame(imageBitmap)
                            }
                            sendFramebufferUpdateRequest(out, incremental = true, 0, 0, fbWidth, fbHeight)
                        }
                        1 -> { // SetColourMapEntries
                            input.readUnsignedByte()
                            input.readUnsignedShort()
                            val nColors = input.readUnsignedShort()
                            input.skipBytes(nColors * 6)
                        }
                        2 -> { /* Bell */ }
                        3 -> { // ServerCutText
                            input.skipBytes(3)
                            val len = input.readInt().coerceAtLeast(0)
                            input.skipBytes(len)
                        }
                        else -> throw IllegalStateException("Unknown RFB message type $msgType")
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        onState(
                            VncConnectionState.Error(
                                e.localizedMessage ?: "Unable to connect to $endpoint"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readRawRect(input: DataInputStream, rx: Int, ry: Int, rw: Int, rh: Int) {
        val rowBytes = ByteArray(rw * 4)
        for (row in 0 until rh) {
            input.readFully(rowBytes)
            val py = ry + row
            if (py >= fbHeight) continue
            val rowOffset = py * fbWidth
            for (col in 0 until rw) {
                val px = rx + col
                if (px >= fbWidth) continue
                val i = col * 4
                val b = rowBytes[i].toInt() and 0xFF
                val g = rowBytes[i + 1].toInt() and 0xFF
                val r = rowBytes[i + 2].toInt() and 0xFF
                pixelBuffer[rowOffset + px] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun readCopyRect(input: DataInputStream, rx: Int, ry: Int, rw: Int, rh: Int) {
        val srcX = input.readUnsignedShort()
        val srcY = input.readUnsignedShort()
        val copy = IntArray(rw * rh)
        for (row in 0 until rh) {
            val sy = srcY + row
            if (sy >= fbHeight) continue
            for (col in 0 until rw) {
                val sx = srcX + col
                if (sx >= fbWidth) continue
                copy[row * rw + col] = pixelBuffer[sy * fbWidth + sx]
            }
        }
        for (row in 0 until rh) {
            val dy = ry + row
            if (dy >= fbHeight) continue
            for (col in 0 until rw) {
                val dx = rx + col
                if (dx >= fbWidth) continue
                pixelBuffer[dy * fbWidth + dx] = copy[row * rw + col]
            }
        }
    }

    private fun sendSetPixelFormat32bpp(out: DataOutputStream) {
        out.writeByte(0) // message-type SetPixelFormat
        out.writeByte(0); out.writeByte(0); out.writeByte(0) // padding
        out.writeByte(32) // bits-per-pixel
        out.writeByte(24) // depth
        out.writeByte(0)  // big-endian-flag (little-endian)
        out.writeByte(1)  // true-colour-flag
        out.writeShort(255) // red-max
        out.writeShort(255) // green-max
        out.writeShort(255) // blue-max
        out.writeByte(16)   // red-shift
        out.writeByte(8)    // green-shift
        out.writeByte(0)    // blue-shift
        out.writeByte(0); out.writeByte(0); out.writeByte(0) // padding
        out.flush()
    }

    private fun sendSetEncodings(out: DataOutputStream) {
        out.writeByte(2) // SetEncodings
        out.writeByte(0) // padding
        out.writeShort(2) // count
        out.writeInt(1) // CopyRect
        out.writeInt(0) // Raw
        out.flush()
    }

    private fun sendFramebufferUpdateRequest(
        out: DataOutputStream,
        incremental: Boolean,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ) {
        out.writeByte(3)
        out.writeByte(if (incremental) 1 else 0)
        out.writeShort(x)
        out.writeShort(y)
        out.writeShort(w)
        out.writeShort(h)
        out.flush()
    }

    fun sendPointerEvent(scope: CoroutineScope, x: Int, y: Int, buttonMask: Int) {
        val out = output ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(out) {
                    out.writeByte(5)
                    out.writeByte(buttonMask)
                    out.writeShort(x.coerceIn(0, (fbWidth - 1).coerceAtLeast(0)))
                    out.writeShort(y.coerceIn(0, (fbHeight - 1).coerceAtLeast(0)))
                    out.flush()
                }
            }
        }
    }

    fun sendKeyPress(scope: CoroutineScope, keysym: Int) {
        val out = output ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(out) {
                    out.writeByte(4)
                    out.writeByte(1) // down
                    out.writeShort(0)
                    out.writeInt(keysym)

                    out.writeByte(4)
                    out.writeByte(0) // up
                    out.writeShort(0)
                    out.writeInt(keysym)
                    out.flush()
                }
            }
        }
    }

    fun sendCombo(scope: CoroutineScope, vararg keysyms: Int) {
        val out = output ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(out) {
                    for (k in keysyms) {
                        out.writeByte(4); out.writeByte(1); out.writeShort(0); out.writeInt(k)
                    }
                    for (k in keysyms.reversed()) {
                        out.writeByte(4); out.writeByte(0); out.writeShort(0); out.writeInt(k)
                    }
                    out.flush()
                }
            }
        }
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    object KeySyms {
        const val ESCAPE = 0xFF1B
        const val RETURN = 0xFF0D
        const val TAB = 0xFF09
        const val BACKSPACE = 0xFF08
        const val DELETE = 0xFFFF
        const val CTRL_LEFT = 0xFFE3
        const val ALT_LEFT = 0xFFE9
        const val SUPER_LEFT = 0xFFEB // Windows / Home key
        const val HOME = 0xFF50
    }
}
