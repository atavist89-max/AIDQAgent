package com.llmtest

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MetroMapScreen(
    pipelineManager: DQPipelineManager,
    modifier: Modifier = Modifier
) {
    val vm = remember { MetroMapViewModel() }
    val scope = rememberCoroutineScope()

    val stage by pipelineManager.currentStage.collectAsState()
    val s1 by pipelineManager.stage1Output.collectAsState()
    val s2 by pipelineManager.stage2Output.collectAsState()
    val s3 by pipelineManager.stage3Output.collectAsState()
    val s4 by pipelineManager.stage4Output.collectAsState()
    val upstream by pipelineManager.upstreamReport.collectAsState()
    val downstream by pipelineManager.downstreamReport.collectAsState()

    val govStatus by GaaSController.governanceStatus.collectAsState()
    val violationModal by GaaSController.violationModal.collectAsState()
    val activeNegotiation by AgentNegotiator.activeNegotiations.collectAsState()
    val pendingApprovals by EscalationRouter.pendingApprovals.collectAsState()
    val pipelineBlocked by GaaSController.pipelineBlocked.collectAsState()

    var isPanelExpanded by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }

    // Timer
    LaunchedEffect(stage) {
        if (stage > 0 && stage < 50) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    // Reset timer when new analysis starts
    LaunchedEffect(pipelineManager.currentAlertDataset) {
        elapsedSeconds = 0
        vm.clearPolicyResults()
    }

    // Animate train based on stage
    LaunchedEffect(stage) {
        val targetIndex = when (stage) {
            1 -> 0f
            2 -> 1f
            3 -> 2f
            41 -> 3f
            42 -> 4f
            43 -> 5f
            50 -> 5.5f
            else -> vm.trainPosition.value
        }
        vm.animateTrainTo(targetIndex, 800)

        // Update agent thought
        val thought = when (stage) {
            1 -> AgentThought(
                agentId = "stage1",
                agentName = "Triage Agent",
                agentRole = "Severity assessment",
                trustScore = TrustScoreManager.getScore("stage1")?.score ?: 50,
                autonomyLevel = TrustScoreManager.getAutonomyLevel("stage1").label,
                reasoning = s1,
                evidence = listOf("reports.json"),
                confidence = 0.95f
            )
            2 -> AgentThought(
                agentId = "stage2",
                agentName = "Context Builder",
                agentRole = "Entity lookup",
                trustScore = TrustScoreManager.getScore("stage2")?.score ?: 50,
                autonomyLevel = TrustScoreManager.getAutonomyLevel("stage2").label,
                reasoning = s2,
                evidence = listOf("entities.json", "catalog_columns.json"),
                confidence = 0.92f
            )
            3 -> AgentThought(
                agentId = "stage3",
                agentName = "Pattern Detector",
                agentRole = "Anomaly detection",
                trustScore = TrustScoreManager.getScore("stage3")?.score ?: 50,
                autonomyLevel = TrustScoreManager.getAutonomyLevel("stage3").label,
                reasoning = s3,
                evidence = listOf("dq_alerts.json", "entities.json"),
                confidence = 0.88f
            )
            41 -> AgentThought(
                agentId = "stage4a",
                agentName = "Upstream Researcher",
                agentRole = "Technical root cause",
                trustScore = TrustScoreManager.getScore("stage4a")?.score ?: 50,
                autonomyLevel = TrustScoreManager.getAutonomyLevel("stage4a").label,
                reasoning = upstream.take(300),
                evidence = listOf("catalog_columns.json", "dq_knowledge.json"),
                confidence = 0.85f,
                isTyping = upstream.length < 100
            )
            42 -> AgentThought(
                agentId = "stage4b",
                agentName = "Downstream Researcher",
                agentRole = "Business impact",
                trustScore = TrustScoreManager.getScore("stage4b")?.score ?: 50,
                autonomyLevel = TrustScoreManager.getAutonomyLevel("stage4b").label,
                reasoning = downstream.take(300),
                evidence = listOf("reports.json", "dq_knowledge.json"),
                confidence = 0.82f,
                isTyping = downstream.length < 100
            )
            43, 50 -> AgentThought(
                agentId = "stage4c",
                agentName = "Synthesizer",
                agentRole = "Executive narrative",
                trustScore = TrustScoreManager.getScore("stage4c")?.score ?: 50,
                autonomyLevel = TrustScoreManager.getAutonomyLevel("stage4c").label,
                reasoning = s4.take(400),
                evidence = listOf("stage4a.json", "stage4b.json"),
                confidence = 0.90f
            )
            else -> null
        }
        vm.setActiveAgentThought(thought)
    }

    // Handle blocked state
    LaunchedEffect(pipelineBlocked) {
        if (pipelineBlocked) {
            vm.triggerTrainBlocked()
        } else {
            vm.clearTrainBlocked()
        }
    }

    // Handle negotiation
    LaunchedEffect(activeNegotiation) {
        vm.setNegotiationVisible(activeNegotiation.isNotEmpty())
    }

    // Build policy results from governance state
    LaunchedEffect(govStatus) {
        val decision = GaaSController.activeDecision.value
        if (decision != null) {
            decision.policyViolations.forEach { v ->
                vm.addPolicyResult(
                    PolicyResultDisplay(
                        policyId = v.policyId,
                        policyName = v.policyName,
                        category = v.category,
                        status = if (govStatus == GovernanceStatus.BLOCKED) PolicyDisplayStatus.BLOCKED
                        else if (govStatus == GovernanceStatus.MODIFIED) PolicyDisplayStatus.REMEDIATED
                        else PolicyDisplayStatus.PASSED,
                        details = v.reason,
                        beforeValue = v.triggeredContent,
                        afterValue = v.remediation
                    )
                )
            }
        }
    }

    // Violation modal
    violationModal?.let { violation ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("🛡 Policy Violation") },
            text = {
                Column {
                    Text(violation.policyName, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(violation.reason)
                    if (violation.triggeredContent != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                violation.triggeredContent,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { GaaSController.resolveViolation(true) }) {
                    Text("Approve & Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { GaaSController.resolveViolation(false) }) {
                    Text("Block")
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Metro Map Canvas (top ~45%)
        MetroMapCanvas(
            stage = stage,
            trainPosition = vm.trainPosition.value,
            govStatus = govStatus,
            pipelineBlocked = pipelineBlocked,
            activeNegotiation = activeNegotiation.firstOrNull(),
            alertDataset = pipelineManager.currentAlertDataset,
            severity = "Critical", // Would come from alert
            dimension = "Adaptability",
            elapsedSeconds = elapsedSeconds,
            progressPercent = ((stage / 50f) * 100).toInt(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Policy evaluations
        val policyResults by vm.policyResults.collectAsState()
        PolicyEvaluationPanel(results = policyResults)

        // Agent thought panel
        val thought by vm.activeAgentThought.collectAsState()
        val negotiation by vm.negotiationVisible.collectAsState()
        val currentNeg = activeNegotiation.firstOrNull()

        AgentThoughtPanel(
            thought = thought,
            negotiation = if (negotiation) currentNeg else null,
            isExpanded = isPanelExpanded,
            onToggleExpand = { isPanelExpanded = !isPanelExpanded },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
}

@Composable
private fun MetroMapCanvas(
    stage: Int,
    trainPosition: Float,
    govStatus: GovernanceStatus,
    pipelineBlocked: Boolean,
    activeNegotiation: AgentNegotiation?,
    alertDataset: String,
    severity: String,
    dimension: String,
    elapsedSeconds: Int,
    progressPercent: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }

    var activeTooltip by remember { mutableStateOf<TooltipInfo?>(null) }

    val stationDefs = listOf(
        StationDef("STAGE 1", "Triage", "stage1", 120f, "Initial severity assessment and alert classification using rule-based triage."),
        StationDef("STAGE 2", "Context", "stage2", 320f, "Entity lookup and metadata enrichment from the data catalog."),
        StationDef("STAGE 3", "Pattern", "stage3", 520f, "Anomaly detection and historical pattern matching against prior alerts."),
        StationDef("STAGE 4A", "Upstream", "stage4a", 720f, "Technical root cause analysis tracing upstream data lineage."),
        StationDef("STAGE 4B", "Downstream", "stage4b", 920f, "Business impact assessment tracing downstream report consumers."),
        StationDef("STAGE 4C", "Synthesis", "stage4c", 1120f, "Executive narrative generation combining upstream and downstream findings.")
    )

    val policies = remember { PolicyEngine.getActivePolicies() }
    val gateDefs = remember(policies) {
        policies
            .filter { it.gateOrder >= 0 }
            .sortedBy { it.gateOrder }
            .mapIndexed { index, policy ->
                GateDef(
                    label = policy.name,
                    x = 220f + index * 200f,
                    afterStation = index,
                    policyId = policy.policyId
                )
            }
    }

    LaunchedEffect(activeTooltip) {
        if (activeTooltip != null) {
            delay(4000)
            activeTooltip = null
        }
    }

    val centerY = 140f
    val trainX = remember(trainPosition) {
        val index = trainPosition.toInt().coerceIn(0, stationDefs.size - 1)
        val frac = trainPosition - index
        val startX = stationDefs[index].x
        val endX = stationDefs.getOrNull(index + 1)?.x ?: startX
        startX + (endX - startX) * frac
    }
    val trainY = centerY - 60f

    BoxWithConstraints(
        modifier = modifier
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val contentWidthPx = with(density) { 1300.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.6f, 2.5f)
                        val scaledContentWidth = contentWidthPx * scale
                        val minOffset = if (scaledContentWidth > containerWidth)
                            -(scaledContentWidth - containerWidth) else 0f
                        offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .width(1300.dp)
                    .height(280.dp)
                    .drawBehind {
                        // Dot grid background covering full content
                        val dotSpacing = 12.dp.toPx()
                        val dotRadius = 1f
                        val dotColor = Color(0xFFE5E7EB).copy(alpha = 0.3f)
                        val cols = (size.width / dotSpacing).toInt() + 1
                        val rows = (size.height / dotSpacing).toInt() + 1
                        for (x in 0..cols) {
                            for (y in 0..rows) {
                                drawCircle(
                                    color = dotColor,
                                    radius = dotRadius,
                                    center = Offset(x * dotSpacing, y * dotSpacing)
                                )
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                    }
            ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw connector lines
                for (i in 0 until stationDefs.size - 1) {
                    val startX = stationDefs[i].x
                    val endX = stationDefs[i + 1].x
                    val midX = (startX + endX) / 2

                    // Analysis segment (solid)
                    drawLine(
                        color = Color(0xFF1A237E),
                        start = Offset(startX + 28f, centerY),
                        end = Offset(midX - 36f, centerY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // GaaS segment (dashed/animated)
                    val gateColor = when (govStatus) {
                        GovernanceStatus.PENDING -> Color(0xFF9CA3AF)
                        GovernanceStatus.APPROVED -> Color(0xFF00BFA5)
                        GovernanceStatus.BLOCKED -> Color(0xFFD50000)
                        GovernanceStatus.ESCALATED -> Color(0xFF7C4DFF)
                        GovernanceStatus.MODIFIED -> Color(0xFF1A237E)
                    }
                    drawLine(
                        color = gateColor,
                        start = Offset(midX + 36f, centerY),
                        end = Offset(endX - 28f, centerY),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (govStatus == GovernanceStatus.PENDING && stage > i + 1)
                            PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        else null
                    )
                }

                // Negotiation loop (between 4a and 4b)
                if (activeNegotiation != null) {
                    val s4aX = stationDefs[3].x
                    val s4bX = stationDefs[4].x
                    val loopPath = Path().apply {
                        moveTo(s4aX + 28f, centerY)
                        cubicTo(
                            s4aX + 80f, centerY + 80f,
                            s4bX - 80f, centerY + 80f,
                            s4bX - 28f, centerY
                        )
                    }
                    drawPath(
                        path = loopPath,
                        color = Color(0xFF7C4DFF),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    )
                }
            }

            // Stations
            stationDefs.forEachIndexed { index, def ->
                val state = when {
                    stage == 50 && index < 6 -> StationState.COMPLETE
                    stage == 43 && index < 5 -> StationState.COMPLETE
                    stage == 42 && index < 4 -> StationState.COMPLETE
                    stage == 41 && index < 3 -> StationState.COMPLETE
                    stage == 3 && index < 2 -> StationState.COMPLETE
                    stage == 2 && index < 1 -> StationState.COMPLETE
                    stage == 1 && index == 0 -> StationState.ACTIVE
                    stage == 2 && index == 1 -> StationState.ACTIVE
                    stage == 3 && index == 2 -> StationState.ACTIVE
                    stage == 41 && index == 3 -> StationState.ACTIVE
                    stage == 42 && index == 4 -> StationState.ACTIVE
                    stage == 43 && index == 5 -> StationState.ACTIVE
                    pipelineBlocked && index == (stage / 10) -> StationState.BLOCKED
                    else -> StationState.PENDING
                }

                val trustScore = TrustScoreManager.getScore(def.agentId)?.score ?: 50

                Box(
                    modifier = Modifier
                        .offset(x = def.x.dp - 28.dp, y = (centerY - 28f).dp)
                ) {
                    MetroStation(
                        label = def.label,
                        agentName = def.agentName,
                        trustScore = trustScore,
                        state = state,
                        isFocused = false,
                        onClick = {
                            activeTooltip = TooltipInfo(
                                title = "${def.label} — ${def.agentName}",
                                description = def.description,
                                itemX = def.x,
                                itemY = centerY - 90f
                            )
                        }
                    )
                }
            }

            // Gates
            gateDefs.forEach { gate ->
                val state = when (govStatus) {
                    GovernanceStatus.PENDING -> GateState.EVALUATING
                    GovernanceStatus.APPROVED -> GateState.APPROVED
                    GovernanceStatus.BLOCKED -> GateState.BLOCKED
                    GovernanceStatus.ESCALATED -> GateState.ESCALATED
                    GovernanceStatus.MODIFIED -> GateState.APPROVED
                }
                Box(
                    modifier = Modifier
                        .offset(x = gate.x.dp - 26.dp, y = (centerY - 26f).dp)
                ) {
                    MetroGate(
                        label = gate.label,
                        state = if (stage > gate.afterStation) state else GateState.APPROVED,
                        onClick = {
                            val policyDesc = PolicyEngine.getPolicy(gate.policyId)?.description
                            activeTooltip = TooltipInfo(
                                title = "${gate.label} Gate",
                                description = policyDesc ?: "Policy description unavailable",
                                itemX = gate.x,
                                itemY = centerY - 90f
                            )
                        },
                        modifier = Modifier
                    )
                }
            }

            // Tooltip overlay
            activeTooltip?.let { tooltip ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = (tooltip.itemX - 100f).dp.coerceAtLeast(0.dp),
                            y = tooltip.itemY.dp.coerceAtLeast(4.dp)
                        )
                        .width(200.dp)
                        .wrapContentHeight()
                ) {
                    TooltipCard(
                        title = tooltip.title,
                        description = tooltip.description,
                        onDismiss = { activeTooltip = null }
                    )
                }
            }

            // Train
            if (stage >= 1) {
                Box(
                    modifier = Modifier
                        .offset(x = trainX.dp - 80.dp, y = (trainY - 40f).dp)
                ) {
                    MetroTrain(
                        alertDataset = alertDataset.ifBlank { "No Alert" },
                        severity = severity,
                        dimension = dimension,
                        elapsedSeconds = elapsedSeconds,
                        progressPercent = progressPercent,
                        isBlocked = pipelineBlocked,
                        isActive = stage in 1..49
                    )
                }
            }
        }
    }
}
}

private data class StationDef(val label: String, val agentName: String, val agentId: String, val x: Float, val description: String)
private data class GateDef(val label: String, val x: Float, val afterStation: Int, val policyId: String)

private data class TooltipInfo(
    val title: String,
    val description: String,
    val itemX: Float,
    val itemY: Float
)

@Composable
private fun TooltipCard(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1A1A2E),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "✕",
                    fontSize = 14.sp,
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFFD1D5DB),
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap anywhere to dismiss",
                fontSize = 9.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}
