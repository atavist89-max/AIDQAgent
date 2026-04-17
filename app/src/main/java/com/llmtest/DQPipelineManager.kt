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
    
    fun startAnalysis(alert: DQAlert) {
        scope.launch {
            _currentStage.value = 1
            _finalReport.value = ""
            
            // Stage 1: Triage
            val stage1 = Stage1Triage.run(alert)
            
            if (stage1.decision == "MINIMAL") {
                _finalReport.value = generateMinimalReport(alert)
                _currentStage.value = 5
                return@launch
            }
            
            // Stage 2: Context (with cooldown)
            _currentStage.value = 2
            delay(10000) // 10s thermal cooldown
            val stage2 = Stage2ContextBuilder.run(alert, stage1)
            
            // Stage 3: Pattern (with cooldown)
            _currentStage.value = 3
            delay(10000)
            val stage3 = Stage3PatternDetector.run(alert, stage2)
            
            // Stage 4: Synthesis (with cooldown)
            _currentStage.value = 4
            delay(10000)
            val stage4 = Stage4Synthesis.run(alert, stage3, engine)
            
            // Complete
            _currentStage.value = 5
            _finalReport.value = stage4.finalReport ?: "Analysis complete - no report generated"
        }
    }
    
    private fun generateMinimalReport(alert: DQAlert): String {
        return """
            IMPACT: Low - Informative check, routine monitoring only
            ROOT_CAUSE: Standard operational variance
            ACTION: No action required, monitor trends
            URGENCY: None - no KPI target for Informative severity
        """.trimIndent()
    }
    
    fun cancel() {
        scope.cancel()
    }
}
