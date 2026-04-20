package com.llmtest

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
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
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyEditorScreen(onBack: () -> Unit = {}) {
    val policies by remember { mutableStateOf(PolicyEngine.getPolicies().toMutableStateList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPolicy by remember { mutableStateOf<PolicyRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Policy Center") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Policy")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            policies.forEach { policy ->
                PolicyCard(
                    policy = policy,
                    onToggle = { active ->
                        PolicyEngine.togglePolicy(policy.policyId, active)
                        val idx = policies.indexOfFirst { it.policyId == policy.policyId }
                        if (idx >= 0) policies[idx] = policy.copy(isActive = active)
                    },
                    onClick = { selectedPolicy = policy },
                    onDelete = {
                        PolicyEngine.removePolicy(policy.policyId)
                        policies.removeAll { it.policyId == policy.policyId }
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreatePolicyDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { newPolicy ->
                PolicyEngine.addPolicy(newPolicy)
                policies.add(newPolicy)
                showCreateDialog = false
            }
        )
    }

    selectedPolicy?.let { policy ->
        PolicyDetailDialog(
            policy = policy,
            onDismiss = { selectedPolicy = null },
            onUpdate = { updated ->
                PolicyEngine.updatePolicy(updated)
                val idx = policies.indexOfFirst { it.policyId == updated.policyId }
                if (idx >= 0) policies[idx] = updated
                selectedPolicy = null
            }
        )
    }
}

@Composable
private fun PolicyCard(
    policy: PolicyRule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = when (policy.category) {
        PolicyCategory.CONTENT -> Color(0xFF1976D2)
        PolicyCategory.PROCESS -> Color(0xFF388E3C)
        PolicyCategory.COMMUNICATION -> Color(0xFFE53935)
        PolicyCategory.FACTUAL -> Color(0xFF7B1FA2)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = categoryColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            policy.category.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(policy.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = policy.isActive,
                    onCheckedChange = onToggle
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(policy.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = when (policy.action) {
                        PolicyAction.REDACT_AND_LOG -> Color(0xFF1976D2)
                        PolicyAction.ESCALATE_TO_HUMAN -> Color(0xFFE53935)
                        PolicyAction.BLOCK_AND_REQUEST_RETRY -> Color(0xFFFFA000)
                        PolicyAction.APPROVE -> Color(0xFF388E3C)
                    }.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        policy.action.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (policy.escalationRequired) {
                    Surface(
                        color = Color(0xFFE53935).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "Escalation Required",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE53935)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935))
                }
            }
        }
    }
}

@Composable
private fun CreatePolicyDialog(
    onDismiss: () -> Unit,
    onCreate: (PolicyRule) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PolicyCategory.CONTENT) }
    var action by remember { mutableStateOf(PolicyAction.ESCALATE_TO_HUMAN) }
    var conditionField by remember { mutableStateOf("") }
    var conditionOp by remember { mutableStateOf("==") }
    var conditionValue by remember { mutableStateOf("") }
    var escalationRequired by remember { mutableStateOf(false) }
    var gateOrderStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Policy") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Policy Name") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                OutlinedTextField(value = gateOrderStr, onValueChange = { gateOrderStr = it }, label = { Text("Gate Order (-1 = hidden, 0+ = shown on map)") }, singleLine = true)

                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PolicyCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Text("Action", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PolicyAction.entries.forEach { act ->
                        FilterChip(
                            selected = action == act,
                            onClick = { action = act },
                            label = { Text(act.name.replace("_", " ")) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = escalationRequired, onCheckedChange = { escalationRequired = it })
                    Text("Escalation Required")
                }

                Text("Condition (optional)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = conditionField, onValueChange = { conditionField = it }, label = { Text("Field (e.g., severity)") }, singleLine = true)
                OutlinedTextField(value = conditionOp, onValueChange = { conditionOp = it }, label = { Text("Operator (e.g., ==)") }, singleLine = true)
                OutlinedTextField(value = conditionValue, onValueChange = { conditionValue = it }, label = { Text("Value (e.g., Critical)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val conditions = if (conditionField.isNotBlank() && conditionValue.isNotBlank()) {
                        listOf(PolicyCondition(conditionField, conditionOp, JsonPrimitive(conditionValue)))
                    } else emptyList()
                    onCreate(
                        PolicyRule(
                            policyId = UUID.randomUUID().toString(),
                            name = name,
                            description = description,
                            category = category,
                            isActive = true,
                            conditions = conditions,
                            action = action,
                            escalationRequired = escalationRequired,
                            gateOrder = gateOrderStr.toIntOrNull() ?: -1
                        )
                    )
                },
                enabled = name.isNotBlank() && description.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PolicyDetailDialog(
    policy: PolicyRule,
    onDismiss: () -> Unit,
    onUpdate: (PolicyRule) -> Unit
) {
    var name by remember { mutableStateOf(policy.name) }
    var description by remember { mutableStateOf(policy.description) }
    var gateOrderStr by remember { mutableStateOf(policy.gateOrder.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Policy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                OutlinedTextField(value = gateOrderStr, onValueChange = { gateOrderStr = it }, label = { Text("Gate Order (-1 = hidden, 0+ = shown)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onUpdate(policy.copy(name = name, description = description, gateOrder = gateOrderStr.toIntOrNull() ?: -1)) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
