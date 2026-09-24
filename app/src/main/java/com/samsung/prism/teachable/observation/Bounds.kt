package com.samsung.prism.teachable.observation

import org.json.JSONObject

data class Bounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun isEmpty(): Boolean = width <= 0 || height <= 0

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    /**
     * Normalized relative position (0.0 to 1.0) on screen.
     * Prevents reliance on fixed pixel coordinates across different device form factors.
     */
    fun normalizedCenter(screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        val w = if (screenWidth > 0) screenWidth.toFloat() else 1080f
        val h = if (screenHeight > 0) screenHeight.toFloat() else 2400f
        return Pair(centerX / w, centerY / h)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("left", left)
        put("top", top)
        put("right", right)
        put("bottom", bottom)
    }

    companion object {
        val ZERO = Bounds(0, 0, 0, 0)

        fun fromJson(json: JSONObject?): Bounds {
            if (json == null) return ZERO
            return Bounds(
                left = json.optInt("left", 0),
                top = json.optInt("top", 0),
                right = json.optInt("right", 0),
                bottom = json.optInt("bottom", 0)
            )
        }
    }
}
