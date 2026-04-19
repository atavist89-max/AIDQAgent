package com.llmtest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

object TrustScoreManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = GhostPaths.GAASTrustScores

    // Default agents in the pipeline
    private val defaultAgents = listOf(
        TrustScore("stage1", "Triage Agent", "Stage 1: Alert classification and routing"),
        TrustScore("stage2", "Context Builder", "Stage 2: Entity and schema context assembly"),
        TrustScore("stage3", "Pattern Detector", "Stage 3: Anomaly and pattern identification"),
        TrustScore("stage4a", "Upstream Researcher", "Stage 4a: Technical root cause analysis"),
        TrustScore("stage4b", "Downstream Researcher", "Stage 4b: Business impact assessment"),
        TrustScore("stage4c", "Synthesizer", "Stage 4c: Executive narrative generation"),
        TrustScore("gaas_mediator", "GaaS Mediator", "Governance mediation and conflict resolution")
    )

    private fun ensureFile(): TrustScoreFile {
        return try {
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(TrustScoreFile.serializer(), content)
                } else {
                    createDefault()
                }
            } else {
                createDefault()
            }
        } catch (e: Exception) {
            BugLogger.logError("TrustScoreManager: Failed to load trust scores", e)
            createDefault()
        }
    }

    private fun createDefault(): TrustScoreFile {
        val data = TrustScoreFile(agents = defaultAgents)
        persist(data)
        return data
    }

    private fun persist(data: TrustScoreFile) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(TrustScoreFile.serializer(), data))
        } catch (e: Exception) {
            BugLogger.logError("TrustScoreManager: Failed to persist trust scores", e)
        }
    }

    fun getAllScores(): List<TrustScore> = ensureFile().agents

    fun getScore(agentId: String): TrustScore? {
        return ensureFile().agents.find { it.agentId == agentId }
    }

    fun getAutonomyLevel(agentId: String): AutonomyLevel {
        val score = getScore(agentId)?.score ?: 0
        return AutonomyLevel.fromScore(score)
    }

    fun updateScore(agentId: String, newScore: Int, reason: String) {
        val clamped = newScore.coerceIn(0, 100)
        val data = ensureFile()
        val updated = data.agents.map { agent ->
            if (agent.agentId == agentId) {
                val history = agent.scoreHistory + ScoreSnapshot(clamped, System.currentTimeMillis(), reason)
                agent.copy(
                    score = clamped,
                    scoreHistory = history.takeLast(50),
                    lastUpdated = System.currentTimeMillis()
                )
            } else agent
        }
        persist(data.copy(agents = updated, lastUpdated = System.currentTimeMillis()))
        AuditLogger.logTrustUpdate(agentId, clamped, reason)
    }

    fun recordDecision(agentId: String, wasCorrect: Boolean) {
        val data = ensureFile()
        val updated = data.agents.map { agent ->
            if (agent.agentId == agentId) {
                val total = agent.decisionsTotal + 1
                val correct = agent.decisionsCorrect + if (wasCorrect) 1 else 0
                val incorrect = agent.decisionsIncorrect + if (!wasCorrect) 1 else 0
                val accuracy = if (total > 0) correct.toFloat() / total else 0f
                agent.copy(
                    decisionsTotal = total,
                    decisionsCorrect = correct,
                    decisionsIncorrect = incorrect,
                    accuracyRate = accuracy,
                    lastUpdated = System.currentTimeMillis()
                )
            } else agent
        }
        persist(data.copy(agents = updated, lastUpdated = System.currentTimeMillis()))
    }

    fun manualOverride(agentId: String, newScore: Int, reason: String) {
        val clamped = newScore.coerceIn(0, 100)
        val data = ensureFile()
        val updated = data.agents.map { agent ->
            if (agent.agentId == agentId) {
                val history = agent.scoreHistory + ScoreSnapshot(clamped, System.currentTimeMillis(), "Manual override: $reason")
                agent.copy(
                    score = clamped,
                    scoreHistory = history.takeLast(50),
                    lastUpdated = System.currentTimeMillis(),
                    manualOverrideReason = reason
                )
            } else agent
        }
        persist(data.copy(agents = updated, lastUpdated = System.currentTimeMillis()))
        AuditLogger.logHumanOverride(
            actor = "coordinator",
            action = "Manual trust score override for $agentId to $clamped",
            reason = reason
        )
    }

    fun adjustForDebate(agentId: String, won: Boolean) {
        val current = getScore(agentId)?.score ?: 50
        val delta = if (won) 2 else -1
        updateScore(agentId, current + delta, if (won) "Won negotiation debate" else "Lost negotiation debate")
    }

    fun getSnapshot(): Map<String, Int> {
        return ensureFile().agents.associate { it.agentId to it.score }
    }
}
