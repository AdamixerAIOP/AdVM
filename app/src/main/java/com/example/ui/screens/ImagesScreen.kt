package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CpuArchitecture
import com.example.data.model.CuratedGsiCatalog
import com.example.data.model.CuratedGsiTemplate
import com.example.data.model.HostHardwareReport
import com.example.data.model.ImageDownloadStatus
import com.example.data.model.ImageFormat
import com.example.data.model.OsCatalog
import com.example.data.model.OsFamily
import com.example.data.model.OsImageEntry
import com.example.data.model.UiState
import com.example.data.model.VmProfile
import com.example.ui.components.TelemetryPill
import com.example.ui.components.WorkstationCard
import com.example.ui.components.formatBytes
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
import com.example.ui.viewmodel.ActiveDownloadState

@Composable
fun ImagesScreen(
    imagesState: UiState<List<OsImageEntry>>,
    activeDownload: ActiveDownloadState?,
    statusBanner: String?,
    hostReport: HostHardwareReport,
    isMutatingVm: Boolean,
    onStartCustomDownload: (String, String, OsFamily, String, Int, ImageFormat, CpuArchitecture) -> Unit,
    onCancelDownload: () -> Unit,
    onRegisterTemplate: (CuratedGsiTemplate) -> Unit,
    onImportLocalUri: (android.net.Uri, OsFamily, String, Int) -> Unit,
    onDeleteImage: (OsImageEntry) -> Unit,
    onCreateVmFromImage: (VmProfile, () -> Unit) -> Unit,
    onClearBanner: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var customTitle by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var selectedFamily by remember { mutableStateOf(OsFamily.ANDROID) }
    var selectedVersionLabel by remember { mutableStateOf("Android 15") }
    var selectedMajor by remember { mutableIntStateOf(15) }
    var selectedFormat by remember { mutableStateOf(ImageFormat.GSI_IMG) }
    var selectedArch by remember { mutableStateOf(CpuArchitecture.ARM64) }
    var showDownloader by remember { mutableStateOf(true) }
    var vmPrefillImage by remember { mutableStateOf<OsImageEntry?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalUri(uri, selectedFamily, selectedVersionLabel, selectedMajor)
        }
    }

    LaunchedEffect(statusBanner) {
        if (!statusBanner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusBanner)
            onClearBanner()
        }
    }

    Scaffold(
        containerColor = ObsidianCanvas,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp)
        ) {
            item {
                WorkstationCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = TelemetryCyan.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GSI & OS Image Downloader", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Download Android GSI or Windows disk images via HTTPS or import local files via SAF.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("import_local_gsi_button"),
                            border = BorderStroke(1.dp, AndroidEmerald.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = AndroidEmerald)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import", color = AndroidEmerald)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedVisibility(visible = activeDownload != null) {
                        activeDownload?.let { dl ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading ${dl.title}...",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TelemetryCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = onCancelDownload) {
                                        Icon(Icons.Rounded.Cancel, contentDescription = "Cancel download", tint = RoseAlert)
                                    }
                                }
                                if (dl.progress.percent >= 0) {
                                    LinearProgressIndicator(
                                        progress = { dl.progress.percent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = TelemetryCyan,
                                        trackColor = SlateElevated
                                    )
                                    Text(
                                        "${dl.progress.percent}% • ${formatBytes(dl.progress.bytesRead)} / ${formatBytes(dl.progress.totalBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        color = TelemetryCyan,
                                        trackColor = SlateElevated
                                    )
                                    Text(
                                        "Downloaded ${formatBytes(dl.progress.bytesRead)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = showDownloader) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = customTitle,
                                onValueChange = { customTitle = it.take(100) },
                                label = { Text("Image Label (e.g. AOSP 16 GSI)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gsi_title_input")
                            )
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it.take(500) },
                                label = { Text("Direct HTTPS Image URL (.img, .zip, .qcow2, .iso)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gsi_url_input")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedFamily == OsFamily.ANDROID,
                                    onClick = {
                                        selectedFamily = OsFamily.ANDROID
                                        selectedVersionLabel = "Android 15"
                                        selectedMajor = 15
                                        selectedFormat = ImageFormat.GSI_IMG
                                        selectedArch = CpuArchitecture.ARM64
                                    },
                                    label = { Text("Android GSI") }
                                )
                                FilterChip(
                                    selected = selectedFamily == OsFamily.WINDOWS,
                                    onClick = {
                                        selectedFamily = OsFamily.WINDOWS
                                        selectedVersionLabel = "Windows 11"
                                        selectedMajor = 11
                                        selectedFormat = ImageFormat.ISO
                                        selectedArch = CpuArchitecture.X86_64
                                    },
                                    label = { Text("Windows Image") }
                                )
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OsCatalog.optionsFor(selectedFamily).forEach { opt ->
                                    FilterChip(
                                        selected = selectedVersionLabel == opt.label,
                                        onClick = {
                                            selectedVersionLabel = opt.label
                                            selectedMajor = opt.majorVersion
                                        },
                                        label = { Text(opt.label) }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onStartCustomDownload(
                                        customTitle,
                                        customUrl,
                                        selectedFamily,
                                        selectedVersionLabel,
                                        selectedMajor,
                                        selectedFormat,
                                        selectedArch
                                    )
                                },
                                enabled = activeDownload == null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("start_gsi_download_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TelemetryCyan,
                                    contentColor = ObsidianCanvas
                                )
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download & Verify SHA-256")
                            }
                        }
                    }
                }
            }

            // Cloud Synced Images
            item {
                Text("Synced Image Library", style = MaterialTheme.typography.titleMedium)
            }
            when (imagesState) {
                UiState.Loading -> item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TelemetryCyan, strokeWidth = 2.dp)
                        Text("Loading synced OS images...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UiState.Error -> item {
                    WorkstationCard(borderColor = RoseAlert) {
                        Text(imagesState.message, color = RoseAlert, style = MaterialTheme.typography.bodySmall)
                    }
                }
                is UiState.Success -> {
                    if (imagesState.data.isEmpty()) {
                        item {
                            WorkstationCard(modifier = Modifier.fillMaxWidth()) {
                                Text("No images registered yet", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Download a GSI from a direct HTTPS link, import a local .img/.iso/.qcow2 file, or add a curated template below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(imagesState.data, key = { it.id }) { img ->
                            UserImageCard(
                                image = img,
                                onCreateVm = { vmPrefillImage = img },
                                onDelete = { onDeleteImage(img) }
                            )
                        }
                    }
                }
            }

            // Curated Catalog Templates
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Curated GSI & OS Templates (Android 4–17 & Windows 7–11)", style = MaterialTheme.typography.titleMedium)
            }
            items(CuratedGsiCatalog.templates, key = { it.id }) { template ->
                CuratedTemplateCard(
                    template = template,
                    onDownload = {
                        onStartCustomDownload(
                            template.title,
                            template.sourceUrl,
                            template.osFamily,
                            template.osVersionLabel,
                            template.osMajorVersion,
                            template.format,
                            template.architecture
                        )
                    },
                    onRegister = { onRegisterTemplate(template) }
                )
            }
        }
    }

    if (vmPrefillImage != null) {
        VmEditorSheet(
            initialVm = null,
            prefilledImage = vmPrefillImage,
            availableImages = (imagesState as? UiState.Success)?.data.orEmpty(),
            hostReport = hostReport,
            isSaving = isMutatingVm,
            onSave = { vm ->
                onCreateVmFromImage(vm) { vmPrefillImage = null }
            },
            onDismiss = { vmPrefillImage = null }
        )
    }
}

