package com.example.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AndroidEmerald
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import kotlin.random.Random

object CaptchaGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun nextCode(length: Int = 6, random: Random = Random.Default): String =
        buildString(length) {
            repeat(length) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }

    fun matches(expected: String, input: String): Boolean =
        expected.isNotBlank() && input.trim().equals(expected, ignoreCase = true)
}

@Composable
fun HumanCaptchaDialog(
    vmName: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var challengeSeed by remember { mutableIntStateOf(Random.nextInt()) }
    var expectedCode by remember(challengeSeed) {
        mutableStateOf(CaptchaGenerator.nextCode())
    }
    var userInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }

    fun regenerate() {
        challengeSeed = Random.nextInt()
        userInput = ""
        errorMessage = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateElevated,
        icon = {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = "Human verification",
                tint = TelemetryCyan,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Human Verification",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Verify that you are human before booting $vmName.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CaptchaChallengeCanvas(
                        code = expectedCode,
                        seed = challengeSeed,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .testTag("captcha_challenge_canvas")
                    )
                    IconButton(
                        onClick = { regenerate() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("captcha_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "New CAPTCHA code",
                            tint = TelemetryCyan
                        )
                    }
                }

                OutlinedTextField(
                    value = userInput,
                    onValueChange = {
                        userInput = it.uppercase().take(8)
                        errorMessage = null
                    },
                    label = { Text("Enter the 6-character code") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("captcha_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAlert
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (CaptchaGenerator.matches(expectedCode, userInput)) {
                        onVerified()
                    } else {
                        attempts += 1
                        if (attempts >= 3) {
                            attempts = 0
                            regenerate()
                            errorMessage = "Incorrect code. A new CAPTCHA has been generated."
                        } else {
                            errorMessage = "Code does not match. Please try again."
                        }
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("captcha_verify_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TelemetryCyan,
                    contentColor = ObsidianCanvas
                )
            ) {
                Text("Verify & Boot", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("captcha_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CaptchaChallengeCanvas(
    code: String,
    seed: Int,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val rng = remember(code, seed) { Random(seed xor code.hashCode()) }
    val charAngles = remember(code, seed) {
        code.map { rng.nextFloat() * 34f - 17f }
    }
    val charYOffsets = remember(code, seed) {
        code.map { rng.nextFloat() * 14f - 7f }
    }
    val linePoints = remember(code, seed) {
        List(5) {
            Pair(
                rng.nextFloat() to rng.nextFloat(),
                rng.nextFloat() to rng.nextFloat()
            )
        }
    }
    val dotPoints = remember(code, seed) {
        List(32) { rng.nextFloat() to rng.nextFloat() }
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(ObsidianCanvas)
            .border(1.dp, HairlineBorder, MaterialTheme.shapes.small)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            // Subtle security grid & noise lines
            linePoints.forEachIndexed { idx, (start, end) ->
                drawLine(
                    color = if (idx % 2 == 0) TelemetryCyan.copy(alpha = 0.28f) else AndroidEmerald.copy(alpha = 0.28f),
                    start = Offset(start.first * w, start.second * h),
                    end = Offset(end.first * w, end.second * h),
                    strokeWidth = 2f
                )
            }

            dotPoints.forEach { (dx, dy) ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = 2.2f,
                    center = Offset(dx * w, dy * h)
                )
            }

            val stepX = w / (code.length + 1)
            code.forEachIndexed { index, ch ->
                val measured = textMeasurer.measure(
                    text = ch.toString(),
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
                val cx = stepX * (index + 1)
                val cy = h / 2f + charYOffsets[index]
                val tint = when (index % 3) {
                    0 -> TelemetryCyan
                    1 -> AndroidEmerald
                    else -> Color.White
                }
                rotate(degrees = charAngles[index], pivot = Offset(cx, cy)) {
                    drawText(
                        textLayoutResult = measured,
                        color = tint,
                        topLeft = Offset(
                            cx - measured.size.width / 2f,
                            cy - measured.size.height / 2f
                        )
                    )
                }
            }
        }
    }
}
