package com.samsung.prism.teachable.teaching

import java.util.UUID

enum class SessionStatus {
    RECORDING,
    COMPLETED,
    CANCELLED,
    TRUNCATED_AT_BOUNDARY
}

data class TeachingSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val originalUtterance: String,
    val startTime: Long = System.currentTimeMillis(),
    var targetPackageHint: String? = null,
    val rawActions: MutableList<RawAction> = mutableListOf(),
    var truncatedAtBoundary: Boolean = false,
    var status: SessionStatus = SessionStatus.RECORDING
) {
    /**
     * All actions retained for workflow generalization (excluding irrelevant/accidental actions).
     */
    val retainedActions: List<RawAction>
        get() = rawActions.filter { !it.isFiltered }

    val allActions: List<RawAction>
        get() = rawActions

    val filteredActions: List<RawAction>
        get() = rawActions.filter { it.isFiltered }

    val filteredActionsCount: Int
        get() = filteredActions.size
}
