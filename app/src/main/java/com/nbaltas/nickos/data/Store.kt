package com.nbaltas.nickos.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

enum class Role { NONE, MASTER, NODE }

/** A Node as the Master remembers it. */
data class LinkedNode(
    val id: String,
    val name: String,
    val color: Int,
    val avatar: String,
    val token: String,
    val host: String? = null,
    val port: Int = 0,
    val lastSeen: Long = 0,
    val lastBattery: Int? = null,
    /** Jellyfin user whose library the Watch tab shows for this tablet. */
    val jfUserId: String? = null,
    val jfUserName: String? = null,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("color", color); put("avatar", avatar); put("token", token)
        put("host", host); put("port", port); put("lastSeen", lastSeen); put("lastBattery", lastBattery)
        put("jfUserId", jfUserId); put("jfUserName", jfUserName)
    }

    companion object {
        fun from(o: JSONObject) = LinkedNode(
            id = o.getString("id"), name = o.getString("name"), color = o.optInt("color"),
            avatar = o.optString("avatar", "initial"), token = o.getString("token"),
            host = o.optStr("host"), port = o.optInt("port"), lastSeen = o.optLong("lastSeen"),
            lastBattery = if (o.isNull("lastBattery") || !o.has("lastBattery")) null else o.optInt("lastBattery"),
            jfUserId = o.optStr("jfUserId"), jfUserName = o.optStr("jfUserName"),
        )
    }
}

/** A Master as a Node remembers it. */
data class LinkedMaster(val id: String, val name: String, val token: String) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("token", token)

    companion object {
        fun from(o: JSONObject) = LinkedMaster(o.getString("id"), o.getString("name"), o.getString("token"))
    }
}

/** A "keep watching" poster the Master pushes to a Node's Jellyfin tile. */
data class ResumeCard(val itemId: String, val title: String, val image: String?) {
    fun toJson() = JSONObject().put("itemId", itemId).put("title", title).put("image", image)

    companion object {
        fun from(o: JSONObject) = ResumeCard(o.getString("itemId"), o.getString("title"), o.optStr("image"))
    }
}

