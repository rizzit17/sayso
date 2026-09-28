package com.samsung.prism.teachable.utility

class FakeSystemUtilityHandler : ISystemUtilityHandler {

    val executedActions = mutableListOf<SystemUtilityAction>()
    var shouldFail: Boolean = false
    var failureReason: String = "Simulated failure"

    override fun parseUtilityAction(utterance: String): SystemUtilityAction? {
        return SystemUtilityParser.parse(utterance)
    }

    override fun canHandle(utterance: String): Boolean {
        return parseUtilityAction(utterance) != null
    }

    override suspend fun execute(utterance: String): SystemUtilityResult? {
        val action = parseUtilityAction(utterance) ?: return null
        return execute(action)
    }

    override suspend fun execute(action: SystemUtilityAction): SystemUtilityResult {
        if (shouldFail) {
            return SystemUtilityResult.Failed(action, failureReason)
        }
        executedActions.add(action)
        val msg = when (action) {
            is SystemUtilityAction.SetAlarm -> "Alarm set for ${action.formattedTime}."
            is SystemUtilityAction.SetTimer -> "Timer set for ${action.formattedDuration}."
            is SystemUtilityAction.AddCalendarEvent -> "Created calendar event: ${action.title}."
            is SystemUtilityAction.ToggleFlashlight -> "Flashlight turned ${if (action.enable) "on" else "off"}."
            is SystemUtilityAction.OpenSettings -> "Opened ${action.settingName} settings."
            is SystemUtilityAction.ShowAlarms -> "Opened alarms."
            is SystemUtilityAction.ShowTimers -> "Opened timers."
        }
        return SystemUtilityResult.Success(action, msg)
    }
}
