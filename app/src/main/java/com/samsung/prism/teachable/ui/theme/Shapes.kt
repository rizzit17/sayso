package com.samsung.prism.teachable.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SaysoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Pill shape alias
val PillShape = CircleShape
val CardShape = RoundedCornerShape(24.dp)
val SubCardShape = RoundedCornerShape(16.dp)
val SaysoSubCardShape = SubCardShape
val SaysoCardShape = CardShape
val ChipShape = CircleShape
val BadgeShape = RoundedCornerShape(8.dp)

