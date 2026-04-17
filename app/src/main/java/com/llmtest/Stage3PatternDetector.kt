package com.llmtest

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage3PatternDetector {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun run(alert: DQAlert, stage2State: AnalysisState): AnalysisState {
        // Load all alerts to check owner workload
        val allAlerts = loadAlerts()
        val ownerFailures = allAlerts.filter { 
            it.ownerEmail == alert.ownerEmail && 
            it.evaluationStatus == "fail" &&
            it.severity in listOf("Critical", "Error")
        }
        
        // Load entities for group health calculation
        val entities = loadEntities()
        val groupEntities = entities.filter { it.entityGroup == alert.datasourceName }
        
        val groupAlerts = allAlerts.filter { alert ->
            entities.any { it.linkedDatasetName == alert.datasetName && it.entityGroup == alert.datasourceName }
        }
        
        val groupPassing = groupAlerts.count { it.evaluationStatus == "pass" }
        val groupTotal = groupAlerts.size
        val healthScore = if (groupTotal > 0) groupPassing.toFloat() / groupTotal else 1.0f
        
        // Pattern detection
        val patternType = when {
            ownerFailures.size > 2 -> "owner_overload"
            healthScore < 0.6f -> "group_collapse"
            else -> "isolated_incident"
        }
        
        val pattern = when {
            ownerFailures.size > 2 -> "owner_overload: ${ownerFailures.size} Critical/Error failures"
            healthScore < 0.6f -> "group_collapse: ${(healthScore * 100).toInt()}% health"
            else -> "isolated_incident"
        }
        
        val patternResult = buildString {
            appendLine("PATTERN: $pattern")
            appendLine("OWNER_LOAD: ${ownerFailures.size} open failures")
            appendLine("GROUP_HEALTH: ${(healthScore * 100).toInt()}% ($groupPassing/$groupTotal)")
        }
        
        val state = stage2State.copy(
            stage = 3,
            patternResult = patternResult,
            ownerLoad = ownerFailures.size,
            healthScore = healthScore,
            patternType = patternType
        )
        
        GhostPaths.stateFile(3).writeText(json.encodeToString(AnalysisState.serializer(), state))
        BugLogger.log("Stage 3 complete: $pattern")
        
        return state
    }
    
    private fun loadAlerts(): List<DQAlert> {
        return try {
            val content = GhostPaths.DQ_ALERTS.readText()
            json.decodeFromString(ListSerializer(DQAlert.serializer()), content)
        } catch (e: Exception) {
            BugLogger.logError("Failed to load alerts", e)
            emptyList()
        }
    }
    
    private fun loadEntities(): List<Entity> {
        return try {
            val content = GhostPaths.ENTITIES.readText()
            json.decodeFromString(ListSerializer(Entity.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
