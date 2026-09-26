package com.samsung.prism.teachable.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.PillShape
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOnTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSubCardShape
import com.samsung.prism.teachable.ui.theme.SubCardShape
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHigh
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLowest
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryContainer

@Composable
fun TeachFlowSetupDialog(
    onDismiss: () -> Unit,
    onStartTeaching: (utterance: String, targetPackage: String?) -> Unit
) {
    var utteranceInput by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w2"
    )
    val waveScale3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w3"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = SaysoSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .background(SaysoSecondaryContainer, ChipShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = SaysoOnSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Interactive Workflow Learning",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaysoOnSecondaryContainer
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SaysoOnSurfaceVariant
                        )
                    }
                }

                // Headline
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "What are you about to show me?",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface,
                        lineHeight = 28.sp
                    )
                    Text(
                        text = "Describe the task in your natural words. Sayso will observe your taps and extract dynamic parameters like items, addresses, or quantities.",
                        fontSize = 13.sp,
                        color = SaysoOnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // Voice Transcript & Waveform Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SubCardShape,
                    colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Live status pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LISTENING ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoPrimary,
                                modifier = Modifier
                                    .background(SaysoSurfaceContainerHigh, BadgeShape)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                            Text(
                                text = "Crisp audio • 98% confidence",
                                fontSize = 11.sp,
                                color = SaysoOnSurfaceVariant
                            )
                        }

                        // Audio Waveform Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(36.dp)
                        ) {
                            val scales = listOf(waveScale1, waveScale2, waveScale3, waveScale1, waveScale2, waveScale3, waveScale1)
                            for (s in scales) {
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height((32 * s).coerceAtLeast(8.0f).dp)
                                        .background(SaysoSecondary, CircleShape)
                                )
                            }
                        }

                        // Transcript Input Field
                        OutlinedTextField(
                            value = utteranceInput,
                            onValueChange = { utteranceInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Spoken Goal") },
                            placeholder = { Text("e.g. Toggle Airplane Mode in Settings") },
                            shape = SubCardShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SaysoPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = SaysoSurfaceContainerLowest,
                                unfocusedContainerColor = SaysoSurfaceContainerLowest
                            )
                        )
                    }
                }

                val lowerInput = utteranceInput.lowercase()

                // Dynamic platform / app name extraction from preposition ("on <App>", "in <App>", etc.)
                val platformMatch = Regex(
                    "(?:on|in|using|via|app)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:and|with|to|deliver|for)|$)",
                    RegexOption.IGNORE_CASE
                ).find(utteranceInput)
                val extractedApp = platformMatch?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }

                val (detectedAppName, detectedPkg) = when {
                    lowerInput.contains("setting") || lowerInput.contains("airplane") || lowerInput.contains("wifi") || lowerInput.contains("bluetooth") ->
                        Pair("System Settings", "com.android.settings")
                    !extractedApp.isNullOrBlank() && !extractedApp.equals("the", ignoreCase = true) ->
                        Pair(extractedApp, null)
                    else ->
                        Pair("Auto-detected during demonstration", null)
                }

                // Dynamic parameter extraction for real-time live preview
                val detectedParams = mutableListOf<Pair<String, String>>()

                // 1. Store / Merchant
                val storeMatch = Regex("(?:from|at)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|to|deliver)|$)", RegexOption.IGNORE_CASE).find(utteranceInput)
                storeMatch?.groupValues?.get(1)?.trim()?.let {
                    if (it.isNotBlank() && !it.equals(extractedApp, ignoreCase = true)) {
                        detectedParams.add(Pair("Store", it.replaceFirstChar { c -> c.uppercase() }))
                    }
                }

                // 2. Item / Core entity
                val itemMatch = Regex("(?:order|get|buy|search\\s+for|find|send|play|open)\\s+(?:(?:a|an|the)\\s+)?([a-zA-Z0-9'\\s]+?)(?:\\s+(?:from|at|on|in|to)|$)", RegexOption.IGNORE_CASE).find(utteranceInput)
                itemMatch?.groupValues?.get(1)?.trim()?.let {
                    if (it.isNotBlank() && it.length > 1 && !it.equals(extractedApp, ignoreCase = true)) {
                        detectedParams.add(Pair("Item", it.replaceFirstChar { c -> c.uppercase() }))
                    }
                }

                // 3. Destination / Address
                val destMatch = Regex("(?:to|deliver\\s+to|send\\s+to)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|from)|$)", RegexOption.IGNORE_CASE).find(utteranceInput)
                destMatch?.groupValues?.get(1)?.trim()?.let {
                    if (it.isNotBlank() && !it.equals(extractedApp, ignoreCase = true)) {
                        detectedParams.add(Pair("To", it.replaceFirstChar { c -> c.uppercase() }))
                    }
                }

                // 4. Platform / App
                if (!extractedApp.isNullOrBlank() && !extractedApp.equals("the", ignoreCase = true)) {
                    detectedParams.add(Pair("App", extractedApp))
                }

                // Detected target parameters preview
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = SaysoPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detected target parameters",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaysoOnSurface
                            )
                        }
                    }

                    // Dynamic Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (detectedParams.isNotEmpty()) {
                            for ((paramLabel, paramVal) in detectedParams.take(3)) {
                                val (bg, fg) = when (paramLabel) {
                                    "Store" -> Pair(SaysoPrimaryFixed, SaysoPrimary)
                                    "Item" -> Pair(SaysoSecondaryContainer, SaysoSecondary)
                                    "App" -> Pair(SaysoTertiaryContainer, SaysoPrimary)
                                    else -> Pair(SaysoSecondaryContainer, SaysoSecondary)
                                }
                                ParamPill(paramLabel, paramVal, bg, fg)
                            }
                        } else {
                            ParamPill(
                                "Action",
                                if (utteranceInput.isBlank()) "Describe task above" else utteranceInput.trim(),
                                SaysoSecondaryContainer,
                                SaysoSecondary
                            )
                        }
                    }
                }

                // Target Application Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaysoSurfaceContainer, SubCardShape)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(SaysoPrimaryFixed, SubCardShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RadioButtonChecked,
                            contentDescription = null,
                            tint = SaysoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TARGET APPLICATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaysoOnSurfaceVariant
                        )
                        Text(
                            text = detectedAppName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SaysoOnSurface
                        )
                        Text(
                            text = "Will record taps & generalize parameters",
                            fontSize = 11.sp,
                            color = SaysoOnSurfaceVariant
                        )
                    }
                }

                // Action Controls
                Button(
                    onClick = {
                        if (utteranceInput.isNotBlank()) {
                            onStartTeaching(utteranceInput.trim(), detectedPkg)
                        }
                    },
                    enabled = utteranceInput.isNotBlank(),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaysoPrimary,
                        contentColor = SaysoOnPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RadioButtonChecked,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Teaching (Record Taps)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Safety Footnote
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaysoSurfaceContainerLow, SubCardShape)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = SaysoSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Zero-Credential Recording",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaysoOnSurface
                        )
                        Text(
                            text = "Sayso never captures passwords, credit cards, or biometrics. Teaching pauses automatically at sensitive fields.",
                            fontSize = 11.sp,
                            color = SaysoOnSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamPill(tag: String, value: String, bg: Color, text: Color) {
    Row(
        modifier = Modifier
            .background(SaysoSurfaceContainerHigh, ChipShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .background(text, BadgeShape)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = SaysoOnSurface
        )
    }
}
