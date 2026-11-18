package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long,
    val percent: Int
)

data class DownloadedImageResult(
    val localPath: String,
    val sizeBytes: Long,
    val sha256: String
)

data class ImportedDocumentInfo(
    val displayName: String,
    val sizeBytes: Long,
    val uriString: String
)

class GsiDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val imagesDir: File by lazy {
        File(appContext.filesDir, "images").apply { if (!exists()) mkdirs() }
    }

    suspend fun downloadHttpsImage(
        url: String,
        targetFileName: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<DownloadedImageResult> = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            return@withContext Result.failure(IllegalArgumentException("Only https:// image URLs are allowed."))
        }
        val safeName = targetFileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").ifBlank { "gsi_image.img" }
        val targetFile = File(imagesDir, safeName)
        val tempFile = File(imagesDir, "$safeName.part")

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code}: ${response.message}")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    IllegalStateException("Empty response body from server.")
                )
                val contentLength = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var totalRead = 0L
                val buffer = ByteArray(64 * 1024)
                var lastReportedPercent = -1

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            totalRead += read

                            val pct = if (contentLength > 0) {
                                ((totalRead * 100L) / contentLength).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            if (pct != lastReportedPercent || totalRead % (512 * 1024) == 0L) {
                                lastReportedPercent = pct
                                onProgress(DownloadProgress(totalRead, contentLength, pct))
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                val sha256Hex = digest.digest().joinToString("") { "%02x".format(it) }
                onProgress(DownloadProgress(totalRead, totalRead, 100))
                Result.success(
                    DownloadedImageResult(
                        localPath = targetFile.absolutePath,
                        sizeBytes = totalRead,
                        sha256 = sha256Hex
                    )
                )
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    fun inspectLocalDocument(uri: Uri): ImportedDocumentInfo {
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Transient grant is still valid for the current session
        }

        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "local_image.img"
        var sizeBytes = 0L

        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    displayName = cursor.getString(nameIdx) ?: displayName
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        val resolvedPath = resolveSafFilesystemPath(uri) ?: uri.toString()
        return ImportedDocumentInfo(
            displayName = displayName,
            sizeBytes = sizeBytes,
            uriString = resolvedPath
        )
    }

    fun resolveSafFilesystemPath(uri: Uri): String? {
        val authority = uri.authority ?: return null
        val segments = uri.pathSegments
        if (authority == "com.android.externalstorage.documents" && segments.size >= 2) {
            val docId = segments.last()
            if (docId.startsWith("primary:", ignoreCase = true)) {
                val relative = docId.substringAfter(":")
                return "/sdcard/$relative"
            }
        }
        val last = uri.lastPathSegment.orEmpty()
        if (last.startsWith("raw:/")) {
            return last.removePrefix("raw:")
        }
        return null
    }

    fun deleteLocalFile(path: String) {
        if (path.startsWith(imagesDir.absolutePath)) {
            runCatching { File(path).delete() }
        }
    }
}
