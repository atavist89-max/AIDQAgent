package com.llmtest

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.contentOrNull

object PolicyEngine {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = GhostPaths.GAASPolicyRules

    private val defaultPolicies = listOf(
        PolicyRule(
            policyId = "critical_alert_oversight",
            name = "Critical Alert Human Oversight",
            description = "Critical severity alerts require coordinator approval regardless of Trust Score",
            category = PolicyCategory.PROCESS,
            isActive = true,
            conditions = emptyList(),
            action = PolicyAction.ESCALATE_TO_HUMAN,
            escalationRequired = true,
            gateLabel = "TRUST",
            gateOrder = 0
        ),
        PolicyRule(
            policyId = "pii_prevention",
            name = "PII Exposure Prevention",
            description = "Blocks any output containing identifiable customer data patterns",
            category = PolicyCategory.CONTENT,
            isActive = true,
            conditions = emptyList(), // regex evaluated at runtime
            action = PolicyAction.REDACT_AND_LOG,
            escalationRequired = false,
            gateLabel = "PII",
            gateOrder = 1
        ),
        PolicyRule(
            policyId = "exec_contact_approval",
            name = "Executive Contact Approval",
            description = "Requires human approval before contacting Class 2 report owners unless agent Trust Score > 90",
            category = PolicyCategory.COMMUNICATION,
            isActive = true,
            conditions = emptyList(),
            action = PolicyAction.ESCALATE_TO_HUMAN,
            escalationRequired = true,
            gateLabel = "EXEC",
            gateOrder = 2
        ),
        PolicyRule(
            policyId = "audit_trail_completeness",
            name = "Audit Trail Completeness",
            description = "Ensures all decisions include required lineage metadata",
            category = PolicyCategory.FACTUAL,
            isActive = true,
            conditions = emptyList(),
            action = PolicyAction.BLOCK_AND_REQUEST_RETRY,
            escalationRequired = false,
            gateLabel = "AUDIT",
            gateOrder = 3
        ),
        PolicyRule(
            policyId = "schema_drift_validation",
            name = "Schema Drift Validation",
            description = "Validates claimed schema changes against actual catalog metadata",
            category = PolicyCategory.FACTUAL,
            isActive = true,
            conditions = emptyList(),
            action = PolicyAction.BLOCK_AND_REQUEST_RETRY,
            escalationRequired = false,
            gateLabel = "SCHEMA",
            gateOrder = 4
        )
    )

