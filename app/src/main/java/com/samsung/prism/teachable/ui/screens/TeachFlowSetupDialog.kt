package com.samsung.prism.teachable.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.samsung.prism.teachable.generalization.ExtractedParameters
import com.samsung.prism.teachable.generalization.UniversalDomainExtractor
import com.samsung.prism.teachable.ui.MainViewModel
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.PillShape
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHigh
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLowest
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SubCardShape
import com.samsung.prism.teachable.voice.VoiceInputState
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachFlowSetupDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onStartTeaching: (utterance: String, targetPackage: String?) -> Unit
) {
    val context = LocalContext.current
    var utteranceInput by remember { mutableStateOf("") }

    val voiceState by viewModel.voiceInputState.collectAsState()
    val partialText by viewModel.speechToText.partialText.collectAsState()
    val isGenAiEnabled by viewModel.isGenAiEnabled.collectAsState()
    val isListening = voiceState == VoiceInputState.LISTENING

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            viewModel.startListening()
        }
    }

    // Activate teaching setup state to prevent spoken words from executing replay commands
    DisposableEffect(Unit) {
        viewModel.setTeachingSetupActive(true)
        if (hasMicPermission) {
            viewModel.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        onDispose {
            viewModel.setTeachingSetupActive(false)
            viewModel.stopListening()
        }
    }

    // Collect recognized speech text live into input field
    LaunchedEffect(Unit) {
        viewModel.speechToText.recognizedText.collect { text ->
            if (text.isNotBlank()) {
                utteranceInput = text
            }
        }
    }

    // Reflect partial speech live while user is actively speaking
    LaunchedEffect(partialText) {
        if (isListening && partialText.isNotBlank()) {
            utteranceInput = partialText
        }
    }

    // Gemini GenAI Parameter Extraction with Instant Local Fallback
    var isAnalyzingWithGemini by remember { mutableStateOf(false) }
    var geminiExtractedParams by remember { mutableStateOf<ExtractedParameters?>(null) }

    // Instant local extraction (0ms lag fallback)
    val localParams = remember(utteranceInput) {
        UniversalDomainExtractor.extract(utteranceInput)
    }

    // Debounced GenAI call to Gemini
    LaunchedEffect(utteranceInput) {
        val trimmed = utteranceInput.trim()
        if (trimmed.length > 3) {
            delay(500) // debounce typing/speech
            isAnalyzingWithGemini = true
            try {
                val aiResult = viewModel.analyzeGoalWithGemini(trimmed)
                geminiExtractedParams = aiResult
            } catch (e: Exception) {
                // Keep local fallback
            } finally {
                isAnalyzingWithGemini = false
            }
        } else {
            geminiExtractedParams = null
            isAnalyzingWithGemini = false
        }
    }

    // Prefer Gemini's deep entity reasoning if available, otherwise local universal extractor
    val effectiveParams = geminiExtractedParams ?: localParams
    val detectedParams = effectiveParams.toUiChips()
    val detectedAppName = effectiveParams.appName ?: "Auto-detected during demonstration"
    val detectedPkg = effectiveParams.targetPackage
    val isGeminiPowered = geminiExtractedParams != null && isGenAiEnabled

    // Waveform animation
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.3f else 0.2f,
        targetValue = if (isListening) 1.0f else 0.2f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.8f else 0.2f,
        targetValue = if (isListening) 0.3f else 0.2f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "w2"
    )
    val waveScale3 by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.2f else 0.2f,
        targetValue = if (isListening) 0.9f else 0.2f,
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
                        text = "Describe your goal by voice or text across any app (Settings, Maps, Messaging, Music, Shopping). Gemini extracts target entities live.",
                        fontSize = 13.sp,
                        color = SaysoOnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // Voice Transcript & Interactive Waveform Card
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
                        // Live status pill + Mic Toggle Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (isListening) SaysoPrimary else SaysoOnSurfaceVariant,
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isListening) "LISTENING ACTIVE" else "MIC PAUSED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isListening) SaysoPrimary else SaysoOnSurfaceVariant
                                )
                            }

                            // Interactive Mic Button
                            IconButton(
                                onClick = {
                                    if (!hasMicPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else if (isListening) {
                                        viewModel.stopListening()
                                    } else {
                                        viewModel.startListening()
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isListening) SaysoPrimary else SaysoSurfaceContainerHigh,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = if (isListening) "Mute Microphone" else "Start Microphone",
                                    tint = if (isListening) SaysoOnPrimary else SaysoOnSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Audio Waveform Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(36.dp)
                        ) {
                            val scales = if (isListening) {
                                listOf(waveScale1, waveScale2, waveScale3, waveScale1, waveScale2, waveScale3, waveScale1)
                            } else {
                                listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
                            }
                            for (s in scales) {
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height((32 * s).coerceAtLeast(6.0f).dp)
                                        .background(
                                            if (isListening) SaysoSecondary else SaysoSurfaceContainerHigh,
                                            CircleShape
                                        )
                                )
                            }
                        }

                        // Transcript Input Field
                        OutlinedTextField(
                            value = utteranceInput,
                            onValueChange = { utteranceInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Spoken Goal / Utterance") },
                            placeholder = { Text("e.g. Turn off Airplane mode in Settings, or Find route to Central Park in Maps") },
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

                // Detected target parameters preview with Gemini AI indicator
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
                                text = "Detected Target Parameters",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaysoOnSurface
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isAnalyzingWithGemini) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = SaysoPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Analyzing...",
                                    fontSize = 11.sp,
                                    color = SaysoPrimary
                                )
                            } else if (isGeminiPowered) {
                                Text(
                                    text = "Gemini AI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaysoPrimary,
                                    modifier = Modifier
                                        .background(SaysoPrimaryFixed, BadgeShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Dynamic multi-domain chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (detectedParams.isNotEmpty()) {
                            for ((paramLabel, paramVal) in detectedParams) {
                                val (bg, fg) = when (paramLabel) {
                                    "Setting" -> Pair(SaysoPrimaryFixed, SaysoPrimary)
                                    "Destination" -> Pair(SaysoPrimaryFixed, SaysoPrimary)
                                    "To" -> Pair(SaysoTertiaryContainer, SaysoPrimary)
                                    "Message" -> Pair(SaysoSecondaryContainer, SaysoSecondary)
                                    "Media" -> Pair(SaysoSecondaryContainer, SaysoSecondary)
                                    "Time" -> Pair(SaysoTertiaryContainer, SaysoPrimary)
                                    "Item" -> Pair(SaysoSecondaryContainer, SaysoSecondary)
                                    "Store" -> Pair(SaysoPrimaryFixed, SaysoPrimary)
                                    "App" -> Pair(SaysoTertiaryContainer, SaysoPrimary)
                                    "Query" -> Pair(SaysoSecondaryContainer, SaysoSecondary)
                                    else -> Pair(SaysoSecondaryContainer, SaysoSecondary)
                                }
                                ParamPill(paramLabel, paramVal, bg, fg)
                            }
                        } else {
                            ParamPill(
                                "Action",
                                if (utteranceInput.isBlank()) "Speak or describe goal above" else utteranceInput.trim(),
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
                            text = if (detectedPkg != null) detectedPkg else "Minimizes to home screen to learn your taps",
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

                Text(
                    text = "SaySo will minimize to your home screen so you can demonstrate your task naturally across any app.",
                    fontSize = 11.sp,
                    color = SaysoOnSurfaceVariant,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

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
