package com.llmtest

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PolicyEvaluationPanel(
    results: List<PolicyResultDisplay>,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "Policy Evaluations",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A2E),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            results.forEach { result ->
                PolicyCard(result = result)
            }
        }
    }
}

@Composable
private fun PolicyCard(result: PolicyResultDisplay) {
    val (borderColor, bgColor, icon, iconTint) = when (result.status) {
        PolicyDisplayStatus.EVALUATING ->
            Quad(Color(0xFFFFAB00), Color(0xFFFFF8E1), Icons.Default.Refresh, Color(0xFFFFAB00))
        PolicyDisplayStatus.PASSED ->
            Quad(Color(0xFF00BFA5), Color(0xFFE0F2F1), Icons.Default.CheckCircle, Color(0xFF00BFA5))
        PolicyDisplayStatus.BLOCKED ->
            Quad(Color(0xFFD50000), Color(0xFFFFEBEE), Icons.Default.Block, Color(0xFFD50000))
        PolicyDisplayStatus.REMEDIATED ->
            Quad(Color(0xFF1A237E), Color(0xFFE8EAF6), Icons.Default.Edit, Color(0xFF1A237E))
    }

    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (result.status == PolicyDisplayStatus.EVALUATING) 1f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (result.status == PolicyDisplayStatus.EVALUATING) 3.dp else 2.dp,
            color = if (result.status == PolicyDisplayStatus.EVALUATING)
                borderColor.copy(alpha = shimmerAlpha) else borderColor
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (result.status) {
                            PolicyDisplayStatus.EVALUATING -> "EVALUATING..."
                            PolicyDisplayStatus.PASSED -> "PASSED"
                            PolicyDisplayStatus.BLOCKED -> "ESCALATED"
                            PolicyDisplayStatus.REMEDIATED -> "REMEDIATED"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = borderColor
                    )
                }
                Text(
                    text = dateFormat.format(Date(result.timestamp)),
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result.policyName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A2E)
            )
            Text(
                text = result.category.name,
                fontSize = 11.sp,
                color = Color(0xFF6B7280)
            )

            if (result.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.details,
                    fontSize = 11.sp,
                    color = Color(0xFF1A1A2E),
                    lineHeight = 14.sp
                )
            }

            if (result.beforeValue != null && result.afterValue != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Text(
                        text = result.beforeValue,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        textDecoration = TextDecoration.LineThrough
                    )
                    Text(
                        text = " → ",
                        fontSize = 11.sp,
                        color = Color(0xFF1A237E)
                    )
                    Text(
                        text = result.afterValue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD50000)
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
