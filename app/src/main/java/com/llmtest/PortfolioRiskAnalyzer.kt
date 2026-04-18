package com.llmtest

import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object PortfolioRiskAnalyzer {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyze(ownerEmail: String, engine: Engine): String = withContext(Dispatchers.IO) {

        // Load all data sources
        val alerts = loadAlerts()
        val entities = loadEntities()
        val reports = loadReports()
        val catalog = loadCatalog()

        // Filter to owner's datasets
        val ownerEntities = entities.filter { it.ownerEmail == ownerEmail }
        val ownerDatasets = ownerEntities.map { it.linkedDatasetName }.toSet()

        if (ownerDatasets.isEmpty()) {
            return@withContext "No datasets found for owner $ownerEmail."
        }

        // Build sourceDB clusters
        val datasetToSourceDBs = ownerDatasets.associateWith { dataset ->
            catalog.filter { it.linkedDatasetName == dataset }
                .mapNotNull { it.sourceDB }
                .distinct()
        }

        // For each sourceDB, collect dataset info
        val sourceDBClusters = mutableMapOf<String, MutableList<DatasetClusterInfo>>()
        ownerDatasets.forEach { dataset ->
            val dbs = datasetToSourceDBs[dataset] ?: listOf("unknown")
            val entity = ownerEntities.find { it.linkedDatasetName == dataset }
            val alert = alerts.find { it.datasetName == dataset && it.evaluationStatus == "fail" }
            val datasetReports = reports.filter { it.dataSources.contains(dataset) }
            val class2Count = datasetReports.count { it.reportClass == "2" }

            val info = DatasetClusterInfo(
                datasetName = dataset,
                entityGroup = entity?.entityGroup ?: "Unknown",
                sourceTables = catalog.filter { it.linkedDatasetName == dataset }.mapNotNull { it.sourceTable }.distinct(),
                isFailing = alert != null,
                severity = alert?.severity ?: "N/A",
                class2Reports = class2Count,
                totalReports = datasetReports.size
            )

            dbs.forEach { db ->
                sourceDBClusters.getOrPut(db) { mutableListOf() }.add(info)
            }
        }

        val totalDatasets = ownerDatasets.size

        // Build prompt
        val prompt = buildString {
            appendLine("You are a Data Infrastructure Analyst mapping source system dependencies for a data owner.")
            appendLine()
            appendLine("OWNER: $ownerEmail")
            appendLine("TOTAL DATASETS: $totalDatasets")
            appendLine()

            appendLine("=== SOURCE SYSTEM CLUSTERS ===")
            sourceDBClusters.toSortedMap().forEach { (sourceDB, datasets) ->
                appendLine()
                appendLine("SourceDB: $sourceDB")
                appendLine("  Datasets: ${datasets.size}")
                val failing = datasets.filter { it.isFailing }
                appendLine("  Failing: ${failing.size} ${if (failing.isNotEmpty()) "(${failing.joinToString { it.datasetName }})" else ""}")
                val class2Total = datasets.sumOf { it.class2Reports }
                appendLine("  Class-2 Reports at Risk: $class2Total")
                datasets.forEach { ds ->
                    appendLine("    - ${ds.datasetName} (${ds.entityGroup}) | Tables: ${ds.sourceTables.joinToString()}")
                    appendLine("      Status: ${if (ds.isFailing) "FAIL (${ds.severity})" else "PASS"} | Reports: ${ds.totalReports} total, ${ds.class2Reports} executive")
                }
            }
            appendLine()

            appendLine("=== INSTRUCTION ===")
            appendLine("Write a structured Dependency Risk Map for this owner.")
            appendLine()
            appendLine("1. CLUSTER OVERVIEW")
            appendLine("   For each sourceDB cluster, assign a risk level:")
            appendLine("   🔴 HIGH if: (a) the cluster contains >50% of the owner's total datasets, OR")
            appendLine("               (b) any failing dataset in the cluster feeds Class-2 (executive) reports.")
            appendLine("   🟡 MEDIUM if: the cluster has at least one failing dataset but no Class-2 impact.")
            appendLine("   🟢 LOW if: all datasets in the cluster are passing.")
            appendLine("   Include: dataset count, failing count + names, Class-2 report count at risk.")
            appendLine()
            appendLine("2. CASCADE ANALYSIS")
            appendLine("   For each 🔴 HIGH cluster, predict the business impact if that sourceDB fails.")
            appendLine("   Which datasets lose data? Which executive reports are affected?")
            appendLine("   State the prediction as a specific scenario with actual names.")
            appendLine()
            appendLine("3. CONCENTRATION WARNING")
            appendLine("   Flag any sourceDB that hosts >50% of the owner's datasets as a single point of failure.")
            appendLine("   Calculate and state the exact percentage.")
            appendLine()
            appendLine("4. MITIGATION SUGGESTIONS")
            appendLine("   Provide 1-2 concrete, specific actions per 🔴 HIGH cluster.")
            appendLine("   Do not write generic advice. Reference actual dataset and sourceDB names.")
            appendLine()
            appendLine("RULES:")
            appendLine("- Use actual dataset names, sourceDB names, and report counts from the data.")
            appendLine("- Be specific. No generic statements like 'check your databases.'")
            appendLine("- State percentages and counts precisely.")
            appendLine("- Tone: expert infrastructure analyst briefing a data owner.")
        }

        BugLogger.log("Portfolio risk map prompt length: ${prompt.length} chars")

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
            BugLogger.logError("Portfolio risk map LLM failed", e)
            buildFallbackRiskMap(ownerEmail, totalDatasets, sourceDBClusters)
        }

        val result = response.trim()
        GhostPaths.PORTFOLIO_RISK_MAP.writeText(result)
        BugLogger.log("Portfolio risk map complete: ${result.length} chars")
        result
    }

    private fun buildFallbackRiskMap(
        ownerEmail: String,
        totalDatasets: Int,
        clusters: Map<String, List<DatasetClusterInfo>>
    ): String {
        return buildString {
            appendLine("Source System Dependency Risk Map")
            appendLine("Owner: $ownerEmail")
            appendLine()

            clusters.toSortedMap().forEach { (sourceDB, datasets) ->
                val failing = datasets.filter { it.isFailing }
                val hasClass2 = datasets.any { it.class2Reports > 0 }
                val pct = (datasets.size.toFloat() / totalDatasets * 100).toInt()
                val risk = when {
                    pct > 50 || (hasClass2 && failing.isNotEmpty()) -> "🔴 HIGH"
                    failing.isNotEmpty() -> "🟡 MEDIUM"
                    else -> "🟢 LOW"
                }

                appendLine("$risk $sourceDB")
                appendLine("  Datasets: ${datasets.size} (${pct}% of portfolio)")
                appendLine("  Failing: ${failing.size} ${if (failing.isNotEmpty()) "(${failing.joinToString { it.datasetName }})" else ""}")
                appendLine("  Class-2 Reports at Risk: ${datasets.sumOf { it.class2Reports }}")

                if (pct > 50) {
                    appendLine("  ⚠️ CONCENTRATION WARNING: This sourceDB hosts >50% of your portfolio.")
                }
                appendLine()
            }

            appendLine("(AI analysis failed — using fallback template)")
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

    private fun loadEntities(): List<Entity> {
        return try {
            val content = GhostPaths.ENTITIES.readText()
            json.decodeFromString(ListSerializer(Entity.serializer()), content)
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

    private fun loadCatalog(): List<CatalogColumn> {
        return try {
            val content = GhostPaths.CATALOG.readText()
            json.decodeFromString(ListSerializer(CatalogColumn.serializer()), content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class DatasetClusterInfo(
        val datasetName: String,
        val entityGroup: String,
        val sourceTables: List<String>,
        val isFailing: Boolean,
        val severity: String,
        val class2Reports: Int,
        val totalReports: Int
    )
}
