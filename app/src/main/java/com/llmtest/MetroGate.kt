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

enum class GateState {
    EVALUATING, APPROVED, BLOCKED, ESCALATED
}

@Composable
fun MetroGate(
    label: String,
    state: GateState,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = when (state) {
        GateState.EVALUATING -> Color(0xFFFFAB00)
        GateState.APPROVED -> Color(0xFF00BFA5)
        GateState.BLOCKED -> Color(0xFFD50000)
        GateState.ESCALATED -> Color(0xFF7C4DFF)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gateDash")
    val dashOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dash"
    )

    val dashModifier = if (state == GateState.EVALUATING) {
        Modifier.graphicsLayer {
            // For evaluating state we simulate dash animation via alpha pulse
            alpha = (0.7f + 0.3f * kotlin.math.sin(dashOffset * kotlin.math.PI / 4f).toFloat())
        }
    } else Modifier

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .rotate(45f)
                .background(
                    color = when (state) {
                        GateState.EVALUATING -> Color(0xFFFFF8E1)
                        GateState.APPROVED -> Color(0xFFE0F2F1)
                        GateState.BLOCKED -> Color(0xFFFFEBEE)
                        GateState.ESCALATED -> Color(0xFFF3E5F5)
                    }
                )
                .border(
                    width = 3.dp,
                    color = borderColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                )
                .then(dashModifier)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(-45f)
            )
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
