package com.llmtest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DQAlert(
    @SerialName("LAST_CHECK_RUN_TIME") val lastCheckRunTime: String,
    @SerialName("EVALUATION_STATUS") val evaluationStatus: String,
    @SerialName("CHECK_NAME") val checkName: String,
    @SerialName("DATASET_NAME") val datasetName: String,
    @SerialName("DATASOURCE_NAME") val datasourceName: String,
    @SerialName("DATASOURCE_TYPE") val datasourceType: String? = null,
    @SerialName("OWNER_EMAIL") val ownerEmail: String,
    @SerialName("DQ_CHECK_SEVERITY") val severity: String,
    @SerialName("DQ_DIMENSION") val dimension: String,
    var analysisState: String = "NEW"
)

@Serializable
data class Entity(
    @SerialName("ENTITY_ID") val entityId: String? = null,
    @SerialName("ENTITY_NAME") val entityName: String,
    @SerialName("ENTITY_GROUP") val entityGroup: String,
    @SerialName("OWNER_EMAIL") val ownerEmail: String,
    @SerialName("DESCRIPTION") val description: String,
    @SerialName("LINKED_DATASET_NAME") val linkedDatasetName: String,
    @SerialName("TAGS") val tags: String? = null,
    @SerialName("USAGE_NOTES") val usageNotes: String? = null
)

@Serializable
data class EntityGroup(
    @SerialName("ENTITY_GROUP_ID") val entityGroupId: String? = null,
    @SerialName("ENTITY_GROUP") val entityGroup: String,
    @SerialName("OWNER_EMAIL") val ownerEmail: String,
    @SerialName("DESCRIPTION") val description: String,
    @SerialName("TAGS") val tags: String? = null
)

@Serializable
data class CatalogColumn(
    @SerialName("ENTITY_NAME") val entityName: String,
    @SerialName("LINKED_DATASET_NAME") val linkedDatasetName: String,
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("businessObject") val businessObject: String,
    @SerialName("definition") val definition: String,
    @SerialName("dataExample") val dataExample: String? = null,
    @SerialName("sourceDB") val sourceDB: String? = null,
    @SerialName("sourceTable") val sourceTable: String? = null,
    @SerialName("sourceAttribute") val sourceAttribute: String? = null,
    @SerialName("sourceDescription") val sourceDescription: String? = null,
    @SerialName("relevantObjects") val relevantObjects: String? = null
)

@Serializable
data class Report(
    @SerialName("Report Name") val reportName: String,
    @SerialName("Report Description") val reportDescription: String,
    @SerialName("Report Owner") val reportOwner: String,
    @SerialName("Data sources") val dataSources: String,
    @SerialName("Report Class") val reportClass: String
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
    val patternType: String? = null,
    val groupName: String? = null,
    val groupDatasets: Int? = null,
    val groupHealthScore: Float? = null
)
