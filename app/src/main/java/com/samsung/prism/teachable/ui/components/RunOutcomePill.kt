package com.samsung.prism.teachable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.storage.RunStatus
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.CredentialHandoffContainer
import com.samsung.prism.teachable.ui.theme.CredentialHandoffText
import com.samsung.prism.teachable.ui.theme.SaysoErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnErrorContainer
import com.samsung.prism.teachable.ui.theme.SaysoOnTertiaryContainer
import com.samsung.prism.teachable.ui.theme.SaysoSuccess
import com.samsung.prism.teachable.ui.theme.SaysoSuccessContainer
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryContainer

@Composable
fun RunOutcomePill(
    status: RunStatus,
    modifier: Modifier = Modifier
) {
    val config = when (status) {
        RunStatus.COMPLETED -> OutcomeConfig("Completed", SaysoSuccessContainer, SaysoSuccess, Icons.Default.Check)
        RunStatus.COMPLETED_TO_BOUNDARY -> OutcomeConfig("Payment Handoff", CredentialHandoffContainer, CredentialHandoffText, Icons.Default.Security)
        RunStatus.ASKED_USER -> OutcomeConfig("Asked User", SaysoTertiaryContainer, SaysoOnTertiaryContainer, Icons.Default.HelpOutline)
        RunStatus.FAILED -> OutcomeConfig("Failed", SaysoErrorContainer, SaysoOnErrorContainer, Icons.Default.Close)
        RunStatus.CANCELLED -> OutcomeConfig("Cancelled", SaysoErrorContainer, SaysoOnErrorContainer, Icons.Default.Close)
    }

    Row(
        modifier = modifier
            .background(config.bg, ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = config.icon,
            contentDescription = null,
            tint = config.text,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = config.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = config.text
        )
    }
}

private data class OutcomeConfig(
    val label: String,
    val bg: androidx.compose.ui.graphics.Color,
    val text: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

