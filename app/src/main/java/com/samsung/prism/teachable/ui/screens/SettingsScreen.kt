package com.samsung.prism.teachable.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.ai.GeminiConfigStore
import com.samsung.prism.teachable.ui.MainViewModel
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.CredentialHandoffBg
import com.samsung.prism.teachable.ui.theme.CredentialHandoffBorder
import com.samsung.prism.teachable.ui.theme.CredentialHandoffContainer
import com.samsung.prism.teachable.ui.theme.CredentialHandoffText
import com.samsung.prism.teachable.ui.theme.SaysoError
import com.samsung.prism.teachable.ui.theme.SaysoErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOutline
import com.samsung.prism.teachable.ui.theme.SaysoOutlineVariant
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSuccess
import com.samsung.prism.teachable.ui.theme.SaysoSuccessContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHigh
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLowest

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val savedApiKey by viewModel.geminiApiKey.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isGenAiEnabled by viewModel.isGenAiEnabled.collectAsState()
    val validationStatus by viewModel.validationStatus.collectAsState()
    val isValidating by viewModel.isValidating.collectAsState()
    val isA11yConnected by viewModel.isA11yConnected.collectAsState()

    var inputKey by remember(savedApiKey) { mutableStateOf(savedApiKey) }
    var showKeyText by remember { mutableStateOf(false) }
    var testFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val tabs = listOf("Your Gemini API Key", "System & Safety")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaysoSurface)
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaysoSurfaceContainerLowest)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SaysoPrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Settings",
                        tint = SaysoOnPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Settings & Intelligence",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface
                    )
                    Text(
                        text = "Configure Google Gemini GenAI & Platform Shields",
                        fontSize = 12.sp,
                        color = SaysoOnSurfaceVariant
                    )
                }
            }
        }

        // Tab Row with "Your Gemini API Key" tab prominently displayed
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = SaysoSurfaceContainerLowest,
            contentColor = SaysoPrimary,
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SaysoOutlineVariant.copy(alpha = 0.5f))
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.VpnKey else Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        // Tab Content
        when (selectedTabIndex) {
            0 -> {
                // TAB 0: "Your Gemini API Key"
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status Badge Banner
                    item {
                        val hasKey = savedApiKey.isNotBlank() && isGenAiEnabled
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasKey) SaysoSuccessContainer else SaysoSurfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (hasKey) SaysoSuccess else SaysoOutline,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (hasKey) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (hasKey) "Gemini GenAI Engine Active" else "Local Deterministic Mode (Offline)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (hasKey) Color(0xFF00210E) else SaysoOnSurface
                                    )
                                    Text(
                                        text = if (hasKey) {
                                            "Active Model: $selectedModel · Speech-to-Intent, 1-shot generalization, and recovery are live."
                                        } else {
                                            "No Gemini API key saved yet. Enter your key below to activate Google Gemini reasoning."
                                        },
                                        fontSize = 12.sp,
                                        color = if (hasKey) Color(0xFF00391A) else SaysoOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Key Input Card
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Your Google Gemini API Key",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = SaysoOnSurface
                                )
                                Text(
                                    text = "SaySo uses Google Gemini to understand complex natural voice phrasing, generalize recorded actions into reusable templates, and generate conversational questions when stuck.",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = SaysoOnSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = inputKey,
                                    onValueChange = {
                                        inputKey = it
                                        testFeedback = null
                                    },
                                    label = { Text("Gemini API Key") },
                                    placeholder = { Text("AIzaSy...") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.VpnKey,
                                            contentDescription = null,
                                            tint = SaysoPrimary
                                        )
                                    },
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = { showKeyText = !showKeyText }) {
                                                Icon(
                                                    imageVector = if (showKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (showKeyText) "Hide" else "Show",
                                                    tint = SaysoOnSurfaceVariant
                                                )
                                            }
                                            if (inputKey.isNotEmpty()) {
                                                IconButton(onClick = { inputKey = "" }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Clear,
                                                        contentDescription = "Clear",
                                                        tint = SaysoOnSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SaysoPrimary,
                                        unfocusedBorderColor = SaysoOutlineVariant
                                    )
                                )

                                // Action Buttons Row: Test & Validate, Save Key
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            testFeedback = null
                                            if (inputKey.isBlank()) {
                                                Toast.makeText(context, "Please enter an API key to test", Toast.LENGTH_SHORT).show()
                                                return@OutlinedButton
                                            }
                                            viewModel.testGeminiApiKey(inputKey) { success, msg ->
                                                testFeedback = Pair(success, msg)
                                            }
                                        },
                                        enabled = !isValidating && inputKey.isNotBlank(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isValidating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = SaysoPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Testing...", fontSize = 12.sp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Test Key", fontSize = 12.sp)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.saveGeminiApiKey(inputKey)
                                            Toast.makeText(context, "Gemini API Key saved successfully!", Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = inputKey.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = SaysoPrimary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save Key", fontSize = 12.sp)
                                    }
                                }

                                if (savedApiKey.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.clearGeminiApiKey()
                                                inputKey = ""
                                                testFeedback = null
                                                Toast.makeText(context, "API Key removed. Reverted to offline mode.", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SaysoError)
                                        ) {
                                            Text("Remove Key (Use Offline)", fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Test Feedback Banner
                                AnimatedVisibility(visible = testFeedback != null) {
                                    testFeedback?.let { (success, msg) ->
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (success) SaysoSuccessContainer else SaysoErrorContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = if (success) SaysoSuccess else SaysoError,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = msg,
                                                    fontSize = 12.sp,
                                                    color = if (success) Color(0xFF00210E) else SaysoOnErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Model Selection Card
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Select Gemini Model",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SaysoOnSurface
                                )
                                Text(
                                    text = "Choose the Gemini model that fits your latency and reasoning needs. All models run zero-touch safety locally.",
                                    fontSize = 12.sp,
                                    color = SaysoOnSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GeminiConfigStore.SUPPORTED_MODELS.forEach { model ->
                                        val isSelected = selectedModel == model
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setModel(model) },
                                            label = {
                                                Text(
                                                    text = when (model) {
                                                        "gemini-1.5-flash" -> "1.5 Flash (Fast)"
                                                        "gemini-2.0-flash" -> "2.0 Flash"
                                                        "gemini-1.5-pro" -> "1.5 Pro"
                                                        else -> model
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = SaysoPrimaryContainer,
                                                selectedLabelColor = SaysoOnPrimaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // What Gemini Does in Sayso (Hackathon Alignment Card)
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "GenAI Capabilities Powered by Gemini",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = SaysoOnSurface
                                )

                                CapabilityItem(
                                    title = "Conversational Paraphrase & Intent (T2-T9)",
                                    description = "Maps natural variations ('get me a pizza', 'feed me dinner', 'deliver a pie') directly to learned workflows."
                                )

                                CapabilityItem(
                                    title = "1-Shot Workflow Generalization",
                                    description = "Deduces variable slots (items, quantities, recipients) from recorded UI touches and creates parameterized schemas."
                                )

                                CapabilityItem(
                                    title = "Generative Stuck Clarification (T10 & Bonus B3)",
                                    description = "Synthesizes human-friendly questions explaining what is on screen when UI elements are missing."
                                )

                                CapabilityItem(
                                    title = "Zero-Touch Credential Shield (T11 Penalty Shield)",
                                    description = "Purely local 5-layer barrier halts automation before payment/password fields; credentials never leave the device."
                                )
                            }
                        }
                    }

                    // Get Free Key Guide Card
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = SaysoPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "How to get a free Gemini API Key",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = SaysoOnSurface
                                    )
                                }
                                Text(
                                    text = "1. Visit Google AI Studio at aistudio.google.com\n2. Sign in with your Google Account\n3. Click 'Create API key'\n4. Copy and paste it into the field above",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = SaysoOnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://aistudio.google.com/app/apikey")
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaysoPrimaryContainer)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = SaysoOnPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Open Google AI Studio",
                                        fontSize = 12.sp,
                                        color = SaysoOnPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: "System & Safety"
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Accessibility Service Status Card
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
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
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = if (isA11yConnected) SaysoSuccess else SaysoError
                                        )
                                        Text(
                                            text = "Accessibility Service",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = SaysoOnSurface
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isA11yConnected) SaysoSuccessContainer else SaysoErrorContainer,
                                                CircleShape
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isA11yConnected) "CONNECTED" else "NOT ENABLED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isA11yConnected) SaysoSuccess else SaysoError
                                        )
                                    }
                                }

                                Text(
                                    text = "SaySo requires Android Accessibility Service permissions to record teaching gestures and replay UI clicks autonomously without partner SDKs.",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = SaysoOnSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SaysoPrimary)
                                ) {
                                    Text("Open Accessibility Settings", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Credential Boundary Safety Card
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = CredentialHandoffBg),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CredentialHandoffBorder.copy(alpha = 0.6f), CardShape)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = CredentialHandoffText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Deterministic Credential Boundary (T11)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = CredentialHandoffText
                                    )
                                }
                                Text(
                                    text = "A deterministic 5-layer shield monitors payment, OTP, CVV, password, and biometric surfaces. When reached, automation halts instantly and hands full physical control to the user. No sensitive data is ever recorded or transmitted.",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = CredentialHandoffText
                                )
                            }
                        }
                    }

                    // System Architecture Specs Card
                    item {
                        Card(
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLowest),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "System Specification",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = SaysoOnSurface
                                )
                                Text(
                                    text = "• Platform: Android 9+ (API 28-34)\n• UI Framework: Jetpack Compose Material 3\n• GenAI Engine: Google Gemini REST via Dispatchers.IO\n• Storage: Room Database + SharedPreferences\n• Accessibility: Non-invasive AccessibilityEvent + AccessibilityNodeInfo",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 18.sp,
                                    color = SaysoOnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityItem(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(6.dp)
                .background(SaysoPrimary, CircleShape)
        )
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = SaysoOnSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = SaysoOnSurfaceVariant
            )
        }
    }
}
