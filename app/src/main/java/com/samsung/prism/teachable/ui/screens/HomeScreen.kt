package com.samsung.prism.teachable.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.replay.ReplayState
import com.samsung.prism.teachable.ui.MainViewModel
import com.samsung.prism.teachable.ui.components.ClarificationCard
import com.samsung.prism.teachable.ui.components.FullScreenHandoffCard
import com.samsung.prism.teachable.ui.components.RunOutcomePill
import com.samsung.prism.teachable.ui.components.SlotChipRow
import com.samsung.prism.teachable.ui.components.StateHeaderBar
import com.samsung.prism.teachable.ui.components.StepDisplayItem
import com.samsung.prism.teachable.ui.components.StepDisplayStatus
import com.samsung.prism.teachable.ui.components.StepTrackerList
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.PillShape
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOnTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnTertiaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoOutline
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryFixedDim
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoSubCardShape
import com.samsung.prism.teachable.ui.theme.SubCardShape
import com.samsung.prism.teachable.ui.theme.SaysoSuccess
import com.samsung.prism.teachable.ui.theme.SaysoSuccessContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHigh
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHighest
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLowest
import com.samsung.prism.teachable.ui.theme.SaysoTertiary
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryFixed
import com.samsung.prism.teachable.voice.VoiceInputState

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToWorkflowDetail: (String) -> Unit,
    onNavigateToFlowsList: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val isA11yConnected by viewModel.isA11yConnected.collectAsState()
    val workflows by viewModel.workflows.collectAsState()
    val replayState by viewModel.replayState.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val stepIndex by viewModel.currentStepIndex.collectAsState()
    val totalSteps by viewModel.totalSteps.collectAsState()
    val boundaryAlert by viewModel.boundaryNotification.collectAsState()
    val stuckQuestion by viewModel.stuckClarification.collectAsState()
    val voiceState by viewModel.voiceInputState.collectAsState()
    val speechError by viewModel.speechErrorMessage.collectAsState()
    val recentRuns by viewModel.recentRuns.collectAsState()
    val activeSession by viewModel.currentTeachingSession.collectAsState()

    var manualTextInput by remember { mutableStateOf("") }
    var showTeachSetupDialog by remember { mutableStateOf(false) }
    var reviewWorkflow by remember { mutableStateOf<Workflow?>(null) }

    val lastLearned by viewModel.lastLearnedWorkflow.collectAsState()
    androidx.compose.runtime.LaunchedEffect(lastLearned) {
        if (lastLearned != null) {
            reviewWorkflow = lastLearned
            viewModel.clearLastLearnedWorkflow()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice commands.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val auraScale1 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura1"
    )
    val auraScale2 by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura2"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SaysoSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Header with App Title & State Header Bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(SaysoSecondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Sayso",
                                tint = SaysoOnSecondaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Home",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoOnSurface,
                                lineHeight = 28.sp
                            )
                            Text(
                                text = "SAYSO ASSISTANT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SaysoOnSurfaceVariant,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Gemini Settings Action Button
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .background(SaysoPrimaryContainer, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Gemini Settings",
                                tint = SaysoOnPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // A11y Settings Action Button
                        IconButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(SaysoSurfaceContainer, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Accessibility Settings",
                                tint = SaysoOnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Global StateHeaderBar per design.md §8
                StateHeaderBar(
                    state = replayState,
                    isA11yConnected = isA11yConnected
                )

                // Accessibility Service OFF Banner
                if (!isA11yConnected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SaysoTertiaryContainer),
                        shape = CardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accessibility Service is OFF",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = SaysoOnTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "SaySo requires Automation Accessibility Service enabled to run workflows and automate actions.",
                                    fontSize = 12.sp,
                                    color = SaysoOnTertiaryContainer.copy(alpha = 0.85f),
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SaysoTertiary)
                            ) {
                                Text("Enable", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Ambient State Headline Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Hey there, what should we automate?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = SaysoOnSurface,
                    lineHeight = 32.sp
                )
                Text(
                    text = "Speak a command, trigger a learned flow, or teach a new workflow by tapping.",
                    fontSize = 13.sp,
                    color = SaysoOnSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // 3. Hero Interactive Microphone FAB Section matching home_sayso/code.html
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    // Pulsing Aura Ring 1
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .scale(if (voiceState == VoiceInputState.LISTENING) auraScale1 else 1.0f)
                            .background(SaysoPrimaryFixedDim.copy(alpha = 0.35f), CircleShape)
                    )
                    // Pulsing Aura Ring 2
                    Box(
                        modifier = Modifier
                            .size(108.dp)
                            .scale(if (voiceState == VoiceInputState.LISTENING) auraScale2 else 1.0f)
                            .background(SaysoSecondaryContainer.copy(alpha = 0.6f), CircleShape)
                    )
                    // Core Hero Mic Button (96x96dp)
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(
                                if (voiceState == VoiceInputState.LISTENING) SaysoSecondary else SaysoPrimary,
                                CircleShape
                            )
                            .clickable {
                                if (voiceState == VoiceInputState.LISTENING) {
                                    viewModel.stopListening()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        viewModel.startListening()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (voiceState == VoiceInputState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Command",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                // Dynamic feedback caption
                Text(
                    text = when {
                        voiceState == VoiceInputState.LISTENING -> "Listening for intent..."
                        voiceState == VoiceInputState.ERROR && speechError != null -> speechError!!
                        voiceState == VoiceInputState.ERROR -> "Speech input error. Please type command below."
                        replayState != ReplayState.IDLE -> statusMsg
                        else -> "Tap to speak or teach"
                    },
                    fontSize = 14.sp,
                    fontWeight = if (voiceState == VoiceInputState.LISTENING || voiceState == VoiceInputState.ERROR) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        voiceState == VoiceInputState.LISTENING -> SaysoSecondary
                        voiceState == VoiceInputState.ERROR -> Color(0xFFD32F2F)
                        else -> SaysoOnSurfaceVariant
                    }
                )

                // Teach Quick Action Pill Button
                Button(
                    onClick = { showTeachSetupDialog = true },
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaysoSurfaceContainerHigh,
                        contentColor = SaysoPrimary
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+ Teach a new flow",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. Quick Text Command Input Bar (for fast testing / headless emulator)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaysoSurfaceContainerLow, SubCardShape)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualTextInput,
                    onValueChange = { manualTextInput = it },
                    placeholder = {
                        Text(
                            text = "Type voice command or prompt...",
                            fontSize = 12.sp,
                            color = SaysoOutline
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = PillShape,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SaysoPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = SaysoSurfaceContainerLowest,
                        unfocusedContainerColor = SaysoSurfaceContainerLowest
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (manualTextInput.isNotBlank()) {
                            viewModel.runCommand(manualTextInput.trim())
                            manualTextInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(SaysoPrimary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Run",
                        tint = SaysoOnPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 5. Active Clarification / Handoff Notifications
        stuckQuestion?.let { question ->
            item {
                ClarificationCard(
                    question = question,
                    onOptionSelected = { option -> viewModel.resolveClarification(option) },
                    onVoiceResponseClick = { viewModel.startListening() }
                )
            }
        }

        boundaryAlert?.let { alert ->
            item {
                FullScreenHandoffCard(
                    reason = alert,
                    onDismiss = { viewModel.dismissBoundaryAlert() }
                )
            }
        }

        // 6. Active Execution Step Tracker (shown during runtime execution)
        if (replayState != ReplayState.IDLE && replayState != ReplayState.COMPLETED) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Running Automation",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoOnSurface
                            )
                            Text(
                                text = "Step $stepIndex of $totalSteps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaysoPrimary
                            )
                        }

                        Text(
                            text = statusMsg,
                            fontSize = 13.sp,
                            color = SaysoOnSurfaceVariant
                        )
                    }
                }
            }
        }

        // 7. Recent Flows Carousel Section matching home_sayso/code.html
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent flows",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface
                    )
                    Row(
                        modifier = Modifier.clickable { onNavigateToFlowsList() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "See all (${workflows.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SaysoPrimary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = SaysoPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Horizontal Flow Cards Carousel
                if (workflows.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No flows taught yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaysoOnSurface
                            )
                            Text(
                                text = "Tap '+ Teach a new flow' or test with sample commands below.",
                                fontSize = 12.sp,
                                color = SaysoOnSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for ((index, wf) in workflows.take(5).withIndex()) {
                            val (catIcon, catBg, catText) = when (index % 3) {
                                0 -> Triple(Icons.Default.PlayArrow, SaysoTertiaryFixed, SaysoOnTertiaryFixed)
                                1 -> Triple(Icons.Default.AutoFixHigh, SaysoSecondaryFixed, SaysoOnSecondaryFixed)
                                else -> Triple(Icons.Default.TipsAndUpdates, SaysoPrimaryFixed, SaysoOnPrimaryContainer)
                            }

                            Card(
                                modifier = Modifier
                                    .width(240.dp)
                                    .clickable { onNavigateToWorkflowDetail(wf.id) },
                                shape = CardShape,
                                colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(catBg, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = catIcon,
                                                contentDescription = null,
                                                tint = catText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Text(
                                            text = "${wf.slotSchema.slots.size} slots",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = SaysoOnSurfaceVariant,
                                            modifier = Modifier
                                                .background(SaysoSurfaceContainerHighest, BadgeShape)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = wf.originalUtterance,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SaysoOnSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = wf.supportedPackages.firstOrNull() ?: wf.intentTag.ifBlank { "Taught Workflow" },
                                            fontSize = 11.sp,
                                            color = SaysoOnSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${wf.steps.size} steps",
                                            fontSize = 11.sp,
                                            color = SaysoOutline
                                        )
                                        IconButton(
                                            onClick = { viewModel.runCommand(wf.originalUtterance) },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(SaysoPrimaryContainer, CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Run",
                                                tint = SaysoOnPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Automation Activity Card (Last-Run Execution) matching home_sayso/code.html
        recentRuns.firstOrNull()?.let { lastRun ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Automation Activity",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = SaysoOnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Last executed",
                                        fontSize = 12.sp,
                                        color = SaysoOnSurfaceVariant
                                    )
                                }
                                RunOutcomePill(status = lastRun.status)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = lastRun.workflowTitle.ifBlank { "Recent Voice Flow" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaysoOnSurface
                                )
                                Text(
                                    text = if (lastRun.status == com.samsung.prism.teachable.storage.RunStatus.COMPLETED_TO_BOUNDARY) {
                                        "Stopped safely at the final payment confirmation screen. Replayed ${lastRun.stepResults.size} UI interactions."
                                    } else {
                                        "Completed ${lastRun.stepResults.size} UI automation steps."
                                    },
                                    fontSize = 12.sp,
                                    color = SaysoOnSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }

                            // Stepper Preview Thumbnail Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StepperPreviewTile(Icons.Default.Search, "Search item", SaysoPrimary, Modifier.weight(1f))
                                StepperPreviewTile(Icons.Default.ShoppingCart, "Cart added", SaysoPrimary, Modifier.weight(1f))
                                StepperPreviewTile(Icons.Default.Lock, "Pay safety", SaysoTertiary, Modifier.weight(1f))
                            }

                            // Action Footer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.runCommand(lastRun.workflowTitle) },
                                    shape = PillShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SaysoPrimary,
                                        contentColor = SaysoOnPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Re-run flow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { onNavigateToWorkflowDetail(lastRun.workflowId) },
                                    shape = PillShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SaysoSurfaceContainerHighest,
                                        contentColor = SaysoOnSurfaceVariant
                                    ),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Trace", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 9. Voice Prompt Hint Tile matching home_sayso/code.html
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaysoSurfaceContainer, CardShape)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SaysoTertiaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = SaysoOnTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "VOICE TIP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaysoOnSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Speak any taught voice command or tap + to teach a new flow",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SaysoPrimary
                    )
                }
            }
        }
    }

    // Interactive Dialogs
    if (showTeachSetupDialog) {
        TeachFlowSetupDialog(
            viewModel = viewModel,
            onDismiss = { showTeachSetupDialog = false },
            onStartTeaching = { utterance, pkg ->
                showTeachSetupDialog = false
                viewModel.startTeaching(utterance, pkg)

                // Minimize the app to the device home page immediately after naming
                context.findActivity()?.moveTaskToBack(true)
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }
        )
    }

    activeSession?.let { session ->
        TeachingLiveCaptureDialog(
            session = session,
            onStopAndSave = {
                viewModel.stopTeachingAndSave()
            },
            onCancel = {
                viewModel.cancelTeaching()
            },
            onMinimize = {
                context.findActivity()?.moveTaskToBack(true)
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
            }
        )
    }

    reviewWorkflow?.let { workflow ->
        LearningReviewDialog(
            workflow = workflow,
            onSaveConfirmed = { reviewWorkflow = null },
            onDiscard = {
                viewModel.deleteWorkflow(workflow.id)
                reviewWorkflow = null
            },
            onSimulateTest = {
                reviewWorkflow = null
                viewModel.runCommand(workflow.originalUtterance)
            }
        )
    }
}

@Composable
private fun StepperPreviewTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(SaysoSurfaceContainer, BadgeShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = SaysoOnSurfaceVariant,
            maxLines = 1
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
