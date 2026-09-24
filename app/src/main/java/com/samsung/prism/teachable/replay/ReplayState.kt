package com.samsung.prism.teachable.replay

enum class ReplayState {
    IDLE,
    RETRIEVING,
    EXTRACTING_SLOTS,
    BINDING,
    EXECUTING_STEP,
    VERIFYING_STATE,
    RECOVERING,
    ASKING_USER,
    STOPPED_AT_BOUNDARY,
    COMPLETED,
    FAILED
}
