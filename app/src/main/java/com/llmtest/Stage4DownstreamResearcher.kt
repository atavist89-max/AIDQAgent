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

        val config = GovernanceConfig.getStage4bConfig()

        // Load reports and severity info (conditionally)
        val allReports = if ("reports.json" in config.availableJsons) loadReports() else emptyList()
        val affectedReports = allReports.filter { it.dataSources.contains(alert.datasetName) }
        
        val knowledge = if ("dq_knowledge.json" in config.availableJsons) loadKnowledge() else null
        val severityRule = knowledge?.severityRules?.get(alert.severity)
        
        // Parse pattern info from stage 3
        val patternLines = stage3State.patternResult?.split("\n") ?: emptyList()
        val ownerLoad = patternLines.find { it.contains("OWNER_LOAD") }?.substringAfter(":")?.trim() ?: "Unknown"
        
        // Load group context for entity group impact (conditionally)
        val entities = if ("entities.json" in config.availableJsons) loadEntities() else emptyList()
        val entity = entities.find { it.linkedDatasetName == alert.datasetName }
        val entityGroups = if ("entity_groups.json" in config.availableJsons) loadEntityGroups() else emptyList()
        val group = entityGroups.find { it.entityGroup == entity?.entityGroup }
        
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

        // Read system prompt from governance config
        val systemPrompt = GovernanceConfig.getStationPrompt("stage4b")?.prompt
            ?: "You are a Business Impact Analyst assessing downstream consequences of a data quality failure."

        // Build deep impact prompt
        val prompt = buildString {
            appendLine(systemPrompt)
            appendLine()
            appendLine("=== AFFECTED REPORTS ===")
            val reportsToShow = affectedReports.take(5)
            if (reportsToShow.isNotEmpty()) {
                reportsToShow.forEach { r ->
                    val classDescription = when(r.reportClass) {
                        "0" -> "C0-Personal"
                        "1" -> "C1-Cross-unit"
                        "2" -> "C2-Executive"
                        else -> "C${r.reportClass}"
                    }
                    appendLine("- ${r.reportName} [$classDescription] Owner:${r.reportOwner} Desc:${r.reportDescription.take(100)}")
                }
                if (affectedReports.size > 5) {
                    appendLine("- ... and ${affectedReports.size - 5} more reports")
                }
            } else {
                appendLine("- No downstream reports")
            }
            
            appendLine("=== FAILURE CONTEXT ===")
            appendLine("Alert Severity: ${alert.severity}")
            appendLine("Resolution Target: ${severityRule?.kpiTargetDays ?: "N/A"} business days")
            appendLine("Check Type: ${alert.checkName}")
            appendLine("Dataset: ${alert.datasetName}")
            appendLine("Owner Workload: ${alert.ownerEmail} has $ownerLoad other Critical failures open")
            appendLine()
            
            if ("entities.json" in config.availableJsons || "dq_alerts.json" in config.availableJsons) {
                appendLine("=== ENTITY GROUP IMPACT ===")
                appendLine("Business Function: ${entity?.entityGroup}")
                appendLine("Group Datasets: ${groupDatasets}")
                appendLine("Group Health: ${(healthScore * 100).toInt()}%")
                appendLine("Group Failing: ${groupTotal - groupPassing} of ${groupTotal} datasets")
                appendLine("Group Owner: ${group?.ownerEmail}")
                appendLine()
            }
            
            appendLine("=== INSTRUCTION ===")
            appendLine("Assess cascade risk across the ${entity?.entityGroup} group (${groupDatasets} datasets). Prioritize C2 Executive reports.")
            appendLine()
            
            appendLine("=== TASK ===")
            appendLine("Business Impact Assessment (200-250 words):")
            appendLine("1. CASCADE: Which reports break first? Dependency chain?")
            appendLine("2. STAKEHOLDERS: C2 (exec/board/regulatory), C1 (cross-unit decisions), C0 (ops teams)")
            appendLine("3. CONSEQUENCES: External/regulatory, internal planning, operational friction")
            appendLine("4. TIME: Report run frequency, hours until next critical run")
            appendLine()
            appendLine("RULES:")
            appendLine("- Use actual report/owner names from data")
            appendLine("- Be specific about who gets notified")
            appendLine("- 200-250 words, professional format")
            appendLine("- Prioritize C2 — flag immediate escalation")

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
