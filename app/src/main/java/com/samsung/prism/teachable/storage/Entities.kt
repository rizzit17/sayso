package com.samsung.prism.teachable.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "intent_tag") val intentTag: String,
    @ColumnInfo(name = "original_utterance") val originalUtterance: String,
    @ColumnInfo(name = "generalized_intent") val generalizedIntent: String,
    @ColumnInfo(name = "intent_embedding") val intentEmbedding: ByteArray? = null,
    @ColumnInfo(name = "supported_packages") val supportedPackages: String, // JSON array string
    @ColumnInfo(name = "slot_schema") val slotSchema: String, // JSON object string
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "version") val version: Int = 1
)

@Entity(
    tableName = "workflow_steps",
    foreignKeys = [
        ForeignKey(
            entity = WorkflowEntity::class,
            parentColumns = ["id"],
            childColumns = ["workflow_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workflow_id")]
)
data class WorkflowStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workflow_id") val workflowId: String,
    @ColumnInfo(name = "step_order") val stepOrder: Int,
    @ColumnInfo(name = "action_type") val actionType: String,
    @ColumnInfo(name = "input_text") val inputText: String? = null,
    @ColumnInfo(name = "step_target") val stepTarget: String, // JSON string
    @ColumnInfo(name = "expected_state_transition") val expectedStateTransition: String, // JSON string
    @ColumnInfo(name = "is_boundary") val isBoundary: Int = 0,
    @ColumnInfo(name = "slot_binding") val slotBinding: String? = null
)

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workflow_id") val workflowId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long = 0L,
    @ColumnInfo(name = "status") val status: String, // COMPLETED, COMPLETED_TO_BOUNDARY, FAILED, ASKED_USER
    @ColumnInfo(name = "stopped_at_step_id") val stoppedAtStepId: String? = null,
    @ColumnInfo(name = "failure_reason") val failureReason: String? = null,
    @ColumnInfo(name = "bound_params") val boundParams: String = "{}", // JSON
    @ColumnInfo(name = "confidence") val confidence: Double = 1.0
)

@Entity(
    tableName = "run_steps",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("run_id")]
)
data class RunStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "step_id") val stepId: String,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "confidence") val confidence: Double = 1.0,
    @ColumnInfo(name = "recovery_invoked") val recoveryInvoked: Int = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_registry")
data class AppRegistryEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "last_seen_installed") val lastSeenInstalled: Long
)
