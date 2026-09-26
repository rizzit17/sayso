package com.samsung.prism.teachable.generalization

import com.samsung.prism.teachable.model.SlotDefinition
import java.util.Locale

enum class AppDomain {
    SETTINGS,
    MESSAGING,
    MEDIA,
    PRODUCTIVITY_CLOCK,
    NAVIGATION,
    COMMERCE,
    GENERIC
}

data class ExtractedParameters(
    val appName: String? = null,
    val targetPackage: String? = null,
    val actionVerb: String? = null,
    val primaryTarget: String? = null,
    val settingName: String? = null,
    val recipient: String? = null,
    val messageContent: String? = null,
    val searchQuery: String? = null,
    val mediaTitle: String? = null,
    val timeOrDuration: String? = null,
    val destination: String? = null,
    val itemName: String? = null,
    val storeOrSource: String? = null,
    val quantity: Int? = null,
    val domain: AppDomain = AppDomain.GENERIC
) {
    /**
     * Formats detected parameters as (Label, Value) pairs for dynamic UI chip display.
     */
    fun toUiChips(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        actionVerb?.let { list.add(Pair("Action", it)) }
        settingName?.let { list.add(Pair("Setting", it)) }
        recipient?.let { list.add(Pair("To", it)) }
        messageContent?.let { list.add(Pair("Message", it)) }
        mediaTitle?.let { list.add(Pair("Media", it)) }
        searchQuery?.let { list.add(Pair("Query", it)) }
        timeOrDuration?.let { list.add(Pair("Time", it)) }
        destination?.let { list.add(Pair("Destination", it)) }
        itemName?.let { list.add(Pair("Item", it)) }
        storeOrSource?.let { list.add(Pair("Store", it)) }
        quantity?.let { list.add(Pair("Qty", it.toString())) }
        primaryTarget?.let {
            if (settingName == null && mediaTitle == null && itemName == null && searchQuery == null) {
                list.add(Pair("Target", it))
            }
        }
        appName?.let { list.add(Pair("App", it)) }
        return list
    }

    /**
     * Converts extracted entities into universal workflow SlotDefinitions.
     */
    fun toSlotDefinitions(): List<SlotDefinition> {
        val slots = mutableListOf<SlotDefinition>()
        settingName?.let {
            slots.add(SlotDefinition(name = "setting", type = "string", required = true, defaultValue = it))
        }
        recipient?.let {
            slots.add(SlotDefinition(name = "recipient", type = "string", required = true, defaultValue = it))
        }
        messageContent?.let {
            slots.add(SlotDefinition(name = "message", type = "string", required = false, defaultValue = it))
        }
        mediaTitle?.let {
            slots.add(SlotDefinition(name = "media", type = "string", required = true, defaultValue = it))
        }
        searchQuery?.let {
            slots.add(SlotDefinition(name = "query", type = "string", required = true, defaultValue = it))
        }
        timeOrDuration?.let {
            slots.add(SlotDefinition(name = "time", type = "string", required = true, defaultValue = it))
        }
        destination?.let {
            val name = if (domain == AppDomain.COMMERCE) "address" else "destination"
            slots.add(SlotDefinition(name = name, type = "string", required = false, defaultValue = it))
        }
        itemName?.let {
            slots.add(SlotDefinition(name = "item", type = "string", required = true, defaultValue = it))
        }
        storeOrSource?.let {
            val name = if (domain == AppDomain.COMMERCE) "restaurant" else "store"
            slots.add(SlotDefinition(name = name, type = "string", required = true, defaultValue = it))
        }
        if (quantity != null) {
            slots.add(SlotDefinition(name = "quantity", type = "integer", required = false, defaultValue = quantity.toString()))
        } else if (domain == AppDomain.COMMERCE && actionVerb?.contains("Order", ignoreCase = true) == true) {
            slots.add(SlotDefinition(name = "quantity", type = "integer", required = false, defaultValue = "1"))
        }

        if (domain == AppDomain.COMMERCE && destination == null && actionVerb?.contains("Order", ignoreCase = true) == true) {
            slots.add(SlotDefinition(name = "address", type = "enum", required = false, defaultValue = "Home", enumValues = listOf("Home", "Work")))
        }

        if (slots.isEmpty() && primaryTarget != null) {
            slots.add(SlotDefinition(name = "target", type = "string", required = true, defaultValue = primaryTarget))
        }
        appName?.let {
            slots.add(SlotDefinition(name = "platform", type = "enum", required = true, defaultValue = it, enumValues = listOf(it)))
        }
        return slots
    }
}

