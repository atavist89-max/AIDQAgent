package com.llmtest

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Lookup data loaded from JSON
    var typeToSources by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var sourceToDatasets by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var datasetToChecks by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var checkToMetadata by remember { mutableStateOf<Map<String, AlertMetadata>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Form state — 4 tiers + auto-filled fields
    var selectedType by rememberSaveable { mutableStateOf("") }
    var selectedSource by rememberSaveable { mutableStateOf("") }
    var selectedDataset by rememberSaveable { mutableStateOf("") }
    var selectedCheck by rememberSaveable { mutableStateOf("") }
    var evaluationStatus by rememberSaveable { mutableStateOf("fail") }
    var lastCheckRunTime by rememberSaveable { mutableStateOf("") }

    // Dropdown expanded states
    var typeExpanded by remember { mutableStateOf(false) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var datasetExpanded by remember { mutableStateOf(false) }
    var checkExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    // Load data on first composition
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val (typeMap, sourceMap, checkMap, metaMap) = loadAlertLookups()
            typeToSources = typeMap.mapValues { it.value.sorted() }
            sourceToDatasets = sourceMap.mapValues { it.value.sorted() }
            datasetToChecks = checkMap.mapValues { it.value.sorted() }
            checkToMetadata = metaMap
            isLoading = false

            if (lastCheckRunTime.isEmpty()) {
                lastCheckRunTime = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date())
            }
        }
    }

    // Derived lists for dropdowns
    val availableSources = remember(selectedType) {
        typeToSources[selectedType]?.sorted() ?: emptyList()
    }
    val availableDatasets = remember(selectedSource) {
        sourceToDatasets[selectedSource]?.sorted() ?: emptyList()
    }
    val availableChecks = remember(selectedDataset) {
        datasetToChecks[selectedDataset]?.sorted() ?: emptyList()
    }

    // Auto-fill when CHECK_NAME selected (Tier 4)
    LaunchedEffect(selectedCheck) {
        if (selectedCheck.isNotEmpty() && selectedDataset.isNotEmpty()) {
            val key = "$selectedDataset|$selectedCheck"
            checkToMetadata[key]?.let { meta ->
                evaluationStatus = meta.evaluationStatus
                lastCheckRunTime = meta.lastCheckRunTime
            }
        }
    }

    // Send enabled when all 4 tiers selected
    val canSend = selectedType.isNotEmpty() && selectedSource.isNotEmpty() &&
            selectedDataset.isNotEmpty() && selectedCheck.isNotEmpty()

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Create DQ Alert", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // Tier 1: DATASOURCE_TYPE
        ExposedDropdownMenuBox(
            expanded = typeExpanded,
            onExpandedChange = { typeExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Data Source Type *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = typeExpanded,
                onDismissRequest = { typeExpanded = false }
            ) {
                typeToSources.keys.sorted().forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            selectedType = type
                            selectedSource = ""
                            selectedDataset = ""
                            selectedCheck = ""
                            typeExpanded = false
                        }
                    )
                }
            }
        }

        // Tier 2: DATASOURCE_NAME
        ExposedDropdownMenuBox(
            expanded = sourceExpanded,
            onExpandedChange = { sourceExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedSource,
                onValueChange = {},
                readOnly = true,
                label = { Text("Data Source Name *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                enabled = selectedType.isNotEmpty()
            )
            ExposedDropdownMenu(
                expanded = sourceExpanded,
                onDismissRequest = { sourceExpanded = false }
            ) {
                availableSources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source) },
                        onClick = {
                            selectedSource = source
                            selectedDataset = ""
                            selectedCheck = ""
                            sourceExpanded = false
                        }
                    )
                }
            }
        }

        // Tier 3: DATASET_NAME
        ExposedDropdownMenuBox(
            expanded = datasetExpanded,
            onExpandedChange = { datasetExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedDataset,
                onValueChange = {},
                readOnly = true,
                label = { Text("Dataset Name *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = datasetExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                enabled = selectedSource.isNotEmpty()
            )
            ExposedDropdownMenu(
                expanded = datasetExpanded,
                onDismissRequest = { datasetExpanded = false }
            ) {
                availableDatasets.forEach { dataset ->
                    DropdownMenuItem(
                        text = { Text(dataset) },
                        onClick = {
                            selectedDataset = dataset
                            selectedCheck = ""
                            datasetExpanded = false
                        }
                    )
                }
            }
        }

        // Tier 4: CHECK_NAME
        ExposedDropdownMenuBox(
            expanded = checkExpanded,
            onExpandedChange = { checkExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedCheck,
                onValueChange = {},
                readOnly = true,
                label = { Text("Check Name *") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = checkExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                enabled = selectedDataset.isNotEmpty()
            )
            ExposedDropdownMenu(
                expanded = checkExpanded,
                onDismissRequest = { checkExpanded = false }
            ) {
                availableChecks.forEach { check ->
                    DropdownMenuItem(
                        text = { Text(check) },
                        onClick = {
                            selectedCheck = check
                            checkExpanded = false
                        }
                    )
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Auto-filled fields (editable)
        val meta = if (selectedCheck.isNotEmpty() && selectedDataset.isNotEmpty()) {
            checkToMetadata["$selectedDataset|$selectedCheck"]
        } else null

        OutlinedTextField(
            value = meta?.severity ?: "",
            onValueChange = { /* read-only for now, or make editable if needed */ },
            label = { Text("Severity") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true
        )

        OutlinedTextField(
            value = meta?.dimension ?: "",
            onValueChange = { },
            label = { Text("Dimension") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true
        )

        OutlinedTextField(
            value = meta?.ownerEmail ?: "",
            onValueChange = { },
            label = { Text("Owner Email") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true
        )

        OutlinedTextField(
            value = lastCheckRunTime,
            onValueChange = { lastCheckRunTime = it },
            label = { Text("Last Check Run Time") },
            modifier = Modifier.fillMaxWidth()
        )

        // Evaluation Status dropdown
        ExposedDropdownMenuBox(
            expanded = statusExpanded,
            onExpandedChange = { statusExpanded = it }
        ) {
            OutlinedTextField(
                value = evaluationStatus,
                onValueChange = {},
                readOnly = true,
                label = { Text("Evaluation Status") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = statusExpanded,
                onDismissRequest = { statusExpanded = false }
            ) {
                listOf("fail", "pass").forEach { status ->
                    DropdownMenuItem(
                        text = { Text(status) },
                        onClick = {
                            evaluationStatus = status
                            statusExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val alert = DQAlert(
                    lastCheckRunTime = lastCheckRunTime,
                    evaluationStatus = evaluationStatus,
                    checkName = selectedCheck,
                    datasetName = selectedDataset,
                    datasourceName = selectedSource,
                    datasourceType = selectedType,
                    ownerEmail = meta?.ownerEmail ?: "",
                    severity = meta?.severity ?: "",
                    dimension = meta?.dimension ?: ""
                )
                scope.launch(Dispatchers.IO) {
                    try {
                        val json = Json { prettyPrint = true }
                        val jsonString = json.encodeToString(DQAlert.serializer(), alert)
                        val outputFile = GhostPaths.inputFile()
                        outputFile.parentFile?.mkdirs()
                        outputFile.writeText(jsonString)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Alert sent to analysis queue", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        BugLogger.logError("Failed to write alert", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send DQ Alert")
        }

        if (!canSend) {
            Text(
                "Select Type, Source, Dataset, and Check to enable sending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// Data class for check-level metadata lookup
data class AlertMetadata(
    val checkName: String,
    val datasetName: String,
    val severity: String,
    val dimension: String,
    val ownerEmail: String,
    val datasourceName: String,
    val datasourceType: String,
    val evaluationStatus: String,
    val lastCheckRunTime: String
)

private fun loadAlertLookups(): Quadruple<Map<String, Set<String>>, Map<String, Set<String>>, Map<String, Set<String>>, Map<String, AlertMetadata>> {
    val json = Json { ignoreUnknownKeys = true }
    val typeToSources = mutableMapOf<String, MutableSet<String>>()
    val sourceToDatasets = mutableMapOf<String, MutableSet<String>>()
    val datasetToChecks = mutableMapOf<String, MutableSet<String>>()
    val checkToMetadata = mutableMapOf<String, AlertMetadata>()

    try {
        val content = GhostPaths.DQ_ALERTS.readText()
        val alerts = json.decodeFromString(ListSerializer(DQAlert.serializer()), content)

        alerts.forEach { alert ->
            val type = alert.datasourceType ?: "unknown"
            val source = alert.datasourceName
            val dataset = alert.datasetName
            val check = alert.checkName
            val key = "$dataset|$check"

            typeToSources.getOrPut(type) { mutableSetOf() }.add(source)
            sourceToDatasets.getOrPut(source) { mutableSetOf() }.add(dataset)
            datasetToChecks.getOrPut(dataset) { mutableSetOf() }.add(check)

            // Store first match (POC scope — no deduplication needed)
            if (!checkToMetadata.containsKey(key)) {
                checkToMetadata[key] = AlertMetadata(
                    checkName = check,
                    datasetName = dataset,
                    severity = alert.severity,
                    dimension = alert.dimension,
                    ownerEmail = alert.ownerEmail,
                    datasourceName = source,
                    datasourceType = type,
                    evaluationStatus = alert.evaluationStatus,
                    lastCheckRunTime = alert.lastCheckRunTime
                )
            }
        }
    } catch (e: Exception) {
        BugLogger.logError("Failed to load alert lookups", e)
    }

    return Quadruple(typeToSources, sourceToDatasets, datasetToChecks, checkToMetadata)
}

// Simple 4-tuple since Kotlin doesn't have one
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
