package com.llmtest

import com.google.ai.edge.litertlm.Engine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DQPipelineManager(private val engine: Engine) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _currentStage = MutableStateFlow(0)
    val currentStage: StateFlow<Int> = _currentStage
    
    private val _finalReport = MutableStateFlow("")
    val finalReport: StateFlow<String> = _finalReport
    
    // Per-stage outputs for UI visibility
    private val _stage1Output = MutableStateFlow("")
    val stage1Output: StateFlow<String> = _stage1Output
    
    private val _stage2Output = MutableStateFlow("")
    val stage2Output: StateFlow<String> = _stage2Output
    
    private val _stage3Output = MutableStateFlow("")
    val stage3Output: StateFlow<String> = _stage3Output
    
    private val _stage4Output = MutableStateFlow("")
    val stage4Output: StateFlow<String> = _stage4Output
    
    // Sub-stage reports for multi-agent Stage 4
    private val _upstreamReport = MutableStateFlow("")
    val upstreamReport: StateFlow<String> = _upstreamReport
    
    private val _downstreamReport = MutableStateFlow("")
    val downstreamReport: StateFlow<String> = _downstreamReport
    
    // Current alert for GaaS matching
    var currentAlertDataset: String = ""
        private set
    
    fun startAnalysis(alert: DQAlert) {
        scope.launch {
            currentAlertDataset = alert.datasetName
            _currentStage.value = 1
            _finalReport.value = ""
            _stage1Output.value = ""
            _stage2Output.value = ""
            _stage3Output.value = ""
            _stage4Output.value = ""
            _upstreamReport.value = ""
            _downstreamReport.value = ""
            
            // Stage 1: Triage
            GaaSController.preStageHook(alert, 1)
            val stage1 = Stage1Triage.run(alert)
            val affectedReports = stage1.decision == "FULL_ANALYSIS"
            val s1Text = "Triage: ${stage1.decision} (${alert.severity} severity${if (affectedReports) ", downstream reports detected" else ""})"
            _stage1Output.value = s1Text
            handlePostStage(alert, 1, s1Text, mapOf("decision" to (stage1.decision ?: ""), "severity" to alert.severity))
            
            if (stage1.decision == "MINIMAL") {
                val minimal = generateMinimalReport(alert)
                _stage4Output.value = minimal
                _finalReport.value = minimal
                _currentStage.value = 50
                TrustScoreManager.recordDecision("stage1", wasCorrect = true)
                return@launch
            }
            
            // Stage 2: Context (with cooldown)
            _currentStage.value = 2
            delay(10000) // 10s thermal cooldown
            GaaSController.preStageHook(alert, 2)
            val stage2 = Stage2ContextBuilder.run(alert, stage1)
            val entityName = stage2.contextSummary?.lines()?.find { it.startsWith("ENTITY:") }?.removePrefix("ENTITY: ")?.trim() ?: "Unknown"
            val source = stage2.contextSummary?.lines()?.find { it.startsWith("SOURCE:") }?.removePrefix("SOURCE: ")?.trim() ?: "Unknown"
            val s2Text = "Context: $entityName | Source: $source"
            _stage2Output.value = s2Text
            handlePostStage(alert, 2, s2Text, mapOf("entity" to entityName, "source" to source))
            
            // Stage 3: Pattern (with cooldown)
            _currentStage.value = 3
            delay(10000)
            GaaSController.preStageHook(alert, 3)
            val stage3 = Stage3PatternDetector.run(alert, stage2)
            val pattern = stage3.patternType ?: "isolated_incident"
            val ownerLoad = stage3.ownerLoad ?: 0
            val health = stage3.healthScore?.let { "${(it * 100).toInt()}%" } ?: "N/A"
            val s3Text = "Pattern: $pattern | Owner load: $ownerLoad | Group health: $health"
            _stage3Output.value = s3Text
            handlePostStage(alert, 3, s3Text, mapOf("pattern" to pattern, "owner_load" to ownerLoad.toString(), "health" to health))
            
            // Stage 4a: Upstream Researcher (Technical Deep Dive)
            _currentStage.value = 41
            delay(10000)
            GaaSController.preStageHook(alert, 41)
            val stage4a = Stage4UpstreamResearcher.run(alert, stage3, engine)
            _upstreamReport.value = stage4a.upstreamReport
            val s4aText = "Upstream: Technical analysis complete (${stage4a.upstreamReport.length} chars)"
            _stage4Output.value = s4aText
            handlePostStage(alert, 41, stage4a.upstreamReport, mapOf("report_length" to stage4a.upstreamReport.length.toString()))
            
            // Stage 4b: Downstream Researcher (Impact Deep Dive)
            _currentStage.value = 42
            delay(10000)
            GaaSController.preStageHook(alert, 42)
            val stage4b = Stage4DownstreamResearcher.run(alert, stage3, engine)
            _downstreamReport.value = stage4b.downstreamReport
            val s4bText = "Downstream: Impact assessment complete (${stage4b.downstreamReport.length} chars)"
            _stage4Output.value = s4bText
            handlePostStage(alert, 42, stage4b.downstreamReport, mapOf("report_length" to stage4b.downstreamReport.length.toString()))
            
            // GaaS agent negotiation check between 4a and 4b
            val hasConflict = GaaSController.postStage4Hook(alert, stage4a.upstreamReport, stage4b.downstreamReport)
            if (hasConflict) {
                waitForPipelineUnblock()
            }
            
            // Stage 4c: Synthesizer (Executive Narrative)
            _currentStage.value = 43
            delay(10000)
            GaaSController.preStageHook(alert, 43)
            val stage4c = Stage4Synthesizer.run(alert, stage4a, stage4b, stage3, engine)
            val report = stage4c.finalReport ?: "Analysis complete - no report generated"
            handlePostStage(alert, 43, report, mapOf("final_report" to report.take(100)))
            
            // Complete
            _currentStage.value = 50
            _stage4Output.value = report
            _finalReport.value = report
            
            // Record decision accuracy for all agents
            TrustScoreManager.recordDecision("stage1", wasCorrect = true)
            TrustScoreManager.recordDecision("stage2", wasCorrect = true)
            TrustScoreManager.recordDecision("stage3", wasCorrect = true)
            TrustScoreManager.recordDecision("stage4a", wasCorrect = true)
            TrustScoreManager.recordDecision("stage4b", wasCorrect = true)
            TrustScoreManager.recordDecision("stage4c", wasCorrect = true)
        }
    }
    
    private suspend fun handlePostStage(alert: DQAlert, stage: Int, output: String, metadata: Map<String, String>) {
        val result = GaaSController.postStageHook(alert, stage, output, metadata)
        when (result) {
            is PostStageResult.ALLOW -> { /* continue */ }
            is PostStageResult.BLOCK -> {
                waitForPipelineUnblock()
            }
            is PostStageResult.ESCALATE -> {
                waitForPipelineUnblock()
            }
            is PostStageResult.MODIFY -> {
                // Content was auto-remediated, continue
            }
        }
    }
    
    private suspend fun waitForPipelineUnblock() {
        var waited = 0
        while (GaaSController.pipelineBlocked.value && waited < 300) {
            delay(1000)
            waited++
        }
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
