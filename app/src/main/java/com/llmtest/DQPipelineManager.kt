package com.llmtest

import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DQPipelineManager(private val engine: Engine) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentStage = MutableStateFlow(0)
    val currentStage: StateFlow<Int> = _currentStage

    private val _finalReport = MutableStateFlow("")
    val finalReport: StateFlow<String> = _finalReport

    private val _stage1Output = MutableStateFlow("")
    val stage1Output: StateFlow<String> = _stage1Output

    private val _stage2Output = MutableStateFlow("")
    val stage2Output: StateFlow<String> = _stage2Output

    private val _stage3Output = MutableStateFlow("")
    val stage3Output: StateFlow<String> = _stage3Output

    private val _stage4Output = MutableStateFlow("")
    val stage4Output: StateFlow<String> = _stage4Output

    private val _upstreamReport = MutableStateFlow("")
    val upstreamReport: StateFlow<String> = _upstreamReport

    private val _downstreamReport = MutableStateFlow("")
    val downstreamReport: StateFlow<String> = _downstreamReport

    var currentAlertDataset: String = ""
        private set

    private var _abandoned = false

    fun startAnalysis(alert: DQAlert) {
        scope.launch {
            try {
                currentAlertDataset = alert.datasetName
                _abandoned = false
                resetState()
                GaaSController.resetGateVisualStates()

                // Stage 1: Triage
                _currentStage.value = 1
                val stage1 = Stage1Triage.run(alert)
                val affectedReports = stage1.decision == "FULL_ANALYSIS"
                val s1Text = "Triage: ${stage1.decision} (${alert.severity} severity${if (affectedReports) ", downstream reports detected" else ""})"
                _stage1Output.value = s1Text

                if (!handleGapReview(1, s1Text, alert) { null }) return@launch

                if (stage1.decision == "MINIMAL") {
                    val minimal = generateMinimalReport(alert)
                    _stage4Output.value = minimal
                    _finalReport.value = minimal
                    _currentStage.value = 50
                    return@launch
                }

                // Stage 2: Context (with cooldown)
                _currentStage.value = 2
                delay(10000)
                val stage2 = Stage2ContextBuilder.run(alert, stage1)
                val entityName = stage2.contextSummary?.lines()?.find { it.startsWith("ENTITY:") }?.removePrefix("ENTITY: ")?.trim() ?: "Unknown"
                val source = stage2.contextSummary?.lines()?.find { it.startsWith("SOURCE:") }?.removePrefix("SOURCE: ")?.trim() ?: "Unknown"
                val s2Text = "Context: $entityName | Source: $source"
                _stage2Output.value = s2Text

                if (!handleGapReview(2, s2Text, alert) { null }) return@launch

                // Stage 3: Pattern (with cooldown)
                _currentStage.value = 3
                delay(10000)
                val stage3 = Stage3PatternDetector.run(alert, stage2)
                val pattern = stage3.patternType ?: "isolated_incident"
                val ownerLoad = stage3.ownerLoad ?: 0
                val health = stage3.healthScore?.let { "${(it * 100).toInt()}%" } ?: "N/A"
                val s3Text = "Pattern: $pattern | Owner load: $ownerLoad | Group health: $health"
                _stage3Output.value = s3Text

                if (!handleGapReview(3, s3Text, alert) { null }) return@launch

                // Stage 4a: Upstream Researcher (Technical Deep Dive)
                _currentStage.value = 41
                delay(10000)
                var stage4a = Stage4UpstreamResearcher.run(alert, stage3, engine)
                _upstreamReport.value = stage4a.upstreamReport
                val s4aText = "Upstream: Technical analysis complete (${stage4a.upstreamReport.length} chars)"
                _stage4Output.value = s4aText

                if (!handleGapReview(41, stage4a.upstreamReport, alert) { feedback ->
                    val newState = Stage4UpstreamResearcher.run(alert, stage3, engine, feedback)
                    stage4a = newState
                    _upstreamReport.value = newState.upstreamReport
                    _stage4Output.value = "Upstream: Technical analysis complete (${newState.upstreamReport.length} chars)"
                    newState.upstreamReport
                }) return@launch

                // Stage 4b: Downstream Researcher (Impact Deep Dive)
                _currentStage.value = 42
                delay(10000)
                var stage4b = Stage4DownstreamResearcher.run(alert, stage3, engine)
                _downstreamReport.value = stage4b.downstreamReport
                val s4bText = "Downstream: Impact assessment complete (${stage4b.downstreamReport.length} chars)"
                _stage4Output.value = s4bText

                if (!handleGapReview(42, stage4b.downstreamReport, alert) { feedback ->
                    val newState = Stage4DownstreamResearcher.run(alert, stage3, engine, feedback)
                    stage4b = newState
                    _downstreamReport.value = newState.downstreamReport
                    _stage4Output.value = "Downstream: Impact assessment complete (${newState.downstreamReport.length} chars)"
                    newState.downstreamReport
                }) return@launch

                // Stage 4c: Synthesizer (Executive Narrative)
                _currentStage.value = 43
                delay(10000)
                var stage4c = Stage4Synthesizer.run(alert, stage4a, stage4b, stage3, engine)
                val report = stage4c.finalReport ?: "Analysis complete - no report generated"
                _stage4Output.value = report

                if (!handleGapReview(43, report, alert) { feedback ->
                    val newState = Stage4Synthesizer.run(alert, stage4a, stage4b, stage3, engine, feedback)
                    stage4c = newState
                    _stage4Output.value = newState.finalReport ?: "Analysis complete - no report generated"
                    newState.finalReport ?: ""
                }) return@launch

                // Complete
                _currentStage.value = 50
                _finalReport.value = report

            } catch (e: Exception) {
                BugLogger.logError("Pipeline failed", e)
            }
        }
    }

    /**
     * Handles the GaaS gap review for a completed stage.
     * Returns true if the pipeline should proceed (approved or overridden).
     * Returns false if the pipeline was abandoned.
     */
    private suspend fun handleGapReview(
        stage: Int,
        output: String,
        alert: DQAlert,
        reRunBlock: suspend (feedback: List<String>) -> String?
    ): Boolean {
        val policy = GaaSController.checkGapPolicy(stage)
        if (policy == null || !policy.enabled) {
            return true // No policy configured or disabled, proceed immediately
        }

        val gateId = GaaSController.getGateIdForStage(stage) ?: return true
        var currentOutput = output
        var attempt = 1

        while (attempt <= 3) {
            GaaSController.markReviewing(gateId)

            val prompt = GaaSController.buildReviewPrompt(policy, currentOutput)
            val response = runGaaSLLM(prompt)
            val result = GaaSController.parseReviewResponse(response)

            when (result) {
                is ReviewResult.Approved -> {
                    GaaSController.markApproved(gateId)
                    return true
                }
                is ReviewResult.Rejected -> {
                    GaaSController.markRejected(gateId, stage, result.feedback, currentOutput)

                    if (attempt == 3) {
                        // Maximum retries reached — human intervention required
                        GaaSController.requireHumanIntervention()
                        val resolution = GaaSController.waitForHumanResolution()
                        return when (resolution) {
                            is HumanResolution.Proceed -> {
                                GaaSController.updateGateVisualState(gateId, GateVisualState.OVERRIDDEN)
                                true
                            }
                            is HumanResolution.Abandon -> {
                                _abandoned = true
                                false
                            }
                            else -> {
                                _abandoned = true
                                false
                            }
                        }
                    }

                    // Prepare retry
                    if (stage >= 41) {
                        // LLM stage: re-run with feedback injected
                        val newOutput = reRunBlock(result.feedback)
                        if (newOutput != null) {
                            currentOutput = newOutput
                        }
                    }
                    // Deterministic stages: output is identical, just re-review
                    attempt++
                }
            }
        }

        return true
    }

    private suspend fun runGaaSLLM(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val conversation = engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(temperature = 0.2, topK = 40, topP = 0.9)
                )
            )
            conversation.use {
                it.sendMessage(Message.of(prompt)).toString()
            }
        } catch (e: Exception) {
            BugLogger.logError("GaaS LLM review failed", e)
            "APPROVED: Review could not be completed due to model error. Proceeding by default."
        }
    }

    private fun resetState() {
        _currentStage.value = 0
        _finalReport.value = ""
        _stage1Output.value = ""
        _stage2Output.value = ""
        _stage3Output.value = ""
        _stage4Output.value = ""
        _upstreamReport.value = ""
        _downstreamReport.value = ""
    }

    private fun generateMinimalReport(alert: DQAlert): String {
        return """
            IMPACT: Low - ${alert.severity} check, routine monitoring only
            ROOT_CAUSE: Standard operational variance in ${alert.datasourceName}
            ACTION: No action required, monitor trends
            URGENCY: None - no KPI target for ${alert.severity} severity
        """.trimIndent()
    }

    fun cancel() {
        scope.cancel()
    }
}
