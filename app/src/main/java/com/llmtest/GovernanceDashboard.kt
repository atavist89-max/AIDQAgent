package com.llmtest

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernanceDashboard(
    onAgentDetail: (String) -> Unit = {},
    onPolicyCenter: () -> Unit = {},
    onDecisionQueue: () -> Unit = {},
    onAuditPlayback: () -> Unit = {}
) {
    val agents by remember { mutableStateOf(TrustScoreManager.getAllScores()) }
    val pendingCount by EscalationRouter.pendingApprovals.collectAsState()
    val decisions by GaaSController.governanceDecisions.collectAsState()
    val violations = remember(decisions) { decisions.flatMap { it.policyViolations } }

    val scope = rememberCoroutineScope()
    var showToast by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Governance Cockpit", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    BadgedBox(
                        badge = {
                            if (pendingCount.isNotEmpty()) {
                                Badge { Text("${pendingCount.size}") }
                            }
                        }
                    ) {
                        IconButton(onClick = onDecisionQueue) {
                            Icon(Icons.Default.Notifications, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPolicyCenter,
                icon = { Icon(Icons.Default.Policy, contentDescription = null) },
                text = { Text("Policies") }
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
            // Stats Overview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Agents",
                    value = "${agents.size}",
                    icon = Icons.Default.Groups,
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Pending",
                    value = "${pendingCount.size}",
                    icon = Icons.Default.PendingActions,
                    color = if (pendingCount.isNotEmpty()) Color(0xFFE53935) else MaterialTheme.colorScheme.secondary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Violations",
                    value = "${violations.size}",
                    icon = Icons.Default.ReportProblem,
                    color = if (violations.isNotEmpty()) Color(0xFFFFA000) else Color(0xFF43A047)
                )
            }

            Text("Agent Trust Scores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            agents.forEach { agent ->
                AgentTrustCard(
                    agent = agent,
                    onClick = { onAgentDetail(agent.agentId) },
                    onQuickAdjust = { newScore ->
                        scope.launch {
                            TrustScoreManager.manualOverride(agent.agentId, newScore, "Quick adjust from dashboard")
                            showToast = "${agent.agentName} score updated to $newScore"
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Actions
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onAuditPlayback,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Audit")
                }
                OutlinedButton(
                    onClick = onDecisionQueue,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Rule, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Queue")
                }
            }
        }

        showToast?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(2000)
                showToast = null
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentTrustCard(
    agent: TrustScore,
    onClick: () -> Unit,
    onQuickAdjust: (Int) -> Unit
) {
    val autonomy = AutonomyLevel.fromScore(agent.score)
    val badgeColor = when (autonomy) {
        AutonomyLevel.TRAINING -> Color(0xFF607D8B)
        AutonomyLevel.SUPERVISED -> Color(0xFF1976D2)
        AutonomyLevel.AUTONOMOUS -> Color(0xFF388E3C)
        AutonomyLevel.EXPERT -> Color(0xFF7B1FA2)
    }
    val progressColor = when {
        agent.score >= 90 -> Color(0xFF7B1FA2)
        agent.score >= 70 -> Color(0xFF388E3C)
        agent.score >= 30 -> Color(0xFFF57C00)
        else -> Color(0xFFE53935)
    }

    val trend = remember(agent.scoreHistory) {
        if (agent.scoreHistory.size >= 2) {
            val prev = agent.scoreHistory[agent.scoreHistory.size - 2].score
            when {
                agent.score > prev -> "↗"
                agent.score < prev -> "↘"
                else -> "→"
            }
        } else "→"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(agent.agentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(agent.agentRole, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        autonomy.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { agent.score / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = progressColor,
                        trackColor = progressColor.copy(alpha = 0.15f),
                        strokeWidth = 5.dp
                    )
                    Text("${agent.score}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { agent.accuracyRate },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF1976D2),
                        trackColor = Color(0xFF1976D2).copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Accuracy: ${(agent.accuracyRate * 100).toInt()}% • Decisions: ${agent.decisionsTotal} $trend",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
