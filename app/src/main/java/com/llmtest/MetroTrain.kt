package com.llmtest

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MetroTrain(
    alertDataset: String,
    severity: String,
    dimension: String,
    elapsedSeconds: Int,
    progressPercent: Int,
    isBlocked: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = if (isActive) 0.25f else 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val shakeOffset by animateFloatAsState(
        targetValue = if (isBlocked) 0f else 0f,
        animationSpec = repeatable(
            iterations = if (isBlocked) 6 else 1,
            animation = tween(50),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    val severityColor = when (severity.uppercase()) {
        "CRITICAL" -> Color(0xFFD50000)
        "ERROR" -> Color(0xFFFF6D00)
        "WARNING" -> Color(0xFFFFAB00)
        else -> Color(0xFF6B7280)
    }

    Box(modifier = modifier.offset(x = if (isBlocked) (kotlin.math.sin(shakeOffset * 10f) * 4).dp else 0.dp)) {
        // Glow effect
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A237E).copy(alpha = glowAlpha))
            )
        }

        Surface(
            modifier = Modifier
                .width(160.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .border(
                        width = 2.dp,
                        color = if (isBlocked) Color(0xFFD50000) else Color(0xFF1A237E),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp)
            ) {
                // Alert ID / Dataset
                Text(
                    text = alertDataset,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Severity + Dimension pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = severityColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = severity,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFF00BFA5).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFF00BFA5).copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = dimension,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF00BFA5),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom row: time + progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${elapsedSeconds / 60}:${String.format("%02d", elapsedSeconds % 60)}",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "$progressPercent%",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            }
        }
    }
}
