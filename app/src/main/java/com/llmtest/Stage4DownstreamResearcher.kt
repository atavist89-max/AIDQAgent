package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage4DownstreamResearcher {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class DownstreamAnalysisState(
        val stage: Int = 42,
        val alertDataset: String,
        val downstreamReport: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun run(
        alert: DQAlert,
        stage3State: AnalysisState,
        engine: Engine,
        feedback: List<String> = emptyList()
    ): DownstreamAnalysisState = withContext(Dispatchers.IO) {

        // Load reports and severity info
        val allReports = loadReports()
        val affectedReports = allReports.filter { it.dataSources.contains(alert.datasetName) }
        
        val knowledge = loadKnowledge()
        val severityRule = knowledge.severityRules[alert.severity]
        
        // Parse pattern info from stage 3
        val patternLines = stage3State.patternResult?.split("\n") ?: emptyList()
        val ownerLoad = patternLines.find { it.contains("OWNER_LOAD") }?.substringAfter(":")?.trim() ?: "Unknown"
        
        // Load group context for entity group impact
        val entities = loadEntities()
        val entity = entities.find { it.linkedDatasetName == alert.datasetName }
        val entityGroups = loadEntityGroups()
        val group = entityGroups.find { it.entityGroup == entity?.entityGroup }
        
        val allAlerts = loadAlerts()
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

        // Read system prompt from governance config
        val systemPrompt = GovernanceConfig.getStationPrompt("stage4b")?.prompt
            ?: "You are a Business Impact Analyst assessing downstream consequences of a data quality failure."

        // Build deep impact prompt
        val prompt = buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("=== AFFECTED REPORTS ===")
            if (affectedReports.isNotEmpty()) {
                affectedReports.forEach { r ->
                    val classDescription = when(r.reportClass) {
                        "0" -> "Class 0 - Personal/unit level (operational monitoring, limited immediate impact)"
                        "1" -> "Class 1 - Cross-unit decision-making (management reviews, planning, operational decisions)"
                        "2" -> "Class 2 - Very Important Reports (board-level, regulatory, external reporting, highest significance)"
                        else -> "Class ${r.reportClass} - Unknown classification"
                    }
                    appendLine("- ${r.reportName}")
                    appendLine("  Classification: $classDescription")
                    appendLine("  Owner: ${r.reportOwner}")
                    appendLine("  Description: ${r.reportDescription}")
                    appendLine()
                }
            } else {
                appendLine("- No downstream reports identified (data silo)")
            }
            
            appendLine("=== FAILURE CONTEXT ===")
            appendLine("Alert Severity: ${alert.severity}")
            appendLine("Resolution Target: ${severityRule?.kpiTargetDays ?: "N/A"} business days")
            appendLine("Check Type: ${alert.checkName}")
            appendLine("Dataset: ${alert.datasetName}")
            appendLine("Owner Workload: ${alert.ownerEmail} has $ownerLoad other Critical failures open")
            appendLine()
            
            appendLine("=== ENTITY GROUP IMPACT ===")
            appendLine("Business Function: ${entity?.entityGroup}")
            appendLine("Group Datasets: ${groupDatasets}")
            appendLine("Group Health: ${(healthScore * 100).toInt()}%")
            appendLine("Group Failing: ${groupTotal - groupPassing} of ${groupTotal} datasets")
            appendLine("Group Owner: ${group?.ownerEmail}")
            appendLine()
            appendLine("=== INSTRUCTION ===")
            appendLine("You are a Business Impact Analyst.")
            appendLine("${alert.datasetName} is one of ${groupDatasets} datasets in ${entity?.entityGroup}.")
            appendLine("Assess cascade risk across the ENTIRE group, not just this one dataset.")
            appendLine("If ${entity?.entityGroup} degrades further, which downstream reports break first?")
            appendLine("Prioritize Class 2 (Executive) reports that consume ANY dataset in this group.")
            appendLine()
            
            appendLine("=== YOUR TASK ===")
            appendLine("Write a Business Impact Assessment (200-250 words) covering:")
            appendLine()
            appendLine("1. CASCADE ANALYSIS: Which reports break first? What's the dependency chain? (e.g., 'Class 2 Executive pulls from Class 1 Management which pulls from this dataset')")
            appendLine()
            appendLine("2. STAKEHOLDER NOTIFICATION PRIORITY by Class:")
            appendLine("   - Class 2 (Very Important): Who must be warned BEFORE the next report run? (executive assistants, board prep teams, regulatory liaisons)")
            appendLine("   - Class 1 (Cross-unit): Which decision-makers will see corrupted data in weekly reviews?")
            appendLine("   - Class 0 (Unit-level): Operational teams who can work around it")
            appendLine()
            appendLine("3. BUSINESS CONSEQUENCE BY CLASS:")
            appendLine("   - Class 2 failure: External implications? (regulatory miss? client reporting error? board credibility?)")
            appendLine("   - Class 1 failure: Internal planning impact? (wrong decisions based on bad data?)")
            appendLine("   - Class 0 failure: Operational friction? (manual workarounds required?)")
            appendLine()
            appendLine("4. TIME SENSITIVITY: When do these reports run? Daily/weekly/monthly? How many hours before the next critical run?")
            appendLine()
            appendLine("RULES:")
            appendLine("- Use actual report names and owner names from the data")
            appendLine("- Be specific about who gets notified and why")
            appendLine("- 200-250 words, professional impact assessment format")
            appendLine("- Prioritize Class 2 above all—flag immediate escalation needs")

            if (feedback.isNotEmpty()) {
                appendLine()
                appendLine("=== GOVERNANCE FEEDBACK ===")
                appendLine("Your previous output was rejected by Governance. Address this feedback before rewriting:")
                feedback.forEach { bullet ->
                    appendLine("- $bullet")
                }
            }
        }

        BugLogger.log("Stage 4b prompt length: ${prompt.length} chars")

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
            BugLogger.logError("Stage 4b LLM failed", e)
            "IMPACT ASSESSMENT UNAVAILABLE: Downstream analysis failed for ${alert.datasetName}. Manual stakeholder notification required."
        }

        val state = DownstreamAnalysisState(
            alertDataset = alert.datasetName,
            downstreamReport = response.trim()
        )

        GhostPaths.DQ_STATE_DIR.resolve("stage4b.json").writeText(
            json.encodeToString(DownstreamAnalysisState.serializer(), state)
        )
        
        BugLogger.log("Stage 4b complete: ${response.length} chars")
        state
    }

    private fun loadReports(): List<Report> {
        return try {
            val content = GhostPaths.REPORTS.readText()
            json.decodeFromString(ListSerializer(Report.serializer()), content)
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

    private fun loadAlerts(): List<DQAlert> {
        return try {
            val content = GhostPaths.DQ_ALERTS.readText()
            json.decodeFromString(ListSerializer(DQAlert.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
