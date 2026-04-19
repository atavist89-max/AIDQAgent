package com.llmtest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MetroMapViewModel {
    // Train position (0.0 = Stage 1, 1.0 = Stage 2, etc.)
    private val _trainPosition = Animatable(0f)
    val trainPosition: Animatable<Float, *> = _trainPosition

    // Map offset for pan
    private val _mapOffsetX = Animatable(0f)
    val mapOffsetX: Animatable<Float, *> = _mapOffsetX

    // Map scale for zoom
    private val _mapScale = Animatable(1f)
    val mapScale: Animatable<Float, *> = _mapScale

    // Current focused station index
    private val _focusedStation = MutableStateFlow(-1)
    val focusedStation: StateFlow<Int> = _focusedStation

    // Policy results for the current pipeline run
    private val _policyResults = MutableStateFlow<List<PolicyResultDisplay>>(emptyList())
    val policyResults: StateFlow<List<PolicyResultDisplay>> = _policyResults

    // Active agent thought
    private val _activeAgentThought = MutableStateFlow<AgentThought?>(null)
    val activeAgentThought: StateFlow<AgentThought?> = _activeAgentThought

    // Negotiation state
    private val _negotiationVisible = MutableStateFlow(false)
    val negotiationVisible: StateFlow<Boolean> = _negotiationVisible

    // Train blocked animation trigger
    private val _trainBlocked = MutableStateFlow(false)
    val trainBlocked: StateFlow<Boolean> = _trainBlocked

    // Score change animation
    private val _scoreChangeEvent = MutableStateFlow<ScoreChangeEvent?>(null)
    val scoreChangeEvent: StateFlow<ScoreChangeEvent?> = _scoreChangeEvent

    suspend fun animateTrainTo(stageIndex: Float, durationMs: Int = 600) {
        _trainPosition.animateTo(
            targetValue = stageIndex,
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
        )
    }

    fun setFocusedStation(index: Int) {
        _focusedStation.value = index
    }

    fun addPolicyResult(result: PolicyResultDisplay) {
        _policyResults.value = _policyResults.value + result
    }

    fun clearPolicyResults() {
        _policyResults.value = emptyList()
    }

    fun setActiveAgentThought(thought: AgentThought?) {
        _activeAgentThought.value = thought
    }

    fun setNegotiationVisible(visible: Boolean) {
        _negotiationVisible.value = visible
    }

    fun triggerTrainBlocked() {
        _trainBlocked.value = true
    }

    fun clearTrainBlocked() {
        _trainBlocked.value = false
    }

    fun triggerScoreChange(agentId: String, oldScore: Int, newScore: Int) {
        _scoreChangeEvent.value = ScoreChangeEvent(agentId, oldScore, newScore, System.currentTimeMillis())
    }

    fun clearScoreChange() {
        _scoreChangeEvent.value = null
    }
}

data class PolicyResultDisplay(
    val policyId: String,
    val policyName: String,
    val category: PolicyCategory,
    val status: PolicyDisplayStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = "",
    val beforeValue: String? = null,
    val afterValue: String? = null
)

enum class PolicyDisplayStatus {
    EVALUATING,
    PASSED,
    BLOCKED,
    REMEDIATED
}

data class AgentThought(
    val agentId: String,
    val agentName: String,
    val agentRole: String,
    val trustScore: Int,
    val autonomyLevel: String,
    val reasoning: String,
    val evidence: List<String>,
    val confidence: Float,
    val isTyping: Boolean = false
)

data class ScoreChangeEvent(
    val agentId: String,
    val oldScore: Int,
    val newScore: Int,
    val timestamp: Long
)
