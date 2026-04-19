package com.llmtest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditPlaybackScreen(onBack: () -> Unit = {}) {
    var records by remember { mutableStateOf(AuditLogger.readAll()) }
    var filterType by remember { mutableStateOf<AuditRecordType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }

    val filtered = remember(records, filterType, searchQuery) {
        records.filter { r ->
            (filterType == null || r.recordType == filterType) &&
            (searchQuery.isBlank() ||
             r.alertDataset?.contains(searchQuery, ignoreCase = true) == true ||
             r.agentIds.any { it.contains(searchQuery, ignoreCase = true) })
        }.sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit & Forensics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by alert or agent") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("All") }
                )
                AuditRecordType.entries.forEach { type ->
                    FilterChip(
                        selected = filterType == type,
                        onClick = { filterType = type },
                        label = { Text(type.name.replace("_", " ")) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("${filtered.size} records", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filtered.forEach { record ->
                    AuditRecordCard(record = record)
                }
            }
        }
    }

    if (showExportDialog) {
        val exportText = remember { AuditLogger.exportForCompliance() }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Audit Log") },
            text = {
                Column {
                    Text("Compliance export generated (${records.size} total records)")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportText.take(2000),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 300.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun AuditRecordCard(record: AuditRecord) {
    val typeColor = when (record.recordType) {
        AuditRecordType.DECISION -> Color(0xFF1976D2)
        AuditRecordType.TRUST_UPDATE -> Color(0xFF7B1FA2)
        AuditRecordType.POLICY_VIOLATION -> Color(0xFFE53935)
        AuditRecordType.POLICY_CHANGE -> Color(0xFFF57C00)
        AuditRecordType.HUMAN_OVERRIDE -> Color(0xFF388E3C)
        AuditRecordType.NEGOTIATION -> Color(0xFF00838F)
        AuditRecordType.ESCALATION -> Color(0xFFD32F2F)
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = typeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            record.recordType.name.replace("_", " "),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        dateFormat.format(Date(record.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.stage != null) {
                    Text("Stage ${record.stage}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (!record.alertDataset.isNullOrBlank()) {
                Text("Alert: ${record.alertDataset}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            if (record.agentIds.isNotEmpty()) {
                Text("Agents: ${record.agentIds.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!record.decision.isNullOrBlank()) {
                Text("Decision: ${record.decision}", style = MaterialTheme.typography.bodySmall)
            }
            if (record.humanOverrideReason != null) {
                Text("Override: ${record.humanOverrideReason}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
            }
            if (record.policyViolations.isNotEmpty()) {
                Text("Violations: ${record.policyViolations.joinToString { it.policyName }}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
            }
            if (record.inputDataHash != null) {
                Text("Hash: ${record.inputDataHash.take(16)}...", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
