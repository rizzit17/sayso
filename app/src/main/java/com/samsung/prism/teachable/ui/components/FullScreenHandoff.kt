package com.samsung.prism.teachable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.CardShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.CredentialHandoffBg
import com.samsung.prism.teachable.ui.theme.CredentialHandoffBorder
import com.samsung.prism.teachable.ui.theme.CredentialHandoffContainer
import com.samsung.prism.teachable.ui.theme.CredentialHandoffText
import com.samsung.prism.teachable.ui.theme.PillShape

@Composable
fun FullScreenHandoffCard(
    reason: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, CredentialHandoffBorder, CardShape),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CredentialHandoffBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Unmistakable Safety Shield Badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(CredentialHandoffContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Guardrail",
                    tint = CredentialHandoffText,
                    modifier = Modifier.size(36.dp)
                )
            }

            Row(
                modifier = Modifier
                    .background(CredentialHandoffContainer, ChipShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = CredentialHandoffText,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CREDENTIAL SAFETY GUARANTEE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CredentialHandoffText,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "Your turn — I've reached the payment screen.",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CredentialHandoffText,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Text(
                text = reason ?: "Sayso strictly halts execution before payment confirmation, UPI PIN, or card details. Zero automated clicks were dispatched.",
                fontSize = 13.sp,
                color = CredentialHandoffText.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            // Manual Takeover Confirmation Button
            Button(
                onClick = onDismiss,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CredentialHandoffText,
                    contentColor = Color.White
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
                    text = "I've Completed Payment / Dismiss",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
