package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage4OwnerGuidance {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class OwnerGuidanceState(
        val stage: Int = 4,
        val alertDataset: String,
        val guidanceReport: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun run(
        alert: DQAlert,
        stage3State: AnalysisState,
        engine: Engine
    ): OwnerGuidanceState = withContext(Dispatchers.IO) {

        // Load governance context
        val knowledge = loadKnowledge()
        val severityRule = knowledge.severityRules[alert.severity]
        val dimensionDef = knowledge.dimensions[alert.dimension]

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
        val kpiTargetDays = severityRule?.kpiTargetDays

        // Build owner guidance prompt
        val prompt = buildString {
            appendLine("You are a Data Quality Operations Guide helping a data owner resolve a failure.")
            appendLine()
            appendLine("=== ALERT ===")
            appendLine("Dataset: ${alert.datasetName}")
            appendLine("Check: ${alert.checkName}")
            appendLine("Severity: ${alert.severity}")
            appendLine("Dimension: ${alert.dimension}")
            appendLine("Status: ${alert.evaluationStatus}")
            appendLine()

            appendLine("=== OWNER CONTEXT ===")
            appendLine("Owner: ${alert.ownerEmail}")
            appendLine("Entity: ${entity?.entityName ?: "Unknown"}")
            appendLine("Group: ${entity?.entityGroup ?: "Unknown"}")
            appendLine("Group Description: ${group?.description ?: "N/A"}")
            appendLine()

            appendLine("=== PATTERN DETECTION ===")
            appendLine("Pattern: $patternType")
            appendLine("Owner Load: $ownerLoad open Critical/Error failures")
            appendLine("Group Health: ${(healthScore * 100).toInt()}% ($groupPassing/$groupTotal passing)")
            appendLine("Group Datasets: $groupDatasets")
            appendLine()

            appendLine("=== TECHNICAL CONTEXT ===")
            appendLine("Source: ${alert.datasourceName} (${alert.datasourceType})")
            if (primaryColumn != null) {
                appendLine("Database: ${primaryColumn.sourceDB}")
                appendLine("Table: ${primaryColumn.sourceTable}")
                appendLine("Column: ${primaryColumn.name}")
                appendLine("Definition: ${primaryColumn.definition}")
            }
            appendLine()

            appendLine("=== DOWNSTREAM IMPACT ===")
            appendLine("Affected Reports: ${affectedReports.size}")
            affectedReports.forEach {
                appendLine("  - ${it.reportName} (Class ${it.reportClass}, Owner: ${it.reportOwner})")
            }
            appendLine()

            appendLine("=== KNOWLEDGE BASE ===")
            appendLine("Dimension Definition: ${dimensionDef?.definition ?: "N/A"}")
            appendLine("Severity Rule: ${severityRule?.description ?: "N/A"}")
            appendLine("Business Criticality: ${severityRule?.businessCriticality ?: "N/A"}")
            if (kpiTargetDays != null) appendLine("SLA Target: $kpiTargetDays days")
            appendLine()

            appendLine("=== YOUR TASK ===")
            appendLine("Write an Owner Guidance Report with exactly these 8 sections:")
            appendLine()
            appendLine("1. SITUATION: What broke and why it matters to THIS owner specifically.")
            appendLine("2. ROOT CAUSE HYPOTHESIS: Most likely cause based on pattern type, source system, and check name. State confidence.")
            appendLine("3. PRIORITIZATION REASONING: Why fix this before the owner's other $ownerLoad failures. Reference report classes and SLA.")
            appendLine("4. IMMEDIATE FIX STEPS: 3-5 concrete, ordered actions the owner can take now.")
            appendLine("5. VERIFICATION CHECKLIST: How to confirm the fix worked.")
            appendLine("6. PREVENTION MEASURE: What process or monitoring change prevents recurrence.")
            appendLine("7. ESCALATION TRIGGER: Specific condition for elevating to group lead or CDO.")
            appendLine("8. CASCADING THREAT PREDICTION: What happens in next 24-48 hours if unresolved.")
            appendLine()
            appendLine("RULES:")
            appendLine("- Use actual dataset, system, and report names from the data.")
            appendLine("- Be specific. No generic advice like 'check the logs.'")
            appendLine("- Tone: direct, expert, no hedging. State conclusions with confidence.")
            appendLine("- If owner_load > 2, flag the systemic nature explicitly.")
            appendLine("- If affectedReports contains Class 2, emphasize executive impact.")
        }

        BugLogger.log("Stage 4 owner guidance prompt length: ${prompt.length} chars")

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
            BugLogger.logError("Stage 4 owner guidance LLM failed", e)
            buildFallbackReport(alert, stage3State, severityRule?.kpiTargetDays.toString(), affectedReports.size)
        }

        val state = OwnerGuidanceState(
            alertDataset = alert.datasetName,
            guidanceReport = response.trim()
        )

        GhostPaths.DQ_STATE_DIR.resolve("stage4.json").writeText(
            json.encodeToString(OwnerGuidanceState.serializer(), state)
        )

        BugLogger.log("Stage 4 owner guidance complete: ${response.length} chars")
        state
    }

    private fun buildFallbackReport(
        alert: DQAlert,
        stage3State: AnalysisState,
        kpiDays: String,
        affectedReportCount: Int
    ): String {
        val ownerLoad = stage3State.ownerLoad ?: 0
        val pattern = stage3State.patternType ?: "isolated"
        return """
            **1. SITUATION**
            Critical data quality failure in ${alert.datasetName} requires immediate attention.

            **2. ROOT CAUSE HYPOTHESIS**
            Likely source system issue in ${alert.datasourceName} related to ${alert.checkName}. Confidence: medium.

            **3. PRIORITIZATION REASONING**
            Severity: ${alert.severity}. Affects $affectedReportCount downstream reports. Owner has $ownerLoad open Critical/Error failures.

            **4. IMMEDIATE FIX STEPS**
            - Verify source system ${alert.datasourceName} is operational
            - Check for recent schema or API changes
            - Re-run the ${alert.checkName} validation
            - Contact ${alert.ownerEmail} if external dependency

            **5. VERIFICATION CHECKLIST**
            - Re-run DQ check and confirm pass status
            - Validate downstream report data freshness

            **6. PREVENTION MEASURE**
            Add pre-deployment validation for ${alert.datasourceName} schema changes.

            **7. ESCALATION TRIGGER**
            Escalate if not resolved within ${kpiDays} days or if pattern type is systemic.

            **8. CASCADING THREAT PREDICTION**
            Unresolved failures in ${alert.datasourceName} may impact additional datasets within 24-48 hours.

            (AI synthesis failed - using fallback template)
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
