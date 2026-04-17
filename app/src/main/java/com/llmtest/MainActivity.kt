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
                        else -> DQAgentScreen()
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
            Text("DQ Agent", style = MaterialTheme.typography.headlineLarge)
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
    fun DQAgentScreen() {
        val pm = pipelineManager ?: return
        
        val stage by pm.currentStage.collectAsState()
        val s1 by pm.stage1Output.collectAsState()
        val s2 by pm.stage2Output.collectAsState()
        val s3 by pm.stage3Output.collectAsState()
        val s4 by pm.stage4Output.collectAsState()
        val upstream by pm.upstreamReport.collectAsState()
        val downstream by pm.downstreamReport.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "DQ Agent - Stage ${if (stage >= 41) "4/4" else if (stage >= 1) "$stage/4" else "0/4"}",
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

            if (stage >= 41) {
                StageBox(
                    title = "Stage 4a: Upstream Researcher",
                    content = upstream.take(150) + if (upstream.length > 150) "..." else "",
                    isActive = stage == 41,
                    isComplete = stage > 41
                )
            }

            if (stage >= 42) {
                StageBox(
                    title = "Stage 4b: Downstream Researcher",
                    content = downstream.take(150) + if (downstream.length > 150) "..." else "",
                    isActive = stage == 42,
                    isComplete = stage > 42
                )
            }

            if (stage >= 43) {
                StageBox(
                    title = "Stage 4c: Synthesizer",
                    content = "Executive Report: " + s4.take(100) + if (s4.length > 100) "..." else "",
                    isActive = stage == 43,
                    isComplete = stage == 5
                )
            }

            if (stage == 5 && s4.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Executive Stewardship Report",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            s4,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
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
    fun StageBox(title: String, content: String, isActive: Boolean, isComplete: Boolean) {
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
                if (content.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (isComplete && title.contains("Synthesis")) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
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
