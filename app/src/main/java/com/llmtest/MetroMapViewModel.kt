package com.llmtest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
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

    // Active agent thought
    private val _activeAgentThought = MutableStateFlow<AgentThought?>(null)
    val activeAgentThought: StateFlow<AgentThought?> = _activeAgentThought

    // Train blocked animation trigger
    private val _trainBlocked = MutableStateFlow(false)
    val trainBlocked: StateFlow<Boolean> = _trainBlocked

    suspend fun animateTrainTo(stageIndex: Float, durationMs: Int = 600) {
        _trainPosition.animateTo(
            targetValue = stageIndex,
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
        )
    }

    fun setFocusedStation(index: Int) {
        _focusedStation.value = index
    }

    fun setActiveAgentThought(thought: AgentThought?) {
        _activeAgentThought.value = thought
    }

    fun triggerTrainBlocked() {
        _trainBlocked.value = true
    }

    fun clearTrainBlocked() {
        _trainBlocked.value = false
    }
}

data class AgentThought(
    val agentId: String,
    val agentName: String,
    val agentRole: String,
    val reasoning: String,
    val evidence: List<String>,
    val confidence: Float,
    val isTyping: Boolean = false
)
