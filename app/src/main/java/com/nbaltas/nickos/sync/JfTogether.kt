package com.nbaltas.nickos.sync

/** The decisions behind "Watch together" from Jellyfin, with no screen in them so they are tested alone. */
object JfTogether {
    enum class Plan { START_NOW, SEND, SAVE }

    /**
     * What one tap on the main button does. [owners] are the tablets (and [Library.SELF]) that have the
     * movie, or empty when no copy is known anywhere. Everywhere already: start. On the parent or on
     * some tablets: send. Nowhere yet: save to the parent first, which then sends.
     */
    fun plan(selected: Set<String>, owners: Set<String>): Plan = when {
        owners.isNotEmpty() && selected.all { it in owners } -> Plan.START_NOW
        owners.isNotEmpty() -> Plan.SEND
        else -> Plan.SAVE
    }

    /** [start] is the set to start on right now, or null to keep waiting. [dropped] went offline and left the selection. */
    class Step(val start: Set<String>?, val dropped: Set<String>)

    /**
     * Called whenever the picture changes while getting ready. A selected tablet that is offline is
     * dropped, never waited for. Start the moment every tablet left owns the file. If nobody is left,
     * [Step.start] stays null and the caller ends the preparation.
     */
    fun step(selected: Set<String>, online: Set<String>, owners: Set<String>, started: Boolean): Step {
        if (started) return Step(null, emptySet())
        val dropped = selected.filter { it !in online }.toSet()
        val left = selected - dropped
        val go = left.isNotEmpty() && left.all { it in owners }
        return Step(if (go) left else null, dropped)
    }

    /** "Leo went offline, starting without them", or "Leo and Mia went offline". [name] gives a tablet's name. */
    fun droppedNote(dropped: Set<String>, anyLeft: Boolean, name: (String) -> String): String {
        val who = TogetherText.join(dropped.map(name))
        return if (anyLeft) "$who went offline, starting without them" else "$who went offline"
    }

    /** One tablet's line while getting ready. */
    fun rowText(owns: Boolean, job: SyncMessages.JobEntry?): String = when {
        owns -> "Ready"
        job != null && JobState.of(job.state) == JobState.FAILED -> "Failed"
        job != null -> TogetherText.jobProgress(job)
        else -> "Waiting"
    }
}
