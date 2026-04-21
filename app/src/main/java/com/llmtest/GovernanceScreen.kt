@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Composable
fun GovernanceScreen() {
    val scrollState = rememberScrollState()
    val policies = remember { mutableStateOf(GovernanceConfig.loadPolicies()) }
    val stationPrompts = remember { mutableStateOf(GovernanceConfig.loadStationPrompts()) }
    val gateVisualStates by GaaSController.gateVisualStates.collectAsState()

    // Load available dimensions for Stage 1 chip selector
    val availableDimensions = remember { loadDimensionNames() }

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
            text = "Station-Agent Configuration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1A1A2E)
        )

        // Stage 1: Triage Decision Matrix
        val stage1Prompt = stationPrompts.value.find { it.stationId == "stage1" } ?: StationPrompt("stage1")
        val stage1Config = remember(stage1Prompt.configJson) {
            GovernanceConfig.parseConfig(stage1Prompt.configJson, Stage1Config.serializer()) ?: Stage1Config()
        }
        Stage1ConfigCard(
            config = stage1Config,
            availableDimensions = availableDimensions,
            onSave = { newConfig ->
                val updated = stage1Prompt.copy(configJson = Json.encodeToString(Stage1Config.serializer(), newConfig))
                GovernanceConfig.updateStationPrompt(updated)
                stationPrompts.value = GovernanceConfig.loadStationPrompts()
            }
        )

        // Stage 2: Entity Enrichment Selector
        val stage2Prompt = stationPrompts.value.find { it.stationId == "stage2" } ?: StationPrompt("stage2")
        val stage2Config = remember(stage2Prompt.configJson) {
            GovernanceConfig.parseConfig(stage2Prompt.configJson, Stage2Config.serializer()) ?: Stage2Config()
        }
        Stage2ConfigCard(
            config = stage2Config,
            onSave = { newConfig ->
                val updated = stage2Prompt.copy(configJson = Json.encodeToString(Stage2Config.serializer(), newConfig))
                GovernanceConfig.updateStationPrompt(updated)
                stationPrompts.value = GovernanceConfig.loadStationPrompts()
            }
        )

        // Stage 3: Pattern Detection Thresholds
        val stage3Prompt = stationPrompts.value.find { it.stationId == "stage3" } ?: StationPrompt("stage3")
        val stage3Config = remember(stage3Prompt.configJson) {
            GovernanceConfig.parseConfig(stage3Prompt.configJson, Stage3Config.serializer()) ?: Stage3Config()
        }
        Stage3ConfigCard(
            config = stage3Config,
            onSave = { newConfig ->
                val updated = stage3Prompt.copy(configJson = Json.encodeToString(Stage3Config.serializer(), newConfig))
                GovernanceConfig.updateStationPrompt(updated)
                stationPrompts.value = GovernanceConfig.loadStationPrompts()
            }
        )

        // Stages 4A, 4B, 4C: Prompt-only cards
        val llmStationDefs = listOf(
            Triple("stage4a", "Stage 4A Upstream Researcher", Icons.Default.Build),
            Triple("stage4b", "Stage 4B Downstream Researcher", Icons.Default.Assessment),
            Triple("stage4c", "Stage 4C Synthesizer", Icons.Default.Article)
        )

        llmStationDefs.forEach { (stationId, name, icon) ->
            val promptObj = stationPrompts.value.find { it.stationId == stationId } ?: StationPrompt(stationId)
            StationPromptCard(
                stationId = stationId,
                name = name,
                icon = icon,
                prompt = promptObj.prompt,
                onSave = { newPrompt ->
                    GovernanceConfig.updateStationPrompt(StationPrompt(stationId, newPrompt, promptObj.configJson))
                    stationPrompts.value = GovernanceConfig.loadStationPrompts()
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun loadDimensionNames(): List<String> {
    return try {
        val content = GhostPaths.DQ_KNOWLEDGE.readText()
        val knowledge = Json { ignoreUnknownKeys = true }.decodeFromString(DQKnowledge.serializer(), content)
        knowledge.dimensions.keys.toList().sorted()
    } catch (e: Exception) {
        listOf("Completeness", "Timeliness", "Accuracy", "Consistency", "Validity", "Uniqueness", "Adaptability")
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

// ==================== STAGE 1: TRIAGE DECISION MATRIX ====================

@Composable
private fun Stage1ConfigCard(
    config: Stage1Config,
    availableDimensions: List<String>,
    onSave: (Stage1Config) -> Unit
) {
    var severityThreshold by remember { mutableStateOf(config.severityThreshold) }
    var requiredDownstreamClass by remember { mutableIntStateOf(config.requiredDownstreamClass) }
    var dimensionBypass by remember { mutableStateOf(config.dimensionBypass.toSet()) }
    var expanded by remember { mutableStateOf(true) }

    val severities = listOf("Informative", "Warning", "Error", "Critical")
    val severityIndex = severities.indexOf(severityThreshold).coerceAtLeast(0)

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = Color(0xFF1A237E),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stage 1 Triage Rules",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A2E)
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Expand")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Severity threshold dropdown
                    Text("Minimum severity for FULL_ANALYSIS", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    var severityExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = severityExpanded,
                        onExpandedChange = { severityExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = severityThreshold,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = severityExpanded) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = severityExpanded,
                            onDismissRequest = { severityExpanded = false }
                        ) {
                            severities.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = {
                                        severityThreshold = s
                                        severityExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Downstream class slider
                    Text("Minimum downstream report class", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = requiredDownstreamClass.toFloat(),
                        onValueChange = { requiredDownstreamClass = it.toInt() },
                        valueRange = 0f..2f,
                        steps = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Class $requiredDownstreamClass",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A237E)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dimension bypass chips
                    Text("Dimensions that always get full analysis", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableDimensions.forEach { dim ->
                            val selected = dimensionBypass.contains(dim)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    dimensionBypass = if (selected) {
                                        dimensionBypass - dim
                                    } else {
                                        dimensionBypass + dim
                                    }
                                },
                                label = { Text(dim, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onSave(
                                Stage1Config(
                                    severityThreshold = severityThreshold,
                                    requiredDownstreamClass = requiredDownstreamClass,
                                    dimensionBypass = dimensionBypass.toList()
                                )
                            )
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ==================== STAGE 2: ENTITY ENRICHMENT SELECTOR ====================

@Composable
private fun Stage2ConfigCard(
    config: Stage2Config,
    onSave: (Stage2Config) -> Unit
) {
    var entityFields by remember { mutableStateOf(config.entityFields.toSet()) }
    var catalogFields by remember { mutableStateOf(config.catalogFields.toSet()) }
    var fallbackChain by remember { mutableStateOf(config.fallbackChain.toMutableList()) }
    var maxCatalogColumns by remember { mutableIntStateOf(config.maxCatalogColumns) }
    var expanded by remember { mutableStateOf(true) }

    val allEntityFields = listOf("entityName", "entityGroup", "ownerEmail", "description", "tags", "usageNotes")
    val allCatalogFields = listOf("sourceDB", "sourceTable", "definition", "dataExample", "sourceAttribute", "sourceDescription")

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = Color(0xFF1A237E),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stage 2 Context Builder Rules",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A2E)
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Expand")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Entity fields
                    Text("Entity fields to include", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    allEntityFields.forEach { field ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = entityFields.contains(field),
                                onCheckedChange = { checked ->
                                    entityFields = if (checked) entityFields + field else entityFields - field
                                }
                            )
                            Text(field, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Catalog fields
                    Text("Catalog fields to include", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    allCatalogFields.forEach { field ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = catalogFields.contains(field),
                                onCheckedChange = { checked ->
                                    catalogFields = if (checked) catalogFields + field else catalogFields - field
                                }
                            )
                            Text(field, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Max catalog columns
                    Text("Max catalog columns per dataset", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Slider(
                        value = maxCatalogColumns.toFloat(),
                        onValueChange = { maxCatalogColumns = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "$maxCatalogColumns",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A237E)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Fallback chain with up/down buttons
                    Text("Fallback chain when entity lookup fails", fontSize = 12.sp, color = Color(0xFF6B7280))
                    Spacer(modifier = Modifier.height(4.dp))
                    fallbackChain.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${index + 1}.", fontSize = 12.sp, modifier = Modifier.width(20.dp))
                            Text(item, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val newList = fallbackChain.toMutableList()
                                        newList[index] = newList[index - 1]
                                        newList[index - 1] = item
                                        fallbackChain = newList
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (index < fallbackChain.size - 1) {
                                        val newList = fallbackChain.toMutableList()
                                        newList[index] = newList[index + 1]
                                        newList[index + 1] = item
                                        fallbackChain = newList
                                    }
                                },
                                enabled = index < fallbackChain.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onSave(
                                Stage2Config(
                                    entityFields = entityFields.toList(),
                                    catalogFields = catalogFields.toList(),
                                    fallbackChain = fallbackChain.toList(),
                                    maxCatalogColumns = maxCatalogColumns
                                )
                            )
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ==================== STAGE 3: PATTERN DETECTION THRESHOLDS ====================

@Composable
private fun Stage3ConfigCard(
    config: Stage3Config,
    onSave: (Stage3Config) -> Unit
) {
    var ownerOverloadThreshold by remember { mutableIntStateOf(config.ownerOverloadThreshold) }
    var ownerOverloadAnySeverityThreshold by remember { mutableIntStateOf(config.ownerOverloadAnySeverityThreshold) }
    var lookbackWindowDays by remember { mutableIntStateOf(config.lookbackWindowDays) }
    var groupHealthThreshold by remember { mutableFloatStateOf(config.groupHealthThreshold) }
    var groupFailingDatasetMin by remember { mutableIntStateOf(config.groupFailingDatasetMin) }
    var defaultIsolatedIncident by remember { mutableStateOf(config.defaultIsolatedIncident) }
    var expanded by remember { mutableStateOf(true) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = Color(0xFF1A237E),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stage 3 Pattern Detection Thresholds",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A2E)
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Expand")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Owner Overload card
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Owner Overload", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A237E))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Flag at ≥ X critical/error failures", fontSize = 11.sp, color = Color(0xFF6B7280))
                            Slider(
                                value = ownerOverloadThreshold.toFloat(),
                                onValueChange = { ownerOverloadThreshold = it.toInt() },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("$ownerOverloadThreshold", fontSize = 11.sp, fontWeight = FontWeight.Medium)

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Flag at ≥ X total failures", fontSize = 11.sp, color = Color(0xFF6B7280))
                            Slider(
                                value = ownerOverloadAnySeverityThreshold.toFloat(),
                                onValueChange = { ownerOverloadAnySeverityThreshold = it.toInt() },
                                valueRange = 1f..20f,
                                steps = 18,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("$ownerOverloadAnySeverityThreshold", fontSize = 11.sp, fontWeight = FontWeight.Medium)

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Lookback window (days)", fontSize = 11.sp, color = Color(0xFF6B7280))
                            Slider(
                                value = lookbackWindowDays.toFloat(),
                                onValueChange = { lookbackWindowDays = it.toInt() },
                                valueRange = 1f..30f,
                                steps = 28,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("$lookbackWindowDays days", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Group Collapse card
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Group Collapse", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A237E))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Trigger when group health drops below", fontSize = 11.sp, color = Color(0xFF6B7280))
                            Slider(
                                value = groupHealthThreshold,
                                onValueChange = { groupHealthThreshold = it },
                                valueRange = 0.1f..0.9f,
                                steps = 7,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${(groupHealthThreshold * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Medium)

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Minimum failing datasets in group", fontSize = 11.sp, color = Color(0xFF6B7280))
                            Slider(
                                value = groupFailingDatasetMin.toFloat(),
                                onValueChange = { groupFailingDatasetMin = it.toInt() },
                                valueRange = 1f..10f,
                                steps = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("$groupFailingDatasetMin", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Default isolated incident toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = defaultIsolatedIncident,
                            onCheckedChange = { defaultIsolatedIncident = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Default to isolated_incident when no other pattern matches",
                            fontSize = 12.sp,
                            color = Color(0xFF1A1A2E)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onSave(
                                Stage3Config(
                                    ownerOverloadThreshold = ownerOverloadThreshold,
                                    ownerOverloadAnySeverityThreshold = ownerOverloadAnySeverityThreshold,
                                    lookbackWindowDays = lookbackWindowDays,
                                    groupHealthThreshold = groupHealthThreshold,
                                    groupFailingDatasetMin = groupFailingDatasetMin,
                                    defaultIsolatedIncident = defaultIsolatedIncident
                                )
                            )
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ==================== GENERIC LLM STATION PROMPT CARD ====================

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
