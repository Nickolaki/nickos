package com.nbaltas.nickos.cam

/**
 * Decides how warm a tablet is from Android's thermal status (0..6, or -1 before Android 10)
 * and the battery temperature in tenths of a degree C (-1 when unknown). Warm steps the picture
 * down, too hot ends the run and starts a cooldown. Pure: every time is passed in.
 */
class ThermalPolicy {
    enum class Level(val wire: String) { NORMAL("normal"), HOT("hot"), TOO_HOT("tooHot") }

    var level = Level.NORMAL
        private set

    /** No camera before this time. 0 until the tablet has been too hot once. */
    var cooldownUntil = 0L
        private set

    /** When the tablet was first seen cool again while HOT, or -1. */
    private var coolSince = -1L

    fun canStart(now: Long): Boolean = now >= cooldownUntil

    /** A new run begins: forget the last run's level. The cooldown stays. */
    fun reset() {
        level = Level.NORMAL
        coolSince = -1L
    }

    fun update(status: Int, batteryTenths: Int, now: Long): Level {
        // Too hot is the end of this run; only reset() leaves it.
        if (level == Level.TOO_HOT) return level
        if (status >= STATUS_CRITICAL || batteryTenths >= TOO_HOT_TENTHS) {
            level = Level.TOO_HOT
            cooldownUntil = now + COOLDOWN_MS
            coolSince = -1L
            return level
        }
        if (status >= STATUS_SEVERE || batteryTenths >= HOT_TENTHS) {
            level = Level.HOT
            coolSince = -1L
            return level
        }
        if (level == Level.HOT) {
            if (status <= STATUS_LIGHT && batteryTenths < COOL_TENTHS) {
                if (coolSince < 0) coolSince = now
                if (now - coolSince >= COOL_FOR_MS) {
                    level = Level.NORMAL
                    coolSince = -1L
                }
            } else {
                coolSince = -1L
            }
        }
        return level
    }

    companion object {
        const val STATUS_LIGHT = 1
        const val STATUS_SEVERE = 3
        const val STATUS_CRITICAL = 4
        const val HOT_TENTHS = 410
        const val TOO_HOT_TENTHS = 450
        const val COOL_TENTHS = 390
        const val COOL_FOR_MS = 60_000L
        const val COOLDOWN_MS = 120_000L
        const val LOW_BATTERY = 15

        /** What the camera actually runs at: a warm tablet gets COOL whatever the parent chose. */
        fun profileFor(chosen: CamProfile, level: Level): CamProfile = if (level == Level.NORMAL) chosen else CamProfile.COOL

        /** Too little battery to spend on a camera. A tablet on its charger is fine. */
        fun lowBattery(percent: Int, charging: Boolean): Boolean = percent < LOW_BATTERY && !charging
    }
}
