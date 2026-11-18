package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Pure-Kotlin QEMU Machine Protocol (QMP) TCP client.
 * Sends real hypervisor commands (pause, resume, ACPI shutdown, hard reset, live savevm)
 * to a running QEMU instance started with `-qmp tcp:127.0.0.1:<port>,server,nowait`.
 */
object QmpClient {

    fun qmpPortForVncPort(vncPort: Int): Int {
        val offset = (vncPort - 5900).coerceIn(0, 999)
        return 4444 + offset
    }

    suspend fun sendCommand(
        host: String,
        qmpPort: Int,
        execute: String,
        arguments: JSONObject? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, qmpPort), 2500)
                socket.soTimeout = 3000

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

                // 1. Read QMP greeting banner
                val greeting = reader.readLine()
                    ?: throw IllegalStateException("QMP socket closed before greeting")
                if (!greeting.contains("QMP")) {
                    throw IllegalStateException("Unexpected QMP greeting: $greeting")
                }

                // 2. Negotiate capabilities
                writer.write("{\"execute\":\"qmp_capabilities\"}\n")
                writer.flush()
                val capResp = readJsonResponse(reader)
                if (capResp.has("error")) {
                    throw IllegalStateException("QMP capability error: ${capResp.optString("error")}")
                }

                // 3. Execute requested hypervisor command
                val cmdObj = JSONObject().apply {
                    put("execute", execute)
                    if (arguments != null) {
                        put("arguments", arguments)
                    }
                }
                writer.write("${cmdObj}\n")
                writer.flush()

                val cmdResp = readJsonResponse(reader)
                if (cmdResp.has("error")) {
                    val errDesc = cmdResp.optJSONObject("error")?.optString("desc") ?: cmdResp.optString("error")
                    throw IllegalStateException("QMP $execute failed: $errDesc")
                }
                cmdResp.optString("return", "ok")
            }
        }
    }

    private fun readJsonResponse(reader: BufferedReader): JSONObject {
        repeat(8) {
            val line = reader.readLine() ?: throw IllegalStateException("QMP connection closed")
            val json = JSONObject(line)
            if (json.has("return") || json.has("error")) {
                return json
            }
            // Skip asynchronous QMP event lines
        }
        throw IllegalStateException("Timed out waiting for QMP command response")
    }

    suspend fun pauseVm(host: String, vncPort: Int) =
        sendCommand(host, qmpPortForVncPort(vncPort), "stop")

    suspend fun resumeVm(host: String, vncPort: Int) =
        sendCommand(host, qmpPortForVncPort(vncPort), "cont")

    suspend fun acpiPowerDown(host: String, vncPort: Int) =
        sendCommand(host, qmpPortForVncPort(vncPort), "system_powerdown")

    suspend fun hardReset(host: String, vncPort: Int) =
        sendCommand(host, qmpPortForVncPort(vncPort), "system_reset")

    suspend fun quitHypervisor(host: String, vncPort: Int) =
        sendCommand(host, qmpPortForVncPort(vncPort), "quit")

    suspend fun saveLiveSnapshot(host: String, vncPort: Int, snapshotName: String): Result<String> {
        val safeName = snapshotName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "snap" }
        val args = JSONObject().put("command-line", "savevm $safeName")
        return sendCommand(host, qmpPortForVncPort(vncPort), "human-monitor-command", args)
    }
}
