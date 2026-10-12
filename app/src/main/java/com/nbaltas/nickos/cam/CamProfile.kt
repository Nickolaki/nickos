package com.nbaltas.nickos.cam

/**
 * How big, how often and how hard a tablet camera's picture is squeezed. [name] is what travels
 * on the link. The parent picks LOW, NORMAL or HIGH; COOL is what a warm tablet steps down to.
 */
data class CamProfile(val name: String, val w: Int, val h: Int, val fps: Int, val q: Int) {
    companion object {
        val LOW = CamProfile("low", 320, 240, 8, 50)
        val NORMAL = CamProfile("normal", 640, 480, 10, 55)
        val HIGH = CamProfile("high", 960, 720, 12, 60)
        val COOL = CamProfile("cool", 320, 240, 5, 45)

        /** The parent's choice by its wire name. Anything unknown is NORMAL. */
        fun of(name: String?): CamProfile = when (name) {
            "low" -> LOW
            "high" -> HIGH
            else -> NORMAL
        }
    }
}

/**
 * The parent's camera settings on the Master (Settings, Cameras). [profile] is a [CamProfile]
 * wire name, [maxMin] the auto-off in minutes, [layoutMs] and [fadeMs] the transition lengths
 * (0 is off).
 */
data class CamPrefs(
    val profile: String = "normal",
    val maxMin: Int = 10,
    val layoutMs: Int = 220,
    val fadeMs: Int = 250,
) {
    val maxMs: Long get() = maxMin * 60_000L
}
