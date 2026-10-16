package com.nbaltas.nickos.talk

/** A kid tablet shown on the Cameras screen, as far as talking to it goes. */
data class TalkTarget(val id: String, val name: String, val online: Boolean, val capable: Boolean)

object TalkRecipients {
    /**
     * Who hears the parent for one press: the kid tablets on the screen that are online and understand talk. Worked out
     * once when the press starts and never again, so a tablet that appears mid press is not suddenly listening.
     */
    fun freeze(shown: List<TalkTarget>): List<TalkTarget> = shown.filter { it.online && it.capable }.distinctBy { it.id }
}

/** The words on the Cameras screen. Plain, no dashes. */
object TalkText {
    const val HOLD = "Hold to talk"
    const val NOBODY = "No tablet to talk to"
    const val UPDATE = "Update this tablet"
    const val UPDATE_HINT = "Update this tablet to talk to it"
    const val BANNER_FALLBACK = "Parent is talking"
    const val NEEDS_MIC = "Allow the microphone, then hold to talk again"
    const val MIC_FAILED = "The microphone stopped working"
    const val MIC_ALLOWED = "Microphone allowed. Hold to talk."
    const val MIC_DENIED = "Talking needs the microphone"

    /** "Hailey", "Hailey, Leo", and a count once the list would be long. */
    fun who(names: List<String>): String = when {
        names.size <= 3 -> names.joinToString(", ")
        else -> "${names.size} tablets"
    }

    fun talking(names: List<String>) = "Talking to ${who(names)}"
    fun ready(names: List<String>) = if (names.isEmpty()) NOBODY else "Hold to talk to ${who(names)}"

    /** The kid's banner. "Dad's Tablet" says "Dad is talking"; no name says "Parent is talking". */
    fun banner(masterName: String?): String {
        val n = masterName?.trim().orEmpty()
        val short = Regex("^(.+?)['’]s\\s+(tablet|phone)$", RegexOption.IGNORE_CASE).find(n)?.groupValues?.get(1) ?: n
        // The default name for an unnamed Master is "Parent's Tablet", which reads "Parent is talking" anyway.
        return if (short.isEmpty()) BANNER_FALLBACK else "$short is talking"
    }
}

/** What the big Hold to talk button and its line say, from the tablets on the screen and whether a press is live. */
data class TalkBar(val enabled: Boolean, val talking: Boolean, val button: String, val line: String) {
    companion object {
        const val TALKING = "Talking"

        /** [live]: the names frozen at the start of the press, or null when nobody is talking. */
        fun of(shown: List<TalkTarget>, live: List<String>?): TalkBar {
            if (live != null) return TalkBar(true, true, TALKING, TalkText.talking(live))
            val names = TalkRecipients.freeze(shown).map { it.name }
            return when {
                names.isNotEmpty() -> TalkBar(true, false, TalkText.HOLD, TalkText.ready(names))
                // A tablet is there but too old to hear: say so rather than just "nobody".
                shown.any { it.online && !it.capable } -> TalkBar(false, false, TalkText.HOLD, TalkText.UPDATE)
                else -> TalkBar(false, false, TalkText.HOLD, TalkText.NOBODY)
            }
        }
    }
}

/** Why a press stopped capturing. Every way out of talking has one. */
enum class TalkStop { RELEASED, CANCELLED, LEFT_SCREEN, BACKGROUNDED, PERMISSION_LOST, AUDIO_ERROR, ALL_GONE, TIME_CAP }

object TalkStopPolicy {
    /** The most one press may last. The Node has the same cap on its side. */
    const val MAX_MS = 120_000L

    data class Inputs(val permission: Boolean, val audioOk: Boolean, val liveRecipients: Int, val elapsedMs: Long)

    /** Null while talking may carry on; otherwise the first reason it must stop. Checked on every frame. */
    fun reason(i: Inputs): TalkStop? = when {
        !i.permission -> TalkStop.PERMISSION_LOST
        !i.audioOk -> TalkStop.AUDIO_ERROR
        i.liveRecipients <= 0 -> TalkStop.ALL_GONE
        i.elapsedMs >= MAX_MS -> TalkStop.TIME_CAP
        else -> null
    }

    /** Whether the parent should be told why it stopped (a release or leaving the screen speaks for itself). */
    fun worthSaying(r: TalkStop) = r == TalkStop.PERMISSION_LOST || r == TalkStop.AUDIO_ERROR || r == TalkStop.ALL_GONE || r == TalkStop.TIME_CAP
}

/**
 * A recipient's waiting frames: about a second of audio at most, and when it is full the OLDEST frame goes. A slow
 * tablet loses its own old audio and nothing else; it never holds up the microphone or any other tablet.
 */
class FrameQueue<T : Any>(val capacity: Int = DEFAULT_CAPACITY) {
    private val items = ArrayDeque<T>()
    @Volatile var dropped = 0L
        private set

    /** Adds [item]; returns how many old frames were thrown away to make room (0 or 1). */
    @Synchronized
    fun offer(item: T): Int {
        var out = 0
        while (items.size >= capacity) { items.removeFirst(); out++; dropped++ }
        items.addLast(item)
        return out
    }

    @Synchronized fun poll(): T? = items.removeFirstOrNull()
    @Synchronized fun clear() = items.clear()
    @get:Synchronized val size get() = items.size

    companion object {
        /** 25 frames of 40 ms. */
        const val DEFAULT_CAPACITY = 25
    }
}
