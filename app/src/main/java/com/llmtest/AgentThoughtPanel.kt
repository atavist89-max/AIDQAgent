package com.llmtest

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentThoughtPanel(
    thought: AgentThought?,
    negotiation: AgentNegotiation?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFE5E7EB))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (negotiation != null) "Agent Negotiation" else "Agent Reasoning",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E)
                )

                val statusColor = when {
                    negotiation != null -> Color(0xFF7C4DFF)
                    thought?.isTyping == true -> Color(0xFFFFAB00)
                    thought != null -> Color(0xFF00BFA5)
                    else -> Color(0xFF6B7280)
                }
                val statusText = when {
                    negotiation != null -> "MEDIATING"
                    thought?.isTyping == true -> "THINKING..."
                    thought != null -> "COMPLETE"
                    else -> "WAITING"
                }

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content
            AnimatedContent(
                targetState = negotiation != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "panel"
            ) { showNegotiation ->
                if (showNegotiation && negotiation != null) {
                    NegotiationView(negotiation = negotiation)
                } else if (thought != null) {
                    SingleAgentView(thought = thought, isExpanded = isExpanded)
                } else {
                    EmptyThoughtView()
                }
            }
        }
    }
}

@Composable
private fun SingleAgentView(thought: AgentThought, isExpanded: Boolean) {
    val level = AutonomyLevel.fromScore(thought.trustScore)
    val levelColor = when (level) {
        AutonomyLevel.TRAINING -> Color(0xFFD50000)
        AutonomyLevel.SUPERVISED -> Color(0xFFFF6D00)
        AutonomyLevel.AUTONOMOUS -> Color(0xFF00BFA5)
        AutonomyLevel.EXPERT -> Color(0xFF7C4DFF)
    }

    Column {
        // Agent header card
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = Color(0xFF1A237E)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF1A237E),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = thought.agentName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A1A2E)
                        )
                        Text(
                            text = "Trust: ${thought.trustScore} pts • ${level.label.uppercase()}",
                            fontSize = 11.sp,
                            color = levelColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Speech bubble
                Surface(
                    color = Color(0xFFE8EAF6),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (thought.isTyping) {
                        TypingText(
                            text = thought.reasoning,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        Text(
                            text = thought.reasoning,
                            fontSize = 13.sp,
                            color = Color(0xFF1A1A2E),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Evidence chips
                    if (thought.evidence.isNotEmpty()) {
                        Text(
                            text = "Evidence referenced:",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            thought.evidence.take(4).forEach { ev ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Color(0xFF1A237E).copy(alpha = 0.2f)
                                    )
                                ) {
                                    Text(
                                        text = ev,
                                        fontSize = 10.sp,
                                        color = Color(0xFF1A237E),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Confidence bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Confidence:",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { thought.confidence },
                            modifier = Modifier.weight(1f).height(4.dp),
                            color = Color(0xFF00BFA5),
                            trackColor = Color(0xFF00BFA5).copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(thought.confidence * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A1A2E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NegotiationView(negotiation: AgentNegotiation) {
    Column {
        // VS Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            // Stage 4a card
            NegotiationAgentCard(
                agentName = negotiation.stage4aPosition.agentName,
                recommendation = negotiation.stage4aPosition.recommendation,
                priority = negotiation.stage4aPosition.priority,
                confidence = negotiation.stage4aPosition.confidence,
                trustScore = TrustScoreManager.getScore("stage4a")?.score ?: 50,
                borderColor = Color(0xFF1A237E)
            )

            // VS badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFAB00)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Stage 4b card
            NegotiationAgentCard(
                agentName = negotiation.stage4bPosition.agentName,
                recommendation = negotiation.stage4bPosition.recommendation,
                priority = negotiation.stage4bPosition.priority,
                confidence = negotiation.stage4bPosition.confidence,
                trustScore = TrustScoreManager.getScore("stage4b")?.score ?: 50,
                borderColor = Color(0xFF00BFA5)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mediator card
        AnimatedVisibility(visible = negotiation.mediatorProposal != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF3E5F5),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(4.dp, Color(0xFF7C4DFF))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = Color(0xFF7C4DFF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GaaS Mediator",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF7C4DFF)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = negotiation.mediatorProposal ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFF1A1A2E),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    AgentNegotiator.resolveNegotiation(negotiation.negotiationId, acceptMediation = true)
                    GaaSController.resumePipeline()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Accept", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { /* debate */ },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFAB00)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Debate", fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { /* override */ },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A237E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Override", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NegotiationAgentCard(
    agentName: String,
    recommendation: String,
    priority: String,
    confidence: Float,
    trustScore: Int,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    val borderWidth = (confidence * 5).coerceIn(1f, 4f).dp

    Surface(
        modifier = modifier.width(140.dp),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = agentName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = borderColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = recommendation,
                fontSize = 11.sp,
                color = Color(0xFF1A1A2E),
                maxLines = 3,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Priority: $priority",
                fontSize = 10.sp,
                color = Color(0xFF6B7280)
            )
            Text(
                text = "Confidence: ${(confidence * 100).toInt()}%",
                fontSize = 10.sp,
                color = Color(0xFF6B7280)
            )
            Text(
                text = "Trust: $trustScore",
                fontSize = 10.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
private fun EmptyThoughtView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Waiting for pipeline to start...",
            fontSize = 13.sp,
            color = Color(0xFF6B7280)
        )
    }
}

@Composable
private fun TypingText(text: String, modifier: Modifier = Modifier) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { index, _ ->
            displayedText = text.take(index + 1)
            kotlinx.coroutines.delay(20)
        }
    }

    Text(
        text = displayedText,
        fontSize = 13.sp,
        color = Color(0xFF1A1A2E),
        lineHeight = 18.sp,
        modifier = modifier
    )
}
