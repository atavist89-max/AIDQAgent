package com.llmtest

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AgentNegotiator {
    private val _activeNegotiations = MutableStateFlow<List<AgentNegotiation>>(emptyList())
    val activeNegotiations: StateFlow<List<AgentNegotiation>> = _activeNegotiations

    private val _negotiationResults = MutableStateFlow<Map<String, AgentNegotiation>>(emptyMap())
    val negotiationResults: StateFlow<Map<String, AgentNegotiation>> = _negotiationResults

    fun detectConflict(
        alert: DQAlert,
        stage4aReport: String,
        stage4bReport: String
    ): Boolean {
        // Simple conflict detection heuristics
        val urgencyKeywords = listOf("immediate", "urgent", "critical fix", "blocker", "stop")
        val lowPriorityKeywords = listOf("low priority", "monitor", "routine", "trend", "observe")

        val aUrgent = urgencyKeywords.any { stage4aReport.contains(it, ignoreCase = true) }
        val aLow = lowPriorityKeywords.any { stage4aReport.contains(it, ignoreCase = true) }
        val bUrgent = urgencyKeywords.any { stage4bReport.contains(it, ignoreCase = true) }
        val bLow = lowPriorityKeywords.any { stage4bReport.contains(it, ignoreCase = true) }

        // Conflict if one says urgent and other says low, or if they strongly diverge
        return (aUrgent && bLow) || (aLow && bUrgent) ||
               (stage4aReport.contains("fix now", ignoreCase = true) && stage4bReport.contains("low impact", ignoreCase = true)) ||
               (stage4aReport.contains("low impact", ignoreCase = true) && stage4bReport.contains("fix now", ignoreCase = true))
    }

    fun startNegotiation(
        alert: DQAlert,
        stage4aReport: String,
        stage4bReport: String
    ): AgentNegotiation {
        val negotiationId = UUID.randomUUID().toString()

        val posA = AgentPosition(
            agentId = "stage4a",
            agentName = "Upstream Researcher",
            recommendation = extractRecommendation(stage4aReport),
            confidence = estimateConfidence(stage4aReport),
            evidence = extractEvidence(stage4aReport),
            priority = if (stage4aReport.contains("urgent", ignoreCase = true) || stage4aReport.contains("immediate", ignoreCase = true)) "High" else "Medium"
        )

        val posB = AgentPosition(
            agentId = "stage4b",
            agentName = "Downstream Researcher",
            recommendation = extractRecommendation(stage4bReport),
            confidence = estimateConfidence(stage4bReport),
            evidence = extractEvidence(stage4bReport),
            priority = if (stage4bReport.contains("urgent", ignoreCase = true) || stage4bReport.contains("immediate", ignoreCase = true)) "High" else "Low"
        )

        val conflictType = determineConflictType(posA, posB)
        val proposal = generateMediationProposal(posA, posB, alert)

        val negotiation = AgentNegotiation(
            negotiationId = negotiationId,
            alertDataset = alert.datasetName,
            stage4aPosition = posA,
            stage4bPosition = posB,
            conflictType = conflictType,
            mediatorProposal = proposal,
            status = NegotiationStatus.ACTIVE
        )

        _activeNegotiations.value = _activeNegotiations.value + negotiation
        AuditLogger.logNegotiation(negotiation)
        return negotiation
    }

    fun resolveNegotiation(
        negotiationId: String,
        acceptMediation: Boolean,
        humanOverride: Boolean = false,
        humanDecision: String? = null
    ) {
        val current = _activeNegotiations.value.find { it.negotiationId == negotiationId } ?: return

        val winner = if (acceptMediation) {
            null // Mediation accepted, no winner
        } else if (humanOverride && humanDecision != null) {
            if (humanDecision.contains("4a", ignoreCase = true)) "stage4a" else "stage4b"
        } else {
            // Auto-resolve based on confidence
            if (current.stage4aPosition.confidence >= current.stage4bPosition.confidence) "stage4a" else "stage4b"
        }

        val resolved = current.copy(
            status = when {
                humanOverride -> NegotiationStatus.HUMAN_OVERRIDDEN
                acceptMediation -> NegotiationStatus.MEDIATED
                else -> NegotiationStatus.RESOLVED
            },
            winnerAgentId = winner,
            humanOverride = humanOverride,
            humanDecision = humanDecision,
            resolvedAt = System.currentTimeMillis()
        )

        _activeNegotiations.value = _activeNegotiations.value.filter { it.negotiationId != negotiationId }
        _negotiationResults.value = _negotiationResults.value + (resolved.alertDataset to resolved)

        // Update trust scores based on outcome
        if (winner == "stage4a") {
            TrustScoreManager.adjustForDebate("stage4a", won = true)
            TrustScoreManager.adjustForDebate("stage4b", won = false)
        } else if (winner == "stage4b") {
            TrustScoreManager.adjustForDebate("stage4a", won = false)
            TrustScoreManager.adjustForDebate("stage4b", won = true)
        } else if (acceptMediation) {
            // Both gain a little for successful mediation
            TrustScoreManager.adjustForDebate("stage4a", won = true)
            TrustScoreManager.adjustForDebate("stage4b", won = true)
        }

        AuditLogger.logNegotiation(resolved)
    }

    fun getNegotiationForAlert(alertDataset: String): AgentNegotiation? {
        return _activeNegotiations.value.find { it.alertDataset == alertDataset }
            ?: _negotiationResults.value[alertDataset]
    }

    private fun extractRecommendation(report: String): String {
        val lines = report.lines()
        val actionLine = lines.find { it.contains("ACTION:", ignoreCase = true) || it.contains("RECOMMENDATION:", ignoreCase = true) }
        return actionLine?.substringAfter(":")?.trim()?.take(200)
            ?: report.take(150)
    }

    private fun extractEvidence(report: String): List<String> {
        return report.lines()
            .filter { it.contains(":") && (it.contains("ROOT", ignoreCase = true) || it.contains("IMPACT", ignoreCase = true) || it.contains("CAUSE", ignoreCase = true)) }
            .take(3)
            .map { it.trim() }
    }

    private fun estimateConfidence(report: String): Float {
        // Heuristic: longer, more structured reports with specific terms get higher confidence
        var score = 0.5f
        if (report.length > 200) score += 0.15f
        if (report.contains("ROOT_CAUSE:", ignoreCase = true)) score += 0.1f
        if (report.contains("IMPACT:", ignoreCase = true)) score += 0.1f
        if (report.contains("specific", ignoreCase = true) || report.contains("confirmed", ignoreCase = true)) score += 0.1f
        if (report.contains("uncertain", ignoreCase = true) || report.contains("might", ignoreCase = true)) score -= 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun determineConflictType(posA: AgentPosition, posB: AgentPosition): String {
        return when {
            posA.priority == "High" && posB.priority == "Low" -> "Priority mismatch: Technical urgency vs Business low impact"
            posA.priority == "Low" && posB.priority == "High" -> "Priority mismatch: Technical low impact vs Business urgency"
            posA.recommendation.contains("fix", ignoreCase = true) && posB.recommendation.contains("monitor", ignoreCase = true) -> "Action divergence: Fix vs Monitor"
            else -> "General disagreement on severity assessment"
        }
    }

    private fun generateMediationProposal(posA: AgentPosition, posB: AgentPosition, alert: DQAlert): String {
        return buildString {
            appendLine("MEDIATION PROPOSAL for ${alert.datasetName}")
            appendLine()
            appendLine("Acknowledged Technical Position (${posA.agentName}): ${posA.recommendation}")
            appendLine("Acknowledged Business Position (${posB.agentName}): ${posB.recommendation}")
            appendLine()
            when {
                posA.priority == "High" && posB.priority == "Low" -> {
                    appendLine("PROPOSED RESOLUTION: Implement technical fix with phased rollout.")
                    appendLine("Business team monitors downstream impact for 48 hours.")
                    appendLine("Escalate to full emergency protocol only if monitoring shows degradation.")
                }
                posA.priority == "Low" && posB.priority == "High" -> {
                    appendLine("PROPOSED RESOLUTION: Business-critical path takes precedence.")
                    appendLine("Apply temporary data quality shield for affected reports.")
                    appendLine("Schedule deeper technical remediation in next sprint cycle.")
                }
                else -> {
                    appendLine("PROPOSED RESOLUTION: Hybrid approach balancing both perspectives.")
                    appendLine("Short-term containment action within 24 hours.")
                    appendLine("Comprehensive fix scheduled based on joint technical-business review.")
                }
            }
            appendLine()
            appendLine("Confidence in mediation: ${((posA.confidence + posB.confidence) / 2 * 100).toInt()}%")
        }
    }
}
