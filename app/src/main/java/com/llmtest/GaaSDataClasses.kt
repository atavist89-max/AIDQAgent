package com.llmtest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ==================== TRUST SCORE SYSTEM ====================

@Serializable
data class TrustScore(
    val agentId: String,
    val agentName: String,
    val agentRole: String,
    val score: Int = 50,
    val decisionsTotal: Int = 0,
    val decisionsCorrect: Int = 0,
    val decisionsIncorrect: Int = 0,
    val accuracyRate: Float = 0.0f,
    val scoreHistory: List<ScoreSnapshot> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val manualOverrideReason: String? = null
)

@Serializable
data class ScoreSnapshot(
    val score: Int,
    val timestamp: Long,
    val reason: String
)

@Serializable
data class TrustScoreFile(
    val agents: List<TrustScore> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class AutonomyLevel(val label: String, val minScore: Int, val maxScore: Int) {
    TRAINING("Training", 0, 30),
    SUPERVISED("Supervised", 30, 70),
    AUTONOMOUS("Autonomous", 70, 90),
    EXPERT("Expert", 90, 100);

    companion object {
        fun fromScore(score: Int): AutonomyLevel = when {
            score >= 90 -> EXPERT
            score >= 70 -> AUTONOMOUS
            score >= 30 -> SUPERVISED
            else -> TRAINING
        }
    }
}

// ==================== POLICY ENGINE ====================

@Serializable
data class PolicyRule(
    val policyId: String,
    val name: String,
    val description: String,
    val category: PolicyCategory,
    val isActive: Boolean = true,
    val conditions: List<PolicyCondition> = emptyList(),
    val action: PolicyAction,
    val escalationRequired: Boolean = false,
    val gateLabel: String? = null,
    val gateOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
enum class PolicyCategory {
    CONTENT,
    PROCESS,
    COMMUNICATION,
    FACTUAL
}

@Serializable
enum class PolicyAction {
    REDACT_AND_LOG,
    ESCALATE_TO_HUMAN,
    BLOCK_AND_REQUEST_RETRY,
    APPROVE
}

@Serializable
data class PolicyCondition(
    val field: String,
    val operator: String,
    val value: JsonElement
)

@Serializable
data class PolicyRulesFile(
    val policies: List<PolicyRule> = emptyList(),
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class PolicyViolation(
    val policyId: String,
    val policyName: String,
    val category: PolicyCategory,
    val reason: String,
    val triggeredContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val remediation: String? = null
)

// ==================== GOVERNANCE STATE ====================

@Serializable
enum class GovernanceStatus {
    PENDING,
    APPROVED,
    BLOCKED,
    ESCALATED,
    MODIFIED
}

@Serializable
data class GaaSState(
    val activeDecisions: List<GovernanceDecision> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val ongoingNegotiations: List<AgentNegotiation> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class GovernanceDecision(
    val decisionId: String,
    val alertDataset: String,
    val stage: Int,
    val status: GovernanceStatus,
    val trustScoresAtDecision: Map<String, Int> = emptyMap(),
    val policiesApplied: List<String> = emptyList(),
    val policyViolations: List<PolicyViolation> = emptyList(),
    val escalationReason: String? = null,
    val mediationId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
)

@Serializable
data class PendingApproval(
    val approvalId: String,
    val decisionId: String,
    val alertDataset: String,
    val title: String,
    val description: String,
    val severity: String,
    val priority: Int = 2,
    val requestedAt: Long = System.currentTimeMillis(),
    val approved: Boolean? = null,
    val approvedAt: Long? = null,
    val approverNotes: String? = null
)

// ==================== AGENT NEGOTIATION ====================

@Serializable
data class AgentNegotiation(
    val negotiationId: String,
    val alertDataset: String,
    val stage4aPosition: AgentPosition,
    val stage4bPosition: AgentPosition,
    val conflictType: String,
    val mediatorProposal: String? = null,
    val status: NegotiationStatus = NegotiationStatus.ACTIVE,
    val winnerAgentId: String? = null,
    val humanOverride: Boolean = false,
    val humanDecision: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
)

@Serializable
data class AgentPosition(
    val agentId: String,
    val agentName: String,
    val recommendation: String,
    val confidence: Float,
    val evidence: List<String> = emptyList(),
    val priority: String
)

@Serializable
enum class NegotiationStatus {
    ACTIVE,
    MEDIATED,
    HUMAN_OVERRIDDEN,
    RESOLVED
}

// ==================== AUDIT & FORENSICS ====================

@Serializable
data class AuditRecord(
    val recordId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val recordType: AuditRecordType,
    val agentIds: List<String> = emptyList(),
    val trustScoresAtDecision: Map<String, Int> = emptyMap(),
    val policiesApplied: List<String> = emptyList(),
    val policyViolations: List<PolicyViolation> = emptyList(),
    val inputDataHash: String? = null,
    val decision: String? = null,
    val humanOverrideReason: String? = null,
    val alertDataset: String? = null,
    val stage: Int? = null,
    val metadata: JsonObject? = null
)

@Serializable
enum class AuditRecordType {
    DECISION,
    TRUST_UPDATE,
    POLICY_VIOLATION,
    POLICY_CHANGE,
    HUMAN_OVERRIDE,
    NEGOTIATION,
    ESCALATION
}

// ==================== ESCALATION ====================

@Serializable
enum class EscalationLevel(val label: String, val priority: Int) {
    AUTO_RESOLVE("Auto-Resolve", 1),
    NOTIFY_LOG("Notify & Log", 2),
    REQUEST_APPROVAL("Request Approval", 3),
    EMERGENCY("Emergency Human-in-Loop", 4)
}

@Serializable
data class EscalationDecision(
    val level: EscalationLevel,
    val reason: String,
    val routingTarget: String? = null,
    val requiresImmediateAction: Boolean = false
)

// ==================== ENHANCED ANALYSIS STATE ====================

@Serializable
data class AnalysisStateGaaS(
    val stage: Int,
    val alertDataset: String,
    val timestamp: Long = System.currentTimeMillis(),
    val decision: String? = null,
    val contextSummary: String? = null,
    val patternResult: String? = null,
    val finalReport: String? = null,
    val ownerLoad: Int? = null,
    val healthScore: Float? = null,
    val patternType: String? = null,
    val groupName: String? = null,
    val groupDatasets: Int? = null,
    val groupHealthScore: Float? = null,
    // GaaS extensions
    val governanceStatus: GovernanceStatus = GovernanceStatus.PENDING,
    val trustScoresAtDecision: Map<String, Int> = emptyMap(),
    val policiesApplied: List<String> = emptyList(),
    val escalationReason: String? = null,
    val mediationId: String? = null
)
