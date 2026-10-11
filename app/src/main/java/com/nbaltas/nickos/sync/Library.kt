package com.nbaltas.nickos.sync

/**
 * One movie or episode in the library the Master shows: the same file (by [fid]) merged across
 * every tablet that has it. [owners] holds tablet ids, with [Library.SELF] for the Master's own
 * tablet. [episode] is set when the title carries an episode code like S02E05.
 */
data class LibraryItem(
    val fid: String,
    val title: String,
    val name: String,
    val size: Long,
    val durMs: Long,
    val owners: Set<String>,
    val episode: Episode?,
)

/** Episodes of one show, sorted by season then number. */
data class Show(val name: String, val episodes: List<LibraryItem>)

/** Pure library maths for the Watch together screens. No Android imports: plain JVM tests. */
object Library {
    /** The Master's own tablet, as a key next to the linked tablets' ids. */
    const val SELF = "self"

    /**
     * Merges what each tablet reported (key: tablet id or [SELF]) into one entry per fid. Sorted by
     * how many tablets have it (most first), then title. The first non-zero duration wins.
     */
    fun merge(videos: Map<String, List<SyncMessages.VideoEntry>>): List<LibraryItem> {
        val byFid = LinkedHashMap<String, LibraryItem>()
        for ((owner, list) in videos) for (v in list) {
            val cur = byFid[v.fid]
            byFid[v.fid] = if (cur == null) {
                LibraryItem(v.fid, v.title, v.name, v.size, v.durMs, setOf(owner), VideoId.episode(v.title)?.takeIf { it.show.isNotBlank() })
            } else cur.copy(owners = cur.owners + owner, durMs = if (cur.durMs > 0) cur.durMs else v.durMs)
        }
        return byFid.values.sortedWith(compareByDescending<LibraryItem> { it.owners.size }.thenBy { it.title.lowercase() })
    }

    /** Everything without an episode code. */
    fun movies(items: List<LibraryItem>): List<LibraryItem> = items.filter { it.episode == null }

    /** One entry per show name (case ignored), sorted by name. */
    fun shows(items: List<LibraryItem>): List<Show> =
        items.filter { it.episode != null }
            .groupBy { it.episode!!.show.lowercase() }
            .map { (_, eps) -> Show(eps.first().episode!!.show, eps.sortedWith(compareBy({ it.episode!!.season }, { it.episode!!.number }))) }
            .sortedBy { it.name.lowercase() }

    /** What tablet [id] (or [SELF]) has, in library order. */
    fun ownedBy(items: List<LibraryItem>, id: String): List<LibraryItem> = items.filter { id in it.owners }

    /**
     * The episode after [fid] in its show, among files that every tablet in [nodes] has. Null for
     * a movie, the last episode, or when the next one is missing on any of those tablets.
     */
    fun next(items: List<LibraryItem>, fid: String, nodes: Set<String>): LibraryItem? {
        val cur = items.firstOrNull { it.fid == fid }?.episode ?: return null
        val eps = shows(items).firstOrNull { it.name.equals(cur.show, ignoreCase = true) }?.episodes ?: return null
        val after = eps.firstOrNull { e -> e.episode!!.let { it.season > cur.season || (it.season == cur.season && it.number > cur.number) } }
        return after?.takeIf { it.owners.containsAll(nodes) }
    }
}
