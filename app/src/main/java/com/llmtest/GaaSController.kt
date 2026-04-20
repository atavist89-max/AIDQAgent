package com.llmtest

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object GaaSController {

    private val _governanceStatus = MutableStateFlow(GovernanceStatus.IDLE)
    val governanceStatus: StateFlow<GovernanceStatus> = _governanceStatus

    private val _currentGate = MutableStateFlow<String?>(null)
    val currentGate: StateFlow<String?> = _currentGate

    private val _blockState = MutableStateFlow<GaaSBlockState?>(null)
    val blockState: StateFlow<GaaSBlockState?> = _blockState

    private val _pipelineBlocked = MutableStateFlow(false)
    val pipelineBlocked: StateFlow<Boolean> = _pipelineBlocked

    private val _humanInterventionRequired = MutableStateFlow(false)
    val humanInterventionRequired: StateFlow<Boolean> = _humanInterventionRequired

    private val _humanResolution = MutableStateFlow<HumanResolution?>(null)

    // Gate visual states for the 5 gaps
    private val _gateVisualStates = MutableStateFlow<Map<String, GateVisualState>>(emptyMap())
    val gateVisualStates: StateFlow<Map<String, GateVisualState>> = _gateVisualStates

    fun initialize() {
        GovernanceConfig.loadPolicies()
        GovernanceConfig.loadStationPrompts()
        _governanceStatus.value = GovernanceStatus.IDLE
        _pipelineBlocked.value = false
        _humanInterventionRequired.value = false
        _blockState.value = null
        _humanResolution.value = null
        _currentGate.value = null
        resetGateVisualStates()
        BugLogger.log("Fast GaaS Controller initialized")
    }

    fun resetGateVisualStates() {
        val policies = GovernanceConfig.loadPolicies()
        val states = mutableMapOf<String, GateVisualState>()
        listOf("gate1", "gate2", "gate3", "gate4", "gate5").forEach { gateId ->
            val policy = policies.find { it.gateId == gateId }
            states[gateId] = if (policy?.enabled == true) GateVisualState.ACTIVE else GateVisualState.INACTIVE
        }
        _gateVisualStates.value = states
    }

    fun updateGateVisualState(gateId: String, state: GateVisualState) {
        val current = _gateVisualStates.value.toMutableMap()
        current[gateId] = state
        _gateVisualStates.value = current
    }

    fun checkGapPolicy(completedStage: Int): GaaSPolicy? {
        val gateId = when (completedStage) {
            1 -> "gate1"
            2 -> "gate2"
            3 -> "gate3"
            41 -> "gate4"
            42 -> "gate5"
            else -> return null
        }
        _currentGate.value = gateId
        val policy = GovernanceConfig.getPolicy(gateId)
        return if (policy?.enabled == true) policy else null
    }

    fun getGateIdForStage(stage: Int): String? {
        return when (stage) {
            1 -> "gate1"
            2 -> "gate2"
            3 -> "gate3"
            41 -> "gate4"
            42 -> "gate5"
            else -> null
        }
    }

    fun buildReviewPrompt(policy: GaaSPolicy, stageOutput: String): String {
        return buildString {
            appendLine("You are the Governance Agent. Review the following output against the policy. Respond only with APPROVED: [reasoning] or REJECTED: [feedback]. If REJECTED, provide structured bullet-point feedback.")
            appendLine()
            appendLine("=== POLICY ===")
            appendLine(policy.prompt)
            appendLine()
            appendLine("=== OUTPUT TO REVIEW ===")
            appendLine(stageOutput.take(1500)) // Truncate to stay within token limits
        }
    }

    fun parseReviewResponse(response: String): ReviewResult {
        val trimmed = response.trim()
        return when {
            trimmed.startsWith("APPROVED:", ignoreCase = true) -> {
                ReviewResult.Approved(trimmed.removePrefix("APPROVED:").trim())
            }
            trimmed.startsWith("REJECTED:", ignoreCase = true) -> {
                val feedbackText = trimmed.removePrefix("REJECTED:").trim()
                val bullets = feedbackText.lines()
                    .map { it.trim() }
                    .filter { it.startsWith("-") || it.startsWith("•") || it.startsWith("*") || it.isNotBlank() }
                    .map { it.trimStart('-', '•', ' ', '*') }
                    .filter { it.isNotBlank() }
                ReviewResult.Rejected(bullets.ifEmpty { listOf(feedbackText) })
            }
            else -> {
                // Fallback heuristics
                when {
                    trimmed.contains("APPROVED", ignoreCase = true) -> ReviewResult.Approved(trimmed)
                    trimmed.contains("REJECTED", ignoreCase = true) -> {
                        val bullets = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
                        ReviewResult.Rejected(bullets)
                    }
                    else -> ReviewResult.Approved("Parsed as approved (response did not contain clear APPROVED/REJECTED marker)")
                }
            }
        }
    }

    fun markReviewing(gateId: String) {
        _governanceStatus.value = GovernanceStatus.REVIEWING
        updateGateVisualState(gateId, GateVisualState.REVIEWING)
    }

    fun markApproved(gateId: String) {
        _governanceStatus.value = GovernanceStatus.APPROVED
        _pipelineBlocked.value = false
        updateGateVisualState(gateId, GateVisualState.APPROVED)
        GovernanceConfig.clearBlockState()
    }

    fun markRejected(gateId: String, previousStage: Int, feedback: List<String>, outputSnapshot: String) {
        val currentBlock = _blockState.value
        val history = currentBlock?.history?.toMutableList() ?: mutableListOf()

        // Save previous attempt to history if it exists
        currentBlock?.let { block ->
            if (block.outputSnapshot.isNotBlank()) {
                history.add(
                    AttemptRecord(
                        attemptNumber = block.retryCount,
                        outputSnapshot = block.outputSnapshot,
                        feedback = block.feedback
                    )
                )
            }
        }

        val newRetryCount = (currentBlock?.retryCount ?: 0) + 1
        val newBlock = GaaSBlockState(
            gateId = gateId,
            previousStage = previousStage,
            feedback = feedback,
            retryCount = newRetryCount,
            timestamp = System.currentTimeMillis(),
            history = history,
            outputSnapshot = outputSnapshot
        )
        _blockState.value = newBlock
        GovernanceConfig.saveBlockState(newBlock)
        _governanceStatus.value = GovernanceStatus.REJECTED
        _pipelineBlocked.value = true
        updateGateVisualState(gateId, GateVisualState.REJECTED)

        BugLogger.log("Gate $gateId rejected (attempt $newRetryCount): ${feedback.joinToString("; ")}")
    }

    fun requireHumanIntervention() {
        _humanInterventionRequired.value = true
    }

    fun resolveHumanIntervention(acceptStation: Boolean) {
        val gateId = _currentGate.value ?: return
        if (acceptStation) {
            _governanceStatus.value = GovernanceStatus.OVERRIDDEN
            _pipelineBlocked.value = false
            _humanResolution.value = HumanResolution.Proceed
            updateGateVisualState(gateId, GateVisualState.OVERRIDDEN)
            BugLogger.log("Human intervention: accepted station version at $gateId")
        } else {
            _governanceStatus.value = GovernanceStatus.ABANDONED
            _pipelineBlocked.value = false
            _humanResolution.value = HumanResolution.Abandon
            updateGateVisualState(gateId, GateVisualState.REJECTED)
            BugLogger.log("Human intervention: accepted GaaS assessment at $gateId (abandoned)")
        }
        _humanInterventionRequired.value = false
    }

    suspend fun waitForHumanResolution(): HumanResolution? {
        var waited = 0
        while (_humanResolution.value == null && waited < 600) { // 10 minutes max
            delay(1000)
            waited++
        }
        return _humanResolution.value.also { _humanResolution.value = null }
    }

    fun abandonPipeline() {
        _governanceStatus.value = GovernanceStatus.ABANDONED
        _pipelineBlocked.value = false
        _humanInterventionRequired.value = false
    }
}
