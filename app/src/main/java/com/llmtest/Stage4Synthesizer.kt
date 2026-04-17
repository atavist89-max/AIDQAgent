package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

object Stage4Synthesizer {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class FinalSynthesisState(
        val stage: Int = 43,
        val alertDataset: String,
        val finalReport: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun run(
        alert: DQAlert,
        stage4a: Stage4UpstreamResearcher.UpstreamAnalysisState,
        stage4b: Stage4DownstreamResearcher.DownstreamAnalysisState,
        stage3State: AnalysisState,
        engine: Engine
    ): FinalSynthesisState = withContext(Dispatchers.IO) {

        // Load severity info
        val knowledge = loadKnowledge()
        val severityRule = knowledge.severityRules[alert.severity]
        val dimensionDef = knowledge.dimensions[alert.dimension]

        // Parse pattern for systemic context
        val patternLines = stage3State.patternResult?.split("\n") ?: emptyList()
        val ownerLoad = patternLines.find { it.contains("OWNER_LOAD") }?.substringAfter(":")?.trim() ?: "Unknown"
        val healthScore = patternLines.find { it.contains("GROUP_HEALTH") }?.substringAfter(":")?.trim() ?: "Unknown"
        val patternType = patternLines.find { it.contains("PATTERN:") }?.substringAfter(":")?.trim() ?: "isolated"

        // Build synthesis prompt
        val prompt = buildString {
            appendLine("You are a Senior Data Steward delivering an executive briefing to the Chief Data Officer.")
            appendLine()
            appendLine("You have received two specialist research reports:")
            appendLine()
            appendLine("=== TECHNICAL ANALYSIS (from Upstream Researcher) ===")
            appendLine(stage4a.upstreamReport.take(800)) // Truncate if too long
            appendLine()
            appendLine("=== BUSINESS IMPACT ANALYSIS (from Downstream Researcher) ===")
            appendLine(stage4b.downstreamReport.take(800)) // Truncate if too long
            appendLine()
            appendLine("=== SYSTEMIC CONTEXT ===")
            appendLine("Alert Owner: ${alert.ownerEmail}")
            appendLine("Owner Workload: $ownerLoad other Critical failures")
            appendLine("Entity Group Health: $healthScore")
            appendLine("Pattern Detected: $patternType")
            appendLine("KPI Resolution Target: ${severityRule?.kpiTargetDays ?: "N/A"} business days")
            appendLine("DQ Dimension: ${alert.dimension} (${dimensionDef?.definition?.take(50)}...)")
            appendLine()
            
            appendLine("=== YOUR TASK: EXECUTIVE STEWARDSHIP REPORT ===")
            appendLine("Synthesize the above research into a compelling executive narrative (350-400 words) with this structure:")
            appendLine()
            appendLine("**SITUATION** (1 paragraph)")
            appendLine("Hook: What broke and why it matters RIGHT NOW. One sentence urgency statement.")
            appendLine()
            appendLine("**TECHNICAL ANALYSIS** (1 paragraph)")
            appendLine("Translate technical findings into business language. Root cause with confidence. Mechanism of failure.")
            appendLine()
            appendLine("**BUSINESS IMPACT** (1 paragraph)")
            appendLine("Who is affected, by when, and with what consequence? Decision-makers who will act on bad data.")
            appendLine()
            appendLine("**SYSTEMIC CONTEXT** (1 paragraph)")
            appendLine("Connect to owner workload pattern. Isolated incident or governance gap? Historical pattern insight.")
            appendLine()
            appendLine("**ACTIONABLE RECOMMENDATIONS** (bullet list)")
            appendLine("- Immediate (next 4 hours): Specific person to call, specific action")
            appendLine("- Short-term (today): Fix strategy with technical approach")
            appendLine("- Governance (this week): Process improvement to prevent recurrence")
            appendLine()
            appendLine("**RISK IF UNADDRESSED** (1 paragraph)")
            appendLine("What happens if this sits for ${severityRule?.kpiTargetDays} days? Cascade effects?")
            appendLine()
            appendLine("TONE: Urgent but authoritative. Expert investigator briefing leadership. No hedging—state conclusions.")
            appendLine("Use actual names from the research reports. Make it feel like a senior steward who investigated for 2 hours.")
        }

        BugLogger.log("Stage 4c prompt length: ${prompt.length} chars")

        // Call Gemma
        val response = try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(temperature = 0.4, topK = 40, topP = 0.9)
                )
            )
            conversation.use {
                it.sendMessage(Message.of(prompt)).toString()
            }
        } catch (e: Exception) {
            BugLogger.logError("Stage 4c LLM failed", e)
            buildFallbackReport(alert, stage4a, stage4b, severityRule?.kpiTargetDays.toString())
        }

        val state = FinalSynthesisState(
            alertDataset = alert.datasetName,
            finalReport = response.trim()
        )

        GhostPaths.DQ_STATE_DIR.resolve("stage4c.json").writeText(
            json.encodeToString(FinalSynthesisState.serializer(), state)
        )
        
        BugLogger.log("Stage 4c complete: ${response.length} chars")
        state
    }

    private fun buildFallbackReport(
        alert: DQAlert,
        stage4a: Stage4UpstreamResearcher.UpstreamAnalysisState,
        stage4b: Stage4DownstreamResearcher.DownstreamAnalysisState,
        kpiDays: String
    ): String {
        return """
            **SITUATION**
            Critical data quality failure in ${alert.datasetName} requires immediate attention.
            
            **TECHNICAL ANALYSIS**
            ${stage4a.upstreamReport.take(200)}...
            
            **BUSINESS IMPACT**
            ${stage4b.downstreamReport.take(200)}...
            
            **ACTIONABLE RECOMMENDATIONS**
            - Immediate: Contact ${alert.ownerEmail}
            - Target: $kpiDays business days
            
            (AI synthesis failed - using truncated research summaries)
        """.trimIndent()
    }

    private fun loadKnowledge(): DQKnowledge {
        return try {
            val content = GhostPaths.DQ_KNOWLEDGE.readText()
            json.decodeFromString(DQKnowledge.serializer(), content)
        } catch (e: Exception) {
            DQKnowledge(emptyMap(), emptyMap(), KPIThresholds(0.85f, 0.60f, emptyMap(), emptyList()))
        }
    }
}