@Composable
private fun UserImageCard(
    image: OsImageEntry,
    onCreateVm: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (image.parsedStatus) {
        ImageDownloadStatus.VERIFIED, ImageDownloadStatus.LOCAL_IMPORT -> AndroidEmerald
        ImageDownloadStatus.DOWNLOADING -> TelemetryCyan
        ImageDownloadStatus.FAILED -> RoseAlert
        ImageDownloadStatus.AVAILABLE -> AmberWarn
    }
    WorkstationCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(image.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${image.osVersionLabel} • ${image.parsedFormat.label} • ${image.architecture}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            TelemetryPill(label = image.parsedStatus.label, accent = statusColor, leadingDot = true)
        }
        if (image.sizeBytes > 0L || image.sha256.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (image.sizeBytes > 0L) {
                    Text(formatBytes(image.sizeBytes), style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                }
                if (image.sha256.isNotBlank()) {
                    Icon(Icons.Rounded.Verified, contentDescription = "Verified checksum", tint = AndroidEmerald, modifier = Modifier.size(14.dp))
                    Text("SHA256: ${image.sha256.take(16)}…", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCreateVm,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan, contentColor = ObsidianCanvas)
            ) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create VM from Image")
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete image", tint = RoseAlert)
            }
        }
    }
}

@Composable
private fun CuratedTemplateCard(
    template: CuratedGsiTemplate,
    onDownload: () -> Unit,
    onRegister: () -> Unit
) {
    val accent = if (template.osFamily == OsFamily.WINDOWS) WindowsCobalt else AndroidEmerald
    WorkstationCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(template.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TelemetryPill(label = template.osVersionLabel, accent = accent)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(template.notes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRegister,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text("Add to Library")
            }
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = ObsidianCanvas)
            ) {
                Text("Fetch Archive")
            }
        }
    }
}
