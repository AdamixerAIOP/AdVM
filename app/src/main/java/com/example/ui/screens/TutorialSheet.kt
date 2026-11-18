package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.TelemetryPill
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WindowsCobalt
import kotlinx.coroutines.launch

data class TutorialStepSpec(
    val id: String,
    val badge: String,
    val title: String,
    val summary: String,
    val checklist: List<String>,
    val proTip: String,
    val accent: Color,
    val icon: ImageVector,
    val targetRoute: String? = null,
    val targetActionLabel: String? = null
)

object AdVmTutorialCatalog {
    val steps = listOf(
        TutorialStepSpec(
            id = "welcome",
            badge = "STEP 1 • OVERVIEW",
            title = "Welcome to AdVm",
            summary = "Run and manage Android (4–17) Phone Mode and Windows (7–11) PC Mode virtual machines from one workstation.",
            checklist = listOf(
                "Check the Hardware Telemetry card at the top of the VMs tab to see your device's AVF, /dev/kvm, Vulkan, and RAM headroom.",
                "Switch freely between Android Phone profiles and Windows PC profiles using the filter bar.",
                "Everything runs smoothly whether you sync to the cloud or work offline in Guest Mode."
            ),
            proTip = "Leave at least 2 GB of free host RAM when sizing a new VM so your Android device stays responsive.",
            accent = TelemetryCyan,
            icon = Icons.Rounded.RocketLaunch
        ),
        TutorialStepSpec(
            id = "images",
            badge = "STEP 2 • OS IMAGES",
            title = "Download or Import a GSI / ISO",
            summary = "Every virtual machine boots from an OS image (.img, .iso, or .qcow2). Add yours in the GSI Images tab.",
            checklist = listOf(
                "Pick a pre-configured template from the Curated Catalog (AOSP 4–17 GSIs or Windows 7–11 images).",
                "Or paste any direct https:// image URL and tap Download to stream it directly to private app storage.",
                "Already have a GSI or ISO on your phone? Tap Import Local File using the Android file picker."
            ),
            proTip = "Once downloaded or imported, tap Provision VM from Image on any image card to auto-fill a ready-to-boot VM.",
            accent = AndroidEmerald,
            icon = Icons.Rounded.DownloadForOffline,
            targetRoute = "images",
            targetActionLabel = "Open GSI Images"
        ),
        TutorialStepSpec(
            id = "create_vm",
            badge = "STEP 3 • PICK AN OS FILE",
            title = "Insert an OS File & Tap Boot",
            summary = "No external apps or terminals needed—pick an OS preset or Add a custom VM, attach a file, and boot.",
            checklist = listOf(
                "Tap + Add VM and pick any Android (4–17) or Windows (7–11) preset, or tap Add a custom VM below.",
                "Download or insert an .img, .iso, or .qcow2 OS file directly inside AdVm.",
                "Tap Boot OS and complete the quick Human Verification CAPTCHA pop-up."
            ),
            proTip = "Inside Add a custom VM you can choose any local OS file and tune vCPU, RAM, and disk size.",
            accent = WindowsCobalt,
            icon = Icons.Rounded.Tune,
            targetRoute = "dashboard",
            targetActionLabel = "Open VMs Tab"
        ),
        TutorialStepSpec(
            id = "console",
            badge = "STEP 4 • LIVE DISPLAY",
            title = "Verify & Use Your VM",
            summary = "After passing the CAPTCHA check, AdVm launches the hypervisor engine on your OS file and streams the screen.",
            checklist = listOf(
                "Touch, drag, right-click, and type directly inside the running guest VM display.",
                "Use the bottom bar anytime to Pause, Resume, Reset, save a live Snapshot, or Power Off.",
                "Everything runs directly inside AdVm with zero external apps or terminal commands."
            ),
            proTip = "Standalone on-device execution requires NDK-compiled QEMU libraries packaged in the APK build.",
            accent = TelemetryCyan,
            icon = Icons.Rounded.CastConnected
        ),
        TutorialStepSpec(
            id = "sync_guest",
            badge = "STEP 5 • SYNC & SNAPSHOTS",
            title = "Snapshots, Cloud Sync & Guest Mode",
            summary = "Save VM checkpoints before major changes, or switch between Cloud Sync and offline Guest Mode.",
            checklist = listOf(
                "Expand any VM and tap Snapshot to save a labeled checkpoint of its hardware and OS setup.",
                "Sign in with Google, Apple, or Microsoft to sync VM profiles and snapshots across your phone, tablet, and PC.",
                "In Guest Mode (CLOUD OFF), all VMs and snapshots stay on-device—tap Cloud Sync anytime to sign in."
            ),
            proTip = "Tap the ? icon in the top bar anytime to reopen this quick-start guide.",
            accent = AmberWarn,
            icon = Icons.Rounded.CloudSync,
            targetRoute = "sync",
            targetActionLabel = "Open Cloud Sync"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialSheet(
    onDismiss: () -> Unit,
    onJumpToRoute: ((String) -> Unit)? = null
) {
    val steps = AdVmTutorialCatalog.steps
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeveloperBoard,
                        contentDescription = null,
                        tint = TelemetryCyan
                    )
                    Text(
                        text = "AdVm Quick-Start Tutorial",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tutorial_skip_button")
                    ) {
                        Text("Skip", color = TextSecondary)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tutorial_close_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close tutorial")
                    }
                }
            }

