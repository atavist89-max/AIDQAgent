package com.llmtest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    private lateinit var pipelineManager: DQPipelineManager

    // State for UI
    private val currentStage = mutableStateOf(0)
    private val finalReport = mutableStateOf("")
    private val statusMessage = mutableStateOf("Waiting for DQ alerts...")
    private val hasPermission = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugLogger.initialize(this)
        
        // Check initial permission state
        hasPermission.value = hasStoragePermission()
        BugLogger.log("onCreate: hasPermission = ${hasPermission.value}")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!hasPermission.value) {
                        PermissionRequestScreen()
                    } else {
                        // Permission granted - initialize and show main UI
                        LaunchedEffect(Unit) {
                            initializeApp()
                        }
                        DQAgentScreen()
                    }
                }
            }
        }
        
        // Setup lifecycle observer to detect when user returns from permission settings
        setupPermissionObserver()
    }
    
    private fun setupPermissionObserver() {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newPermission = hasStoragePermission()
                BugLogger.log("ON_RESUME: permission changed to $newPermission")
                if (newPermission != hasPermission.value) {
                    hasPermission.value = newPermission
                    if (newPermission) {
                        // User just granted permission - initialize app
                        lifecycleScope.launch {
                            initializeApp()
                        }
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
    }
    
    private suspend fun initializeApp() {
        withContext(Dispatchers.IO) {
            try {
                // Check if data files exist
                if (!GhostPaths.isDQDataAvailable()) {
                    BugLogger.log("WARNING: DQ data not available at ${GhostPaths.DQ_DATA_DIR}")
                }
                if (!GhostPaths.isModelAvailable()) {
                    BugLogger.log("WARNING: Model not available at ${GhostPaths.MODEL_FILE}")
                }
                
                // Initialize engine
                initEngine()
                pipelineManager = DQPipelineManager(engine!!)
                
                // Start file polling
                startFilePolling()
                
                withContext(Dispatchers.Main) {
                    statusMessage.value = "Ready. Waiting for alerts..."
                }
            } catch (e: Exception) {
                BugLogger.logError("Failed to initialize app", e)
            }
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
            Text(
                "DQ Agent",
                style = MaterialTheme.typography.headlineLarge
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                contentDescription = "Permission Required",
                modifier = Modifier.size(64.dp),
                tint = Color.Yellow
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "Permission Required",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "This app needs 'All Files Access' permission to read:\n" +
                "• The AI model (gemma-4-e2b.litertlm)\n" +
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
                "After granting, return to this app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                BugLogger.logError("Failed to open permission settings", e)
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
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
                delay(5000) // Check every 5 seconds
            }
        }
    }

    private suspend fun checkForNewAlerts() {
        val inputFile = GhostPaths.inputFile()
        if (!inputFile.exists()) return

        try {
            val json = Json { ignoreUnknownKeys = true }
            val alert = json.decodeFromString(DQAlert.serializer(), inputFile.readText())

            // Move file to prevent reprocessing
            inputFile.delete()

            withContext(Dispatchers.Main) {
                statusMessage.value = "Processing ${alert.datasetName}..."
                pipelineManager.startAnalysis(alert)
            }
        } catch (e: Exception) {
            BugLogger.logError("Failed to process input file", e)
        }
    }

    @Composable
    fun DQAgentScreen() {
        val stage by pipelineManager.currentStage.collectAsState()
        val report by pipelineManager.finalReport.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "DQ Agent",
                style = MaterialTheme.typography.headlineMedium
            )

            // Status indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = when (stage) {
                            0 -> Color.Gray
                            1, 2, 3, 4 -> Color.Yellow
                            5 -> if (report.contains("Error")) Color.Red else Color.Green
                            else -> Color.Gray
                        },
                        shape = CircleShape
                    )
            )

            // Stage indicator
            Text(
                when (stage) {
                    0 -> statusMessage.value
                    1 -> "Stage 1: Triage Analysis..."
                    2 -> "Stage 2: Building Context..."
                    3 -> "Stage 3: Pattern Detection..."
                    4 -> "Stage 4: AI Synthesis..."
                    5 -> "Analysis Complete"
                    else -> "Unknown"
                },
                style = MaterialTheme.typography.bodyLarge
            )

            // Progress bar
            if (stage in 1..4) {
                LinearProgressIndicator(
                    progress = { stage / 4f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Final report card
            if (stage == 5 && report.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Analysis Report",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            report,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Debug buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { /* manual trigger for testing */ }) {
                    Text("Test")
                }
                Button(onClick = { 
                    val logs = BugLogger.readLog()
                    Toast.makeText(this@MainActivity, logs.take(200), Toast.LENGTH_LONG).show()
                }) {
                    Text("Logs")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::pipelineManager.isInitialized) {
            pipelineManager.cancel()
        }
        engine?.close()
    }
}
