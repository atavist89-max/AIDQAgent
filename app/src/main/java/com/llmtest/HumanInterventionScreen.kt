package com.llmtest

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HumanInterventionScreen(
    blockState: GaaSBlockState,
    onAcceptStation: () -> Unit,
    onAcceptGaaS: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Human Intervention Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD50000)
            )
            Text(
                text = "The GaaS agent has rejected this output 3 times. Please review and arbitrate.",
                fontSize = 13.sp,
                color = Color(0xFF6B7280)
            )

            // Station Output Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF1A237E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Station Agent Output (Final)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1A237E)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFE8EAF6),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = parseMarkdownToAnnotatedString(blockState.outputSnapshot.take(2000)),
                            fontSize = 12.sp,
                            color = Color(0xFF1A1A2E),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // GaaS Feedback Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFFD50000),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GaaS Rejection Feedback (Final)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD50000)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            blockState.feedback.forEach { bullet ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "\u2022",
                                        fontSize = 12.sp,
                                        color = Color(0xFFD50000),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Text(
                                        text = parseMarkdownToAnnotatedString(bullet),
                                        fontSize = 12.sp,
                                        color = Color(0xFF1A1A2E),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Attempt History
            Text(
                text = "Attempt History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A2E)
            )

            val allAttempts = blockState.history + AttemptRecord(
                attemptNumber = blockState.retryCount,
                outputSnapshot = blockState.outputSnapshot,
                feedback = blockState.feedback
            )

            allAttempts.forEach { attempt ->
                AttemptCard(attempt = attempt)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Button(
                onClick = onAcceptStation,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00))
            ) {
                Text("Accept Station Version", fontSize = 14.sp)
            }

            OutlinedButton(
                onClick = onAcceptGaaS,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD50000))
            ) {
                Text("Accept GaaS Assessment (Abandon)", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttemptCard(attempt: AttemptRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Attempt ${attempt.attemptNumber}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A237E)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Output:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6B7280)
            )
            Text(
                text = parseMarkdownToAnnotatedString(attempt.outputSnapshot.take(500)),
                fontSize = 11.sp,
                color = Color(0xFF1A1A2E),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Feedback:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF6B7280)
            )
            attempt.feedback.forEach { bullet ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "\u2022",
                        fontSize = 11.sp,
                        color = Color(0xFFD50000),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = parseMarkdownToAnnotatedString(bullet),
                        fontSize = 11.sp,
                        color = Color(0xFF1A1A2E),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