            // Progress dots + counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEachIndexed { index, step ->
                        val active = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (active) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (active) step.accent else HairlineBorder)
                        )
                    }
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${steps.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Step Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { page ->
                val step = steps[page]
                TutorialStepPage(
                    step = step,
                    onJumpToRoute = onJumpToRoute?.let { jump ->
                        step.targetRoute?.let { route ->
                            {
                                onDismiss()
                                jump(route)
                            }
                        }
                    }
                )
            }

            // Bottom Navigation Buttons
            val isFirst = pagerState.currentPage == 0
            val isLast = pagerState.currentPage == steps.lastIndex
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isFirst) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("tutorial_back_button"),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back")
                    }
                }

                Button(
                    onClick = {
                        if (isLast) {
                            onDismiss()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(if (isLast) "tutorial_done_button" else "tutorial_next_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = steps[pagerState.currentPage].accent,
                        contentColor = ObsidianCanvas
                    )
                ) {
                    if (isLast) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Got It, Let's Go", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Next Step", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStepPage(
    step: TutorialStepSpec,
    onJumpToRoute: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Visual hero banner card with subtle precision grid
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = SlateElevated,
            border = BorderStroke(1.dp, step.accent.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    val gridStep = 44f
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = step.accent.copy(alpha = 0.06f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += gridStep
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(step.accent.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width * 0.88f, size.height * 0.3f),
                            radius = size.width * 0.45f
                        ),
                        radius = size.width * 0.45f,
                        center = Offset(size.width * 0.88f, size.height * 0.3f)
                    )
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryPill(label = step.badge, accent = step.accent, leadingDot = true)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(step.accent.copy(alpha = 0.16f))
                                .border(1.dp, step.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = step.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = step.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Numbered checklist
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(ObsidianCanvas)
                .border(1.dp, HairlineBorder, MaterialTheme.shapes.medium)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "HOW TO DO IT",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            step.checklist.forEachIndexed { idx, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(step.accent.copy(alpha = 0.18f))
                            .border(1.dp, step.accent.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = step.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Pro tip banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(step.accent.copy(alpha = 0.10f))
                .border(1.dp, step.accent.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = step.accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = step.proTip,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        // Quick-jump action button when inside the workstation shell
        if (onJumpToRoute != null && step.targetActionLabel != null) {
            OutlinedButton(
                onClick = onJumpToRoute,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("tutorial_jump_tab_button"),
                border = BorderStroke(1.dp, step.accent.copy(alpha = 0.5f))
            ) {
                Text(step.targetActionLabel, color = step.accent)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = step.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
