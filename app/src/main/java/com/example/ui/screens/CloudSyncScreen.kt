package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.UserSyncProfile
import com.example.data.model.VmSnapshot
import com.example.ui.components.OAuthSetupGuideSheet
import com.example.ui.components.TelemetryPill
import com.example.ui.components.WorkstationCard
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WindowsCobalt

@Composable
fun CloudSyncScreen(
    isGuestMode: Boolean,
    userProfileState: UiState<UserSyncProfile?>,
    snapshotsState: UiState<List<VmSnapshot>>,
    onDeleteSnapshot: (String) -> Unit,
    onExitGuestToSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onOpenTutorial: () -> Unit
) {
    var showGuideSheet by remember { mutableStateOf(false) }

    Scaffold(containerColor = ObsidianCanvas) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp)
        ) {
            item {
                if (isGuestMode) {
                    WorkstationCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = AmberWarn.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = AmberWarn)
                                Text("Cloud Sync Deactivated", style = MaterialTheme.typography.titleLarge)
                            }
                            TelemetryPill(label = "GUEST MODE", accent = AmberWarn, leadingDot = true)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "You are running AdVm as a guest. VM profiles, GSI image entries, and snapshots remain local to this session and do not sync across devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onExitGuestToSignIn,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("guest_enable_sync_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TelemetryCyan,
                                    contentColor = ObsidianCanvas
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign In to Enable Cloud Sync")
                            }
                        }
                    }
                } else {
                    val profile = (userProfileState as? UiState.Success)?.data
                    val providerLabel = when (profile?.providerId) {
                        "google.com" -> "Google Account"
                        "apple.com" -> "Apple ID"
                        "microsoft.com" -> "Microsoft Account"
                        else -> "Federated Cloud"
                    }
                    val providerColor = when (profile?.providerId) {
                        "apple.com" -> TelemetryCyan
                        "microsoft.com" -> WindowsCobalt
                        else -> AndroidEmerald
                    }

                    WorkstationCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = AndroidEmerald.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = AndroidEmerald)
                                Text("Multi-Device Cloud Sync", style = MaterialTheme.typography.titleLarge)
                            }
                            TelemetryPill(label = providerLabel, accent = providerColor, leadingDot = true)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = profile?.displayName ?: "Authenticated Operator",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (profile != null && profile.email.isNotBlank()) {
                            Text(
                                text = profile.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Active Hardware: ${profile?.lastDeviceModel ?: "Current Device"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { showGuideSheet = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("sync_oauth_guide_button"),
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("OAuth Setup")
                            }
                            Button(
                                onClick = onSignOut,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("sign_out_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoseAlert.copy(alpha = 0.2f),
                                    contentColor = RoseAlert
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_sign_out))
                            }
                        }
                    }
                }
            }

            item {
                WorkstationCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = TelemetryCyan.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Need a Quick Walkthrough?", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Learn how to insert OS files, create Android & Windows VMs, and boot inside AdVm.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenTutorial,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("sync_open_tutorial_button"),
                        border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_open_tutorial), color = TelemetryCyan)
                    }
                }
            }

            item {
                Text("Synced VM Snapshots & Checkpoints", style = MaterialTheme.typography.titleMedium)
            }

            when (snapshotsState) {
                UiState.Loading -> item {
                    Text("Loading cloud snapshots...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                is UiState.Error -> item {
                    WorkstationCard(borderColor = RoseAlert) {
                        Text(snapshotsState.message, color = RoseAlert, style = MaterialTheme.typography.bodySmall)
                    }
                }
                is UiState.Success -> {
                    if (snapshotsState.data.isEmpty()) {
                        item {
                            WorkstationCard(modifier = Modifier.fillMaxWidth()) {
                                Text("No Cloud Snapshots Yet", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Select any VM on the Dashboard and tap Snapshot to record an immutable cross-device checkpoint.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        items(snapshotsState.data, key = { it.id }) { snap ->
                            WorkstationCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(snap.label, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${snap.vmName} • ${snap.osVersionLabel} (${snap.ramMb} MB)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        Text(
                                            "Captured on ${snap.deviceOrigin}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSnapshot(snap.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete snapshot", tint = RoseAlert)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGuideSheet) {
        OAuthSetupGuideSheet(
            highlightedProvider = null,
            onDismiss = { showGuideSheet = false }
        )
    }
}
