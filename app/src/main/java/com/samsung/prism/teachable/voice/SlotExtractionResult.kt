package com.samsung.prism.teachable.voice

data class SlotExtractionResult(
    val boundSlots: Map<String, Any>,
    val missingRequiredSlots: List<String>
) {
    val isComplete: Boolean get() = missingRequiredSlots.isEmpty()
}
