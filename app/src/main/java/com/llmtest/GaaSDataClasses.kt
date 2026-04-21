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
    val dimensionBypass: List<String> = emptyList()
)

@Serializable
data class Stage2Config(
    val entityFields: List<String> = listOf("entityName", "entityGroup", "ownerEmail", "description"),
    val catalogFields: List<String> = listOf("sourceDB", "sourceTable", "definition", "dataExample"),
    val fallbackChain: List<String> = listOf("entityGroup", "ownerEmail", "datasetName"),
    val maxCatalogColumns: Int = 5
)

@Serializable
data class Stage3Config(
    val ownerOverloadThreshold: Int = 2,
    val ownerOverloadAnySeverityThreshold: Int = 5,
    val lookbackWindowDays: Int = 7,
    val groupHealthThreshold: Float = 0.6f,
    val groupFailingDatasetMin: Int = 2,
    val defaultIsolatedIncident: Boolean = true
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
