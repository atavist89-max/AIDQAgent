package com.llmtest

import java.io.File

object GhostPaths {
    // Base paths (existing)
    val BASE_DIR = File("/storage/emulated/0/Download/GhostModels")
    val MODEL_FILE = File(BASE_DIR, "gemma-4-e2b.litertlm")
    
    // DQ Agent paths (new)
    val DQ_AGENT_DIR = File(BASE_DIR, "DQAgent")
    val DQ_DATA_DIR = File(DQ_AGENT_DIR, "data")
    val DQ_STATE_DIR = File(DQ_AGENT_DIR, "state")
    val DQ_INPUT_DIR = File(DQ_AGENT_DIR, "demo_input")
    
    // Data files (new)
    val DQ_ALERTS = File(DQ_DATA_DIR, "dq_alerts.json")
    val ENTITIES = File(DQ_DATA_DIR, "entities.json")
    val ENTITY_GROUPS = File(DQ_DATA_DIR, "entity_groups.json")
    val CATALOG = File(DQ_DATA_DIR, "catalog_columns.json")
    val REPORTS = File(DQ_DATA_DIR, "reports.json")
    val DQ_KNOWLEDGE = File(DQ_DATA_DIR, "dq_knowledge.json")
    val GOVERNANCE_SCHEMA = File(DQ_DATA_DIR, "governance_schema.json")
    
    // State file helpers
    fun stateFile(stage: Int) = File(DQ_STATE_DIR, "stage${stage}.json")
    fun inputFile() = File(DQ_INPUT_DIR, "new_alert.json")
    
    // GaaS governance files
    val GAASTrustScores = File(DQ_DATA_DIR, "trust_scores.json")
    val GAASPolicyRules = File(DQ_DATA_DIR, "policy_rules.json")
    val GAASStateFile = File(DQ_STATE_DIR, "gaas_state.json")
    val GAASAuditLog = File(DQ_STATE_DIR, "audit_log.jsonl")
    
    // Validation
    fun isModelAvailable(): Boolean = MODEL_FILE.exists() && MODEL_FILE.length() > 1_000_000_000L
    fun isDQDataAvailable(): Boolean = DQ_DATA_DIR.exists() && DQ_ALERTS.exists()
}
