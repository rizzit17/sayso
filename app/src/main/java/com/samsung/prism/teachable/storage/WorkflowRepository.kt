package com.samsung.prism.teachable.storage

import android.content.Context
import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.teaching.ActionType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RunStatus {
    COMPLETED,
    COMPLETED_TO_BOUNDARY,
    FAILED,
    ASKED_USER,
    CANCELLED
}

data class StepRunResult(
    val stepId: String,
    val outcome: String,
    val confidence: Double = 1.0,
    val recoveryInvoked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class RunResult(
    val runId: String = UUID.randomUUID().toString(),
    val workflowId: String,
    val workflowTitle: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long = System.currentTimeMillis(),
    val status: RunStatus = RunStatus.COMPLETED,
    val stoppedAtStepId: String? = null,
    val failureReason: String? = null,
    val boundParams: Map<String, Any> = emptyMap(),
    val confidence: Double = 1.0,
    val stepResults: List<StepRunResult> = emptyList()
)

interface IWorkflowRepository {
    suspend fun save(workflow: Workflow)
    suspend fun findActive(): List<Workflow>
    suspend fun findById(workflowId: String): Workflow?
    suspend fun delete(workflowId: String)
    suspend fun recordRun(result: RunResult)
    suspend fun lastRun(workflowId: String? = null): RunResult?
    suspend fun getRecentRuns(limit: Int = 20): List<RunResult>
}

class WorkflowRepository(
    private val db: PrismDatabase
) : IWorkflowRepository {

    private val workflowDao = db.workflowDao()
    private val runDao = db.runDao()

    override suspend fun save(workflow: Workflow) {
        val pkgsJson = JSONArray().apply {
            workflow.supportedPackages.forEach { put(it) }
        }.toString()

        val entity = WorkflowEntity(
            id = workflow.id,
            intentTag = workflow.intentTag,
            originalUtterance = workflow.originalUtterance,
            generalizedIntent = workflow.generalizedIntent,
            intentEmbedding = workflow.intentEmbedding?.let { floatsToBytes(it) },
            supportedPackages = pkgsJson,
            slotSchema = workflow.slotSchema.toJson().toString(),
            status = workflow.status.name,
            createdAt = workflow.createdAt,
            updatedAt = workflow.updatedAt,
            version = workflow.version
        )

        val stepEntities = workflow.steps.map { step ->
            WorkflowStepEntity(
                id = step.id,
                workflowId = workflow.id,
                stepOrder = step.stepOrder,
                actionType = step.actionType.name,
                inputText = step.inputText,
                stepTarget = step.target.toJson().toString(),
                expectedStateTransition = step.expectedStateTransition.toJson().toString(),
                isBoundary = if (step.isBoundary) 1 else 0,
                slotBinding = step.slotBinding
            )
        }

        workflowDao.saveWorkflowWithSteps(entity, stepEntities)
    }

    override suspend fun findActive(): List<Workflow> {
        val entities = workflowDao.getActiveWorkflows()
        return entities.map { entityToWorkflow(it) }
    }

    override suspend fun findById(workflowId: String): Workflow? {
        val entity = workflowDao.getWorkflowById(workflowId) ?: return null
        return entityToWorkflow(entity)
    }

    override suspend fun delete(workflowId: String) {
        workflowDao.deleteWorkflow(workflowId)
    }

    override suspend fun recordRun(result: RunResult) {
        val boundJson = JSONObject(result.boundParams).toString()
        val runEntity = RunEntity(
            id = result.runId,
            workflowId = result.workflowId,
            startedAt = result.startedAt,
            endedAt = result.endedAt,
            status = result.status.name,
            stoppedAtStepId = result.stoppedAtStepId,
            failureReason = result.failureReason,
            boundParams = boundJson,
            confidence = result.confidence
        )

        val stepEntities = result.stepResults.map { step ->
            RunStepEntity(
                id = UUID.randomUUID().toString(),
                runId = result.runId,
                stepId = step.stepId,
                outcome = step.outcome,
                confidence = step.confidence,
                recoveryInvoked = if (step.recoveryInvoked) 1 else 0,
                timestamp = step.timestamp
            )
        }

        runDao.insertRun(runEntity)
        if (stepEntities.isNotEmpty()) {
            runDao.insertRunSteps(stepEntities)
        }
    }

    override suspend fun lastRun(workflowId: String?): RunResult? {
        val entity = if (workflowId != null) {
            runDao.getLastRunForWorkflow(workflowId)
        } else {
            runDao.getLastRun()
        } ?: return null

        return entityToRunResult(entity)
    }

    override suspend fun getRecentRuns(limit: Int): List<RunResult> {
        val entities = runDao.getRecentRuns(limit)
        return entities.map { entityToRunResult(it) }
    }

    private suspend fun entityToWorkflow(entity: WorkflowEntity): Workflow {
        val steps = workflowDao.getStepsForWorkflow(entity.id).map { s ->
            WorkflowStep(
                id = s.id,
                workflowId = s.workflowId,
                stepOrder = s.stepOrder,
                actionType = ActionType.valueOf(s.actionType),
                inputText = s.inputText,
                target = StepTarget.fromJson(JSONObject(s.stepTarget)),
                expectedStateTransition = ExpectedStateTransition.fromJson(JSONObject(s.expectedStateTransition)),
                isBoundary = s.isBoundary == 1,
                slotBinding = s.slotBinding
            )
        }

        val pkgs = mutableListOf<String>()
        val pkgArr = JSONArray(entity.supportedPackages)
        for (i in 0 until pkgArr.length()) {
            pkgs.add(pkgArr.getString(i))
        }

        val slotSchema = SlotSchema.fromJson(JSONObject(entity.slotSchema))

        return Workflow(
            id = entity.id,
            intentTag = entity.intentTag,
            originalUtterance = entity.originalUtterance,
            generalizedIntent = entity.generalizedIntent,
            intentEmbedding = entity.intentEmbedding?.let { bytesToFloats(it) },
            supportedPackages = pkgs,
            slotSchema = slotSchema,
            steps = steps,
            status = WorkflowStatus.valueOf(entity.status),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            version = entity.version
        )
    }

    private suspend fun entityToRunResult(entity: RunEntity): RunResult {
        val stepEntities = runDao.getRunSteps(entity.id)
        val stepResults = stepEntities.map { s ->
            StepRunResult(
                stepId = s.stepId,
                outcome = s.outcome,
                confidence = s.confidence,
                recoveryInvoked = s.recoveryInvoked == 1,
                timestamp = s.timestamp
            )
        }

        val boundParams = mutableMapOf<String, Any>()
        val boundJson = JSONObject(entity.boundParams)
        for (k in boundJson.keys()) {
            boundParams[k] = boundJson.get(k)
        }

        val wf = workflowDao.getWorkflowById(entity.workflowId)
        val title = wf?.originalUtterance ?: "Workflow"

        return RunResult(
            runId = entity.id,
            workflowId = entity.workflowId,
            workflowTitle = title,
            startedAt = entity.startedAt,
            endedAt = entity.endedAt,
            status = RunStatus.valueOf(entity.status),
            stoppedAtStepId = entity.stoppedAtStepId,
            failureReason = entity.failureReason,
            boundParams = boundParams,
            confidence = entity.confidence,
            stepResults = stepResults
        )
    }

    private fun floatsToBytes(floats: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(floats.size * 4)
        for (f in floats) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    private fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        val count = bytes.size / 4
        val floats = FloatArray(count)
        for (i in 0 until count) {
            floats[i] = buffer.float
        }
        return floats
    }

    companion object {
        fun create(context: Context): WorkflowRepository {
            return WorkflowRepository(PrismDatabase.getInstance(context))
        }

        fun createInMemory(context: Context): WorkflowRepository {
            return WorkflowRepository(PrismDatabase.createInMemory(context))
        }
    }
}

class InMemoryWorkflowRepository : IWorkflowRepository {
    private val workflows = mutableMapOf<String, Workflow>()
    private val runs = mutableListOf<RunResult>()

    override suspend fun save(workflow: Workflow) {
        workflows[workflow.id] = workflow
    }

    override suspend fun findActive(): List<Workflow> {
        return workflows.values
            .filter { it.status == WorkflowStatus.ACTIVE }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun findById(workflowId: String): Workflow? {
        return workflows[workflowId]
    }

    override suspend fun delete(workflowId: String) {
        workflows.remove(workflowId)
    }

    override suspend fun recordRun(result: RunResult) {
        runs.add(0, result)
    }

    override suspend fun lastRun(workflowId: String?): RunResult? {
        return if (workflowId != null) {
            runs.firstOrNull { it.workflowId == workflowId }
        } else {
            runs.firstOrNull()
        }
    }

    override suspend fun getRecentRuns(limit: Int): List<RunResult> {
        return runs.take(limit)
    }
}
