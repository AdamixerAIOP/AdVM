package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.LaptopWindows
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.AuthManager
import com.example.auth.OAuthOutcome
import com.example.ui.components.HypervisorEmblemGraphic
import com.example.ui.components.OAuthSetupGuideSheet
import com.example.ui.components.TelemetryPill
import com.example.ui.components.WorkstationCard
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WindowsCobalt
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var activeLoadingProvider by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGuideSheet by remember { mutableStateOf(false) }
    var guideHighlightedProvider by remember { mutableStateOf<String?>(null) }
    var showTutorialSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = { /* Interactive buttons remain ready */ }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianCanvas,
                        SlateElevated.copy(alpha = 0.55f),
                        ObsidianCanvas
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HypervisorEmblemGraphic(
                modifier = Modifier.size(132.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryPill(label = "ANDROID 4–17", accent = AndroidEmerald, leadingDot = true)
                TelemetryPill(label = "WINDOWS 7–11", accent = WindowsCobalt, leadingDot = true)
            }

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = stringResource(R.string.auth_title),
                style = MaterialTheme.typography.titleLarge,
                color = TelemetryCyan,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            WorkstationCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = TelemetryCyan
                    )
                    Text(
                        text = "Multi-Device Cloud Sync Gate",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Authenticate with Google, Apple, or Microsoft to isolate and synchronize VM hardware profiles, GSI manifests, and snapshots.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 1. Google Sign-In (Credential Manager)
                Button(
                    onClick = {
                        errorMessage = null
                        activeLoadingProvider = "google"
                        scope.launch {
                            when (val outcome = AuthManager.signInWithGoogleInteractive(context, credentialManager)) {
                                OAuthOutcome.Success -> {
                                    activeLoadingProvider = null
                                    onAuthSuccess()
                                }
                                OAuthOutcome.Cancelled -> {
                                    activeLoadingProvider = null
                                }
                                is OAuthOutcome.ProviderNotConfigured -> {
                                    activeLoadingProvider = null
                                    guideHighlightedProvider = outcome.providerName
                                    showGuideSheet = true
                                }
                                is OAuthOutcome.Error -> {
                                    activeLoadingProvider = null
                                    errorMessage = outcome.message
                                }
                            }
                        }
                    },
                    enabled = activeLoadingProvider == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("google_sign_in_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TelemetryCyan,
                        contentColor = ObsidianCanvas
                    )
                ) {
                    if (activeLoadingProvider == "google") {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ObsidianCanvas,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = stringResource(R.string.auth_google_button))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Apple Sign-In (OAuthProvider)
                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        activeLoadingProvider = "apple"
                        scope.launch {
                            when (val outcome = AuthManager.signInWithFederatedOAuth(context, "apple.com", "Apple")) {
                                OAuthOutcome.Success -> {
                                    activeLoadingProvider = null
                                    onAuthSuccess()
                                }
                                OAuthOutcome.Cancelled -> activeLoadingProvider = null
                                is OAuthOutcome.ProviderNotConfigured -> {
                                    activeLoadingProvider = null
                                    guideHighlightedProvider = outcome.providerName
                                    showGuideSheet = true
                                }
                                is OAuthOutcome.Error -> {
                                    activeLoadingProvider = null
                                    errorMessage = outcome.message
                                }
                            }
                        }
                    },
                    enabled = activeLoadingProvider == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("apple_sign_in_button"),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    if (activeLoadingProvider == "apple") {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.auth_apple_button),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Microsoft Sign-In (OAuthProvider)
                OutlinedButton(
                    onClick = {
                        errorMessage = null
                        activeLoadingProvider = "microsoft"
                        scope.launch {
                            when (val outcome = AuthManager.signInWithFederatedOAuth(context, "microsoft.com", "Microsoft")) {
                                OAuthOutcome.Success -> {
                                    activeLoadingProvider = null
                                    onAuthSuccess()
                                }
                                OAuthOutcome.Cancelled -> activeLoadingProvider = null
                                is OAuthOutcome.ProviderNotConfigured -> {
                                    activeLoadingProvider = null
                                    guideHighlightedProvider = outcome.providerName
                                    showGuideSheet = true
                                }
                                is OAuthOutcome.Error -> {
                                    activeLoadingProvider = null
                                    errorMessage = outcome.message
                                }
                            }
                        }
                    },
                    enabled = activeLoadingProvider == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("microsoft_sign_in_button"),
                    border = BorderStroke(1.dp, WindowsCobalt.copy(alpha = 0.5f))
                ) {
                    if (activeLoadingProvider == "microsoft") {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.LaptopWindows,
                            contentDescription = null,
                            tint = WindowsCobalt,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.auth_microsoft_button),
                            color = WindowsCobalt
                        )
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = RoseAlert,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            TextButton(
                onClick = {
                    guideHighlightedProvider = null
                    showGuideSheet = true
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("open_oauth_guide_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = null,
                    tint = AmberWarn,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auth_oauth_guide_button),
                    color = AmberWarn
                )
            }

            TextButton(
                onClick = { showTutorialSheet = true },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("auth_open_tutorial_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = TelemetryCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auth_tutorial_button),
                    color = TelemetryCyan
                )
            }

            HorizontalDivider(color = HairlineBorder)

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onContinueAsGuest,
                    enabled = activeLoadingProvider == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("continue_as_guest_button"),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auth_guest_button),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.auth_guest_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberWarn,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showGuideSheet) {
        OAuthSetupGuideSheet(
            highlightedProvider = guideHighlightedProvider,
            onDismiss = { showGuideSheet = false }
        )
    }

    if (showTutorialSheet) {
        TutorialSheet(
            onDismiss = { showTutorialSheet = false },
            onJumpToRoute = null
        )
    }
}
