package com.llmtest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agentId: String,
    onBack: () -> Unit
) {
    val agent by remember(agentId) { mutableStateOf(TrustScoreManager.getScore(agentId)) }
    val scope = rememberCoroutineScope()
    var showAdjustDialog by remember { mutableStateOf(false) }
    var newScore by remember { mutableStateOf(agent?.score?.toString() ?: "50") }

    if (agent == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Agent not found")
        }
        return
    }

    val a = agent!!
    val autonomy = AutonomyLevel.fromScore(a.score)
    val autonomyColor = when (autonomy) {
        AutonomyLevel.TRAINING -> Color(0xFF607D8B)
        AutonomyLevel.SUPERVISED -> Color(0xFF1976D2)
        AutonomyLevel.AUTONOMOUS -> Color(0xFF388E3C)
        AutonomyLevel.EXPERT -> Color(0xFF7B1FA2)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a.agentName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdjustDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Adjust")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = autonomyColor.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(a.agentName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(a.agentRole, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { a.score / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = autonomyColor,
                            trackColor = autonomyColor.copy(alpha = 0.15f),
                            strokeWidth = 8.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${a.score}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = autonomyColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            autonomy.label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = autonomyColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailStatCard(modifier = Modifier.weight(1f), label = "Total", value = "${a.decisionsTotal}")
                DetailStatCard(modifier = Modifier.weight(1f), label = "Correct", value = "${a.decisionsCorrect}")
                DetailStatCard(modifier = Modifier.weight(1f), label = "Incorrect", value = "${a.decisionsIncorrect}")
                DetailStatCard(modifier = Modifier.weight(1f), label = "Accuracy", value = "${(a.accuracyRate * 100).toInt()}%")
            }

            // Score history
            if (a.scoreHistory.isNotEmpty()) {
                Text("Score History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                a.scoreHistory.takeLast(10).forEach { snapshot ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(snapshot.reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "${snapshot.score}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                snapshot.score >= 70 -> Color(0xFF388E3C)
                                snapshot.score >= 30 -> Color(0xFFF57C00)
                                else -> Color(0xFFE53935)
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            if (a.manualOverrideReason != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(Modifier.width(8.dp))
                        Text("Last manual override: ${a.manualOverrideReason}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showAdjustDialog) {
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Adjust Trust Score") },
            text = {
                Column {
                    Text("Current: ${a.score}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newScore,
                        onValueChange = { newScore = it.filter { c -> c.isDigit() } },
                        label = { Text("New Score (0-100)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val score = newScore.toIntOrNull()?.coerceIn(0, 100) ?: return@TextButton
                    scope.launch {
                        TrustScoreManager.manualOverride(agentId, score, "Manual adjustment from detail screen")
                    }
                    showAdjustDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailStatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
