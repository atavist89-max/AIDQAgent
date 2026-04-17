package com.llmtest

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage1Triage {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun run(alert: DQAlert): AnalysisState {
        // Load reports to check downstream impact
        val reports = loadReports()
        val affectedReports = reports.filter { it.dataSources.contains(alert.datasetName) }
        val hasExecutiveReport = affectedReports.any { it.reportClass == "2" }
        
        // Decision logic
        val decision = when (alert.severity) {
            "Critical", "Error" -> "FULL_ANALYSIS"
            "Warning" -> if (hasExecutiveReport) "FULL_ANALYSIS" else "MINIMAL"
            "Informative" -> if (hasExecutiveReport) "FULL_ANALYSIS" else "MINIMAL"
            else -> "MINIMAL"
        }
        
        val state = AnalysisState(
            stage = 1,
            alertDataset = alert.datasetName,
            decision = decision
        )
        
        // Save state to file
        GhostPaths.stateFile(1).writeText(json.encodeToString(AnalysisState.serializer(), state))
        BugLogger.log("Stage 1 complete: $decision")
        
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
