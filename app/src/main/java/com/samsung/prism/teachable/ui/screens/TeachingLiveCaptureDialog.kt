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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.samsung.prism.teachable.teaching.TeachingSession
import com.samsung.prism.teachable.ui.components.StepDisplayItem
import com.samsung.prism.teachable.ui.components.StepDisplayStatus
import com.samsung.prism.teachable.ui.components.StepTrackerList
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.PillShape
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOnTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOutline
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoSubCardShape
import com.samsung.prism.teachable.ui.theme.SubCardShape
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHighest
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryContainer
import kotlinx.coroutines.delay

@Composable
fun TeachingLiveCaptureDialog(
    session: TeachingSession,
    onStopAndSave: () -> Unit,
    onCancel: () -> Unit
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timerText = String.format("%02d:%02d", minutes, seconds)

    Dialog(
        onDismissRequest = onCancel,
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
                // Persistent Active Teaching / Listening Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SubCardShape,
                    colors = CardDefaults.cardColors(containerColor = SaysoSecondaryFixed)
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
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(SaysoSecondary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TEACHING — Watching Taps",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaysoOnSecondaryContainer
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .background(SaysoSurfaceContainerHighest.copy(alpha = 0.6f), ChipShape)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = SaysoOnSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = timerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SaysoOnSurfaceVariant
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "RECORDED GOAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoOnSecondaryFixed.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "“${session.originalUtterance}”",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaysoOnSecondaryFixed
                            )
                        }
                    }
                }

                // Safety Guardrail Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaysoTertiaryContainer, SubCardShape)
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = SaysoOnTertiaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Safety Guardrail Active",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaysoOnTertiaryContainer
                        )
                        Text(
                            text = "Replay strictly halts before OTP screens, card credentials, or 1-click checkout payment confirmation.",
                            fontSize = 11.sp,
                            color = SaysoOnTertiaryContainer.copy(alpha = 0.9f),
                            lineHeight = 15.sp
                        )
                    }
                }

                // Captured Actions Stream
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Captured Actions (${session.retainedActions.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SaysoOnSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = SaysoPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Real-time stream",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SaysoPrimary
                            )
                        }
                    }

                    // Map retained actions to StepDisplayItem
                    val stepItems = mutableListOf<StepDisplayItem>()
                    for ((idx, action) in session.retainedActions.withIndex()) {
                        stepItems.add(
                            StepDisplayItem(
                                stepNumber = idx + 1,
                                title = when (action.actionType) {
                                    com.samsung.prism.teachable.teaching.ActionType.CLICK -> "Tapped on '${action.targetNode.text ?: action.targetNode.resourceId ?: "element"}'"
                                    com.samsung.prism.teachable.teaching.ActionType.SET_TEXT -> "Entered text: \"${action.inputText}\""
                                    com.samsung.prism.teachable.teaching.ActionType.SCROLL_FORWARD -> "Scrolled forward in list"
                                    com.samsung.prism.teachable.teaching.ActionType.SCROLL_BACKWARD -> "Scrolled backward in list"
                                    com.samsung.prism.teachable.teaching.ActionType.LONG_CLICK -> "Long-pressed on element"
                                },
                                subtitle = action.targetNode.className,
                                paramTag = if (!action.inputText.isNullOrBlank()) "Param: [Text: ${action.inputText}]" else null,
                                status = StepDisplayStatus.COMPLETED
                            )
                        )
                    }

                    // Add synthetic ignored action if session has filtered count > 0 (Bonus B1)
                    if (session.filteredActionsCount > 0) {
                        stepItems.add(
                            StepDisplayItem(
                                stepNumber = stepItems.size + 1,
                                title = "Transient UI Interruption",
                                subtitle = "Incoming phone call popup or accidental tap loop",
                                isFiltered = true
                            )
                        )
                    }

                    // Active live awaiting step
                    stepItems.add(
                        StepDisplayItem(
                            stepNumber = stepItems.size + 1,
                            title = "Observing next tap in target app...",
                            subtitle = "Tap button in target app now. Sayso will map UI coordinates and labels automatically.",
                            status = StepDisplayStatus.ACTIVE
                        )
                    )

                    StepTrackerList(items = stepItems)
                }

                // Controls
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStopAndSave,
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
                            text = "Stop & Save Flow",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = SaysoOutline,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cancel Session",
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
