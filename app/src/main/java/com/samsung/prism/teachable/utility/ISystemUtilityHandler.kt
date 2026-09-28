package com.samsung.prism.teachable.utility

interface ISystemUtilityHandler {
    fun parseUtilityAction(utterance: String): SystemUtilityAction?
    fun canHandle(utterance: String): Boolean
    suspend fun execute(action: SystemUtilityAction): SystemUtilityResult
    suspend fun execute(utterance: String): SystemUtilityResult?
}
