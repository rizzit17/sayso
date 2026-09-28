package com.samsung.prism.teachable.utility

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import com.samsung.prism.teachable.ai.GenAiManager
import java.util.Locale

class AndroidSystemUtilityHandler(
    private val context: Context,
    private val genAiManager: GenAiManager? = null
) : ISystemUtilityHandler {

    private val tag = "AndroidSystemUtility"

    override fun parseUtilityAction(utterance: String): SystemUtilityAction? {
        return SystemUtilityParser.parse(utterance)
    }

    override fun canHandle(utterance: String): Boolean {
        if (SystemUtilityParser.parse(utterance) != null) return true
        if (genAiManager?.isGenAiActive == true) {
            val lower = utterance.lowercase(Locale.ROOT)
            return lower.contains("alarm") || lower.contains("wake me up") || lower.contains("wake up") ||
                    lower.contains("timer") || lower.contains("countdown") ||
                    lower.contains("reminder") || lower.contains("calendar") || lower.contains("remind me") ||
                    lower.contains("flashlight") || lower.contains("torch") ||
                    lower.contains("setting") || lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth")
        }
        return false
    }

    override suspend fun execute(utterance: String): SystemUtilityResult? {
        val action = if (genAiManager != null && genAiManager.isGenAiActive) {
            genAiManager.parseSystemUtility(utterance)
        } else {
            parseUtilityAction(utterance)
        } ?: return null
        return execute(action)
    }

    override suspend fun execute(action: SystemUtilityAction): SystemUtilityResult {
        return try {
            when (action) {
                is SystemUtilityAction.SetAlarm -> handleSetAlarm(action)
                is SystemUtilityAction.SetTimer -> handleSetTimer(action)
                is SystemUtilityAction.AddCalendarEvent -> handleCalendarEvent(action)
                is SystemUtilityAction.ToggleFlashlight -> handleFlashlight(action)
                is SystemUtilityAction.OpenSettings -> handleOpenSettings(action)
                is SystemUtilityAction.ShowAlarms -> handleShowAlarms(action)
                is SystemUtilityAction.ShowTimers -> handleShowTimers(action)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to execute system utility action $action", e)
            SystemUtilityResult.Failed(action, "Could not perform action: ${e.message}")
        }
    }

    private fun handleSetAlarm(action: SystemUtilityAction.SetAlarm): SystemUtilityResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            action.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            SystemUtilityResult.Success(action, "Alarm set for ${action.formattedTime}.")
        } catch (e: Exception) {
            Log.w(tag, "Direct alarm intent failed, falling back to show alarms", e)
            val fallbackIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            SystemUtilityResult.Success(action, "Opened Clock for ${action.formattedTime} alarm.")
        }
    }

    private fun handleSetTimer(action: SystemUtilityAction.SetTimer): SystemUtilityResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, action.durationSeconds)
            action.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            SystemUtilityResult.Success(action, "Timer set for ${action.formattedDuration}.")
        } catch (e: Exception) {
            Log.w(tag, "Direct timer intent failed, falling back", e)
            val fallbackIntent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            SystemUtilityResult.Success(action, "Timer configured for ${action.formattedDuration}.")
        }
    }

    private fun handleCalendarEvent(action: SystemUtilityAction.AddCalendarEvent): SystemUtilityResult {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, action.title)
            action.startMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            action.endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            action.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
        return SystemUtilityResult.Success(action, "Created calendar event: ${action.title}.")
    }

    private fun handleFlashlight(action: SystemUtilityAction.ToggleFlashlight): SystemUtilityResult {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager != null) {
            try {
                val cameraId = cameraManager.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, action.enable)
                    val state = if (action.enable) "on" else "off"
                    return SystemUtilityResult.Success(action, "Flashlight turned $state.")
                }
            } catch (e: Exception) {
                Log.w(tag, "CameraManager torch failed: ${e.message}")
            }
        }
        val state = if (action.enable) "on" else "off"
        return SystemUtilityResult.Success(action, "Flashlight toggle requested ($state).")
    }

    private fun handleOpenSettings(action: SystemUtilityAction.OpenSettings): SystemUtilityResult {
        val intent = Intent(action.intentAction).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return SystemUtilityResult.Success(action, "Opened ${action.settingName} settings.")
    }

    private fun handleShowAlarms(action: SystemUtilityAction.ShowAlarms): SystemUtilityResult {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return SystemUtilityResult.Success(action, "Opened alarms.")
    }

    private fun handleShowTimers(action: SystemUtilityAction.ShowTimers): SystemUtilityResult {
        val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return SystemUtilityResult.Success(action, "Opened timers.")
    }
}
