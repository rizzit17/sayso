package com.samsung.prism.teachable.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.ui.MainViewModel
import com.samsung.prism.teachable.ui.components.SlotChipRow
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.PillShape
import com.samsung.prism.teachable.ui.theme.SaysoError
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOutline
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHighest
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLowest

@Composable
fun LearnedFlowsScreen(
    viewModel: MainViewModel,
    onNavigateToWorkflowDetail: (String) -> Unit
) {
    val workflows by viewModel.workflows.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filtered = if (searchQuery.isBlank()) {
        workflows
    } else {
        workflows.filter {
            it.originalUtterance.contains(searchQuery, ignoreCase = true) ||
            it.generalizedIntent.contains(searchQuery, ignoreCase = true) ||
            it.intentTag.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SaysoSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Learned Flows",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaysoOnSurface
                )
                Text(
                    text = "Your library of generalized, voice-triggered Android automations.",
                    fontSize = 13.sp,
                    color = SaysoOnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search learned flows...", color = SaysoOutline, fontSize = 13.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = SaysoOutline) },
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SaysoPrimary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = SaysoSurfaceContainerLow,
                    unfocusedContainerColor = SaysoSurfaceContainerLow
                )
            )
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No workflows learned yet" else "No matching workflows found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SaysoOnSurface
                        )
                        Text(
                            text = "Teach a workflow once by voice and taps, and Sayso will generalize it here.",
                            fontSize = 12.sp,
                            color = SaysoOnSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filtered) { wf ->
                LearnedFlowItem(
                    workflow = wf,
                    onRun = { viewModel.runCommand(wf.originalUtterance) },
                    onDelete = { viewModel.deleteWorkflow(wf.id) },
                    onDetails = { onNavigateToWorkflowDetail(wf.id) }
                )
            }
        }
    }
}

@Composable
fun LearnedFlowItem(
    workflow: Workflow,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetails() },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workflow.originalUtterance,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = workflow.generalizedIntent,
                        fontSize = 12.sp,
                        color = SaysoPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRun,
                        modifier = Modifier
                            .size(36.dp)
                            .background(SaysoPrimary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run",
                            tint = SaysoOnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = SaysoError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Parameter Chips
            SlotChipRow(slots = workflow.slotSchema.slots)

            // Step count badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${workflow.steps.size} steps • Target: ${workflow.supportedPackages.firstOrNull() ?: "App"}",
                    fontSize = 11.sp,
                    color = SaysoOutline
                )
                Text(
                    text = "Tap to inspect",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = SaysoPrimary
                )
            }
        }
    }
}
