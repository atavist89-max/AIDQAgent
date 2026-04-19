package com.llmtest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object AuditLogger {
    private val json = Json { ignoreUnknownKeys = true }
    private val file: File get() = GhostPaths.GAASAuditLog

    private fun append(record: AuditRecord) {
        try {
            file.parentFile?.mkdirs()
            val line = json.encodeToString(AuditRecord.serializer(), record)
            file.appendText("$line\n")
        } catch (e: Exception) {
            BugLogger.logError("AuditLogger: Failed to append audit record", e)
        }
    }

    private fun hash(data: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun logDecision(
        agentIds: List<String>,
        alertDataset: String,
        stage: Int,
        trustScores: Map<String, Int>,
        policiesApplied: List<String>,
        violations: List<PolicyViolation>,
        inputData: String? = null,
        decision: String? = null,
        metadata: Map<String, String>? = null
    ) {
        append(
            AuditRecord(
                recordId = UUID.randomUUID().toString(),
                recordType = AuditRecordType.DECISION,
                agentIds = agentIds,
                trustScoresAtDecision = trustScores,
                policiesApplied = policiesApplied,
                policyViolations = violations,
                inputDataHash = inputData?.let { hash(it) },
                decision = decision,
                alertDataset = alertDataset,
                stage = stage,
                metadata = metadata?.let { m -> JsonObject(m.mapValues { JsonPrimitive(it.value) }) }
            )
        )
    }

    fun logTrustUpdate(agentId: String, newScore: Int, reason: String) {
        append(
            AuditRecord(
                recordId = UUID.randomUUID().toString(),
                recordType = AuditRecordType.TRUST_UPDATE,
                agentIds = listOf(agentId),
                trustScoresAtDecision = mapOf(agentId to newScore),
                decision = "Trust score updated to $newScore",
                metadata = JsonObject(mapOf("reason" to JsonPrimitive(reason)))
            )
        )
    }

    fun logPolicyChange(policyId: String, changeType: String) {
        append(
            AuditRecord(
                recordId = UUID.randomUUID().toString(),
                recordType = AuditRecordType.POLICY_CHANGE,
                policiesApplied = listOf(policyId),
                decision = "Policy $policyId $changeType",
                metadata = JsonObject(mapOf("change_type" to JsonPrimitive(changeType)))
            )
        )
    }

    fun logHumanOverride(actor: String, action: String, reason: String) {
        append(
            AuditRecord(
                recordId = UUID.randomUUID().toString(),
                recordType = AuditRecordType.HUMAN_OVERRIDE,
                decision = action,
                humanOverrideReason = reason,
                metadata = JsonObject(mapOf("actor" to JsonPrimitive(actor)))
            )
        )
    }

    fun logNegotiation(negotiation: AgentNegotiation) {
        append(
            AuditRecord(
                recordId = UUID.randomUUID().toString(),
                recordType = AuditRecordType.NEGOTIATION,
                agentIds = listOf(negotiation.stage4aPosition.agentId, negotiation.stage4bPosition.agentId),
                decision = negotiation.mediatorProposal,
                alertDataset = negotiation.alertDataset,
                metadata = JsonObject(
                    mapOf(
                        "negotiation_id" to JsonPrimitive(negotiation.negotiationId),
                        "status" to JsonPrimitive(negotiation.status.name),
                        "winner" to JsonPrimitive(negotiation.winnerAgentId ?: "none")
                    )
                )
            )
        )
    }

    fun logEscalation(
        level: EscalationLevel,
        reason: String,
        alertDataset: String,
        agentId: String? = null
    ) {
        append(
            AuditRecord(
                recordId = UUID.randomUUID().toString(),
                recordType = AuditRecordType.ESCALATION,
                agentIds = agentId?.let { listOf(it) } ?: emptyList(),
                decision = level.name,
                alertDataset = alertDataset,
                metadata = JsonObject(
                    mapOf(
                        "escalation_level" to JsonPrimitive(level.label),
                        "reason" to JsonPrimitive(reason)
                    )
                )
            )
        )
    }

    fun readAll(): List<AuditRecord> {
        return try {
            if (!file.exists()) return emptyList()
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        json.decodeFromString(AuditRecord.serializer(), line)
                    } catch (e: Exception) {
                        null
                    }
                }
        } catch (e: Exception) {
            BugLogger.logError("AuditLogger: Failed to read audit log", e)
            emptyList()
        }
    }

    fun search(
        startDate: Long? = null,
        endDate: Long? = null,
        agentId: String? = null,
        policyId: String? = null,
        alertDataset: String? = null,
        recordType: AuditRecordType? = null
    ): List<AuditRecord> {
        return readAll().filter { record ->
            (startDate == null || record.timestamp >= startDate) &&
            (endDate == null || record.timestamp <= endDate) &&
            (agentId == null || record.agentIds.contains(agentId)) &&
            (policyId == null || record.policiesApplied.contains(policyId)) &&
            (alertDataset == null || record.alertDataset == alertDataset) &&
            (recordType == null || record.recordType == recordType)
        }
    }

    fun getDecisionPath(alertDataset: String, stage: Int? = null): List<AuditRecord> {
        return readAll()
            .filter { it.alertDataset == alertDataset && (stage == null || it.stage == stage) }
            .sortedBy { it.timestamp }
    }

    fun exportForCompliance(startDate: Long? = null, endDate: Long? = null): String {
        val records = search(startDate = startDate, endDate = endDate)
        val header = "# DQ Agent GaaS Audit Export\nGenerated: ${System.currentTimeMillis()}\nTotal Records: ${records.size}\n\n"
        val body = records.joinToString("\n---\n") { record ->
            buildString {
                appendLine("Record ID: ${record.recordId}")
                appendLine("Type: ${record.recordType}")
                appendLine("Timestamp: ${record.timestamp}")
                appendLine("Alert: ${record.alertDataset ?: "N/A"}")
                appendLine("Stage: ${record.stage ?: "N/A"}")
                appendLine("Agents: ${record.agentIds.joinToString()}")
                appendLine("Trust Scores: ${record.trustScoresAtDecision}")
                appendLine("Policies: ${record.policiesApplied.joinToString()}")
                appendLine("Decision: ${record.decision ?: "N/A"}")
                appendLine("Human Override: ${record.humanOverrideReason ?: "N/A"}")
                appendLine("Input Hash: ${record.inputDataHash ?: "N/A"}")
            }
        }
        return header + body
    }
}
