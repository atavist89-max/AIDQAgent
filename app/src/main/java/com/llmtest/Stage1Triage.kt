package com.llmtest

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage1Triage {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun run(alert: DQAlert): AnalysisState {
        val config = GovernanceConfig.getStage1Config()
        
        // Load reports to check downstream impact (if allowed)
        val reports = if ("reports.json" in config.availableJsons) loadReports() else emptyList()
        val affectedReports = reports.filter { it.dataSources.contains(alert.datasetName) }
        val hasExecutiveReport = affectedReports.any { it.reportClass.toIntOrNull() ?: 0 >= config.requiredDownstreamClass }
        
        // Severity ranking for comparison
        val severityRank = mapOf("Informative" to 0, "Warning" to 1, "Error" to 2, "Critical" to 3)
        val alertRank = severityRank[alert.severity] ?: 0
        val thresholdRank = severityRank[config.severityThreshold] ?: 1
        
        // Decision logic driven by config
        val decision = when {
            alertRank >= thresholdRank -> "FULL_ANALYSIS"
            hasExecutiveReport -> "FULL_ANALYSIS"
            alert.dimension in config.dimensionBypass -> "FULL_ANALYSIS"
            else -> "MINIMAL"
        }
        
        val state = AnalysisState(
            stage = 1,
            alertDataset = alert.datasetName,
            decision = decision
        )
        
        // Save state to file
        GhostPaths.stateFile(1).writeText(json.encodeToString(AnalysisState.serializer(), state))
        BugLogger.log("Stage 1 complete: $decision (threshold=${config.severityThreshold}, class>=${config.requiredDownstreamClass}, bypass=${config.dimensionBypass})")
        
        return state
    }
    
    private fun loadReports(): List<Report> {
        return try {
            val content = GhostPaths.REPORTS.readText()
            json.decodeFromString(ListSerializer(Report.serializer()), content)
        } catch (e: Exception) {
            BugLogger.logError("Failed to load reports", e)
            emptyList()
        }
    }
}
