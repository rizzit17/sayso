package com.samsung.prism.teachable.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.teaching.ActionType
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.CredentialHandoffContainer
import com.samsung.prism.teachable.ui.theme.CredentialHandoffText
import com.samsung.prism.teachable.ui.theme.FilteredActionBg
import com.samsung.prism.teachable.ui.theme.FilteredActionText
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOutline
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoSubCardShape
import com.samsung.prism.teachable.ui.theme.SubCardShape
import com.samsung.prism.teachable.ui.theme.SaysoSuccess
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainer
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryFixed

data class StepDisplayItem(
    val stepNumber: Int,
    val title: String,
    val subtitle: String? = null,
    val paramTag: String? = null,
    val status: StepDisplayStatus = StepDisplayStatus.PENDING,
    val isFiltered: Boolean = false,
    val isBoundary: Boolean = false
)

enum class StepDisplayStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    FAILED
}

@Composable
fun StepTrackerList(
    items: List<StepDisplayItem>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (item in items) {
            StepTrackerCard(item = item)
        }
    }
}

@Composable
fun StepTrackerCard(
    item: StepDisplayItem,
    modifier: Modifier = Modifier
) {
    if (item.isBoundary) {
        // Guaranteed Safety Handoff Marker Card per learning_review/code.html
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(CredentialHandoffContainer, SubCardShape)
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(CredentialHandoffText, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Safety Handoff Point",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CredentialHandoffText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Guaranteed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CredentialHandoffText,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.6f), BadgeShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Halts automatically before payment/credential screen. Complete manually.",
                    fontSize = 12.sp,
                    color = CredentialHandoffText.copy(alpha = 0.9f),
                    lineHeight = 16.sp
                )
            }
        }
        return
    }

    if (item.isFiltered) {
        // Bonus B1: Irrelevant Action Filtered Marker
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(FilteredActionBg, SubCardShape)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.LightGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = FilteredActionText
                )
                Text(
                    text = "${item.subtitle ?: "Accidental touch"} — ignored",
                    fontSize = 11.sp,
                    color = FilteredActionText
                )
            }
            Text(
                text = "Filtered",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = FilteredActionText,
                modifier = Modifier
                    .background(Color.White, BadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        return
    }

    // Standard Step Card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (item.status == StepDisplayStatus.ACTIVE) SaysoSecondaryFixed.copy(alpha = 0.5f) else SaysoSurfaceContainerLow,
                SubCardShape
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Indicator
        when (item.status) {
            StepDisplayStatus.COMPLETED -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SaysoSecondaryFixed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SaysoSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            StepDisplayStatus.ACTIVE -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SaysoSecondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SaysoPrimaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.stepNumber.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${item.stepNumber}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.status == StepDisplayStatus.ACTIVE) SaysoSecondary else SaysoOutline
                )
                if (item.status == StepDisplayStatus.ACTIVE) {
                    Text(
                        text = "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoSecondary,
                        modifier = Modifier
                            .background(SaysoSecondaryContainer, BadgeShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SaysoOnSurface
            )

            if (!item.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    fontSize = 12.sp,
                    color = SaysoOnSurfaceVariant
                )
            }

            if (!item.paramTag.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .background(SaysoSecondaryContainer, ChipShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = SaysoOnSecondaryFixed,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.paramTag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SaysoOnSecondaryFixed
                    )
                }
            }
        }
    }
}
