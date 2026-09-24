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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.storage.RunResult
import com.samsung.prism.teachable.storage.RunStatus
import com.samsung.prism.teachable.ui.MainViewModel
import com.samsung.prism.teachable.ui.components.RunOutcomePill
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.CredentialHandoffContainer
import com.samsung.prism.teachable.ui.theme.CredentialHandoffText
import com.samsung.prism.teachable.ui.theme.SaysoErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoSubCardShape
import com.samsung.prism.teachable.ui.theme.SaysoSurface
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHighest
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RunHistoryScreen(viewModel: MainViewModel) {
    val recentRuns by viewModel.recentRuns.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SaysoSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Execution History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = SaysoOnSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Audit trail of all voice replays, safety boundaries, and recoveries.",
                fontSize = 13.sp,
                color = SaysoOnSurfaceVariant
            )
        }

        if (recentRuns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = SaysoOnSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No runs recorded yet",
                            color = SaysoOnSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(recentRuns) { run ->
                RunResultCard(run = run)
            }
        }
    }
}

@Composable
fun RunResultCard(run: RunResult) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(run.startedAt))
    val durationSeconds = (run.endedAt - run.startedAt) / 1000.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = run.workflowTitle.ifBlank { "Workflow Execution" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaysoOnSurface,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge using reusable RunOutcomePill
                RunOutcomePill(status = run.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = SaysoOnSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = SaysoOnSurfaceVariant
                    )
                }
                Text(
                    text = "%.1fs duration".format(durationSeconds),
                    fontSize = 12.sp,
                    color = SaysoOnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            if (run.boundParams.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((k, v) in run.boundParams) {
                        Box(
                            modifier = Modifier
                                .background(SaysoSurfaceContainerHighest, ChipShape)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$k: $v",
                                fontSize = 11.sp,
                                color = SaysoOnSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Guaranteed Payment / Credential Safety Stop Highlight
            if (run.status == RunStatus.COMPLETED_TO_BOUNDARY) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CredentialHandoffContainer, SaysoSubCardShape)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CredentialHandoffText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Zero-Touch Security Boundary",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CredentialHandoffText
                        )
                        Text(
                            text = run.failureReason ?: "Automation paused before payment screen. User completed purchase.",
                            fontSize = 11.sp,
                            color = CredentialHandoffText
                        )
                    }
                }
            } else if (!run.failureReason.isNullOrBlank() && run.status == RunStatus.FAILED) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SaysoErrorContainer, SaysoSubCardShape)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Error: ${run.failureReason}",
                        fontSize = 12.sp,
                        color = SaysoOnErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
