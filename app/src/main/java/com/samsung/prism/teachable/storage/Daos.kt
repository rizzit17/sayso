package com.samsung.prism.teachable.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class WorkflowWithSteps(
    val workflow: WorkflowEntity,
    val steps: List<WorkflowStepEntity>
)

@Dao
interface WorkflowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkflow(workflow: WorkflowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<WorkflowStepEntity>)

    @Query("DELETE FROM workflow_steps WHERE workflow_id = :workflowId")
    suspend fun deleteSteps(workflowId: String)

    @Transaction
    suspend fun saveWorkflowWithSteps(workflow: WorkflowEntity, steps: List<WorkflowStepEntity>) {
        insertWorkflow(workflow)
        deleteSteps(workflow.id)
        insertSteps(steps)
    }

    @Query("SELECT * FROM workflows WHERE status = 'ACTIVE' ORDER BY updated_at DESC")
    suspend fun getActiveWorkflows(): List<WorkflowEntity>

    @Query("SELECT * FROM workflows WHERE id = :id LIMIT 1")
    suspend fun getWorkflowById(id: String): WorkflowEntity?

    @Query("SELECT * FROM workflow_steps WHERE workflow_id = :workflowId ORDER BY step_order ASC")
    suspend fun getStepsForWorkflow(workflowId: String): List<WorkflowStepEntity>

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun deleteWorkflow(id: String)

    @Query("SELECT COUNT(*) FROM workflows WHERE status = 'ACTIVE'")
    suspend fun getActiveWorkflowCount(): Int
}

@Dao
interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunSteps(steps: List<RunStepEntity>)

    @Query("SELECT * FROM runs ORDER BY started_at DESC LIMIT :limit")
    suspend fun getRecentRuns(limit: Int = 20): List<RunEntity>

    @Query("SELECT * FROM runs WHERE workflow_id = :workflowId ORDER BY started_at DESC LIMIT 1")
    suspend fun getLastRunForWorkflow(workflowId: String): RunEntity?

    @Query("SELECT * FROM runs ORDER BY started_at DESC LIMIT 1")
    suspend fun getLastRun(): RunEntity?

    @Query("SELECT * FROM run_steps WHERE run_id = :runId ORDER BY timestamp ASC")
    suspend fun getRunSteps(runId: String): List<RunStepEntity>
}

@Dao
interface AppRegistryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: AppRegistryEntity)

    @Query("SELECT * FROM app_registry")
    suspend fun getAllRegisteredApps(): List<AppRegistryEntity>

    @Query("SELECT * FROM app_registry WHERE package_name = :pkg LIMIT 1")
    suspend fun getApp(pkg: String): AppRegistryEntity?
}
