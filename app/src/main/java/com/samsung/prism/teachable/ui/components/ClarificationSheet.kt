package com.samsung.prism.teachable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.stuck.ClarificationActionType
import com.samsung.prism.teachable.stuck.ClarificationOption
import com.samsung.prism.teachable.stuck.ClarificationQuestion
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.PillShape
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOnTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSubCardShape
import com.samsung.prism.teachable.ui.theme.SubCardShape
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHigh
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLow
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerLowest
import com.samsung.prism.teachable.ui.theme.SaysoTertiary
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryContainer

@Composable
fun ClarificationCard(
    question: ClarificationQuestion,
    onOptionSelected: (ClarificationOption) -> Unit,
    onVoiceResponseClick: () -> Unit,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = SaysoSurfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .background(SaysoTertiaryContainer, ChipShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = SaysoOnTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clarification Needed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaysoOnTertiaryContainer
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = SaysoOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Question prompt
            Text(
                text = question.questionText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = SaysoOnSurface,
                lineHeight = 24.sp
            )

            Text(
                text = "Tap an option below or speak your choice:",
                fontSize = 12.sp,
                color = SaysoOnSurfaceVariant
            )

            // Selectable Option Cards / Pills
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in question.options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SubCardShape)
                            .background(SaysoSurfaceContainerLowest)
                            .clickable { onOptionSelected(option) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when (option.actionType) {
                            ClarificationActionType.TAP_ALTERNATIVE -> Icons.Default.TouchApp
                            ClarificationActionType.SKIP_STEP -> Icons.Default.SkipNext
                            ClarificationActionType.ABORT -> Icons.Default.Close
                        }
                        val tint = when (option.actionType) {
                            ClarificationActionType.TAP_ALTERNATIVE -> SaysoPrimary
                            ClarificationActionType.SKIP_STEP -> SaysoTertiary
                            ClarificationActionType.ABORT -> SaysoOnSurfaceVariant
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SaysoOnSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Voice speak response button
            Button(
                onClick = onVoiceResponseClick,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SaysoSecondaryContainer,
                    contentColor = SaysoOnSecondaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Speak Response (or tap an option)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
