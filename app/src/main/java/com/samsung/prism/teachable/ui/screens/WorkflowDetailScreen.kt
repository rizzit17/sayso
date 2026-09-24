package com.samsung.prism.teachable.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.ui.MainViewModel
import com.samsung.prism.teachable.ui.components.SlotChipRow
import com.samsung.prism.teachable.ui.components.StepDisplayItem
import com.samsung.prism.teachable.ui.components.StepDisplayStatus
import com.samsung.prism.teachable.ui.components.StepTrackerList
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHigh
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHighest
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow

@Composable
fun WorkflowDetailScreen(
    workflowId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val workflows by viewModel.workflows.collectAsState()
    val workflow = workflows.find { it.id == workflowId }

    if (workflow == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SaysoSurface),
            contentAlignment = Alignment.Center
        ) {
            Text("Workflow not found", color = SaysoOnSurfaceVariant, fontSize = 16.sp)
        }
        return
    }

    var showParamDialog by remember { mutableStateOf(false) }
    val paramOverrides = remember {
        mutableStateMapOf<String, String>().apply {
            workflow.slotSchema.slots.forEach { slot ->
                put(slot.name, slot.defaultValue ?: "")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SaysoSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = SaysoOnSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Workflow Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface
                    )
                    Text(
                        text = "Sayso Learned Routine",
                        fontSize = 12.sp,
                        color = SaysoOnSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .background(SaysoSecondaryContainer, BadgeShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${workflow.steps.size} steps",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SaysoOnSecondaryContainer
                    )
                }
            }
        }

        // Summary Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(SaysoPrimaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = SaysoOnPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = workflow.supportedPackages.firstOrNull() ?: "Native App",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = SaysoOnSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showParamDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SaysoPrimary,
                                contentColor = SaysoOnPrimary
                            ),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Replay", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = workflow.originalUtterance,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Intent: ${workflow.generalizedIntent}",
                        fontSize = 13.sp,
                        color = SaysoPrimary,
                        fontWeight = FontWeight.Medium
                    )

                    if (workflow.slotSchema.slots.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Detected Dynamic Slots (${workflow.slotSchema.slots.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SaysoOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SlotChipRow(slots = workflow.slotSchema.slots)
                    }
                }
            }
        }

        // Steps Section Header
        item {
            Text(
                text = "Execution Steps (${workflow.steps.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SaysoOnSurface
            )
        }

        // Steps List using StepTrackerList
        item {
            val stepItems = workflow.steps.mapIndexed { index, step ->
                val targetDesc = step.target.text
                    ?: step.target.contentDescription
                    ?: step.target.semanticRole
                    ?: "Element"

                val subText = buildString {
                    if (!step.inputText.isNullOrEmpty()) {
                        append("Input: '${step.inputText}'")
                    }
                    if (!step.expectedStateTransition.expectedTextSubstring.isNullOrEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        append("Expects: '${step.expectedStateTransition.expectedTextSubstring}'")
                    }
                }.ifBlank { null }

                StepDisplayItem(
                    stepNumber = index + 1,
                    title = "${step.actionType.name}: '$targetDesc'",
                    subtitle = subText,
                    paramTag = step.slotBinding?.let { "Bound: {$it}" },
                    status = StepDisplayStatus.PENDING,
                    isFiltered = false,
                    isBoundary = step.isBoundary
                )
            }

            StepTrackerList(items = stepItems)
        }
    }

    // Parameter Override Test Run Dialog
    if (showParamDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showParamDialog = false }) {
            Card(
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Replay with Custom Slots",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Override parameter values to test semantic generalization (e.g. T4–T6, T9–T10).",
                        fontSize = 13.sp,
                        color = SaysoOnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    for (slot in workflow.slotSchema.slots) {
                        OutlinedTextField(
                            value = paramOverrides[slot.name] ?: "",
                            onValueChange = { paramOverrides[slot.name] = it },
                            label = { Text(slot.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SaysoPrimary,
                                focusedLabelColor = SaysoPrimary,
                                cursorColor = SaysoPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showParamDialog = false }
                        ) {
                            Text("Cancel", color = SaysoOnSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                var customUtterance = workflow.generalizedIntent
                                for ((k, v) in paramOverrides) {
                                    customUtterance = customUtterance.replace("{$k}", v)
                                }
                                viewModel.runCommand(customUtterance)
                                showParamDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SaysoPrimary,
                                contentColor = SaysoOnPrimary
                            ),
                            shape = CircleShape
                        ) {
                            Text("Run Replay", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
