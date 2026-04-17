package com.llmtest

import kotlinx.serialization.Serializable

@Serializable
data class DQAlert(
    val lastCheckRunTime: String,
    val evaluationStatus: String,
    val checkName: String,
    val datasetName: String,
    val datasourceName: String,
    val datasourceType: String? = null,
    val ownerEmail: String,
    val severity: String,
    val dimension: String,
    var analysisState: String = "NEW"
)

@Serializable
data class Entity(
    val entityId: String? = null,
    val entityName: String,
    val entityGroup: String,
    val ownerEmail: String,
    val description: String,
    val linkedDatasetName: String,
    val tags: String? = null,
    val usageNotes: String? = null
)

@Serializable
data class EntityGroup(
    val entityGroupId: String? = null,
    val entityGroup: String,
    val ownerEmail: String,
    val description: String,
    val tags: String? = null
)

@Serializable
data class CatalogColumn(
    val entityName: String,
    val linkedDatasetName: String,
    val id: String,
    val name: String,
    val businessObject: String,
    val definition: String,
    val dataExample: String? = null,
    val sourceDB: String? = null,
    val sourceTable: String? = null,
    val sourceAttribute: String? = null,
    val relevantObjects: String? = null
)

@Serializable
data class Report(
    val reportName: String,
    val reportDescription: String,
    val reportOwner: String,
    val dataSources: String,
    val reportClass: String
)

@Serializable
data class AnalysisState(
    val stage: Int,
    val alertDataset: String,
    val timestamp: Long = System.currentTimeMillis(),
    val decision: String? = null,
    val contextSummary: String? = null,
    val patternResult: String? = null,
    val finalReport: String? = null,
    val ownerLoad: Int? = null,
    val healthScore: Float? = null,
    val patternType: String? = null
)
