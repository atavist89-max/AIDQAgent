package com.llmtest

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GovernanceScreen() {
    val scrollState = rememberScrollState()
    val policies = remember { mutableStateOf(GovernanceConfig.loadPolicies()) }
    val stationPrompts = remember { mutableStateOf(GovernanceConfig.loadStationPrompts()) }
    val gateVisualStates by GaaSController.gateVisualStates.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Governance Configuration",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A2E)
        )

        // Section A: Fixed System Prompt Card
        SystemPromptCard()

        // Section B: Five Policy Cards
        Text(
            text = "Gate Policies",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1A2E)
        )

        val gateLabels = listOf(
            "Gate 1: Stage 1 → Stage 2",
            "Gate 2: Stage 2 → Stage 3",
            "Gate 3: Stage 3 → Stage 4A",
            "Gate 4: Stage 4A → Stage 4B",
            "Gate 5: Stage 4B → Stage 4C"
        )

        gateLabels.forEachIndexed { index, label ->
            val gateId = "gate${index + 1}"
            val policy = policies.value.find { it.gateId == gateId } ?: GaaSPolicy(gateId)
            val visualState = gateVisualStates[gateId] ?: GateVisualState.INACTIVE

            PolicyCard(
                label = label,
                gateId = gateId,
                policy = policy,
                visualState = visualState,
                onUpdate = { updatedPolicy ->
                    GovernanceConfig.updatePolicy(updatedPolicy)
                    policies.value = GovernanceConfig.loadPolicies()
                }
            )
        }

        // Section C: Six Station-Agent Editor Cards
        Text(
            text = "Station-Agent Prompts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1A2E)
        )

        val stationDefs = listOf(
            Triple("stage1", "Stage 1 Triage", Icons.Default.FilterList),
            Triple("stage2", "Stage 2 Context Builder", Icons.Default.Storage),
            Triple("stage3", "Stage 3 Pattern Detector", Icons.Default.Analytics),
            Triple("stage4a", "Stage 4A Upstream Researcher", Icons.Default.Build),
            Triple("stage4b", "Stage 4B Downstream Researcher", Icons.Default.Assessment),
            Triple("stage4c", "Stage 4C Synthesizer", Icons.Default.Article)
        )

        stationDefs.forEach { (stationId, name, icon) ->
            val promptObj = stationPrompts.value.find { it.stationId == stationId }
                ?: StationPrompt(stationId)

            StationPromptCard(
                stationId = stationId,
                name = name,
                icon = icon,
                prompt = promptObj.prompt,
                onSave = { newPrompt ->
                    GovernanceConfig.updateStationPrompt(StationPrompt(stationId, newPrompt))
                    stationPrompts.value = GovernanceConfig.loadStationPrompts()
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SystemPromptCard() {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Governance Agent System Prompt",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A237E)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Expand")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You are the Governance Agent. Review the following output against the policy. Respond only with APPROVED: [reasoning] or REJECTED: [feedback]. If REJECTED, provide structured bullet-point feedback.",
                            fontSize = 13.sp,
                            color = Color(0xFF1A1A2E),
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyCard(
    label: String,
    gateId: String,
    policy: GaaSPolicy,
    visualState: GateVisualState,
    onUpdate: (GaaSPolicy) -> Unit
) {
    var enabled by remember(policy.enabled) { mutableStateOf(policy.enabled) }
    var promptText by remember(policy.prompt) { mutableStateOf(policy.prompt) }

    val statusLabel = when (visualState) {
        GateVisualState.INACTIVE -> "Inactive"
        GateVisualState.ACTIVE -> "Active"
        GateVisualState.REVIEWING -> "Reviewing"
        GateVisualState.REJECTED -> "Blocked"
        GateVisualState.APPROVED -> "Approved"
        GateVisualState.OVERRIDDEN -> "Overridden"
    }

    val statusColor = when (visualState) {
        GateVisualState.INACTIVE -> Color(0xFF9CA3AF)
        GateVisualState.ACTIVE -> Color(0xFF2196F3)
        GateVisualState.REVIEWING -> Color(0xFFFFAB00)
        GateVisualState.REJECTED -> Color(0xFFD50000)
        GateVisualState.APPROVED -> Color(0xFF00BFA5)
        GateVisualState.OVERRIDDEN -> Color(0xFFFF6D00)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A2E)
                )

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Enable Gate",
                    fontSize = 13.sp,
                    color = Color(0xFF1A1A2E)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        onUpdate(policy.copy(enabled = it, prompt = promptText))
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Policy Prompt",
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onUpdate(policy.copy(enabled = enabled, prompt = promptText)) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun StationPromptCard(
    stationId: String,
    name: String,
    icon: ImageVector,
    prompt: String,
    onSave: (String) -> Unit
) {
    var promptText by remember(prompt) { mutableStateOf(prompt) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1A237E),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A2E)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onSave(promptText) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save")
            }
        }
    }
}
