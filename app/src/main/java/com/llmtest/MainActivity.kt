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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BugLogger.initialize(this)
        
        // Initialize engine
        initEngine()
        pipelineManager = DQPipelineManager(engine!!)
        
        // Start file polling
        startFilePolling()
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DQAgentScreen()
                }
            }
        }
    }
    
    private fun initEngine() {
        val config = EngineConfig(
            modelPath = GhostPaths.MODEL_FILE.absolutePath,
            backend = Backend.GPU(),
            maxNumTokens = 2048,
            cacheDir = cacheDir.path
        )
        engine = Engine(config)
        engine?.initialize()
    }
    
    private fun startFilePolling() {
        lifecycleScope.launch(Dispatchers.IO) {
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
                    0 -> "Waiting for alerts..."
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
                Button(onClick = { BugLogger.readLog() }) {
                    Text("Logs")
                }
            }
        }
    }
    
    private fun hasStoragePermission(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        BugLogger.log("hasStoragePermission() = $result")
        return result
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pipelineManager.cancel()
        engine?.close()
    }
}
