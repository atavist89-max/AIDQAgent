package com.llmtest

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentThoughtPanel(
    thought: AgentThought?,
    stage: Int,
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
                    .pointerInput(isExpanded) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount > 30f && isExpanded) {
                                onToggleExpand()
                            }
                        }
                    }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Agent Reasoning",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E)
                )

                val statusColor = when {
                    thought?.isTyping == true -> Color(0xFFFFAB00)
                    thought != null -> Color(0xFF00BFA5)
                    else -> Color(0xFF6B7280)
                }
                val statusText = when {
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
            if (thought != null) {
                SingleAgentView(thought = thought, isExpanded = isExpanded)
            } else {
                EmptyThoughtView(stage = stage)
            }
        }
    }
}

@Composable
private fun SingleAgentView(thought: AgentThought, isExpanded: Boolean) {
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
                            text = thought.agentRole,
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Speech bubble with formatted text
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
                            text = parseMarkdownToAnnotatedString(thought.reasoning),
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
private fun EmptyThoughtView(stage: Int) {
    val message = when {
        stage >= 50 -> "Analysis complete. Tap a station to review findings."
        stage > 0 -> "Processing..."
        else -> "Waiting for pipeline to start..."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
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
        text = parseMarkdownToAnnotatedString(displayedText),
        fontSize = 13.sp,
        color = Color(0xFF1A1A2E),
        lineHeight = 18.sp,
        modifier = modifier
    )
}

/**
 * Parse simple Markdown-style emphasis (**bold**, *italic*) into an AnnotatedString.
 * Also converts lines starting with "- " or "* " into bullet points.
 */
fun parseMarkdownToAnnotatedString(input: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = input.lines()
        lines.forEachIndexed { lineIndex, rawLine ->
            val line = rawLine.trim()
            if (lineIndex > 0) append("\n")

            // Bullet detection
            val isBullet = line.startsWith("- ") || line.startsWith("* ")
            val content = if (isBullet) line.removePrefix("- ").removePrefix("* ") else line

            if (isBullet) {
                append("\u2022 ")
            }

            // Parse inline formatting
            parseInlineMarkdown(content)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.parseInlineMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // Bold: **text**
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Italic: *text* (but not ** which was handled above)
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
