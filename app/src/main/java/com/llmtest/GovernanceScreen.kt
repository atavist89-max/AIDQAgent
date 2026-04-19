package com.llmtest

import androidx.compose.animation.*
import androidx.compose.runtime.*

enum class GovernanceRoute {
    DASHBOARD,
    AGENT_DETAIL,
    POLICY_EDITOR,
    DECISION_QUEUE,
    AUDIT_PLAYBACK
}

@Composable
fun GovernanceScreen() {
    var currentRoute by remember { mutableStateOf(GovernanceRoute.DASHBOARD) }
    var selectedAgentId by remember { mutableStateOf("") }

    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        }
    ) { route ->
        when (route) {
            GovernanceRoute.DASHBOARD -> GovernanceDashboard(
                onAgentDetail = { agentId ->
                    selectedAgentId = agentId
                    currentRoute = GovernanceRoute.AGENT_DETAIL
                },
                onPolicyCenter = { currentRoute = GovernanceRoute.POLICY_EDITOR },
                onDecisionQueue = { currentRoute = GovernanceRoute.DECISION_QUEUE },
                onAuditPlayback = { currentRoute = GovernanceRoute.AUDIT_PLAYBACK }
            )
            GovernanceRoute.AGENT_DETAIL -> AgentDetailScreen(
                agentId = selectedAgentId,
                onBack = { currentRoute = GovernanceRoute.DASHBOARD }
            )
            GovernanceRoute.POLICY_EDITOR -> PolicyEditorScreen(
                onBack = { currentRoute = GovernanceRoute.DASHBOARD }
            )
            GovernanceRoute.DECISION_QUEUE -> DecisionQueueScreen(
                onBack = { currentRoute = GovernanceRoute.DASHBOARD }
            )
            GovernanceRoute.AUDIT_PLAYBACK -> AuditPlaybackScreen(
                onBack = { currentRoute = GovernanceRoute.DASHBOARD }
            )
        }
    }
}