/**
 * Universal, domain-agnostic extractor that analyzes user utterances across all kinds of apps:
 * - System Settings (toggles, network, display, volume)
 * - Messaging & Social (WhatsApp, Telegram, SMS, Email)
 * - Media & Entertainment (Spotify, YouTube, Music, Camera)
 * - Clock, Timer & Productivity (Alarms, Timers, Keep, Calendar)
 * - Navigation & Transportation (Google Maps, Uber)
 * - E-Commerce & Delivery (Amazon, Zomato, Starbucks)
 * - Open-ended general apps
 */
object UniversalDomainExtractor {

    private val numberWords = mapOf(
        "one" to 1, "a" to 1, "an" to 1, "two" to 2, "three" to 3,
        "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    fun extract(utterance: String, fallbackTargetPackage: String? = null): ExtractedParameters {
        val raw = utterance.trim()
        if (raw.isBlank()) return ExtractedParameters()

        val lower = raw.lowercase(Locale.ROOT)

        // 1. Detect Target Application and Package
        val (appName, targetPackage) = detectApp(raw, lower, fallbackTargetPackage)

        // 2. Identify Domain and Domain-Specific Entities
        var domain = AppDomain.GENERIC
        var actionVerb: String? = null
        var settingName: String? = null
        var recipient: String? = null
        var messageContent: String? = null
        var mediaTitle: String? = null
        var searchQuery: String? = null
        var timeOrDuration: String? = null
        var destination: String? = null
        var itemName: String? = null
        var storeOrSource: String? = null
        var primaryTarget: String? = null
        var quantity: Int? = null

        // Domain A: System Settings & Utilities
        val settingKeywords = listOf(
            "airplane mode" to "Airplane Mode",
            "airplane" to "Airplane Mode",
            "flight mode" to "Airplane Mode",
            "wi-fi" to "Wi-Fi",
            "wifi" to "Wi-Fi",
            "bluetooth" to "Bluetooth",
            "hotspot" to "Mobile Hotspot",
            "dark mode" to "Dark Theme",
            "dark theme" to "Dark Theme",
            "mobile data" to "Mobile Data",
            "battery saver" to "Battery Saver",
            "flashlight" to "Flashlight",
            "torch" to "Flashlight",
            "do not disturb" to "Do Not Disturb",
            "dnd" to "Do Not Disturb",
            "auto-rotate" to "Auto-rotate",
            "nfc" to "NFC",
            "location" to "Location Services"
        )
        val matchedSetting = settingKeywords.firstOrNull { lower.contains(it.first) }

        if (matchedSetting != null || lower.contains("setting")) {
            domain = AppDomain.SETTINGS
            settingName = matchedSetting?.second ?: "Settings"
            actionVerb = when {
                lower.contains("turn on") || lower.contains("enable") || lower.contains("activate") -> "Turn on"
                lower.contains("turn off") || lower.contains("disable") || lower.contains("deactivate") -> "Turn off"
                lower.contains("toggle") || lower.contains("switch") -> "Toggle"
                else -> "Toggle"
            }
        }

        // Domain B: Messaging & Communication (WhatsApp, Telegram, Messages, Mail)
        val isMessaging = lower.contains("whatsapp") || lower.contains("message") ||
                lower.contains("text") || lower.contains("email") || lower.contains("chat") ||
                lower.contains("telegram") || lower.startsWith("send ")

        if (isMessaging && domain == AppDomain.GENERIC) {
            domain = AppDomain.MESSAGING
            actionVerb = if (lower.contains("email")) "Email" else "Send Message"

            // Extract recipient from "to <Recipient>"
            val recipientMatch = Regex("(?:to|message|text|email)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|via|app|saying|text|and|with)|$)", RegexOption.IGNORE_CASE).find(raw)
            recipient = recipientMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }

            // Extract message body from "saying <Message>" or "text <Message>"
            val messageMatch = Regex("(?:saying|with text|text)\\s+(?:that\\s+)?([\"']?)(.+?)\\1$", RegexOption.IGNORE_CASE).find(raw)
            messageContent = messageMatch?.groupValues?.get(2)?.trim()
        }

