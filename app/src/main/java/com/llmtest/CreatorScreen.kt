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
    var datasetToDefaults by remember { mutableStateOf<Map<String, AlertDefaults>>(emptyMap()) }
    var datasetToOwner by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Form state
    var selectedType by rememberSaveable { mutableStateOf("") }
    var selectedSource by rememberSaveable { mutableStateOf("") }
    var selectedDataset by rememberSaveable { mutableStateOf("") }
    var checkName by rememberSaveable { mutableStateOf("") }
    var severity by rememberSaveable { mutableStateOf("") }
    var dimension by rememberSaveable { mutableStateOf("") }
    var ownerEmail by rememberSaveable { mutableStateOf("") }
    var evaluationStatus by rememberSaveable { mutableStateOf("fail") }
    var lastCheckRunTime by rememberSaveable { mutableStateOf("") }

    // Dropdown expanded states
    var typeExpanded by remember { mutableStateOf(false) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var datasetExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    // Load data on first composition
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val (typeMap, sourceMap, defaultsMap) = loadAlertLookups()
            val ownerMap = loadEntityOwners()
            typeToSources = typeMap.mapValues { it.value.sorted() }
            sourceToDatasets = sourceMap.mapValues { it.value.sorted() }
            datasetToDefaults = defaultsMap
            datasetToOwner = ownerMap
            isLoading = false

            // Set default timestamp
            if (lastCheckRunTime.isEmpty()) {
                lastCheckRunTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
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

    // Auto-fill when dataset changes
    LaunchedEffect(selectedDataset) {
        if (selectedDataset.isNotEmpty()) {
            datasetToDefaults[selectedDataset]?.let { defaults ->
                checkName = defaults.checkName
                severity = defaults.severity
                dimension = defaults.dimension
            }
            datasetToOwner[selectedDataset]?.let { owner ->
                ownerEmail = owner
            }
        }
    }

    // Send enabled when all 3 tiers selected
    val canSend = selectedType.isNotEmpty() && selectedSource.isNotEmpty() && selectedDataset.isNotEmpty()

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
                            datasetExpanded = false
                        }
                    )
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Auto-filled fields (editable)
        OutlinedTextField(
            value = checkName,
            onValueChange = { checkName = it },
            label = { Text("Check Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = severity,
            onValueChange = { severity = it },
            label = { Text("Severity") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = dimension,
            onValueChange = { dimension = it },
            label = { Text("Dimension") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = ownerEmail,
            onValueChange = { ownerEmail = it },
            label = { Text("Owner Email") },
            modifier = Modifier.fillMaxWidth()
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
                    checkName = checkName,
                    datasetName = selectedDataset,
                    datasourceName = selectedSource,
                    datasourceType = selectedType,
                    ownerEmail = ownerEmail,
                    severity = severity,
                    dimension = dimension
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
                "Select Type, Source, and Dataset to enable sending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// Data class for dataset defaults lookup
data class AlertDefaults(
    val checkName: String,
    val severity: String,
    val dimension: String
)

private fun loadAlertLookups(): Triple<Map<String, Set<String>>, Map<String, Set<String>>, Map<String, AlertDefaults>> {
    val json = Json { ignoreUnknownKeys = true }
    val typeToSources = mutableMapOf<String, MutableSet<String>>()
    val sourceToDatasets = mutableMapOf<String, MutableSet<String>>()
    val datasetToDefaults = mutableMapOf<String, AlertDefaults>()

    try {
        val content = GhostPaths.DQ_ALERTS.readText()
        val alerts = json.decodeFromString(ListSerializer(DQAlert.serializer()), content)

        alerts.forEach { alert ->
            val type = alert.datasourceType ?: "unknown"
            val source = alert.datasourceName
            val dataset = alert.datasetName

            typeToSources.getOrPut(type) { mutableSetOf() }.add(source)
            sourceToDatasets.getOrPut(source) { mutableSetOf() }.add(dataset)

            // Keep the most recent defaults (if multiple alerts for same dataset)
            datasetToDefaults[dataset] = AlertDefaults(
                checkName = alert.checkName,
                severity = alert.severity,
                dimension = alert.dimension
            )
        }
    } catch (e: Exception) {
        BugLogger.logError("Failed to load alert lookups", e)
    }

    return Triple(typeToSources, sourceToDatasets, datasetToDefaults)
}

private fun loadEntityOwners(): Map<String, String> {
    val json = Json { ignoreUnknownKeys = true }
    val datasetToOwner = mutableMapOf<String, String>()

    try {
        val content = GhostPaths.ENTITIES.readText()
        val entities = json.decodeFromString(ListSerializer(Entity.serializer()), content)
        entities.forEach { entity ->
            datasetToOwner[entity.linkedDatasetName] = entity.ownerEmail
        }
    } catch (e: Exception) {
        BugLogger.logError("Failed to load entity owners", e)
    }

    return datasetToOwner
}
