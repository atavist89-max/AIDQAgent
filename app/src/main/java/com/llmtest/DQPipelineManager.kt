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
    
    fun startAnalysis(alert: DQAlert) {
        scope.launch {
            _currentStage.value = 1
            _finalReport.value = ""
            _stage1Output.value = ""
            _stage2Output.value = ""
            _stage3Output.value = ""
            _stage4Output.value = ""
            _upstreamReport.value = ""
            _downstreamReport.value = ""
            
            // Stage 1: Triage
            val stage1 = Stage1Triage.run(alert)
            val affectedReports = stage1.decision == "FULL_ANALYSIS"
            _stage1Output.value = "Triage: ${stage1.decision} (${alert.severity} severity${if (affectedReports) ", downstream reports detected" else ""})"
            
            if (stage1.decision == "MINIMAL") {
                val minimal = generateMinimalReport(alert)
                _stage4Output.value = minimal
                _finalReport.value = minimal
                _currentStage.value = 5
                return@launch
            }
            
            // Stage 2: Context (with cooldown)
            _currentStage.value = 2
            delay(10000) // 10s thermal cooldown
            val stage2 = Stage2ContextBuilder.run(alert, stage1)
            val entityName = stage2.contextSummary?.lines()?.find { it.startsWith("ENTITY:") }?.removePrefix("ENTITY: ")?.trim() ?: "Unknown"
            val source = stage2.contextSummary?.lines()?.find { it.startsWith("SOURCE:") }?.removePrefix("SOURCE: ")?.trim() ?: "Unknown"
            _stage2Output.value = "Context: $entityName | Source: $source"
            
            // Stage 3: Pattern (with cooldown)
            _currentStage.value = 3
            delay(10000)
            val stage3 = Stage3PatternDetector.run(alert, stage2)
            val pattern = stage3.patternType ?: "isolated_incident"
            val ownerLoad = stage3.ownerLoad ?: 0
            val health = stage3.healthScore?.let { "${(it * 100).toInt()}%" } ?: "N/A"
            _stage3Output.value = "Pattern: $pattern | Owner load: $ownerLoad | Group health: $health"
            
            // Stage 4a: Upstream Researcher (Technical Deep Dive)
            _currentStage.value = 41
            delay(10000)
            val stage4a = Stage4UpstreamResearcher.run(alert, stage3, engine)
            _upstreamReport.value = stage4a.upstreamReport
            _stage4Output.value = "Upstream: Technical analysis complete (${stage4a.upstreamReport.length} chars)"
            
            // Stage 4b: Downstream Researcher (Impact Deep Dive)
            _currentStage.value = 42
            delay(10000)
            val stage4b = Stage4DownstreamResearcher.run(alert, stage3, engine)
            _downstreamReport.value = stage4b.downstreamReport
            _stage4Output.value = "Downstream: Impact assessment complete (${stage4b.downstreamReport.length} chars)"
            
            // Stage 4c: Synthesizer (Executive Narrative)
            _currentStage.value = 43
            delay(10000)
            val stage4c = Stage4Synthesizer.run(alert, stage4a, stage4b, stage3, engine)
            
            // Complete
            _currentStage.value = 5
            val report = stage4c.finalReport ?: "Analysis complete - no report generated"
            _stage4Output.value = report
            _finalReport.value = report
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
