package com.samsung.prism.teachable.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.replay.ReplayState
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.CredentialHandoffContainer
import com.samsung.prism.teachable.ui.theme.CredentialHandoffText
import com.samsung.prism.teachable.ui.theme.SaysoErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOnTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSuccess
import com.samsung.prism.teachable.ui.theme.SaysoSuccessContainer
import com.samsung.prism.teachable.ui.theme.SaysoTertiary
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryContainer

@Composable
fun StateHeaderBar(
    state: ReplayState,
    isA11yConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // State mapping per design.md §5
    val config = when (state) {
        ReplayState.IDLE -> StateConfig("READY", SaysoSecondaryContainer, SaysoOnSecondaryContainer, Icons.Default.RecordVoiceOver)
        ReplayState.RETRIEVING, ReplayState.EXTRACTING_SLOTS, ReplayState.BINDING, ReplayState.EXECUTING_STEP, ReplayState.VERIFYING_STATE ->
            StateConfig("RUNNING", SaysoPrimaryContainer, SaysoOnPrimaryContainer, Icons.Default.PlayArrow)
        ReplayState.RECOVERING -> StateConfig("RECOVERING", SaysoTertiaryContainer, SaysoOnTertiaryContainer, Icons.Default.Warning)
        ReplayState.ASKING_USER -> StateConfig("WAITING FOR USER", SaysoTertiaryContainer, SaysoOnTertiaryContainer, Icons.Default.Info)
        ReplayState.STOPPED_AT_BOUNDARY -> StateConfig("PAYMENT BOUNDARY", CredentialHandoffContainer, CredentialHandoffText, Icons.Default.Security)
        ReplayState.COMPLETED -> StateConfig("COMPLETED", SaysoSuccessContainer, SaysoSuccess, Icons.Default.CheckCircle)
        ReplayState.FAILED -> StateConfig("FAILED", SaysoErrorContainer, SaysoOnErrorContainer, Icons.Default.Warning)
    }
    val label = config.label
    val containerColor = config.containerColor
    val contentColor = config.contentColor
    val icon = config.icon

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // State Pill
        Row(
            modifier = Modifier
                .background(containerColor, ChipShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(if (state != ReplayState.IDLE && state != ReplayState.COMPLETED) pulseScale else 1.0f)
                    .background(contentColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                letterSpacing = 0.5.sp
            )
        }

        // Accessibility Service Badge
        Row(
            modifier = Modifier
                .background(
                    if (isA11yConnected) SaysoSuccessContainer else SaysoTertiaryContainer,
                    ChipShape
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isA11yConnected) SaysoSuccess else SaysoTertiary,
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isA11yConnected) "A11y Active" else "A11y Off",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isA11yConnected) SaysoSuccess else SaysoOnTertiaryContainer
            )
        }
    }
}

private data class StateConfig(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