fun JSONObject.optStr(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

data class JellyfinAccount(
    val url: String,
    val username: String,
    val token: String,
    val userId: String,
    val isAdmin: Boolean,
)

/**
 * Everything a tablet remembers, in one SharedPreferences file. Values the UI watches are
 * mirrored into StateFlows; writes go through the setters so both stay in sync.
 */
class Store(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("nickos", Context.MODE_PRIVATE)

    val deviceId: String = p.getString("deviceId", null) ?: randomHex(6).also { p.edit().putString("deviceId", it).apply() }

    private val _role = MutableStateFlow(Role.valueOf(p.getString("role", Role.NONE.name)!!))
    val role: StateFlow<Role> = _role
    fun setRole(r: Role) { p.edit().putString("role", r.name).apply(); _role.value = r }

    // ---- this tablet ----
    private val _name = MutableStateFlow(p.getString("name", "") ?: "")
    val name: StateFlow<String> = _name
    fun setName(v: String) { p.edit().putString("name", v).apply(); _name.value = v }

    private val _color = MutableStateFlow(p.getInt("color", 0))
    val color: StateFlow<Int> = _color
    fun setColor(v: Int) { p.edit().putInt("color", v).apply(); _color.value = v }

    private val _avatar = MutableStateFlow(p.getString("avatar", "initial") ?: "initial")
    val avatar: StateFlow<String> = _avatar
    fun setAvatar(v: String) { p.edit().putString("avatar", v).apply(); _avatar.value = v }

    private val _theme = MutableStateFlow(p.getString("theme", "DARK") ?: "DARK")
    val theme: StateFlow<String> = _theme
    fun setTheme(v: String) { p.edit().putString("theme", v).apply(); _theme.value = v }

    /** Parent PIN. Set on the Master, synced to every Node. */
    private val _pin = MutableStateFlow(p.getString("pin", DEFAULT_PIN) ?: DEFAULT_PIN)
    val pin: StateFlow<String> = _pin
    fun setPin(v: String) { p.edit().putString("pin", v).apply(); _pin.value = v }

    // ---- Master ----
    private val _nodes = MutableStateFlow(readList("nodes") { LinkedNode.from(it) })
    val nodes: StateFlow<List<LinkedNode>> = _nodes
    @Synchronized fun upsertNode(n: LinkedNode) {
        val list = _nodes.value.filterNot { it.id == n.id } + n
        writeList("nodes", list) { it.toJson() }; _nodes.value = list
    }
    @Synchronized fun updateNode(id: String, f: (LinkedNode) -> LinkedNode) {
        val list = _nodes.value.map { if (it.id == id) f(it) else it }
        writeList("nodes", list) { it.toJson() }; _nodes.value = list
    }
    @Synchronized fun removeNode(id: String) {
        val list = _nodes.value.filterNot { it.id == id }
        writeList("nodes", list) { it.toJson() }; _nodes.value = list
    }

    private val _jellyfin = MutableStateFlow(readJellyfin())
    val jellyfin: StateFlow<JellyfinAccount?> = _jellyfin
    fun setJellyfin(a: JellyfinAccount?) {
        p.edit().apply {
            if (a == null) remove("jf") else putString("jf", JSONObject()
                .put("url", a.url).put("username", a.username).put("token", a.token)
                .put("userId", a.userId).put("isAdmin", a.isAdmin).toString())
        }.apply()
        _jellyfin.value = a
    }
    private fun readJellyfin(): JellyfinAccount? = p.getString("jf", null)?.let {
        val o = JSONObject(it)
        JellyfinAccount(o.getString("url"), o.getString("username"), o.getString("token"), o.getString("userId"), o.optBoolean("isAdmin"))
    }

    /** Apps pinned to the Master's top bar. */
    private val _quickApps = MutableStateFlow(readStrings("quickApps"))
    val quickApps: StateFlow<List<String>> = _quickApps
    fun setQuickApps(v: List<String>) { writeStrings("quickApps", v); _quickApps.value = v }

    // ---- Node ----
    private val _masters = MutableStateFlow(readList("masters") { LinkedMaster.from(it) })
    val masters: StateFlow<List<LinkedMaster>> = _masters
    @Synchronized fun addMaster(m: LinkedMaster) {
        val list = _masters.value.filterNot { it.id == m.id } + m
        writeList("masters", list) { it.toJson() }; _masters.value = list
    }
    @Synchronized fun removeMaster(id: String) {
        val list = _masters.value.filterNot { it.id == id }
        writeList("masters", list) { it.toJson() }; _masters.value = list
    }

    private val _allowed = MutableStateFlow(readStrings("allowed"))
    /** Packages on the kid home screen, in display order. */
    val allowed: StateFlow<List<String>> = _allowed
    fun setAllowed(v: List<String>) { writeStrings("allowed", v); _allowed.value = v }

    private val _lockedApp = MutableStateFlow(p.getString("lockedApp", null))
    /** "Lock to this app": the Node keeps returning to this package. */
    val lockedApp: StateFlow<String?> = _lockedApp
    fun setLockedApp(v: String?) { p.edit().putString("lockedApp", v).apply(); _lockedApp.value = v }

    private val _locked = MutableStateFlow(p.getString("lockedBy", null))
    /** Non-null while the parent has locked the screen; holds the parent's display name. */
    val lockedBy: StateFlow<String?> = _locked
    fun setLockedBy(v: String?) { p.edit().putString("lockedBy", v).apply(); _locked.value = v }

    private val _bedtime = MutableStateFlow(p.getLong("bedtimeAt", 0))
    /** Wall-clock millis when the bedtime timer turns the screen off, 0 when unset. */
    val bedtimeAt: StateFlow<Long> = _bedtime
    fun setBedtimeAt(v: Long) { p.edit().putLong("bedtimeAt", v).apply(); _bedtime.value = v }

    private val _resume = MutableStateFlow(readList("resume") { ResumeCard.from(it) })
    val resume: StateFlow<List<ResumeCard>> = _resume
    fun setResume(v: List<ResumeCard>) { writeList("resume", v) { it.toJson() }; _resume.value = v }

    fun resetAll() {
        p.edit().clear().putString("deviceId", deviceId).apply()
        _role.value = Role.NONE; _name.value = ""; _color.value = 0; _avatar.value = "initial"
        _pin.value = DEFAULT_PIN; _nodes.value = emptyList(); _jellyfin.value = null; _quickApps.value = emptyList()
        _masters.value = emptyList(); _allowed.value = emptyList(); _lockedApp.value = null; _locked.value = null
        _bedtime.value = 0; _resume.value = emptyList(); _theme.value = "DARK"
    }

    // ---- helpers ----
    private fun <T> readList(key: String, f: (JSONObject) -> T): List<T> = try {
        val a = JSONArray(p.getString(key, "[]"))
        (0 until a.length()).map { f(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun <T> writeList(key: String, list: List<T>, f: (T) -> JSONObject) {
        val a = JSONArray(); list.forEach { a.put(f(it)) }
        p.edit().putString(key, a.toString()).apply()
    }

    private fun readStrings(key: String): List<String> = try {
        val a = JSONArray(p.getString(key, "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    private fun writeStrings(key: String, v: List<String>) = p.edit().putString(key, JSONArray(v).toString()).apply()

    companion object {
        const val DEFAULT_PIN = "0000"
        private val rnd = SecureRandom()
        fun randomHex(bytes: Int) = ByteArray(bytes).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}
