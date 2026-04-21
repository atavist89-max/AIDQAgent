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

        val config = GovernanceConfig.getStage4cConfig()

        // Load severity info (conditionally)
        val knowledge = if ("dq_knowledge.json" in config.availableJsons) loadKnowledge() else null
        val severityRule = knowledge?.severityRules?.get(alert.severity)
        val dimensionDef = knowledge?.dimensions?.get(alert.dimension)

        // Load governance context (conditionally)
        val entities = if ("entities.json" in config.availableJsons) loadEntities() else emptyList()
        val entity = entities.find { it.linkedDatasetName == alert.datasetName }
        val entityGroups = if ("entity_groups.json" in config.availableJsons) loadEntityGroups() else emptyList()
        val group = entityGroups.find { it.entityGroup == entity?.entityGroup }
        
        val catalog = if ("catalog_columns.json" in config.availableJsons) loadCatalog() else emptyList()
        val primaryColumn = catalog.filter { it.linkedDatasetName == alert.datasetName }.firstOrNull()
        
        val reports = if ("reports.json" in config.availableJsons) loadReports() else emptyList()
        val affectedReports = reports.filter { it.dataSources.contains(alert.datasetName) }
        
        val allAlerts = if ("dq_alerts.json" in config.availableJsons) loadAlerts() else emptyList()
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
            appendLine("=== TECHNICAL ANALYSIS ===")
            appendLine(stage4a.upstreamReport.take(400))
            appendLine()
            appendLine("=== BUSINESS IMPACT ===")
            appendLine(stage4b.downstreamReport.take(400))
            appendLine()
            
            if ("entities.json" in config.availableJsons || "catalog_columns.json" in config.availableJsons || "reports.json" in config.availableJsons || "dq_alerts.json" in config.availableJsons) {
                appendLine("=== GOVERNANCE HIERARCHY ===")
                appendLine("Technical: ${alert.datasourceName} (${alert.datasourceType}) → ${primaryColumn?.sourceDB}.${primaryColumn?.sourceTable}")
                appendLine("Business: ${entity?.entityName} → ${entity?.entityGroup}")
                appendLine("Organizational: ${group?.ownerEmail} manages ${groupDatasets} datasets in ${entity?.entityGroup}")
                appendLine("Downstream: ${affectedReports.size} reports affected")
                appendLine("Group Health: ${(healthScore * 100).toInt()}%")
                appendLine()
            }
            
            appendLine("=== INSTRUCTION ===")
            appendLine("Synthesize cross-layer risk for CDO: technical root cause → business function → organizational capacity.")
            appendLine()
            appendLine("STRUCTURE:")
            appendLine("1. SITUATION: What broke and why it matters NOW")
            appendLine("2. TECHNICAL ROOT CAUSE: Source system failure with confidence")
            appendLine("3. BUSINESS IMPACT: ${entity?.entityGroup} degrading (${groupDatasets} datasets, ${groupTotal - groupPassing} failing)")
            appendLine("4. ORGANIZATIONAL RISK: ${group?.ownerEmail} capacity")
            appendLine("5. CASCADING THREAT: Next 24-48h if unresolved")
            appendLine("6. RECOMMENDATIONS: Immediate, short-term, governance")
            appendLine()
            appendLine("TONE: Urgent, authoritative. No hedging.")

            // Append raw previous stage outputs if selected
            if ("stage1.json" in config.availableJsons) {
                appendRawStageOutput("STAGE 1", 1)
            }
            if ("stage2.json" in config.availableJsons) {
                appendRawStageOutput("STAGE 2", 2)
            }
            if ("stage3.json" in config.availableJsons) {
                appendRawStageOutput("STAGE 3", 3)
            }
            if ("stage4a.json" in config.availableJsons) {
                appendRawStageOutput("STAGE 4A", 41)
            }
            if ("stage4b.json" in config.availableJsons) {
                appendRawStageOutput("STAGE 4B", 42)
            }

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

    private fun StringBuilder.appendRawStageOutput(label: String, stageNum: Int) {
        try {
            val file = GhostPaths.stateFile(stageNum)
            if (file.exists()) {
                appendLine()
                appendLine("=== $label RAW OUTPUT ===")
                appendLine(file.readText().take(500))
            }
        } catch (_: Exception) { }
    }
}
