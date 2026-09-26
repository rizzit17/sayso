package com.samsung.prism.teachable.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.ui.components.SlotChipRow
import com.samsung.prism.teachable.ui.components.StepDisplayItem
import com.samsung.prism.teachable.ui.components.StepDisplayStatus
import com.samsung.prism.teachable.ui.components.StepTrackerList
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.PillShape
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOutline
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSubCardShape
import com.samsung.prism.teachable.ui.theme.SubCardShape
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLowest

@Composable
fun LearningReviewDialog(
    workflow: Workflow,
    onSaveConfirmed: () -> Unit,
    onDiscard: () -> Unit,
    onSimulateTest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDiscard,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp),
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
                // Header with Flow Synthesized Badge
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SubCardShape,
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
                            Row(
                                modifier = Modifier
                                    .background(SaysoSecondaryContainer, ChipShape)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SaysoOnSecondaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Flow Synthesized",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaysoOnSecondaryContainer
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .background(SaysoSurfaceContainer, ChipShape)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = SaysoOnSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Captured in 42s",
                                    fontSize = 11.sp,
                                    color = SaysoOnSurfaceVariant
                                )
                            }
                        }

                        // Headline "Learned: {workflow.originalUtterance}"
                        Text(
                            text = "Learned: ${workflow.originalUtterance}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaysoOnSurface,
                            lineHeight = 26.sp
                        )

                        Text(
                            text = "Sayso mapped ${workflow.steps.size} execution steps and synthesized ${workflow.slotSchema.slots.size} dynamic slots you can freely change when speaking.",
                            fontSize = 12.sp,
                            color = SaysoOnSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        // Spoken trigger preview bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SaysoSurfaceContainerLowest, ChipShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(SaysoPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Voice trigger phrase",
                                    fontSize = 10.sp,
                                    color = SaysoOnSurfaceVariant
                                )
                                Text(
                                    text = "“${workflow.originalUtterance}”",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SaysoOnSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = SaysoOutline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Detected Parameters / Variable Slots Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = SaysoSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detected parameters",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoOnSurface
                            )
                            Text(
                                text = " (voice-swappable)",
                                fontSize = 11.sp,
                                color = SaysoOutline
                            )
                        }
                    }

                    SlotChipRow(slots = workflow.slotSchema.slots)

                    // Friendly Tip Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SaysoSurfaceContainerLowest, SubCardShape)
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(SaysoSecondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = SaysoSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Smart Voice Adaptability",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoSecondary
                            )
                            Text(
                                text = "You can speak altered parameters (e.g. different items, dates, or destinations) and Sayso will bind them automatically!",
                                fontSize = 11.sp,
                                color = SaysoOnSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Action Steps Captured Timeline
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                contentDescription = null,
                                tint = SaysoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Action steps captured",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoOnSurface
                            )
                        }
                        Text(
                            text = "${workflow.steps.size} Events",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = SaysoOnSurfaceVariant
                        )
                    }

                    val stepItems = workflow.steps.mapIndexed { idx, step ->
                        StepDisplayItem(
                            stepNumber = idx + 1,
                            title = when (step.actionType) {
                                com.samsung.prism.teachable.teaching.ActionType.CLICK -> "Tap on '${step.target.text ?: step.target.resourceId ?: "element"}'"
                                com.samsung.prism.teachable.teaching.ActionType.SET_TEXT -> "Enter text: \"${step.inputText}\""
                                com.samsung.prism.teachable.teaching.ActionType.SCROLL_FORWARD -> "Scroll forward in list"
                                com.samsung.prism.teachable.teaching.ActionType.SCROLL_BACKWARD -> "Scroll backward in list"
                                com.samsung.prism.teachable.teaching.ActionType.LONG_CLICK -> "Long-click on element"
                            },
                            subtitle = step.target.className,
                            paramTag = if (!step.inputText.isNullOrBlank()) "Param: [${step.inputText}]" else null,
                            status = StepDisplayStatus.COMPLETED
                        )
                    }.toMutableList()

                    // Only add the Safety Handoff Marker if the workflow halted at a credential/payment boundary
                    if (workflow.steps.any { it.isBoundary }) {
                        stepItems.add(
                            StepDisplayItem(
                                stepNumber = stepItems.size + 1,
                                title = "Safety Handoff Point",
                                isBoundary = true
                            )
                        )
                    }

                    StepTrackerList(items = stepItems)
                }

                // Action Controls
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSimulateTest,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SaysoSurfaceContainerLow,
                            contentColor = SaysoPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = SaysoPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test flow in simulator",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SaysoPrimary
                        )
                    }

                    Button(
                        onClick = onSaveConfirmed,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SaysoPrimary,
                            contentColor = SaysoOnPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Flow to Library",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onDiscard,
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Text(
                            text = "Discard & Retrain",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = SaysoOutline
                        )
                    }
                }
            }
        }
    }
}
