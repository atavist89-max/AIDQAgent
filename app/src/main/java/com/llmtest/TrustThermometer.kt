package com.llmtest

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrustThermometer(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateIntAsState(
        targetValue = score.coerceIn(0, 100),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "score"
    )

    val level = AutonomyLevel.fromScore(animatedScore)
    val indicatorColor = when (level) {
        AutonomyLevel.TRAINING -> Color(0xFFD50000)
        AutonomyLevel.SUPERVISED -> Color(0xFFFF6D00)
        AutonomyLevel.AUTONOMOUS -> Color(0xFF00BFA5)
        AutonomyLevel.EXPERT -> Color(0xFF7C4DFF)
    }

    BoxWithConstraints(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val maxWidth = constraints.maxWidth.toFloat()
        val indicatorOffset = (animatedScore / 100f) * (maxWidth - 16.dp.value).coerceAtLeast(0f)

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("TRAINING", "SUPERVISED", "AUTONOMOUS", "EXPERT").forEach { label ->
                    val isActive = level.label.uppercase() == label
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) indicatorColor else Color(0xFF6B7280),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE5E7EB))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(16.dp)
                        .offset(x = indicatorOffset.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "$animatedScore pts",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = indicatorColor
            )
        }
    }
}
