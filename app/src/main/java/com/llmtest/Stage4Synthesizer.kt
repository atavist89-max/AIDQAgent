package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
        engine: Engine,
        feedback: List<String> = emptyList()
    ): FinalSynthesisState = withContext(Dispatchers.IO) {

        // Load severity info
        val knowledge = loadKnowledge()
        val severityRule = knowledge.severityRules[alert.severity]
        val dimensionDef = knowledge.dimensions[alert.dimension]

        // Load governance context
        val entities = loadEntities()
        val entity = entities.find { it.linkedDatasetName == alert.datasetName }
        val entityGroups = loadEntityGroups()
        val group = entityGroups.find { it.entityGroup == entity?.entityGroup }
        
        val catalog = loadCatalog()
        val primaryColumn = catalog.filter { it.linkedDatasetName == alert.datasetName }.firstOrNull()
        
        val reports = loadReports()
        val affectedReports = reports.filter { it.dataSources.contains(alert.datasetName) }
        
        val allAlerts = loadAlerts()
        val currentGroup = entity?.entityGroup
        val groupDatasetsList = entities
            .filter { it.entityGroup == currentGroup }
            .map { it.linkedDatasetName }
            .toSet()
        val groupAlerts = allAlerts.filter { groupDatasetsList.contains(it.datasetName) }
        val groupPassing = groupAlerts.count { it.evaluationStatus == "pass" }
        val groupTotal = groupAlerts.size
        val healthScore = stage3State.groupHealthScore ?: if (groupTotal > 0) groupPassing.toFloat() / groupTotal else 1.0f
        val groupDatasets = stage3State.groupDatasets ?: groupDatasetsList.size
        val ownerLoad = stage3State.ownerLoad ?: 0
        val patternType = stage3State.patternType ?: "isolated"

        // Read system prompt from governance config
        val systemPrompt = GovernanceConfig.getStationPrompt("stage4c")?.prompt
            ?: "You are a Senior Data Steward delivering an executive briefing to the Chief Data Officer."

        // Build synthesis prompt
        val prompt = buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("You have received two specialist research reports:")
            appendLine()
            appendLine("=== TECHNICAL ANALYSIS (from Upstream Researcher) ===")
            appendLine(stage4a.upstreamReport.take(800)) // Truncate if too long
            appendLine()
            appendLine("=== BUSINESS IMPACT ANALYSIS (from Downstream Researcher) ===")
            appendLine(stage4b.downstreamReport.take(800)) // Truncate if too long
            appendLine()
            appendLine("=== GOVERNANCE HIERARCHY ===")
            appendLine("Technical: ${alert.datasourceName} (${alert.datasourceType}) → ${primaryColumn?.sourceDB}.${primaryColumn?.sourceTable}")
            appendLine("Business: ${entity?.entityName} → ${entity?.entityGroup}")
            appendLine("Organizational: ${group?.ownerEmail} manages ${groupDatasets} datasets in ${entity?.entityGroup}")
            appendLine("Downstream: ${affectedReports.size} reports affected")
            appendLine("Group Health: ${(healthScore * 100).toInt()}%")
            appendLine()
            
            appendLine("=== INSTRUCTION ===")
            appendLine("You are a Senior Data Steward briefing the CDO.")
            appendLine("Synthesize cross-layer risk: connect technical root cause → business function → organizational capacity.")
            appendLine("Structure your narrative:")
            appendLine("1. SITUATION: What broke and why it matters NOW")
            appendLine("2. TECHNICAL ROOT CAUSE: Source system failure with confidence")
            appendLine("3. BUSINESS IMPACT: ${entity?.entityGroup} function degrading (${groupDatasets} datasets, ${groupTotal - groupPassing} failing)")
            appendLine("4. ORGANIZATIONAL RISK: ${group?.ownerEmail} capacity with multiple concurrent failures")
            appendLine("5. CASCADING THREAT: Predict next 24-48 hours if unresolved")
            appendLine("6. ACTIONABLE RECOMMENDATIONS: Immediate, short-term, governance")
            appendLine()
            
            appendLine("TONE: Urgent but authoritative. Expert investigator briefing leadership. No hedging—state conclusions.")
            appendLine("Use actual names from the research reports. Make it feel like a senior steward who investigated for 2 hours.")

            if (feedback.isNotEmpty()) {
                appendLine()
                appendLine("=== GOVERNANCE FEEDBACK ===")
                appendLine("Your previous output was rejected by Governance. Address this feedback before rewriting:")
                feedback.forEach { bullet ->
                    appendLine("- $bullet")
                }
            }
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

    private fun loadEntities(): List<Entity> {
        return try {
            val content = GhostPaths.ENTITIES.readText()
            json.decodeFromString(ListSerializer(Entity.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadEntityGroups(): List<EntityGroup> {
        return try {
            val content = GhostPaths.ENTITY_GROUPS.readText()
            json.decodeFromString(ListSerializer(EntityGroup.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadCatalog(): List<CatalogColumn> {
        return try {
            val content = GhostPaths.CATALOG.readText()
            json.decodeFromString(ListSerializer(CatalogColumn.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadReports(): List<Report> {
        return try {
            val content = GhostPaths.REPORTS.readText()
            json.decodeFromString(ListSerializer(Report.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadAlerts(): List<DQAlert> {
        return try {
            val content = GhostPaths.DQ_ALERTS.readText()
            json.decodeFromString(ListSerializer(DQAlert.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
