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
        engine: Engine
    ): UpstreamAnalysisState = withContext(Dispatchers.IO) {

        // Load enriched context
        val entities = loadEntities()
        val entity = entities.find { it.linkedDatasetName == alert.datasetName }
        
        val catalog = loadCatalog()
        val columns = catalog.filter { it.linkedDatasetName == alert.datasetName }
        val primaryColumn = columns.firstOrNull()
        
        val knowledge = loadKnowledge()
        val dimensionDef = knowledge.dimensions[alert.dimension]

        // Build deep technical prompt
        val prompt = buildString {
            appendLine("You are a Technical Data Architect investigating a data quality failure in a financial institution.")
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
            
            appendLine("=== ENTITY CONTEXT ===")
            appendLine("Entity Name: ${entity?.entityName ?: "Unknown"}")
            appendLine("Entity Group: ${entity?.entityGroup ?: "Unknown"}")
            appendLine("Description: ${entity?.description ?: "No description available"}")
            appendLine()
            
            appendLine("=== DQ DIMENSION ANALYSIS ===")
            appendLine("Dimension: ${alert.dimension}")
            appendLine("Definition: ${dimensionDef?.definition ?: "N/A"}")
            appendLine("Risk Type: ${dimensionDef?.riskType ?: "N/A"}")
            appendLine("Check Failed: ${alert.checkName}")
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

    private fun loadKnowledge(): DQKnowledge {
        return try {
            val content = GhostPaths.DQ_KNOWLEDGE.readText()
            json.decodeFromString(DQKnowledge.serializer(), content)
        } catch (e: Exception) {
            DQKnowledge(emptyMap(), emptyMap(), KPIThresholds(0.85f, 0.60f, emptyMap(), emptyList()))
        }
    }
}
