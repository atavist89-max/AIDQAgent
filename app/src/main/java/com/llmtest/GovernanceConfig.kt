package com.llmtest

import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

object GovernanceConfig {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ---------- Default Policies (5 gates, all disabled by default) ----------
    private val defaultPolicies = listOf(
        GaaSPolicy(gateId = "gate1", enabled = false, prompt = "Ensure the triage decision is consistent with the alert severity and downstream report impact."),
        GaaSPolicy(gateId = "gate2", enabled = false, prompt = "Verify that entity metadata and catalog columns are correctly matched to the alert dataset."),
        GaaSPolicy(gateId = "gate3", enabled = false, prompt = "Confirm the pattern detection considers owner workload and group health holistically."),
        GaaSPolicy(gateId = "gate4", enabled = false, prompt = "Validate that the upstream technical analysis uses actual system names and provides specific root-cause hypotheses."),
        GaaSPolicy(gateId = "gate5", enabled = false, prompt = "Validate that the downstream impact assessment prioritizes Class 2 reports and identifies specific stakeholders.")
    )

    // ---------- Default Station Prompts ----------
    private val defaultStationPrompts = listOf(
        StationPrompt(
            stationId = "stage1",
            prompt = "Deterministic rule-based triage agent. Evaluate alert severity and downstream report impact to classify as FULL_ANALYSIS or MINIMAL.",
            configJson = json.encodeToString(Stage1Config.serializer(), Stage1Config())
        ),
        StationPrompt(
            stationId = "stage2",
            prompt = "Deterministic context builder. Enrich alerts with entity metadata, catalog columns, and entity group information from the data catalog.",
            configJson = json.encodeToString(Stage2Config.serializer(), Stage2Config())
        ),
        StationPrompt(
            stationId = "stage3",
            prompt = "Deterministic pattern detector. Analyze owner workload and group health to identify patterns: owner_overload, group_collapse, or isolated_incident.",
            configJson = json.encodeToString(Stage3Config.serializer(), Stage3Config())
        ),
        StationPrompt(
            stationId = "stage4a",
            prompt = buildString {
                appendLine("You are a Technical Data Architect investigating a data quality failure in a financial institution.")
                appendLine()
                appendLine("Your task is to write a Technical Data Steward Briefing (200-250 words) covering:")
                appendLine()
                appendLine("1. BUSINESS PURPOSE: What is this data? What business process depends on it?")
                appendLine()
                appendLine("2. TECHNICAL ARCHITECTURE: Analyze the source system. What are typical failure patterns? Reliability considerations.")
                appendLine()
                appendLine("3. DIMENSION CONTEXT: What does the DQ dimension specifically mean for THIS dataset? Contextualize the risk.")
                appendLine()
                appendLine("4. ROOT CAUSE HYPOTHESIS: Based on source system and check type, what is the most likely technical cause? State confidence level.")
                appendLine()
                appendLine("5. INVESTIGATION PATH: Specific technical steps to confirm hypothesis (SQL queries, system checks, validation steps).")
                appendLine()
                appendLine("RULES:")
                appendLine("- Use actual system names, table names, and database names provided in the data")
                appendLine("- Be specific and technical but explain business relevance")
                appendLine("- 200-250 words, professional technical briefing format")
                appendLine("- Do not hedge—state conclusions with confidence levels")
            }.trim()
        ),
        StationPrompt(
            stationId = "stage4b",
            prompt = buildString {
                appendLine("You are a Business Impact Analyst assessing downstream consequences of a data quality failure in a financial institution.")
                appendLine()
                appendLine("Your task is to write a Business Impact Assessment (200-250 words) covering:")
                appendLine()
                appendLine("1. CASCADE ANALYSIS: Which reports break first? What's the dependency chain?")
                appendLine()
                appendLine("2. STAKEHOLDER NOTIFICATION PRIORITY by Class:")
                appendLine("   - Class 2 (Very Important): Who must be warned BEFORE the next report run?")
                appendLine("   - Class 1 (Cross-unit): Which decision-makers will see corrupted data in weekly reviews?")
                appendLine("   - Class 0 (Unit-level): Operational teams who can work around it")
                appendLine()
                appendLine("3. BUSINESS CONSEQUENCE BY CLASS:")
                appendLine("   - Class 2 failure: External implications? (regulatory miss? client reporting error?)")
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
            }.trim()
        ),
        StationPrompt(
            stationId = "stage4c",
            prompt = buildString {
                appendLine("You are a Senior Data Steward delivering an executive briefing to the Chief Data Officer.")
                appendLine()
                appendLine("Synthesize the upstream technical analysis and downstream business impact into a cohesive narrative.")
                appendLine()
                appendLine("Structure your narrative:")
                appendLine("1. SITUATION: What broke and why it matters NOW")
                appendLine("2. TECHNICAL ROOT CAUSE: Source system failure with confidence")
                appendLine("3. BUSINESS IMPACT: Business function degrading (datasets, failing count)")
                appendLine("4. ORGANIZATIONAL RISK: Capacity with multiple concurrent failures")
                appendLine("5. CASCADING THREAT: Predict next 24-48 hours if unresolved")
                appendLine("6. ACTIONABLE RECOMMENDATIONS: Immediate, short-term, governance")
                appendLine()
                appendLine("TONE: Urgent but authoritative. Expert investigator briefing leadership. No hedging—state conclusions.")
                appendLine("Use actual names from the research reports. Make it feel like a senior steward who investigated for 2 hours.")
            }.trim()
        )
    )

