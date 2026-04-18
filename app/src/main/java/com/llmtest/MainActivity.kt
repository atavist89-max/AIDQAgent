package com.llmtest

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    private var engine: Engine? = null
    private var pipelineManager: DQPipelineManager? = null

    // UI State
    private val hasPermission = mutableStateOf(false)
    private val isInitialized = mutableStateOf(false)
    private val currentStage = mutableStateOf(0)
    private val finalReport = mutableStateOf("")
    private val statusMessage = mutableStateOf("Initializing...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugLogger.initialize(this)
        
        // Check permission immediately (same as Gemma template)
        hasPermission.value = hasStoragePermission()
        BugLogger.log("onCreate: hasPermission = ${hasPermission.value}")

        // If already permitted, initialize now (synchronous, before UI)
        if (hasPermission.value) {
            initializeApp()
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        !hasPermission.value -> PermissionRequestScreen()
                        !isInitialized.value -> LoadingScreen()
                        else -> MainScaffold()
                    }
                }
            }
        }
        
        // Lifecycle observer (same pattern as Gemma template)
        setupPermissionObserver()
    }
    
    private fun setupPermissionObserver() {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowHasPermission = hasStoragePermission()
                BugLogger.log("ON_RESUME: permission = $nowHasPermission")
                
                if (nowHasPermission && !hasPermission.value) {
                    // User just granted permission
                    hasPermission.value = true
                    initializeApp()
                }
            }
        })
    }
    
    private fun initializeApp() {
        // Prevent double initialization
        if (isInitialized.value) return
        
        BugLogger.log("Initializing app...")
        
        try {
            // Check data availability
            if (!GhostPaths.isDQDataAvailable()) {
                BugLogger.log("WARNING: DQ data not available")
                statusMessage.value = "Warning: DQ data missing"
            }
            if (!GhostPaths.isModelAvailable()) {
                BugLogger.log("WARNING: Model not available")
                statusMessage.value = "Warning: AI model missing"
            }
            
            // Initialize engine (synchronous, same as Gemma template)
            initEngine()
            
            // Create pipeline manager
            val engineInstance = engine ?: throw IllegalStateException("Engine failed to initialize")
            pipelineManager = DQPipelineManager(engineInstance)
            
            // Start polling
            startFilePolling()
            
            // Mark ready
            isInitialized.value = true
            statusMessage.value = "Ready. Waiting for alerts..."
            BugLogger.log("App initialized successfully")
            
        } catch (e: Exception) {
            BugLogger.logError("Failed to initialize app", e)
            statusMessage.value = "Error: ${e.message}"
        }
    }

    @Composable
    fun PermissionRequestScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("DQ Agent - Owner's Overview", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Permission Required",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "This app needs 'All Files Access' permission to read:\n" +
                "• AI model (gemma-4-e2b.litertlm)\n" +
                "• DQ data files from GhostModels/DQAgent/",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { requestStoragePermission() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permission")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Tap button → Toggle permission → Return to app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    fun LoadingScreen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text(statusMessage.value)
            }
        }
    }

    @Composable
    fun MainScaffold() {
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Text("+") },
                        label = { Text("Create") },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Text("P") },
                        label = { Text("Portfolio") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    NavigationBarItem(
                        icon = { Text("A") },
                        label = { Text("Analyze") },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> CreatorScreen()
                    1 -> PortfolioScreen(engine)
                    2 -> DQAgentScreen()
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                BugLogger.log("Opened permission settings")
            } catch (e: Exception) {
                BugLogger.logError("Failed to open settings", e)
                Toast.makeText(this, "Please enable permission manually", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initEngine() {
        BugLogger.log("Initializing engine...")
        val config = EngineConfig(
            modelPath = GhostPaths.MODEL_FILE.absolutePath,
            backend = Backend.GPU(),
            maxNumTokens = 2048,
            cacheDir = cacheDir.path
        )
        engine = Engine(config)
        engine?.initialize()
        BugLogger.log("Engine initialized")
    }

    private fun startFilePolling() {
        lifecycleScope.launch(Dispatchers.IO) {
            BugLogger.log("Starting file polling...")
            while (isActive) {
                checkForNewAlerts()
                delay(5000)
            }
        }
    }

    private suspend fun checkForNewAlerts() {
        val inputFile = GhostPaths.inputFile()
        if (!inputFile.exists()) return

        try {
            val json = Json { ignoreUnknownKeys = true }
            val alert = json.decodeFromString(DQAlert.serializer(), inputFile.readText())
            inputFile.delete()

            withContext(Dispatchers.Main) {
                statusMessage.value = "Processing ${alert.datasetName}..."
                pipelineManager?.startAnalysis(alert)
            }
        } catch (e: Exception) {
            BugLogger.logError("Failed to process input file", e)
        }
    }

    @Composable
    fun PortfolioScreen(engine: Engine?) {
        val scope = rememberCoroutineScope()
        var isLoading by remember { mutableStateOf(true) }
        var portfolioData by remember { mutableStateOf<PortfolioData?>(null) }
        var riskMap by remember { mutableStateOf<String?>(null) }
        var isRiskMapLoading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                portfolioData = loadPortfolioData()
                // Try to load cached risk map
                if (GhostPaths.PORTFOLIO_RISK_MAP.exists()) {
                    riskMap = GhostPaths.PORTFOLIO_RISK_MAP.readText()
                }
                isLoading = false
            }
        }

        fun generateRiskMap(ownerEmail: String) {
            if (engine == null) return
            isRiskMapLoading = true
            scope.launch(Dispatchers.IO) {
                val result = try {
                    PortfolioRiskAnalyzer.analyze(ownerEmail, engine)
                } catch (e: Exception) {
                    BugLogger.logError("Risk map generation failed", e)
                    "Risk map generation failed. Please try again."
                }
                withContext(Dispatchers.Main) {
                    riskMap = result
                    isRiskMapLoading = false
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("My Portfolio", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (portfolioData == null) {
                Text("No portfolio data available.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val data = portfolioData!!

                // Summary Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Owner: ${data.ownerEmail}", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Groups: ${data.groupCount} | Datasets: ${data.datasetCount}")
                        Text("Overall Health: ${(data.overallHealth * 100).toInt()}%")
                        if (data.openCriticalError > 0) {
                            Text(
                                "Open Critical/Error: ${data.openCriticalError}",
                                color = Color(0xFFD32F2F),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }

                // Workload Insight
                if (data.patternType == "owner_overload") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "⚠️ Systemic Overload Detected",
                                color = Color(0xFFD32F2F),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text("You have ${data.openCriticalError} open Critical/Error failures. This suggests a governance gap, not isolated incidents.")
                        }
                    }
                }

                // Dependency Risk Map
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🗺️ Source System Dependency Risk Map",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isRiskMapLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text(
                                    "Analyzing infrastructure dependencies...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (riskMap != null) {
                            Text(
                                riskMap!!,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "Gemma will analyze your source system dependencies, identify concentration risks, and predict cascade paths across your portfolio.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { generateRiskMap(data.ownerEmail) },
                            enabled = !isRiskMapLoading && engine != null,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                if (riskMap == null) "Generate Risk Map" else "Refresh Analysis",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Entity Groups
                data.groups.forEach { group ->
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(group.groupName, style = MaterialTheme.typography.titleSmall)
                            Text(group.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { group.healthScore },
                                modifier = Modifier.fillMaxWidth(),
                                color = when {
                                    group.healthScore >= 0.85f -> Color(0xFF2E7D32)
                                    group.healthScore >= 0.6f -> Color(0xFFF57C00)
                                    else -> Color(0xFFD32F2F)
                                }
                            )
                            Text("Health: ${(group.healthScore * 100).toInt()}% | Datasets: ${group.datasets.size}", style = MaterialTheme.typography.bodySmall)

                            if (expanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                group.datasets.forEach { ds ->
                                    val statusColor = if (ds.hasFailure) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(ds.datasetName, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            if (ds.hasFailure) "FAIL" else "PASS",
                                            color = statusColor,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            TextButton(
                                onClick = { expanded = !expanded },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    if (expanded) "Collapse" else "Expand",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // Recent Alerts
                if (data.recentAlerts.isNotEmpty()) {
                    Text("Recent Alerts", style = MaterialTheme.typography.titleMedium)
                    data.recentAlerts.forEach { alert ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(alert.datasetName, style = MaterialTheme.typography.bodySmall)
                                    Text(alert.checkName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Text(
                                    alert.severity,
                                    color = when (alert.severity) {
                                        "Critical" -> Color(0xFFD32F2F)
                                        "Error" -> Color(0xFFF57C00)
                                        else -> Color(0xFF2E7D32)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DQAgentScreen() {
        val pm = pipelineManager ?: return

        val stage by pm.currentStage.collectAsState()
        val s1 by pm.stage1Output.collectAsState()
        val s2 by pm.stage2Output.collectAsState()
        val s3 by pm.stage3Output.collectAsState()
        val s4 by pm.stage4Output.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val headerText = when {
                    stage == 50 -> "Owner's Overview - Guidance Ready"
                    stage >= 4 -> "Owner's Overview - Stage $stage/4"
                    stage >= 1 -> "Owner's Overview - Stage $stage/4"
                    else -> "Owner's Overview - Stage 0/4"
                }
                Text(
                    headerText,
                    style = MaterialTheme.typography.headlineSmall
                )

                StageBox(
                    title = "Stage 1: Triage",
                    content = s1,
                    isActive = stage == 1,
                    isComplete = stage > 1
                )

                StageBox(
                    title = "Stage 2: Context Builder",
                    content = s2,
                    isActive = stage == 2,
                    isComplete = stage > 2
                )

                StageBox(
                    title = "Stage 3: Pattern Detection",
                    content = s3,
                    isActive = stage == 3,
                    isComplete = stage > 3
                )

                if (stage >= 4) {
                    StageBox(
                        title = "Stage 4: Owner Guidance",
                        content = if (stage == 50) s4 else "Generating owner guidance...",
                        fullContent = if (stage == 50) s4 else null,
                        isActive = stage == 4,
                        isComplete = stage == 50
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Button(onClick = { /* manual trigger */ }) {
                    Text("Test")
                }
                Button(onClick = {
                    Toast.makeText(this@MainActivity, BugLogger.readLog().take(300), Toast.LENGTH_LONG).show()
                }) {
                    Text("Logs")
                }
            }
        }
    }

    @Composable
    fun StageBox(title: String, content: String, isActive: Boolean, isComplete: Boolean, fullContent: String? = null) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        val displayContent = if (expanded) fullContent ?: content else content
        val canExpand = fullContent != null && fullContent != content

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isActive -> Color(0xFFFFF9C4)
                    isComplete -> Color(0xFFE8F5E9)
                    else -> Color(0xFFF5F5F5)
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        isActive -> Color(0xFFF57C00)
                        isComplete -> Color(0xFF2E7D32)
                        else -> Color.Gray
                    }
                )
                if (displayContent.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        displayContent,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (expanded || (isComplete && title.contains("Owner Guidance"))) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (canExpand) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                if (expanded) "Collapse" else "Expand",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else if (isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pipelineManager?.cancel()
        engine?.close()
    }
}

// Portfolio data models
private data class PortfolioData(
    val ownerEmail: String,
    val groupCount: Int,
    val datasetCount: Int,
    val overallHealth: Float,
    val openCriticalError: Int,
    val patternType: String?,
    val groups: List<PortfolioGroup>,
    val recentAlerts: List<PortfolioAlert>
)

private data class PortfolioGroup(
    val groupName: String,
    val description: String,
    val healthScore: Float,
    val datasets: List<PortfolioDataset>
)

private data class PortfolioDataset(
    val datasetName: String,
    val hasFailure: Boolean
)

private data class PortfolioAlert(
    val datasetName: String,
    val checkName: String,
    val severity: String
)

private fun loadPortfolioData(): PortfolioData? {
    val json = Json { ignoreUnknownKeys = true }

    return try {
        // Load all data sources
        val alerts = try {
            json.decodeFromString(ListSerializer(DQAlert.serializer()), GhostPaths.DQ_ALERTS.readText())
        } catch (e: Exception) { emptyList() }

        val entities = try {
            json.decodeFromString(ListSerializer(Entity.serializer()), GhostPaths.ENTITIES.readText())
        } catch (e: Exception) { emptyList() }

        val groups = try {
            json.decodeFromString(ListSerializer(EntityGroup.serializer()), GhostPaths.ENTITY_GROUPS.readText())
        } catch (e: Exception) { emptyList() }

        // Derive current owner from most recent failing alert
        val currentOwner = alerts
            .filter { it.evaluationStatus == "fail" }
            .maxByOrNull { it.lastCheckRunTime }
            ?.ownerEmail
            ?: alerts.firstOrNull()?.ownerEmail
            ?: return null

        // Owner's entities and groups
        val ownerEntities = entities.filter { it.ownerEmail == currentOwner }
        val ownerGroupNames = ownerEntities.map { it.entityGroup }.toSet()
        val ownerGroups = groups.filter { it.entityGroup in ownerGroupNames }

        // Owner's datasets
        val ownerDatasets = ownerEntities.map { it.linkedDatasetName }.toSet()

        // Open Critical/Error failures for this owner
        val ownerFailures = alerts.filter {
            it.ownerEmail == currentOwner &&
                    it.evaluationStatus == "fail" &&
                    it.severity in listOf("Critical", "Error")
        }

        // Pattern detection (same logic as Stage3)
        val patternType = when {
            ownerFailures.size > 2 -> "owner_overload"
            else -> "isolated"
        }

        // Build per-group data
        val portfolioGroups = ownerGroups.map { group ->
            val groupEntities = ownerEntities.filter { it.entityGroup == group.entityGroup }
            val groupDatasets = groupEntities.map { it.linkedDatasetName }.toSet()

            val groupAlerts = alerts.filter { it.datasetName in groupDatasets }
            val passing = groupAlerts.count { it.evaluationStatus == "pass" }
            val total = groupAlerts.size
            val health = if (total > 0) passing.toFloat() / total else 1.0f

            val datasets = groupDatasets.map { ds ->
                val hasFailure = alerts.any { it.datasetName == ds && it.evaluationStatus == "fail" }
                PortfolioDataset(ds, hasFailure)
            }.sortedBy { it.datasetName }

            PortfolioGroup(
                groupName = group.entityGroup,
                description = group.description,
                healthScore = health,
                datasets = datasets
            )
        }

        // Overall health (weighted average by dataset count)
        val totalDatasets = portfolioGroups.sumOf { it.datasets.size }
        val overallHealth = if (totalDatasets > 0) {
            portfolioGroups.map { it.healthScore * it.datasets.size }.sum() / totalDatasets
        } else 1.0f

        // Recent alerts (last 5 failing for owner's datasets)
        val recentAlerts = alerts
            .filter { it.datasetName in ownerDatasets && it.evaluationStatus == "fail" }
            .sortedByDescending { it.lastCheckRunTime }
            .take(5)
            .map { PortfolioAlert(it.datasetName, it.checkName, it.severity) }

        PortfolioData(
            ownerEmail = currentOwner,
            groupCount = ownerGroups.size,
            datasetCount = ownerDatasets.size,
            overallHealth = overallHealth,
            openCriticalError = ownerFailures.size,
            patternType = patternType,
            groups = portfolioGroups,
            recentAlerts = recentAlerts
        )
    } catch (e: Exception) {
        BugLogger.logError("Failed to load portfolio data", e)
        null
    }
}
