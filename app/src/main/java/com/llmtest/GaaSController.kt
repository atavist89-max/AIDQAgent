package com.llmtest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

object GaaSController {
    private val _governanceDecisions = MutableStateFlow<List<GovernanceDecision>>(emptyList())
    val governanceDecisions: StateFlow<List<GovernanceDecision>> = _governanceDecisions

    private val _activeDecision = MutableStateFlow<GovernanceDecision?>(null)
    val activeDecision: StateFlow<GovernanceDecision?> = _activeDecision

    private val _pipelineBlocked = MutableStateFlow(false)
    val pipelineBlocked: StateFlow<Boolean> = _pipelineBlocked

    private val _governanceStatus = MutableStateFlow<GovernanceStatus>(GovernanceStatus.PENDING)
    val governanceStatus: StateFlow<GovernanceStatus> = _governanceStatus

    private val _violationModal = MutableStateFlow<PolicyViolation?>(null)
    val violationModal: StateFlow<PolicyViolation?> = _violationModal

    private val _lastCheckpoint = MutableStateFlow<Int?>(null)
    val lastCheckpoint: StateFlow<Int?> = _lastCheckpoint

    fun initialize() {
        // Ensure all data files exist
        TrustScoreManager.getAllScores()
        PolicyEngine.getPolicies()
        AuditLogger.readAll()
        BugLogger.log("GaaS Controller initialized")
    }

    fun preStageHook(alert: DQAlert, stage: Int): Boolean {
        val scores = TrustScoreManager.getSnapshot()
        val agentId = stageToAgentId(stage)
        val decision = GovernanceDecision(
            decisionId = UUID.randomUUID().toString(),
            alertDataset = alert.datasetName,
            stage = stage,
            status = GovernanceStatus.PENDING,
            trustScoresAtDecision = scores
        )
        _activeDecision.value = decision
        _governanceStatus.value = GovernanceStatus.PENDING
        _lastCheckpoint.value = stage

        // Evaluate trust-based autonomy
        val autonomy = agentId?.let { TrustScoreManager.getAutonomyLevel(it) } ?: AutonomyLevel.TRAINING
        if (autonomy == AutonomyLevel.TRAINING && stage >= 41) {
            // Low trust agents need oversight on LLM stages
            _governanceStatus.value = GovernanceStatus.ESCALATED
        }

        return true
    }

    fun postStageHook(
        alert: DQAlert,
        stage: Int,
        output: String,
        metadata: Map<String, String> = emptyMap()
    ): PostStageResult {
        val agentId = stageToAgentId(stage) ?: return PostStageResult.ALLOW
        val trustScore = TrustScoreManager.getScore(agentId)?.score ?: 0

        // Evaluate policies
        val violations = PolicyEngine.evaluate(
            content = output,
            alert = alert,
            agentId = agentId,
            metadata = metadata
        )

        if (violations.isNotEmpty()) {
            // Log violations
            val criticalViolation = violations.find { it.policyId == "critical_alert_oversight" || it.policyId == "exec_contact_approval" }
            if (criticalViolation != null) {
                _violationModal.value = criticalViolation
                _governanceStatus.value = GovernanceStatus.BLOCKED
                _pipelineBlocked.value = true

                val decision = _activeDecision.value?.copy(
                    status = GovernanceStatus.BLOCKED,
                    policyViolations = violations,
                    policiesApplied = violations.map { it.policyId }
                )
                decision?.let { updateDecision(it) }

                // Route escalation
                EscalationRouter.route(alert, agentId, violations)

                return PostStageResult.BLOCK(violations)
            }

            // Auto-remediate PII
            val piiViolations = violations.filter { it.policyId == "pii_prevention" }
            if (piiViolations.isNotEmpty()) {
                val remediated = PolicyEngine.applyRemediation(output, piiViolations)
                val decision = _activeDecision.value?.copy(
                    status = GovernanceStatus.MODIFIED,
                    policyViolations = violations,
                    policiesApplied = violations.map { it.policyId }
                )
                decision?.let { updateDecision(it) }

                AuditLogger.logDecision(
                    agentIds = listOf(agentId),
                    alertDataset = alert.datasetName,
                    stage = stage,
                    trustScores = mapOf(agentId to trustScore),
                    policiesApplied = violations.map { it.policyId },
                    violations = violations,
                    inputData = output,
                    decision = "Auto-remediated PII"
                )

                return PostStageResult.MODIFY(remediated)
            }

            // Other violations - notify and log
            val decision = _activeDecision.value?.copy(
                status = GovernanceStatus.ESCALATED,
                policyViolations = violations,
                policiesApplied = violations.map { it.policyId },
                escalationReason = violations.joinToString("; ") { it.reason }
            )
            decision?.let { updateDecision(it) }

            EscalationRouter.route(alert, agentId, violations)
            return PostStageResult.ESCALATE(violations)
        }

        // No violations - approve
        val decision = _activeDecision.value?.copy(
            status = GovernanceStatus.APPROVED,
            policiesApplied = PolicyEngine.getActivePolicies().map { it.policyId }
        )
        decision?.let { updateDecision(it) }
        _governanceStatus.value = GovernanceStatus.APPROVED

        AuditLogger.logDecision(
            agentIds = listOf(agentId),
            alertDataset = alert.datasetName,
            stage = stage,
            trustScores = mapOf(agentId to trustScore),
            policiesApplied = PolicyEngine.getActivePolicies().map { it.policyId },
            violations = emptyList(),
            decision = "Approved"
        )

        return PostStageResult.ALLOW
    }