    // ---------- Policy File Management ----------

    fun loadPolicies(): List<GaaSPolicy> {
        val file = GhostPaths.GaaSPolicies
        return try {
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(GaaSPoliciesFile.serializer(), content).policies
                } else {
                    createDefaultPolicies()
                }
            } else {
                createDefaultPolicies()
            }
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to load policies", e)
            createDefaultPolicies()
        }
    }

    private fun createDefaultPolicies(): List<GaaSPolicy> {
        val data = GaaSPoliciesFile(policies = defaultPolicies)
        persistPolicies(data)
        return defaultPolicies
    }

    fun savePolicies(policies: List<GaaSPolicy>) {
        persistPolicies(GaaSPoliciesFile(policies = policies))
    }

    fun getPolicy(gateId: String): GaaSPolicy? {
        return loadPolicies().find { it.gateId == gateId }
    }

    fun updatePolicy(policy: GaaSPolicy) {
        val policies = loadPolicies().toMutableList()
        val index = policies.indexOfFirst { it.gateId == policy.gateId }
        if (index >= 0) {
            policies[index] = policy
        } else {
            policies.add(policy)
        }
        savePolicies(policies)
    }

    private fun persistPolicies(data: GaaSPoliciesFile) {
        try {
            GhostPaths.GaaSPolicies.parentFile?.mkdirs()
            GhostPaths.GaaSPolicies.writeText(json.encodeToString(GaaSPoliciesFile.serializer(), data))
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to persist policies", e)
        }
    }

    // ---------- Station Prompt File Management ----------

    fun loadStationPrompts(): List<StationPrompt> {
        val file = GhostPaths.StationPrompts
        return try {
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(StationPromptsFile.serializer(), content).stations
                } else {
                    createDefaultStationPrompts()
                }
            } else {
                createDefaultStationPrompts()
            }
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to load station prompts", e)
            createDefaultStationPrompts()
        }
    }

    private fun createDefaultStationPrompts(): List<StationPrompt> {
        val data = StationPromptsFile(stations = defaultStationPrompts)
        persistStationPrompts(data)
        return defaultStationPrompts
    }

    fun saveStationPrompts(stations: List<StationPrompt>) {
        persistStationPrompts(StationPromptsFile(stations = stations))
    }

    fun getStationPrompt(stationId: String): StationPrompt? {
        return loadStationPrompts().find { it.stationId == stationId }
    }

    fun updateStationPrompt(stationPrompt: StationPrompt) {
        val stations = loadStationPrompts().toMutableList()
        val index = stations.indexOfFirst { it.stationId == stationPrompt.stationId }
        if (index >= 0) {
            stations[index] = stationPrompt
        } else {
            stations.add(stationPrompt)
        }
        saveStationPrompts(stations)
    }

    private fun persistStationPrompts(data: StationPromptsFile) {
        try {
            GhostPaths.StationPrompts.parentFile?.mkdirs()
            GhostPaths.StationPrompts.writeText(json.encodeToString(StationPromptsFile.serializer(), data))
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to persist station prompts", e)
        }
    }

    // ---------- Config Helpers ----------

    inline fun <reified T> parseConfig(configJson: String): T? {
        return try {
            if (configJson.isNotBlank()) {
                json.decodeFromString(serializer<T>(), configJson)
            } else null
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to parse config", e)
            null
        }
    }

    fun getStage1Config(): Stage1Config {
        return getStationPrompt("stage1")?.configJson?.let { parseConfig<Stage1Config>(it) } ?: Stage1Config()
    }

    fun getStage2Config(): Stage2Config {
        return getStationPrompt("stage2")?.configJson?.let { parseConfig<Stage2Config>(it) } ?: Stage2Config()
    }

    fun getStage3Config(): Stage3Config {
        return getStationPrompt("stage3")?.configJson?.let { parseConfig<Stage3Config>(it) } ?: Stage3Config()
    }

    // ---------- Block State ----------

    fun loadBlockState(): GaaSBlockState? {
        val file = GhostPaths.GaaSBlock
        return try {
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(GaaSBlockState.serializer(), content)
                } else null
            } else null
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to load block state", e)
            null
        }
    }

    fun saveBlockState(state: GaaSBlockState) {
        try {
            GhostPaths.GaaSBlock.parentFile?.mkdirs()
            GhostPaths.GaaSBlock.writeText(json.encodeToString(GaaSBlockState.serializer(), state))
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to save block state", e)
        }
    }

    fun clearBlockState() {
        try {
            if (GhostPaths.GaaSBlock.exists()) {
                GhostPaths.GaaSBlock.delete()
            }
        } catch (e: Exception) {
            BugLogger.logError("GovernanceConfig: Failed to clear block state", e)
        }
    }
}
