package com.samsung.prism.teachable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.ui.theme.BadgeShape
import com.samsung.prism.teachable.ui.theme.ChipShape
import com.samsung.prism.teachable.ui.theme.SaysoOnPrimary
import com.samsung.prism.teachable.ui.theme.SaysoOnSecondary
import com.samsung.prism.teachable.ui.theme.SaysoOnSurface
import com.samsung.prism.teachable.ui.theme.SaysoOnSurfaceVariant
import com.samsung.prism.teachable.ui.theme.SaysoOutline
import com.samsung.prism.teachable.ui.theme.SaysoPrimary
import com.samsung.prism.teachable.ui.theme.SaysoPrimaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoSecondary
import com.samsung.prism.teachable.ui.theme.SaysoSecondaryFixed
import com.samsung.prism.teachable.ui.theme.SaysoSurfaceContainerHigh
import com.samsung.prism.teachable.ui.theme.SaysoTertiaryFixed

@Composable
fun SlotChipRow(
    slots: List<SlotDefinition>,
    boundValues: Map<String, Any> = emptyMap(),
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((index, slot) in slots.withIndex()) {
            val value = boundValues[slot.name]?.toString() ?: slot.defaultValue ?: slot.name
            val (containerColor, tagBg, tagText) = when (index % 3) {
                0 -> Triple(SaysoSecondaryFixed, SaysoSecondary, SaysoOnSecondary)
                1 -> Triple(SaysoPrimaryFixed, SaysoPrimary, SaysoOnPrimary)
                else -> Triple(SaysoTertiaryFixed, SaysoPrimary, SaysoOnPrimary)
            }

            Row(
                modifier = Modifier
                    .background(containerColor, ChipShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slot Tag Label
                Text(
                    text = slot.name.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaysoOnSurfaceVariant,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.6f), BadgeShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Bound / Default Value
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaysoOnSurface
                )
                if (slot.type == "enum") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Enum Slot",
                        tint = SaysoOutline,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
