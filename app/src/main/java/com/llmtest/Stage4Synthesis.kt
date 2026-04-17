package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        
        // Build minimal prompt (under 2000 tokens)
        val prompt = buildString {
            appendLine("Analyze this data quality alert:")
            appendLine()
            appendLine("ALERT: ${alert.checkName}")
            appendLine("DATASET: ${alert.datasetName}")
            appendLine("SEVERITY: ${alert.severity} (target: ${severityRule?.kpiTargetDays} days)")
            appendLine("DIMENSION: ${alert.dimension} - ${dimensionDef?.definition?.take(80)}")
            appendLine()
            appendLine("CONTEXT:")
            appendLine(stage3State.contextSummary?.take(500) ?: "N/A")
            appendLine()
            appendLine("PATTERNS:")
            appendLine(stage3State.patternResult?.take(300) ?: "None detected")
            appendLine()
            appendLine("Respond in exactly 4 lines:")
            appendLine("IMPACT: [one sentence business impact]")
            appendLine("ROOT_CAUSE: [one sentence probable cause]")
            appendLine("ACTION: [one specific fix]")
            appendLine("URGENCY: [hours or days until KPI target]")
        }
        
        // Call Gemma (with 10s delay handled by caller)
        val response = try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(temperature = 0.3, topK = 40, topP = 0.9)
                )
            )
            
            conversation.use {
                it.sendMessage(Message.of(prompt)).toString()
            }
        } catch (e: Exception) {
            BugLogger.logError("LLM failed in Stage 4", e)
            "IMPACT: High - affects reporting accuracy\nROOT_CAUSE: Unknown - manual investigation required\nACTION: Contact owner ${alert.ownerEmail}\nURGENCY: ${severityRule?.kpiTargetDays} days per KPI"
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
