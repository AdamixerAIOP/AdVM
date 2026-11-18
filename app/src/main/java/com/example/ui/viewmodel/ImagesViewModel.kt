package com.example.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WorkstationRepository
import com.example.data.model.CpuArchitecture
import com.example.data.model.CuratedGsiTemplate
import com.example.data.model.ImageDownloadStatus
import com.example.data.model.ImageFormat
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.UiState
import com.example.service.DownloadProgress
import com.example.service.GsiDownloadManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveDownloadState(
    val title: String,
    val progress: DownloadProgress
)

class ImagesViewModel(
    private val repository: WorkstationRepository,
    private val downloadManager: GsiDownloadManager,
    private val currentUserId: String
) : ViewModel() {

    private val _activeDownload = MutableStateFlow<ActiveDownloadState?>(null)
    val activeDownload: StateFlow<ActiveDownloadState?> = _activeDownload.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var downloadJob: Job? = null

    val imagesState: StateFlow<UiState<List<OsImageEntry>>> = repository.observeImages(currentUserId)
        .map<List<OsImageEntry>, UiState<List<OsImageEntry>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing OS images", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load OS image library"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun registerTemplate(template: CuratedGsiTemplate) {
        viewModelScope.launch {
            val entry = OsImageEntry(
                userId = currentUserId,
                title = template.title,
                osFamily = template.osFamily.name,
                osVersionLabel = template.osVersionLabel,
                osMajorVersion = template.osMajorVersion,
                format = template.format.name,
                architecture = template.architecture.name,
                sourceUrl = template.sourceUrl,
                localPath = "",
                sizeBytes = 0L,
                sha256 = "",
                downloadStatus = ImageDownloadStatus.AVAILABLE.name
            )
            repository.saveImageEntry(currentUserId, entry)
                .onSuccess {
                    _statusBanner.value = "Added \"${template.title}\" to your cloud image library."
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Could not register image."
                }
        }
    }

    fun startCustomDownload(
        title: String,
        url: String,
        osFamily: OsFamily,
        osVersionLabel: String,
        osMajorVersion: Int,
        format: ImageFormat,
        architecture: CpuArchitecture
    ) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
            _statusBanner.value = "Enter a valid https:// URL to download an image."
            return
        }
        val cleanTitle = title.trim().ifBlank { cleanUrl.substringAfterLast('/').ifBlank { "GSI Image" } }.take(100)
        val fileName = "${cleanTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_")}.${format.extension}"

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val pendingEntry = OsImageEntry(
                userId = currentUserId,
                title = cleanTitle,
                osFamily = osFamily.name,
                osVersionLabel = osVersionLabel,
                osMajorVersion = osMajorVersion,
                format = format.name,
                architecture = architecture.name,
                sourceUrl = cleanUrl,
                localPath = "",
                sizeBytes = 0L,
                sha256 = "",
                downloadStatus = ImageDownloadStatus.DOWNLOADING.name
            )
            val savedId = repository.saveImageEntry(currentUserId, pendingEntry).getOrNull()
            _activeDownload.value = ActiveDownloadState(cleanTitle, DownloadProgress(0L, -1L, 0))

            val downloadResult = downloadManager.downloadHttpsImage(cleanUrl, fileName) { progress ->
                _activeDownload.value = ActiveDownloadState(cleanTitle, progress)
            }
            _activeDownload.value = null

            downloadResult.onSuccess { res ->
                val verifiedEntry = pendingEntry.copy(
                    id = savedId.orEmpty(),
                    localPath = res.localPath,
                    sizeBytes = res.sizeBytes,
                    sha256 = res.sha256,
                    downloadStatus = ImageDownloadStatus.VERIFIED.name
                )
                repository.saveImageEntry(currentUserId, verifiedEntry)
                _statusBanner.value = "Downloaded & SHA-256 verified ($cleanTitle)."
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val failedEntry = pendingEntry.copy(
                    id = savedId.orEmpty(),
                    downloadStatus = ImageDownloadStatus.FAILED.name
                )
                if (savedId != null) {
                    repository.saveImageEntry(currentUserId, failedEntry)
                }
                _statusBanner.value = err.localizedMessage ?: "Download failed."
            }
        }
    }

    fun cancelActiveDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _activeDownload.value = null
        _statusBanner.value = "Download cancelled."
    }

    fun importLocalDocument(
        uri: Uri,
        osFamily: OsFamily,
        osVersionLabel: String,
        osMajorVersion: Int
    ) {
        viewModelScope.launch {
            val info = downloadManager.inspectLocalDocument(uri)
            val lower = info.displayName.lowercase()
            val format = when {
                lower.endsWith(".qcow2") -> ImageFormat.QCOW2
                lower.endsWith(".iso") -> ImageFormat.ISO
                else -> ImageFormat.GSI_IMG
            }
            val arch = if (osFamily == OsFamily.WINDOWS && format != ImageFormat.GSI_IMG) {
                CpuArchitecture.X86_64
            } else {
                CpuArchitecture.ARM64
            }
            val entry = OsImageEntry(
                userId = currentUserId,
                title = info.displayName.take(100),
                osFamily = osFamily.name,
                osVersionLabel = osVersionLabel,
                osMajorVersion = osMajorVersion,
                format = format.name,
                architecture = arch.name,
                sourceUrl = "",
                localPath = info.uriString.take(500),
                sizeBytes = info.sizeBytes.coerceAtLeast(0L),
                sha256 = "",
                downloadStatus = ImageDownloadStatus.LOCAL_IMPORT.name
            )
            repository.saveImageEntry(currentUserId, entry)
                .onSuccess { _statusBanner.value = "Imported \"${info.displayName}\" via Storage Access Framework." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save imported image." }
        }
    }

    fun deleteImage(entry: OsImageEntry) {
        viewModelScope.launch {
            if (entry.localPath.isNotBlank()) {
                downloadManager.deleteLocalFile(entry.localPath)
            }
            repository.deleteImageEntry(currentUserId, entry.id)
                .onSuccess { _statusBanner.value = "Removed \"${entry.title}\"." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete image." }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    private companion object {
        const val TAG = "ImagesVM"
    }
}
