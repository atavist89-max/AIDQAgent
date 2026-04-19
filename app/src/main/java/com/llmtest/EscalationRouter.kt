package com.llmtest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

object EscalationRouter {
    private val _pendingApprovals = MutableStateFlow<List<PendingApproval>>(emptyList())
    val pendingApprovals: StateFlow<List<PendingApproval>> = _pendingApprovals

    private val _approvalHistory = MutableStateFlow<List<PendingApproval>>(emptyList())
    val approvalHistory: StateFlow<List<PendingApproval>> = _approvalHistory

    private val _escalationResults = MutableStateFlow<Map<String, EscalationDecision>>(emptyMap())
    val escalationResults: StateFlow<Map<String, EscalationDecision>> = _escalationResults

    fun route(
        alert: DQAlert,
        agentId: String,
        violations: List<PolicyViolation>,
        hasConflict: Boolean = false
    ): EscalationDecision {
        val trustScore = TrustScoreManager.getScore(agentId)?.score ?: 0
        val autonomy = TrustScoreManager.getAutonomyLevel(agentId)
        val severity = alert.severity

        // Emergency: any compliance/policy violation that blocks
        val blockingViolations = violations.filter {
            it.policyId == "critical_alert_oversight" || it.policyId == "exec_contact_approval"
        }
        if (blockingViolations.isNotEmpty()) {
            val decision = EscalationDecision(
                level = EscalationLevel.EMERGENCY,
                reason = "Compliance/policy blocking violation: ${blockingViolations.first().policyName}",
                requiresImmediateAction = true
            )
            _escalationResults.value = _escalationResults.value + (alert.datasetName to decision)
            AuditLogger.logEscalation(decision.level, decision.reason, alert.datasetName, agentId)
            return decision
        }

        // Critical alert always needs approval if trust is not expert
        if (severity.equals("Critical", ignoreCase = true) && trustScore < 90) {
            val decision = EscalationDecision(
                level = EscalationLevel.REQUEST_APPROVAL,
                reason = "Critical alert with ${autonomy.label} agent (trust: $trustScore)",
                requiresImmediateAction = true
            )
            createPendingApproval(alert, decision)
            _escalationResults.value = _escalationResults.value + (alert.datasetName to decision)
            AuditLogger.logEscalation(decision.level, decision.reason, alert.datasetName, agentId)
            return decision
        }

        // High severity with low trust
        if (severity.equals("Error", ignoreCase = true) && trustScore < 50) {
            val decision = EscalationDecision(
                level = EscalationLevel.REQUEST_APPROVAL,
                reason = "Error severity alert with low-trust agent (trust: $trustScore)",
                requiresImmediateAction = false
            )
            createPendingApproval(alert, decision)
            _escalationResults.value = _escalationResults.value + (alert.datasetName to decision)
            AuditLogger.logEscalation(decision.level, decision.reason, alert.datasetName, agentId)
            return decision
        }

        // Agent conflict detected
        if (hasConflict) {
            val decision = EscalationDecision(
                level = EscalationLevel.REQUEST_APPROVAL,
                reason = "Agent negotiation conflict detected between Stage 4a and 4b",
                requiresImmediateAction = false
            )
            createPendingApproval(alert, decision, isNegotiation = true)
            _escalationResults.value = _escalationResults.value + (alert.datasetName to decision)
            AuditLogger.logEscalation(decision.level, decision.reason, alert.datasetName, agentId)
            return decision
        }

        // Non-critical with redactable violations
        if (violations.isNotEmpty() && violations.all { it.policyId == "pii_prevention" || it.policyId == "audit_trail_completeness" || it.policyId == "schema_drift_validation" }) {
            val decision = EscalationDecision(
                level = EscalationLevel.NOTIFY_LOG,
                reason = "Minor policy violations auto-remediated: ${violations.joinToString { it.policyName }}",
                requiresImmediateAction = false
            )
            _escalationResults.value = _escalationResults.value + (alert.datasetName to decision)
            AuditLogger.logEscalation(decision.level, decision.reason, alert.datasetName, agentId)
            return decision
        }

        // High trust agent with routine alert
        if (trustScore >= 70 && (severity.equals("Warning", ignoreCase = true) || severity.equals("Informative", ignoreCase = true))) {
            val decision = EscalationDecision(
                level = EscalationLevel.AUTO_RESOLVE,
                reason = "${autonomy.label} agent ($trustScore) handling $severity severity alert",
                requiresImmediateAction = false
            )
            _escalationResults.value = _escalationResults.value + (alert.datasetName to decision)
            return decision
        }

        // Default: notify and log
        val decision = EscalationDecision(
            level = EscalationLevel.NOTIFY_LOG,
            reason = "Standard routing for ${autonomy.label} agent ($trustScore) on $severity alert",
            requiresImmediateAction = false
        )
        _escalationResults.value = _escalationResults.value + (alert.datasetName to decision)
        AuditLogger.logEscalation(decision.level, decision.reason, alert.datasetName, agentId)
        return decision
    }

    private fun createPendingApproval(alert: DQAlert, decision: EscalationDecision, isNegotiation: Boolean = false) {
        val approval = PendingApproval(
            approvalId = UUID.randomUUID().toString(),
            decisionId = UUID.randomUUID().toString(),
            alertDataset = alert.datasetName,
            title = if (isNegotiation) "Agent Conflict: ${alert.datasetName}" else "${alert.severity} Alert: ${alert.datasetName}",
            description = decision.reason,
            severity = alert.severity,
            priority = if (alert.severity.equals("Critical", ignoreCase = true)) 4 else if (isNegotiation) 3 else 2
        )
        _pendingApprovals.value = _pendingApprovals.value + approval
    }

    fun approve(approvalId: String, notes: String? = null) {
        val approval = _pendingApprovals.value.find { it.approvalId == approvalId } ?: return
        val updated = approval.copy(
            approved = true,
            approvedAt = System.currentTimeMillis(),
            approverNotes = notes
        )
        _pendingApprovals.value = _pendingApprovals.value.filter { it.approvalId != approvalId }
        _approvalHistory.value = _approvalHistory.value + updated
        AuditLogger.logHumanOverride("coordinator", "Approved ${approval.alertDataset}", notes ?: "No notes")
    }

    fun reject(approvalId: String, notes: String? = null) {
        val approval = _pendingApprovals.value.find { it.approvalId == approvalId } ?: return
        val updated = approval.copy(
            approved = false,
            approvedAt = System.currentTimeMillis(),
            approverNotes = notes
        )
        _pendingApprovals.value = _pendingApprovals.value.filter { it.approvalId != approvalId }
        _approvalHistory.value = _approvalHistory.value + updated
        AuditLogger.logHumanOverride("coordinator", "Rejected ${approval.alertDataset}", notes ?: "No notes")
    }

    fun getDecisionForAlert(datasetName: String): EscalationDecision? {
        return _escalationResults.value[datasetName]
    }

    fun getPendingForAlert(datasetName: String): PendingApproval? {
        return _pendingApprovals.value.find { it.alertDataset == datasetName }
    }
}
