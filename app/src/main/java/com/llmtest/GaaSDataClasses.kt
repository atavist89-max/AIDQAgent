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
    val prompt: String = ""
)

@Serializable
data class StationPromptsFile(
    val stations: List<StationPrompt> = emptyList()
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


