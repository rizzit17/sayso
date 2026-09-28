package com.samsung.prism.teachable.utility

enum class UtilityType {
    ALARM,
    TIMER,
    CALENDAR,
    FLASHLIGHT,
    SETTINGS,
    STOPWATCH,
    UNKNOWN
}

sealed class SystemUtilityAction {
    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val message: String? = null,
        val isAm: Boolean? = null
    ) : SystemUtilityAction() {
        val formattedTime: String
            get() {
                val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                val amPm = if (hour < 12) "AM" else "PM"
                return String.format("%d:%02d %s", h, minute, amPm)
            }
    }

    data class SetTimer(
        val durationSeconds: Int,
        val message: String? = null
    ) : SystemUtilityAction() {
        val formattedDuration: String
            get() {
                val hours = durationSeconds / 3600
                val minutes = (durationSeconds % 3600) / 60
                val seconds = durationSeconds % 60
                val parts = mutableListOf<String>()
                if (hours > 0) parts.add("$hours hour${if (hours > 1) "s" else ""}")
                if (minutes > 0) parts.add("$minutes minute${if (minutes > 1) "s" else ""}")
                if (seconds > 0 || parts.isEmpty()) parts.add("$seconds second${if (seconds > 1) "s" else ""}")
                return parts.joinToString(" and ")
            }
    }

    data class AddCalendarEvent(
        val title: String,
        val startMillis: Long? = null,
        val endMillis: Long? = null,
        val description: String? = null
    ) : SystemUtilityAction()

    data class ToggleFlashlight(
        val enable: Boolean
    ) : SystemUtilityAction()

    data class OpenSettings(
        val settingName: String,
        val intentAction: String
    ) : SystemUtilityAction()

    object ShowAlarms : SystemUtilityAction()

    object ShowTimers : SystemUtilityAction()
}

sealed class SystemUtilityResult {
    data class Success(
        val action: SystemUtilityAction,
        val message: String
    ) : SystemUtilityResult()

    data class Failed(
        val action: SystemUtilityAction?,
        val reason: String
    ) : SystemUtilityResult()
}
