package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WindowsCobalt

@Composable
fun WorkstationCard(
    modifier: Modifier = Modifier,
    borderColor: Color = HairlineBorder,
    containerColor: Color = SlateSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun TelemetryPill(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (leadingDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun HypervisorEmblemGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Subtle telemetry grid
        val step = w / 8f
        for (i in 1..7) {
            val pos = step * i
            drawLine(
                color = TelemetryCyan.copy(alpha = 0.08f),
                start = Offset(pos, 0f),
                end = Offset(pos, h),
                strokeWidth = 1f
            )
            drawLine(
                color = TelemetryCyan.copy(alpha = 0.08f),
                start = Offset(0f, pos),
                end = Offset(w, pos),
                strokeWidth = 1f
            )
        }

        // Radial glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TelemetryCyan.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(cx, cy),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy)
        )

        val r = minOf(w, h) * 0.30f
        // Top diamond face
        val topPath = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.88f, cy - r * 0.45f)
            lineTo(cx, cy + r * 0.10f)
            lineTo(cx - r * 0.88f, cy - r * 0.45f)
            close()
        }
        drawPath(topPath, color = TelemetryCyan.copy(alpha = 0.88f))

        // Left face (Android Emerald)
        val leftPath = Path().apply {
            moveTo(cx - r * 0.88f, cy - r * 0.38f)
            lineTo(cx - r * 0.04f, cy + r * 0.16f)
            lineTo(cx - r * 0.04f, cy + r * 1.02f)
            lineTo(cx - r * 0.88f, cy + r * 0.48f)
            close()
        }
        drawPath(leftPath, color = AndroidEmerald.copy(alpha = 0.82f))

        // Right face (Windows Cobalt)
        val rightPath = Path().apply {
            moveTo(cx + r * 0.88f, cy - r * 0.38f)
            lineTo(cx + r * 0.04f, cy + r * 0.16f)
            lineTo(cx + r * 0.04f, cy + r * 1.02f)
            lineTo(cx + r * 0.88f, cy + r * 0.48f)
            close()
        }
        drawPath(rightPath, color = WindowsCobalt.copy(alpha = 0.82f))

        // Precision outer frame ring
        drawCircle(
            color = TelemetryCyan.copy(alpha = 0.42f),
            radius = r * 1.38f,
            center = Offset(cx, cy + r * 0.05f),
            style = Stroke(width = 2.5f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthSetupGuideSheet(
    highlightedProvider: String?,
    onDismiss: () -> Unit
) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = TelemetryCyan
                    )
                    Text(
                        text = "Multi-Provider OAuth Setup",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_oauth_guide_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close setup guide")
                }
            }

            if (highlightedProvider != null) {
                TelemetryPill(
                    label = "$highlightedProvider requires Firebase Console OAuth keys",
                    accent = AmberWarn,
                    leadingDot = true
                )
            }

            Text(
                text = "Google Sign-In is pre-provisioned and works immediately. Apple and Microsoft OAuth flows are wired in AdVm via Firebase Auth, and become active as soon as you enter their developer credentials in your Firebase Console:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            GuideStepCard(
                badge = "GOOGLE",
                badgeColor = AndroidEmerald,
                title = "Google Sign-In (Active Ready)",
                steps = listOf(
                    "Provisioned automatically via Jetpack Credential Manager.",
                    "SHA-1 signing fingerprint and Web Client ID are already registered."
                )
            )

            GuideStepCard(
                badge = "APPLE",
                badgeColor = TelemetryCyan,
                title = "Sign in with Apple (apple.com)",
                steps = listOf(
                    "1. Open Firebase Console -> Authentication -> Sign-in method -> Add Apple.",
                    "2. In Apple Developer Portal, create a Services ID & Sign In with Apple key (.p8).",
                    "3. Add the Firebase OAuth redirect URI to your Services ID return URLs.",
                    "4. Paste your Services ID, Apple Team ID, Key ID, and Private Key into Firebase."
                )
            )

            GuideStepCard(
                badge = "MICROSOFT",
                badgeColor = WindowsCobalt,
                title = "Microsoft Account (microsoft.com)",
                steps = listOf(
                    "1. Open Firebase Console -> Authentication -> Sign-in method -> Add Microsoft.",
                    "2. In Microsoft Entra / Azure Portal, register an App under App Registrations.",
                    "3. Add the Firebase callback URI as a Web Redirect URI.",
                    "4. Copy Application (client) ID and Client Secret into Firebase Console."
                )
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GuideStepCard(
    badge: String,
    badgeColor: Color,
    title: String,
    steps: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(SlateElevated)
            .border(1.dp, HairlineBorder, MaterialTheme.shapes.small)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryPill(label = badge, accent = badgeColor)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        steps.forEach { step ->
            Text(
                text = step,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