    fun postStage4Hook(
        alert: DQAlert,
        stage4aReport: String,
        stage4bReport: String
    ): Boolean {
        val hasConflict = AgentNegotiator.detectConflict(alert, stage4aReport, stage4bReport)
        if (hasConflict) {
            AgentNegotiator.startNegotiation(alert, stage4aReport, stage4bReport)
            val agentId = "stage4a"
            val violations = PolicyEngine.evaluate(
                content = stage4aReport,
                alert = alert,
                agentId = agentId
            )
            EscalationRouter.route(alert, agentId, violations, hasConflict = true)
            _pipelineBlocked.value = true
            return true
        }
        return false
    }

    fun resolveViolation(approved: Boolean, notes: String? = null) {
        val violation = _violationModal.value ?: return
        if (approved) {
            _governanceStatus.value = GovernanceStatus.APPROVED
            _pipelineBlocked.value = false
            val decision = _activeDecision.value?.copy(
                status = GovernanceStatus.APPROVED,
                escalationReason = notes ?: "Human override approved"
            )
            decision?.let { updateDecision(it) }
            AuditLogger.logHumanOverride("coordinator", "Approved policy violation: ${violation.policyName}", notes ?: "")
        } else {
            val decision = _activeDecision.value?.copy(
                status = GovernanceStatus.BLOCKED,
                escalationReason = notes ?: "Human override rejected"
            )
            decision?.let { updateDecision(it) }
            AuditLogger.logHumanOverride("coordinator", "Rejected policy violation: ${violation.policyName}", notes ?: "")
        }
        _violationModal.value = null
    }

    fun resumePipeline() {
        _pipelineBlocked.value = false
        _governanceStatus.value = GovernanceStatus.APPROVED
    }

    fun recordDecisionAccuracy(agentId: String, wasCorrect: Boolean) {
        TrustScoreManager.recordDecision(agentId, wasCorrect)
    }

    fun updateTrustScore(agentId: String, delta: Int, reason: String) {
        val current = TrustScoreManager.getScore(agentId)?.score ?: 50
        TrustScoreManager.updateScore(agentId, current + delta, reason)
    }

    fun getDecisionForAlert(datasetName: String): GovernanceDecision? {
        return _governanceDecisions.value.find { it.alertDataset == datasetName }
    }

    private fun updateDecision(decision: GovernanceDecision) {
        _governanceDecisions.value = _governanceDecisions.value.filter { it.decisionId != decision.decisionId } + decision
        _activeDecision.value = decision
    }

    private fun stageToAgentId(stage: Int): String? {
        return when (stage) {
            1 -> "stage1"
            2 -> "stage2"
            3 -> "stage3"
            41 -> "stage4a"
            42 -> "stage4b"
            43 -> "stage4c"
            else -> null
        }
    }
}

sealed class PostStageResult {
    object ALLOW : PostStageResult()
    data class BLOCK(val violations: List<PolicyViolation>) : PostStageResult()
    data class ESCALATE(val violations: List<PolicyViolation>) : PostStageResult()
    data class MODIFY(val remediatedOutput: String) : PostStageResult()
}
