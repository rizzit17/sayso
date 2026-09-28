package com.samsung.prism.teachable.utility

import android.provider.Settings
import java.util.Calendar
import java.util.Locale

object SystemUtilityParser {

    private val wordToNumber = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "forty-five" to 45, "fifty" to 50
    )

    fun parse(utterance: String): SystemUtilityAction? {
        val raw = utterance.trim()
        if (raw.isBlank()) return null
        val lower = raw.lowercase(Locale.ROOT)

        // 1. Flashlight / Torch
        parseFlashlight(lower)?.let { return it }

        // 2. Settings Panels
        parseSettings(lower)?.let { return it }

        // 3. Timers (check before alarms since timer phrases are specific)
        parseTimer(raw, lower)?.let { return it }

        // 4. Alarms
        parseAlarm(raw, lower)?.let { return it }

        // 5. Calendar / Reminders
        parseCalendar(raw, lower)?.let { return it }

        return null
    }

    private fun parseFlashlight(lower: String): SystemUtilityAction? {
        val isTorch = lower.contains("flashlight") || lower.contains("torch")
        if (!isTorch) return null

        return when {
            lower.contains("turn on") || lower.contains("enable") || lower.contains("switch on") ||
                    lower.contains("torch on") || lower.contains("flashlight on") -> {
                SystemUtilityAction.ToggleFlashlight(enable = true)
            }
            lower.contains("turn off") || lower.contains("disable") || lower.contains("switch off") ||
                    lower.contains("torch off") || lower.contains("flashlight off") -> {
                SystemUtilityAction.ToggleFlashlight(enable = false)
            }
            lower.contains("toggle") -> {
                SystemUtilityAction.ToggleFlashlight(enable = true)
            }
            else -> SystemUtilityAction.ToggleFlashlight(enable = true)
        }
    }

    private fun parseSettings(lower: String): SystemUtilityAction? {
        if (!lower.contains("setting") && !lower.startsWith("open wi-fi") && !lower.startsWith("open wifi") && !lower.startsWith("open bluetooth")) {
            return null
        }

        return when {
            lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("internet") || lower.contains("network") -> {
                SystemUtilityAction.OpenSettings("Wi-Fi", Settings.ACTION_WIFI_SETTINGS)
            }
            lower.contains("bluetooth") -> {
                SystemUtilityAction.OpenSettings("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            lower.contains("display") || lower.contains("brightness") || lower.contains("screen") -> {
                SystemUtilityAction.OpenSettings("Display", Settings.ACTION_DISPLAY_SETTINGS)
            }
            lower.contains("sound") || lower.contains("volume") || lower.contains("audio") -> {
                SystemUtilityAction.OpenSettings("Sound", Settings.ACTION_SOUND_SETTINGS)
            }
            lower.contains("battery") || lower.contains("power") -> {
                SystemUtilityAction.OpenSettings("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS)
            }
            lower.contains("date") || lower.contains("time setting") -> {
                SystemUtilityAction.OpenSettings("Date & Time", Settings.ACTION_DATE_SETTINGS)
            }
            lower.trim() == "open settings" || lower.trim() == "settings" -> {
                SystemUtilityAction.OpenSettings("System Settings", Settings.ACTION_SETTINGS)
            }
            else -> null
        }
    }

    private fun parseTimer(raw: String, lower: String): SystemUtilityAction? {
        if (lower.contains("show timer") || lower.contains("view timer") || lower.contains("open timer")) {
            return SystemUtilityAction.ShowTimers
        }

        val isTimer = lower.contains("timer") || lower.contains("countdown")
        if (!isTimer) return null

        var totalSeconds = 0

        // Match Hours: e.g. "1 hour", "2 hours", "two hours"
        val hourMatch = Regex("(\\d+|one|two|three|four|five)\\s*(?:hour|hr)s?", RegexOption.IGNORE_CASE).find(lower)
        if (hourMatch != null) {
            val numStr = hourMatch.groupValues[1]
            val h = numStr.toIntOrNull() ?: wordToNumber[numStr] ?: 0
            totalSeconds += h * 3600
        }

        // Match Minutes: e.g. "10 minutes", "5 mins", "15 minute timer"
        val minMatch = Regex("(\\d+|one|two|three|four|five|ten|fifteen|twenty|thirty|forty-five)\\s*(?:minute|min)s?", RegexOption.IGNORE_CASE).find(lower)
        if (minMatch != null) {
            val numStr = minMatch.groupValues[1]
            val m = numStr.toIntOrNull() ?: wordToNumber[numStr] ?: 0
            totalSeconds += m * 60
        }

        // Match Seconds: e.g. "30 seconds", "45 secs"
        val secMatch = Regex("(\\d+|ten|twenty|thirty|forty|fifty)\\s*(?:second|sec)s?", RegexOption.IGNORE_CASE).find(lower)
        if (secMatch != null) {
            val numStr = secMatch.groupValues[1]
            val s = numStr.toIntOrNull() ?: wordToNumber[numStr] ?: 0
            totalSeconds += s
        }

        if (totalSeconds <= 0) {
            // Check standalone number before/after timer e.g. "timer 5" -> 5 minutes default
            val standaloneMatch = Regex("(?:timer\\s+(?:for\\s+)?|set\\s+)(\\d+)(?:\\s*$)").find(lower)
            if (standaloneMatch != null) {
                val num = standaloneMatch.groupValues[1].toIntOrNull() ?: 0
                totalSeconds = num * 60
            }
        }

        if (totalSeconds > 0) {
            // Optional label e.g. "for baking", "named laundry"
            val labelMatch = Regex("(?:for|named|called|label)\\s+([a-zA-Z0-9\\s]+?)(?:\\s+timer|$)", RegexOption.IGNORE_CASE).find(raw)
            val label = labelMatch?.groupValues?.get(1)?.trim()?.takeIf { !it.contains("minute") && !it.contains("second") && !it.contains("hour") }
            return SystemUtilityAction.SetTimer(durationSeconds = totalSeconds, message = label)
        }

        return null
    }

    private fun parseAlarm(raw: String, lower: String): SystemUtilityAction? {
        if (lower.contains("show alarm") || lower.contains("view alarm") || lower.contains("open alarm")) {
            return SystemUtilityAction.ShowAlarms
        }

        val isAlarm = lower.contains("alarm") || lower.contains("wake me up") || lower.contains("wake up")
        if (!isAlarm) return null

        // 1. Time patterns:
        // Pattern A: "7:30 AM", "7:30pm", "06:45"
        val timeColonMatch = Regex("(\\d{1,2}):(\\d{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(lower)
        if (timeColonMatch != null) {
            var hour = timeColonMatch.groupValues[1].toInt()
            val minute = timeColonMatch.groupValues[2].toInt()
            val amPm = timeColonMatch.groupValues[3].lowercase(Locale.ROOT)

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            val message = extractAlarmLabel(raw)
            return SystemUtilityAction.SetAlarm(hour = hour, minute = minute, message = message)
        }

        // Pattern B: "7 AM", "6 pm", "seven am", "eight o'clock"
        val timeSimpleMatch = Regex("(?:at|for)\\s+(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\\s*(am|pm|o'clock)?", RegexOption.IGNORE_CASE).find(lower)
        if (timeSimpleMatch != null) {
            val hourStr = timeSimpleMatch.groupValues[1]
            var hour = hourStr.toIntOrNull() ?: wordToNumber[hourStr] ?: 0
            val amPm = timeSimpleMatch.groupValues[2].lowercase(Locale.ROOT)

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            // If no AM/PM specified, check morning / evening context
            if (amPm.isBlank() || amPm == "o'clock") {
                if (lower.contains("evening") || lower.contains("night")) {
                    if (hour < 12) hour += 12
                }
            }

            val message = extractAlarmLabel(raw)
            return SystemUtilityAction.SetAlarm(hour = hour, minute = 0, message = message)
        }

        // Pattern C: "wake me up at 6"
        val wakeMatch = Regex("wake\\s+(?:me\\s+)?up\\s+(?:tomorrow\\s+)?at\\s+(\\d{1,2})\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(lower)
        if (wakeMatch != null) {
            var hour = wakeMatch.groupValues[1].toInt()
            val amPm = wakeMatch.groupValues[2].lowercase(Locale.ROOT)
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            // Default wake up without AM/PM is usually AM
            if (amPm.isBlank() && hour in 4..11) {
                // morning
            }
            return SystemUtilityAction.SetAlarm(hour = hour, minute = 0, message = "Wake Up")
        }

        return null
    }

    private fun extractAlarmLabel(raw: String): String? {
        val labelMatch = Regex("(?:called|named|with message|with note|with label|for)\\s+([a-zA-Z0-9\\s]+)$", RegexOption.IGNORE_CASE).find(raw)
        return labelMatch?.groupValues?.get(1)?.trim()?.takeIf {
            !it.startsWith("tomorrow", ignoreCase = true) &&
                    !it.startsWith("morning", ignoreCase = true) &&
                    !it.startsWith("evening", ignoreCase = true)
        }
    }

    private fun parseCalendar(raw: String, lower: String): SystemUtilityAction? {
        val isCalendar = lower.contains("calendar") || lower.contains("reminder") ||
                lower.startsWith("remind me to") || lower.contains("schedule a meeting") || lower.contains("create event")
        if (!isCalendar) return null

        // Extract title
        var title = ""
        val titleMatch = Regex("(?:remind me to|reminder to|reminder for|add to calendar|create event|add event|schedule)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:at|on|for|tomorrow|today|next)|$)", RegexOption.IGNORE_CASE).find(raw)
        if (titleMatch != null) {
            title = titleMatch.groupValues[1].trim()
        }

        if (title.isBlank()) {
            title = raw.replace(Regex("(?i)add to calendar|on calendar|create event|set reminder"), "").trim()
        }

        title = title.replaceFirstChar { it.uppercase() }

        // Determine start and end time
        val cal = Calendar.getInstance()
        if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Look for time e.g. "at 5 PM", "at 10:30 AM", "at 3"
        val timeMatch = Regex("(?:at|for)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(lower)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toInt()
            val minute = timeMatch.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 0
            val amPm = timeMatch.groupValues[3].lowercase(Locale.ROOT)

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
        } else {
            // Default 1 hour from now
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }

        val startMillis = cal.timeInMillis
        val endMillis = startMillis + (60 * 60 * 1000) // 1 hour duration default

        return SystemUtilityAction.AddCalendarEvent(
            title = if (title.isBlank()) "Event" else title,
            startMillis = startMillis,
            endMillis = endMillis
        )
    }
}
