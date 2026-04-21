package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage4UpstreamResearcher {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class UpstreamAnalysisState(
        val stage: Int = 41,
        val alertDataset: String,
        val upstreamReport: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun run(
        alert: DQAlert,
        stage3State: AnalysisState,
        engine: Engine,
        feedback: List<String> = emptyList()
    ): UpstreamAnalysisState = withContext(Dispatchers.IO) {

        val config = GovernanceConfig.getStage4aConfig()

        // Load enriched context (conditionally)
        val entities = if ("entities.json" in config.availableJsons) loadEntities() else emptyList()
        val entity = entities.find { it.linkedDatasetName == alert.datasetName }
        
        val catalog = if ("catalog_columns.json" in config.availableJsons) loadCatalog() else emptyList()
        val columns = catalog.filter { it.linkedDatasetName == alert.datasetName }
        val primaryColumn = columns.firstOrNull()
        
        val knowledge = if ("dq_knowledge.json" in config.availableJsons) loadKnowledge() else null
        val dimensionDef = knowledge?.dimensions?.get(alert.dimension)
        
        // Load group context for governance hierarchy (conditionally)
        val allAlerts = if ("dq_alerts.json" in config.availableJsons) loadAlerts() else emptyList()
        val currentGroup = entity?.entityGroup
        val groupDatasetsList = entities
            .filter { it.entityGroup == currentGroup }
            .map { it.linkedDatasetName }
            .toSet()
        val groupAlerts = allAlerts.filter { groupDatasetsList.contains(it.datasetName) }
        val groupPassing = groupAlerts.count { it.evaluationStatus == "pass" }
        val groupTotal = groupAlerts.size
        val healthScore = if (groupTotal > 0) groupPassing.toFloat() / groupTotal else 1.0f
        val groupDatasets = groupDatasetsList.size

        // Read system prompt from governance config (editable in Governance tab)
        val systemPrompt = GovernanceConfig.getStationPrompt("stage4a")?.prompt
            ?: "You are a Technical Data Architect investigating a data quality failure."

        // Build deep technical prompt
        val prompt = buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("=== INVESTIGATION TARGET ===")
            appendLine("Dataset: ${alert.datasetName}")
            appendLine("Source System: ${alert.datasourceName} (${alert.datasourceType})")
            if (primaryColumn != null) {
                appendLine("Database: ${primaryColumn.sourceDB}")
                appendLine("Table: ${primaryColumn.sourceTable}")
                appendLine("Column: ${primaryColumn.name}")
                appendLine("Business Definition: ${primaryColumn.definition}")
            }
            appendLine()
            
            if ("entities.json" in config.availableJsons) {
                appendLine("=== ENTITY CONTEXT ===")
                appendLine("Entity Name: ${entity?.entityName ?: "Unknown"}")
                appendLine("Entity Group: ${entity?.entityGroup ?: "Unknown"}")
                appendLine("Description: ${entity?.description ?: "No description available"}")
                appendLine()
            }
            
            if ("dq_knowledge.json" in config.availableJsons) {
                appendLine("=== DQ DIMENSION ANALYSIS ===")
                appendLine("Dimension: ${alert.dimension}")
                appendLine("Definition: ${dimensionDef?.definition ?: "N/A"}")
                appendLine("Risk Type: ${dimensionDef?.riskType ?: "N/A"}")
                appendLine("Check Failed: ${alert.checkName}")
                appendLine()
            }
            
            if ("entities.json" in config.availableJsons || "dq_alerts.json" in config.availableJsons) {
                appendLine("=== GOVERNANCE HIERARCHY ===")
                appendLine("Technical Layer: ${alert.datasourceName} (${alert.datasourceType}) → ${primaryColumn?.sourceDB}.${primaryColumn?.sourceTable}")
                appendLine("Business Layer: ${entity?.entityName} → Group: ${entity?.entityGroup}")
                appendLine("Organizational Layer: Group ${entity?.entityGroup} contains ${groupDatasets} datasets")
                appendLine("Group Health: ${(healthScore * 100).toInt()}% (${groupPassing}/${groupTotal} passing)")
                appendLine()
            }
            
            appendLine("=== INSTRUCTION ===")
            appendLine("You are investigating a Technical Data Architect.")
            appendLine("This dataset belongs to the ${entity?.entityGroup} business function.")
            appendLine("Analyze ${alert.datasourceName} as infrastructure supporting ${groupDatasets} datasets in this group.")
            appendLine("If this is a source system failure, estimate how many other group datasets are at risk.")
            appendLine("Do not treat this as an isolated table failure — assess group-wide technical impact.")
            appendLine()
            
            appendLine("=== YOUR TASK ===")
            appendLine("Write a Technical Data Steward Briefing (200-250 words) covering:")
            appendLine()
            appendLine("1. BUSINESS PURPOSE: What is this data? What business process depends on it? Why does this entity/column exist?")
            appendLine()
            appendLine("2. TECHNICAL ARCHITECTURE: Analyze ${alert.datasourceName} as a source system. What are typical failure patterns for ${alert.datasourceType} systems? Reliability considerations.")
            appendLine()
            appendLine("3. DIMENSION CONTEXT: What does '${alert.dimension}' specifically mean for THIS dataset? Contextualize the risk (not generic definition).")
            appendLine()
            appendLine("4. ROOT CAUSE HYPOTHESIS: Based on source system (${alert.datasourceName}) and check type (${alert.checkName}), what is the most likely technical cause? Schema drift? API change? ETL timing issue? State confidence level.")
            appendLine()
            appendLine("5. INVESTIGATION PATH: Specific technical steps to confirm hypothesis (SQL queries, system checks, validation steps).")
            appendLine()
            appendLine("RULES:")
            appendLine("- Use actual system names, table names, and database names provided in the data")
            appendLine("- Be specific and technical but explain business relevance")
            appendLine("- 200-250 words, professional technical briefing format")
            appendLine("- Do not hedge—state conclusions with confidence levels")

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

            if (feedback.isNotEmpty()) {
                appendLine()
                appendLine("=== GOVERNANCE FEEDBACK ===")
                appendLine("Your previous output was rejected by Governance. Address this feedback before rewriting:")
                feedback.forEach { bullet ->
                    appendLine("- $bullet")
                }
            }
        }

        BugLogger.log("Stage 4a prompt length: ${prompt.length} chars")

        // Call Gemma
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
            BugLogger.logError("Stage 4a LLM failed", e)
            "TECHNICAL BRIEFING UNAVAILABLE: ${alert.datasourceName} system analysis failed. Manual investigation required for ${alert.datasetName}."
        }

        val state = UpstreamAnalysisState(
            alertDataset = alert.datasetName,
            upstreamReport = response.trim()
        )

        GhostPaths.DQ_STATE_DIR.resolve("stage4a.json").writeText(
            json.encodeToString(UpstreamAnalysisState.serializer(), state)
        )
        
        BugLogger.log("Stage 4a complete: ${response.length} chars")
        state
    }

    private fun loadEntities(): List<Entity> {
        return try {
            val content = GhostPaths.ENTITIES.readText()
            json.decodeFromString(ListSerializer(Entity.serializer()), content)
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

    private fun loadAlerts(): List<DQAlert> {
        return try {
            val content = GhostPaths.DQ_ALERTS.readText()
            json.decodeFromString(ListSerializer(DQAlert.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadKnowledge(): DQKnowledge {
        return try {
            val content = GhostPaths.DQ_KNOWLEDGE.readText()
            json.decodeFromString(DQKnowledge.serializer(), content)
        } catch (e: Exception) {
            DQKnowledge(emptyMap(), emptyMap(), KPIThresholds(0.85f, 0.60f, emptyMap(), emptyList()))
        }
    }

    private fun StringBuilder.appendRawStageOutput(label: String, stageNum: Int) {
        try {
            val file = GhostPaths.stateFile(stageNum)
            if (file.exists()) {
                appendLine()
                appendLine("=== $label RAW OUTPUT ===")
                appendLine(file.readText())
            }
        } catch (_: Exception) { }
    }
}
