package com.llmtest

import kotlinx.serialization.Serializable

// ==================== FAST GaaS POLICIES ====================

@Serializable
data class GaaSPolicy(
    val gateId: String,
    val enabled: Boolean = false,
    val prompt: String = ""
)

@Serializable
data class GaaSPoliciesFile(
    val policies: List<GaaSPolicy> = emptyList()
)

// ==================== STATION PROMPTS ====================

@Serializable
data class StationPrompt(
    val stationId: String,
    val prompt: String = "",
    val configJson: String = ""
)

@Serializable
data class StationPromptsFile(
    val stations: List<StationPrompt> = emptyList()
)

// ==================== STAGE CONFIGURATION MODELS ====================

@Serializable
data class Stage1Config(
    val severityThreshold: String = "Warning",
    val requiredDownstreamClass: Int = 2,
    val dimensionBypass: List<String> = emptyList(),
    val availableJsons: List<String> = listOf("reports.json")
)

@Serializable
data class Stage2Config(
    val entityFields: List<String> = listOf("entityName", "entityGroup", "ownerEmail", "description"),
    val catalogFields: List<String> = listOf("sourceDB", "sourceTable", "definition", "dataExample"),
    val fallbackChain: List<String> = listOf("entityGroup", "ownerEmail", "datasetName"),
    val maxCatalogColumns: Int = 5,
    val availableJsons: List<String> = listOf("entities.json", "catalog_columns.json", "entity_groups.json", "stage1.json")
)

@Serializable
data class Stage3Config(
    val ownerOverloadThreshold: Int = 2,
    val ownerOverloadAnySeverityThreshold: Int = 5,
    val lookbackWindowDays: Int = 7,
    val groupHealthThreshold: Float = 0.6f,
    val groupFailingDatasetMin: Int = 2,
    val defaultIsolatedIncident: Boolean = true,
    val availableJsons: List<String> = listOf("dq_alerts.json", "entities.json", "stage1.json", "stage2.json")
)

@Serializable
data class Stage4aConfig(
    val availableJsons: List<String> = listOf(
        "entities.json", "catalog_columns.json", "dq_knowledge.json", "dq_alerts.json",
        "stage1.json", "stage2.json", "stage3.json"
    )
)

@Serializable
data class Stage4bConfig(
    val availableJsons: List<String> = listOf(
        "reports.json", "dq_knowledge.json", "entities.json", "entity_groups.json", "dq_alerts.json",
        "stage1.json", "stage2.json", "stage3.json", "stage4a.json"
    )
)

@Serializable
data class Stage4cConfig(
    val availableJsons: List<String> = listOf(
        "dq_knowledge.json", "entities.json", "entity_groups.json", "catalog_columns.json",
        "reports.json", "dq_alerts.json",
        "stage1.json", "stage2.json", "stage3.json", "stage4a.json", "stage4b.json"
    )
)

// ==================== ATTEMPT RECORD FOR HUMAN INTERVENTION ====================

@Serializable
data class AttemptRecord(
    val attemptNumber: Int,
    val outputSnapshot: String,
    val feedback: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== GaaS BLOCK STATE ====================

@Serializable
data class GaaSBlockState(
    val gateId: String,
    val previousStage: Int,
    val feedback: List<String> = emptyList(),
    val retryCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val history: List<AttemptRecord> = emptyList(),
    val outputSnapshot: String = ""
)

// ==================== GATE VISUAL STATES ====================

enum class GateVisualState {
    INACTIVE,   // Gray hollow diamond — no policy configured
    ACTIVE,     // Blue — policy enabled, train not arrived
    REVIEWING,  // Yellow — GaaS agent is reviewing
    REJECTED,   // Red pulsing — rejected
    APPROVED,   // Green — approved
    OVERRIDDEN  // Orange — human has overridden
}

// ==================== GOVERNANCE STATUS ====================

enum class GovernanceStatus {
    IDLE,
    REVIEWING,
    APPROVED,
    REJECTED,
    OVERRIDDEN,
    ABANDONED
}

// ==================== REVIEW RESULT ====================

sealed class ReviewResult {
    data class Approved(val reasoning: String) : ReviewResult()
    data class Rejected(val feedback: List<String>) : ReviewResult()
}

// ==================== HUMAN RESOLUTION ====================

sealed class HumanResolution {
    object Proceed : HumanResolution()
    object Abandon : HumanResolution()
}

// ==================== POST STAGE RESULT ====================

sealed class PostStageResult {
    object Allow : PostStageResult()
    data class NeedsReview(val prompt: String) : PostStageResult()
}
