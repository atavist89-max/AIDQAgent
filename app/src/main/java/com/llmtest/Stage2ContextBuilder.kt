package com.llmtest

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object Stage2ContextBuilder {
    private val json = Json { ignoreUnknownKeys = true }
    
    fun run(alert: DQAlert, stage1State: AnalysisState): AnalysisState {
        val config = GovernanceConfig.getStage2Config()
        
        // Load entity
        val entities = loadEntities()
        val entity = entities.find { it.linkedDatasetName == alert.datasetName }
        
        // Load catalog columns for this entity
        val catalog = loadCatalog()
        val entityColumns = catalog.filter { it.linkedDatasetName == alert.datasetName }
            .take(config.maxCatalogColumns)
        val keyColumn = entityColumns.find { it.name.contains(alert.checkName.split(" ").first(), ignoreCase = true) } 
            ?: entityColumns.firstOrNull()
        
        // Load entity group
        val groups = loadEntityGroups()
        val group = groups.find { it.entityGroup == entity?.entityGroup }
        
        // Build condensed context (under 1200 tokens) using only configured fields
        val contextSummary = buildString {
            appendLine("DATASET: ${alert.datasetName}")
            appendLine("CHECK: ${alert.checkName}")
            appendLine("SEVERITY: ${alert.severity}")
            appendLine("DIMENSION: ${alert.dimension}")
            appendLine()
            
            // Entity fields
            if (config.entityFields.contains("entityName")) appendLine("ENTITY: ${entity?.entityName ?: "Unknown"}")
            if (config.entityFields.contains("entityGroup")) appendLine("GROUP: ${entity?.entityGroup ?: "Unknown"}")
            if (config.entityFields.contains("ownerEmail")) appendLine("OWNER: ${entity?.ownerEmail ?: "Unknown"}")
            if (config.entityFields.contains("description")) appendLine("DESC: ${entity?.description?.take(100) ?: "N/A"}")
            if (config.entityFields.contains("tags")) appendLine("TAGS: ${entity?.tags ?: "N/A"}")
            if (config.entityFields.contains("usageNotes")) appendLine("USAGE: ${entity?.usageNotes ?: "N/A"}")
            
            if (config.entityFields.isNotEmpty()) appendLine()
            
            // Catalog fields
            if (config.catalogFields.contains("sourceDB") || config.catalogFields.contains("sourceTable")) {
                appendLine("SOURCE: ${keyColumn?.sourceDB}.${keyColumn?.sourceTable}")
            }
            if (config.catalogFields.contains("definition")) appendLine("MEANING: ${keyColumn?.definition?.take(100) ?: "N/A"}")
            if (config.catalogFields.contains("dataExample")) appendLine("EXAMPLE: ${keyColumn?.dataExample ?: "N/A"}")
            if (config.catalogFields.contains("sourceAttribute")) appendLine("ATTRIBUTE: ${keyColumn?.sourceAttribute ?: "N/A"}")
            if (config.catalogFields.contains("sourceDescription")) appendLine("SOURCE_DESC: ${keyColumn?.sourceDescription ?: "N/A"}")
        }
        
        val state = stage1State.copy(
            stage = 2,
            contextSummary = contextSummary
        )
        
        GhostPaths.stateFile(2).writeText(json.encodeToString(AnalysisState.serializer(), state))
        BugLogger.log("Stage 2 complete: context built with ${config.entityFields.size} entity fields, ${config.catalogFields.size} catalog fields, max ${config.maxCatalogColumns} columns")
        
        return state
    }
    
    private fun loadEntities(): List<Entity> {
        return try {
            val content = GhostPaths.ENTITIES.readText()
            json.decodeFromString(ListSerializer(Entity.serializer()), content)
        } catch (e: Exception) {
            BugLogger.logError("Failed to load entities", e)
            emptyList()
        }
    }
    
    private fun loadCatalog(): List<CatalogColumn> {
        return try {
            val content = GhostPaths.CATALOG.readText()
            json.decodeFromString(ListSerializer(CatalogColumn.serializer()), content)
        } catch (e: Exception) {
            BugLogger.logError("Failed to load catalog", e)
            emptyList()
        }
    }
    
    private fun loadEntityGroups(): List<EntityGroup> {
        return try {
            val content = GhostPaths.ENTITY_GROUPS.readText()
            json.decodeFromString(ListSerializer(EntityGroup.serializer()), content)
        } catch (e: Exception) {
            BugLogger.logError("Failed to load entity groups", e)
            emptyList()
        }
    }
}