    private fun ensureFile(): PolicyRulesFile {
        return try {
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(PolicyRulesFile.serializer(), content)
                } else {
                    createDefault()
                }
            } else {
                createDefault()
            }
        } catch (e: Exception) {
            BugLogger.logError("PolicyEngine: Failed to load policies", e)
            createDefault()
        }
    }

    private fun createDefault(): PolicyRulesFile {
        val data = PolicyRulesFile(policies = defaultPolicies)
        persist(data)
        return data
    }

    private fun persist(data: PolicyRulesFile) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(PolicyRulesFile.serializer(), data))
        } catch (e: Exception) {
            BugLogger.logError("PolicyEngine: Failed to persist policies", e)
        }
    }

    fun getPolicies(): List<PolicyRule> = ensureFile().policies

    fun getActivePolicies(): List<PolicyRule> = ensureFile().policies.filter { it.isActive }

    fun getPolicy(policyId: String): PolicyRule? = ensureFile().policies.find { it.policyId == policyId }

    fun addPolicy(policy: PolicyRule) {
        val data = ensureFile()
        val updated = data.policies.filter { it.policyId != policy.policyId } + policy
        persist(data.copy(policies = updated, lastUpdated = System.currentTimeMillis()))
        AuditLogger.logPolicyChange(policy.policyId, "ADDED")
    }

    fun updatePolicy(policy: PolicyRule) {
        val data = ensureFile()
        val updated = data.policies.map { if (it.policyId == policy.policyId) policy.copy(updatedAt = System.currentTimeMillis()) else it }
        persist(data.copy(policies = updated, lastUpdated = System.currentTimeMillis()))
        AuditLogger.logPolicyChange(policy.policyId, "UPDATED")
    }

    fun removePolicy(policyId: String) {
        val data = ensureFile()
        val updated = data.policies.filter { it.policyId != policyId }
        persist(data.copy(policies = updated, lastUpdated = System.currentTimeMillis()))
        AuditLogger.logPolicyChange(policyId, "REMOVED")
    }

    fun togglePolicy(policyId: String, active: Boolean) {
        val data = ensureFile()
        val updated = data.policies.map { if (it.policyId == policyId) it.copy(isActive = active, updatedAt = System.currentTimeMillis()) else it }
        persist(data.copy(policies = updated, lastUpdated = System.currentTimeMillis()))
        AuditLogger.logPolicyChange(policyId, if (active) "ENABLED" else "DISABLED")
    }

    // Evaluate content against policies and return violations
    fun evaluate(
        content: String,
        alert: DQAlert? = null,
        agentId: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): List<PolicyViolation> {
        val violations = mutableListOf<PolicyViolation>()
        val policies = getActivePolicies()
        val trustScore = agentId?.let { TrustScoreManager.getScore(it)?.score } ?: 0

        for (policy in policies) {
            when (policy.policyId) {
                "pii_prevention" -> {
                    val piiPatterns = listOf(
                        Regex("""CP-\d{5,}"""),
                        Regex("""OH-\d{5,}"""),
                        Regex("""\b\d{3}-\d{2}-\d{4}\b"""), // SSN-like
                        Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b""")
                    )
                    for (pattern in piiPatterns) {
                        val match = pattern.find(content)
                        if (match != null) {
                            violations.add(
                                PolicyViolation(
                                    policyId = policy.policyId,
                                    policyName = policy.name,
                                    category = policy.category,
                                    reason = "Potential PII detected: ${match.value.take(20)}",
                                    triggeredContent = match.value,
                                    remediation = "Content redacted automatically"
                                )
                            )
                            break
                        }
                    }
                }
                "exec_contact_approval" -> {
                    if (metadata["report_class"] == "2" && trustScore < 90) {
                        violations.add(
                            PolicyViolation(
                                policyId = policy.policyId,
                                policyName = policy.name,
                                category = policy.category,
                                reason = "Executive contact (Class 2 report) requires approval for agent with trust score $trustScore",
                                remediation = "Escalate to Data Quality Coordinator"
                            )
                        )
                    }
                }
                "critical_alert_oversight" -> {
                    if (alert?.severity?.equals("Critical", ignoreCase = true) == true) {
                        violations.add(
                            PolicyViolation(
                                policyId = policy.policyId,
                                policyName = policy.name,
                                category = policy.category,
                                reason = "Critical severity alert requires human oversight",
                                remediation = "Escalate to Data Quality Coordinator for approval"
                            )
                        )
                    }
                }
                "audit_trail_completeness" -> {
                    val required = listOf("alert_dataset", "source_system", "confidence_score", "recommendation_type")
                    val missing = required.filter { metadata[it].isNullOrBlank() }
                    if (missing.isNotEmpty()) {
                        violations.add(
                            PolicyViolation(
                                policyId = policy.policyId,
                                policyName = policy.name,
                                category = policy.category,
                                reason = "Missing required lineage metadata: ${missing.joinToString()}",
                                remediation = "Block and request retry with complete metadata"
                            )
                        )
                    }
                }
                "schema_drift_validation" -> {
                    if (metadata["dimension"] == "Adaptability" && metadata["schema_validated"] != "true") {
                        violations.add(
                            PolicyViolation(
                                policyId = policy.policyId,
                                policyName = policy.name,
                                category = policy.category,
                                reason = "Schema drift claim not validated against catalog metadata",
                                remediation = "Block and request retry with validated schema reference"
                            )
                        )
                    }
                }
                else -> {
                    // Dynamic policy evaluation
                    evaluateDynamicPolicy(policy, content, alert, metadata, trustScore)?.let {
                        violations.add(it)
                    }
                }
            }
        }
        return violations
    }

    private fun evaluateDynamicPolicy(
        policy: PolicyRule,
        content: String,
        alert: DQAlert?,
        metadata: Map<String, String>,
        trustScore: Int
    ): PolicyViolation? {
        for (condition in policy.conditions) {
            val strValue = (condition.value as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: condition.value.toString()
            val matches = when (condition.field) {
                "severity" -> alert?.severity.equals(strValue, ignoreCase = true)
                "trust_score" -> (condition.value as? kotlinx.serialization.json.JsonPrimitive)?.let { evaluateNumericCondition(trustScore, condition.operator, it) } ?: false
                "report_class" -> metadata["report_class"] == strValue
                "dimension" -> alert?.dimension.equals(strValue, ignoreCase = true)
                "content_contains" -> content.contains(strValue, ignoreCase = true)
                else -> false
            }
            if (matches) {
                return PolicyViolation(
                    policyId = policy.policyId,
                    policyName = policy.name,
                    category = policy.category,
                    reason = "Dynamic policy '${policy.name}' triggered: ${condition.field} ${condition.operator} ${condition.value}",
                    triggeredContent = content.take(200),
                    remediation = when (policy.action) {
                        PolicyAction.REDACT_AND_LOG -> "Content redacted automatically"
                        PolicyAction.ESCALATE_TO_HUMAN -> "Escalated to Data Quality Coordinator"
                        PolicyAction.BLOCK_AND_REQUEST_RETRY -> "Blocked pending retry with corrections"
                        PolicyAction.APPROVE -> null
                    }
                )
            }
        }
        return null
    }

    private fun evaluateNumericCondition(actual: Int, operator: String, value: JsonPrimitive): Boolean {
        val target = value.intOrNull ?: value.floatOrNull?.toInt() ?: return false
        return when (operator) {
            "==" -> actual == target
            "!=" -> actual != target
            "<" -> actual < target
            ">" -> actual > target
            "<=" -> actual <= target
            ">=" -> actual >= target
            else -> false
        }
    }

    fun applyRemediation(content: String, violations: List<PolicyViolation>): String {
        var result = content
        for (v in violations) {
            when (v.policyId) {
                "pii_prevention" -> {
                    result = result.replace(Regex("""CP-\d{5,}"""), "[REDACTED-CP]")
                    result = result.replace(Regex("""OH-\d{5,}"""), "[REDACTED-OH]")
                    result = result.replace(Regex("""\b\d{3}-\d{2}-\d{4}\b"""), "[REDACTED-SSN]")
                    result = result.replace(Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b"""), "[REDACTED-EMAIL]")
                }
            }
        }
        return result
    }
}
