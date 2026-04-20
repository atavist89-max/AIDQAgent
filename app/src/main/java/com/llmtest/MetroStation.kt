package com.llmtest

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class StationState {
    PENDING, ACTIVE, COMPLETE, BLOCKED
}

@Composable
fun MetroStation(
    label: String,
    agentName: String,
    trustScore: Int,
    state: StationState,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when (state) {
        StationState.PENDING -> Color(0xFF9CA3AF)
        StationState.ACTIVE -> Color(0xFF1A237E)
        StationState.COMPLETE -> Color(0xFF00BFA5)
        StationState.BLOCKED -> Color(0xFFD50000)
    }

    val fillColor = when (state) {
        StationState.PENDING -> Color.Transparent
        StationState.ACTIVE -> Color(0xFF1A237E).copy(alpha = 0.1f)
        StationState.COMPLETE -> Color(0xFF1A237E)
        StationState.BLOCKED -> Color(0xFFD50000).copy(alpha = 0.1f)
    }

    val iconColor = when (state) {
        StationState.PENDING -> Color(0xFF1A237E)
        StationState.ACTIVE -> Color(0xFF1A237E)
        StationState.COMPLETE -> Color.White
        StationState.BLOCKED -> Color(0xFFD50000)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == StationState.ACTIVE) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Station circle
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(
                    width = 3.dp,
                    color = borderColor,
                    shape = CircleShape
                )
                .then(if (state == StationState.ACTIVE) Modifier.graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                } else Modifier)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Label
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A2E),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Agent name
        Text(
            text = agentName,
            fontSize = 9.sp,
            color = Color(0xFF6B7280),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Trust score pill
        val trustColor = when {
            trustScore < 30 -> Color(0xFFD50000)
            trustScore < 70 -> Color(0xFFFF6D00)
            trustScore < 90 -> Color(0xFF00BFA5)
            else -> Color(0xFF7C4DFF)
        }

        Surface(
            color = trustColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "$trustScore",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = trustColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
