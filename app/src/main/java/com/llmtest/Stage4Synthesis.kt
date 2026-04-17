package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage4Synthesis {
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun run(
        alert: DQAlert, 
        stage3State: AnalysisState,
        engine: Engine
    ): AnalysisState = withContext(Dispatchers.IO) {
        
        // Load knowledge base for severity rules
        val knowledge = loadKnowledge()
        val severityRule = knowledge.severityRules[alert.severity]
        val dimensionDef = knowledge.dimensions[alert.dimension]
        
        // Load reports and catalog for this dataset
        val reports = loadReportsForDataset(alert.datasetName)
        val catalog = loadCatalogForDataset(alert.datasetName)
        
        // Extract structured pattern data
        val ownerLoad = stage3State.ownerLoad ?: 0
        val healthScore = stage3State.healthScore ?: 1.0f
        val patternType = stage3State.patternType ?: "isolated_incident"
        
        // Build enhanced prompt with few-shot example and constraints
        val prompt = buildString {
            appendLine("You are a senior data steward. Analyze this Critical alert using ONLY the provided data.")
            appendLine("Do NOT generalize. Do NOT use phrases like 'investigate the data' or 'check the source'.")
            appendLine()
            
            appendLine("=== PROVIDED DATA (Base your analysis ONLY on this) ===")
            appendLine("Alert: ${alert.datasetName}")
            appendLine("Check: ${alert.checkName}")
            appendLine("Severity: ${alert.severity} (Target: ${severityRule?.kpiTargetDays} business days)")
            appendLine("Dimension: ${alert.dimension} - ${dimensionDef?.definition?.take(60)}")
            appendLine("Owner: ${alert.ownerEmail}")
            appendLine()
            
            appendLine("AFFECTED REPORTS:")
            if (reports.isNotEmpty()) {
                reports.forEach { appendLine("- ${it.reportName} (Class ${it.reportClass}): ${it.reportDescription.take(60)}") }
            } else {
                appendLine("- Unknown downstream consumers")
            }
            appendLine()
            
            appendLine("TECHNICAL SOURCE:")
            val col = catalog.firstOrNull()
            if (col != null) {
                appendLine("- Database: ${col.sourceDB}")
                appendLine("- Table: ${col.sourceTable}")
                appendLine("- System: ${alert.datasourceName} (${alert.datasourceType})")
            } else {
                appendLine("- System: ${alert.datasourceName}")
            }
            appendLine()
            
            appendLine("PATTERN CONTEXT:")
            appendLine("- Owner open failures: $ownerLoad")
            appendLine("- Entity group health: ${(healthScore * 100).toInt()}%")
            appendLine("- Pattern type: $patternType")
            appendLine()
            
            appendLine("=== EXAMPLE OF CORRECT ANALYSIS ===")
            appendLine("ALERT: SCHEMA_DRIFT in DIM_PRODUCT | Reports: Product Catalog Health (Class 1), Regional Sales (Class 2) | Source: inventory_api")
            appendLine()
            appendLine("CORRECT OUTPUT:")
            appendLine("IMPACT: Product Catalog Health will show invalid codes to category managers. Regional Sales Dashboard (executive) may aggregate incorrectly.")
            appendLine("ROOT_CAUSE: inventory_api schema change 6 hours ago. Pattern shows 2 other inventory_api failures—systemic change management breach.")
            appendLine("ACTION: Contact inventory_api owner immediately. Revert API schema. Escalate to Data Quality Coordinator.")
            appendLine("URGENCY: Critical—1 business day target. Executive dashboard runs in 18 hours.")
            appendLine()
            
            appendLine("=== RULES (Follow exactly) ===")
            appendLine("1. IMPACT: Name SPECIFIC reports from list, or state 'Unknown downstream impact'")
            appendLine("2. ROOT_CAUSE: Name SPECIFIC system (${alert.datasourceName}) or state 'Unknown upstream source'")
            appendLine("3. ACTION: Name SPECIFIC contact/escalation target, or state 'Unknown escalation target'")
            appendLine("4. NEVER say 'investigate the data' or 'check the source'—name WHO/WHERE specifically")
            appendLine("5. If owner_overload pattern detected (>2 failures), mention 'systemic governance gap, not isolated incident'")
            appendLine()
            
            appendLine("Respond in EXACT format:")
            appendLine("IMPACT: [specific report names] will [specific consequence], or 'Unknown'")
            appendLine("ROOT_CAUSE: [specific system] [specific change type], or 'Unknown'")
            appendLine("ACTION: [specific contact/escalation], or 'Unknown'")
            appendLine("URGENCY: [severity]—${severityRule?.kpiTargetDays} business day target. [time implication]")
        }
        
        // Call Gemma with low temperature for consistency
        val response = try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(temperature = 0.2f, topK = 40, topP = 0.9)
                )
            )
            
            conversation.use {
                it.sendMessage(Message.of(prompt)).toString()
            }
        } catch (e: Exception) {
            BugLogger.logError("LLM failed in Stage 4", e)
            buildFallbackReport(alert, reports, severityRule, patternType)
        }
        
        val state = stage3State.copy(
            stage = 4,
            finalReport = response.trim()
        )
        
        GhostPaths.stateFile(4).writeText(json.encodeToString(AnalysisState.serializer(), state))
        BugLogger.log("Stage 4 complete")
        
        state
    }
    
    private fun loadKnowledge(): DQKnowledge {
        return try {
            val content = GhostPaths.DQ_KNOWLEDGE.readText()
            json.decodeFromString(DQKnowledge.serializer(), content)
        } catch (e: Exception) {
            BugLogger.logError("Failed to load knowledge", e)
            DQKnowledge(emptyMap(), emptyMap(), KPIThresholds(0.85f, 0.6f, emptyMap(), emptyList()))
        }
    }
    
    private fun loadReportsForDataset(datasetName: String): List<Report> {
        return try {
            val content = GhostPaths.REPORTS.readText()
            val allReports = json.decodeFromString(ListSerializer(Report.serializer()), content)
            allReports.filter { it.dataSources.contains(datasetName) }
        } catch (e: Exception) {
            BugLogger.logError("Failed to load reports for dataset $datasetName", e)
            emptyList()
        }
    }
    
    private fun loadCatalogForDataset(datasetName: String): List<CatalogColumn> {
        return try {
            val content = GhostPaths.CATALOG.readText()
            val allColumns = json.decodeFromString(ListSerializer(CatalogColumn.serializer()), content)
            allColumns.filter { it.linkedDatasetName == datasetName }
        } catch (e: Exception) {
            BugLogger.logError("Failed to load catalog for dataset $datasetName", e)
            emptyList()
        }
    }
    
    private fun buildFallbackReport(
        alert: DQAlert,
        reports: List<Report>,
        severityRule: SeverityRule?,
        patternType: String
    ): String {
        val reportNames = reports.take(2).joinToString(", ") { it.reportName }
        val impact = if (reportNames.isNotEmpty()) {
            "IMPACT: $reportNames may be affected by ${alert.checkName}"
        } else {
            "IMPACT: Unknown downstream impact from ${alert.checkName}"
        }
        
        val rootCause = "ROOT_CAUSE: ${alert.datasourceName} (${alert.datasourceType})—${alert.dimension} failure detected"
        
        val governanceNote = if (patternType == "owner_overload") {
            " Owner shows systemic governance gap."
        } else ""
        
        val action = "ACTION: Contact ${alert.ownerEmail}. Request immediate status on ${alert.datasourceName}.${governanceNote}"
        
        val urgency = "URGENCY: ${alert.severity}—${severityRule?.kpiTargetDays} business day target. KPI clock running."
        
        return "$impact\n$rootCause\n$action\n$urgency"
    }
}

@kotlinx.serialization.Serializable
data class DQKnowledge(
    val severityRules: Map<String, SeverityRule>,
    val dimensions: Map<String, DimensionDef>,
    val kpiThresholds: KPIThresholds
)

@kotlinx.serialization.Serializable
data class SeverityRule(
    val kpiTargetDays: Int?,
    val businessCriticality: String,
    val description: String
)

@kotlinx.serialization.Serializable
data class DimensionDef(
    val definition: String,
    val riskType: String
)

@kotlinx.serialization.Serializable
data class KPIThresholds(
    val healthScoreGood: Float,
    val healthScoreCaution: Float,
    val healthScoreDescriptions: Map<String, String>,
    val aggregationLevels: List<String>
)
