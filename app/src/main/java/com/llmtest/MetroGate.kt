package com.llmtest

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MetroGate(
    label: String,
    state: GateVisualState,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = when (state) {
        GateVisualState.INACTIVE -> Color(0xFF9CA3AF)
        GateVisualState.ACTIVE -> Color(0xFF2196F3)
        GateVisualState.REVIEWING -> Color(0xFFFFAB00)
        GateVisualState.REJECTED -> Color(0xFFD50000)
        GateVisualState.APPROVED -> Color(0xFF00BFA5)
        GateVisualState.OVERRIDDEN -> Color(0xFFFF6D00)
    }

    val bgColor = when (state) {
        GateVisualState.INACTIVE -> Color.Transparent
        GateVisualState.ACTIVE -> Color(0xFFE3F2FD)
        GateVisualState.REVIEWING -> Color(0xFFFFF8E1)
        GateVisualState.REJECTED -> Color(0xFFFFEBEE)
        GateVisualState.APPROVED -> Color(0xFFE0F2F1)
        GateVisualState.OVERRIDDEN -> Color(0xFFFFF3E0)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gatePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (state == GateVisualState.REJECTED) 1f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val alphaModifier = if (state == GateVisualState.REJECTED) {
        Modifier.graphicsLayer { alpha = pulseAlpha }
    } else Modifier

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .rotate(45f)
                .background(bgColor)
                .border(
                    width = if (state == GateVisualState.INACTIVE) 2.dp else 3.dp,
                    color = borderColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .then(alphaModifier)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (state != GateVisualState.INACTIVE) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(-45f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = borderColor,
            letterSpacing = 1.sp
        )
    }
}