        // Domain C: Clock, Alarms & Timers
        val isClock = lower.contains("alarm") || lower.contains("timer") || lower.contains("clock") || lower.contains("stopwatch")
        if (isClock && domain == AppDomain.GENERIC) {
            domain = AppDomain.PRODUCTIVITY_CLOCK
            actionVerb = if (lower.contains("timer")) "Set Timer" else "Set Alarm"

            // Extract time e.g. "7:30 AM", "7 AM", "10 minutes"
            val timeMatch = Regex("(?:for|at)\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|\\d+\\s*(?:minute|min|hour|second)s?)", RegexOption.IGNORE_CASE).find(raw)
            timeOrDuration = timeMatch?.groupValues?.get(1)?.trim()
        }

        // Domain D: Media & Entertainment (Spotify, YouTube, Music, Camera)
        val isMedia = lower.contains("spotify") || lower.contains("youtube") || lower.contains("music") ||
                lower.contains("play") || lower.contains("watch") || lower.contains("listen")

        if (isMedia && domain == AppDomain.GENERIC) {
            domain = AppDomain.MEDIA
            actionVerb = when {
                lower.contains("watch") -> "Watch"
                lower.contains("listen") -> "Listen"
                else -> "Play"
            }
            val songMatch = Regex("(?:play|watch|listen to)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|via|app)|$)", RegexOption.IGNORE_CASE).find(raw)
            mediaTitle = songMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }
        }

        // Domain E: Navigation & Directions (Maps, Uber)
        val isNavigation = lower.contains("navigate") || lower.contains("directions") || lower.contains("map") ||
                lower.contains("drive to") || lower.contains("go to") || lower.contains("uber")

        if (isNavigation && domain == AppDomain.GENERIC) {
            domain = AppDomain.NAVIGATION
            actionVerb = if (lower.contains("uber")) "Book Ride" else "Navigate"

            val destMatch = Regex("(?:to|directions to|navigate to|go to)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|via|app)|$)", RegexOption.IGNORE_CASE).find(raw)
            destination = destMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }
        }

        // Domain F: Search & Browse
        val isSearch = lower.contains("search for") || lower.startsWith("search ") || lower.contains("find ") || lower.contains("lookup")
        if (isSearch && domain == AppDomain.GENERIC) {
            domain = AppDomain.GENERIC
            actionVerb = "Search"
            val queryMatch = Regex("(?:search for|search|find|lookup)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|via|app)|$)", RegexOption.IGNORE_CASE).find(raw)
            searchQuery = queryMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }
        }

        // Domain G: E-Commerce, Food & Delivery
        val isCommerce = lower.contains("order") || lower.contains("buy") || lower.contains("purchase") ||
                lower.contains("deliver") || lower.contains("zomato") || lower.contains("amazon") || lower.contains("starbucks")

        if (isCommerce && (domain == AppDomain.GENERIC || domain == AppDomain.SETTINGS)) {
            domain = AppDomain.COMMERCE
            searchQuery = null // Clear generic searchQuery in favor of ecommerce itemName
            actionVerb = when {
                lower.contains("search") -> "Search"
                lower.contains("buy") -> "Buy"
                else -> "Order"
            }

            val itemMatch = Regex("(?:order|buy|get|search\\s+for|search|find)\\s+(?:(?:a|an|the|\\d+)\\s+)?([a-zA-Z0-9'\\s]+?)(?:\\s+(?:from|at|on|in|to|using|and)|$)", RegexOption.IGNORE_CASE).find(raw)
            itemName = itemMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }

            val storeMatch = Regex("(?:from|at)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|to|deliver|and)|$)", RegexOption.IGNORE_CASE).find(raw)
            storeOrSource = storeMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }

            val excludedCommerceDestinations = setOf("cart", "bag", "checkout", "basket", "wishlist", "buy", "order")
            val destMatch = Regex("(?:deliver to|to)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|from|and)|$)", RegexOption.IGNORE_CASE).find(raw)
            val candidateDest = destMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }
            destination = if (candidateDest != null && candidateDest.lowercase() !in excludedCommerceDestinations) {
                candidateDest
            } else {
                null
            }

            quantity = extractQuantity(lower)
        }

        // Fallback Primary Target if not yet identified
        if (primaryTarget == null) {
            primaryTarget = settingName ?: mediaTitle ?: searchQuery ?: itemName ?: recipient ?: destination
        }

        if (actionVerb == null) {
            val firstWord = raw.split(" ").firstOrNull()?.replaceFirstChar { it.uppercase() }
            actionVerb = firstWord ?: "Run"
        }

        return ExtractedParameters(
            appName = appName,
            targetPackage = targetPackage,
            actionVerb = actionVerb,
            primaryTarget = primaryTarget,
            settingName = settingName,
            recipient = recipient,
            messageContent = messageContent,
            searchQuery = searchQuery,
            mediaTitle = mediaTitle,
            timeOrDuration = timeOrDuration,
            destination = destination,
            itemName = itemName,
            storeOrSource = storeOrSource,
            quantity = quantity,
            domain = domain
        )
    }

    private fun detectApp(raw: String, lower: String, fallbackPackage: String?): Pair<String, String?> {
        // Explicit preposition pattern: "on <App>", "in <App>", "using <App>", "via <App>"
        val platformMatch = Regex(
            "(?:on|in|using|via|app)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:and|with|to|deliver|for|saying)|$)",
            RegexOption.IGNORE_CASE
        ).find(raw)
        val extractedApp = platformMatch?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }

        return when {
            lower.contains("setting") || lower.contains("airplane") || lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("hotspot") ->
                Pair("System Settings", "com.android.settings")

            lower.contains("whatsapp") ->
                Pair("WhatsApp", "com.whatsapp")

            lower.contains("youtube") ->
                Pair("YouTube", "com.google.android.youtube")

            lower.contains("spotify") ->
                Pair("Spotify", "com.spotify.music")

            lower.contains("maps") || lower.contains("google maps") ->
                Pair("Google Maps", "com.google.android.apps.maps")

            lower.contains("clock") || lower.contains("alarm") || lower.contains("timer") ->
                Pair("Clock", "com.google.android.deskclock")

            lower.contains("camera") ->
                Pair("Camera", null)

            lower.contains("calendar") ->
                Pair("Calendar", "com.google.android.calendar")

            lower.contains("keep") || lower.contains("notes") ->
                Pair("Google Keep", "com.google.android.keep")

            lower.contains("amazon") ->
                Pair("Amazon", "com.amazon.mShop.android.shopping")

            lower.contains("zomato") ->
                Pair("Zomato", "com.application.zomato")

            !extractedApp.isNullOrBlank() && !extractedApp.equals("the", ignoreCase = true) && !extractedApp.equals("a", ignoreCase = true) ->
                Pair(extractedApp, fallbackPackage)

            else ->
                Pair(fallbackPackage?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() } ?: "Auto-detected during demonstration", fallbackPackage)
        }
    }

    private fun extractQuantity(lower: String): Int? {
        val digitMatch = Regex("\\b(\\d+)\\b").find(lower)
        if (digitMatch != null) {
            return digitMatch.groupValues[1].toIntOrNull()
        }
        for ((word, num) in numberWords) {
            if (Regex("\\b$word\\b").containsMatchIn(lower) && word != "a" && word != "an") {
                return num
            }
        }
        return null
    }

    private fun cleanEntity(raw: String): String {
        var cleaned = raw.trim()
        val prefixes = listOf("a ", "an ", "the ", "some ")
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
            }
        }
        return cleaned.replaceFirstChar { it.uppercase() }
    }
}
