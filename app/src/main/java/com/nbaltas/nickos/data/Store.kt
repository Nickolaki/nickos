package com.nbaltas.nickos.data

import android.content.Context
import android.content.SharedPreferences
import com.nbaltas.nickos.cam.CamPrefs
import com.nbaltas.nickos.cam.RtspUrl
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
    /** The Node's last report of whether it has the car hotspot saved; null until it reports. */
    val wifiSaved: Boolean? = null,
    /** The landscape lock the parent last chose for this tablet; null if never chosen. Sent again on every connect, so a lost command cannot undo it. */
    val landscape: Boolean? = null,
    /** The power settings the parent last chose for this tablet; null if never chosen. Sent again on every connect. */
    val powerWake: Boolean? = null,
    val powerOffMin: Int? = null,
) {
    fun toJson() = JSONObject().apply {
        put("landscape", landscape ?: JSONObject.NULL)
        put("powerWake", powerWake ?: JSONObject.NULL)
        put("powerOffMin", powerOffMin ?: JSONObject.NULL)
        put("id", id); put("name", name); put("color", color); put("avatar", avatar); put("token", token)
        put("host", host); put("port", port); put("lastSeen", lastSeen); put("lastBattery", lastBattery)
        put("jfUserId", jfUserId); put("jfUserName", jfUserName)
        put("wifiSaved", wifiSaved ?: JSONObject.NULL)
    }

    companion object {
        fun from(o: JSONObject) = LinkedNode(
            id = o.getString("id"), name = o.getString("name"), color = o.optInt("color"),
            avatar = o.optString("avatar", "initial"), token = o.getString("token"),
            host = o.optStr("host"), port = o.optInt("port"), lastSeen = o.optLong("lastSeen"),
            lastBattery = if (o.isNull("lastBattery") || !o.has("lastBattery")) null else o.optInt("lastBattery"),
            jfUserId = o.optStr("jfUserId"), jfUserName = o.optStr("jfUserName"),
            wifiSaved = if (!o.has("wifiSaved") || o.isNull("wifiSaved")) null else o.optBoolean("wifiSaved"),
            landscape = if (!o.has("landscape") || o.isNull("landscape")) null else o.optBoolean("landscape"),
            powerWake = if (!o.has("powerWake") || o.isNull("powerWake")) null else o.optBoolean("powerWake"),
            powerOffMin = if (!o.has("powerOffMin") || o.isNull("powerOffMin")) null else o.optInt("powerOffMin"),
        )
    }
}

/**
 * The car network the Master hands to every Node. The password sits in this app's private prefs, like the PIN.
 * [direct] is true for the Wi-Fi Direct group the Master hosts itself, false for a hotspot the parent typed in.
 */
data class Hotspot(val ssid: String, val pass: String, val direct: Boolean = false)

/**
 * A camera feed on the Master. [rotation] is 0/90/180/270 (portrait baby cams).
 * A Tapo camera has [host], [user], [pass] and [hd]; anything else has a raw [url].
 */
data class Camera(
    val id: String, val name: String, val url: String = "", val rotation: Int = 0,
    val host: String? = null, val user: String = "", val pass: String = "", val hd: Boolean = false,
) {
    val isTapo: Boolean get() = host != null

    /** What the player opens. Built here, never stored and never shown. */
    fun streamUrl(): String = if (host != null) RtspUrl.tapo(host, user, pass, hd) else url

    /** Never the password: this is what ends up in logs. */
    override fun toString() = "Camera($id, $name, host=$host, hd=$hd, url=${RtspUrl.redact(url)})"

    fun toJson() = JSONObject().put("id", id).put("name", name).put("url", url).put("rotation", rotation)
        .put("host", host).put("user", user).put("pass", pass).put("hd", hd)

    companion object {
        fun from(o: JSONObject) = Camera(
            o.getString("id"), o.getString("name"), o.optString("url"), o.optInt("rotation"),
            o.optString("host").takeIf { it.isNotEmpty() }, o.optString("user"), o.optString("pass"), o.optBoolean("hd"),
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

    private val _masterHomeOptIn = MutableStateFlow(p.getBoolean("masterHomeOptIn", false))
    /** The parent asked for NickOS to be this tablet's home screen. Separate from whether Android really made it so. */
    val masterHomeOptIn: StateFlow<Boolean> = _masterHomeOptIn
    fun setMasterHomeOptIn(v: Boolean) { p.edit().putBoolean("masterHomeOptIn", v).commit(); _masterHomeOptIn.value = v }

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

    private val _hotspot = MutableStateFlow(readHotspot())
    /** Set in Settings, Tablets, Car hotspot; null until the parent saves one. */
    val hotspot: StateFlow<Hotspot?> = _hotspot
    @Synchronized
    fun setHotspot(h: Hotspot?) {
        val keep = h?.takeIf { it.ssid.isNotEmpty() }
        // A different network name means no Node has it saved any more (Android 7 may hand back a new one after a
        // reboot). Same name with a new passphrase is not cleared: the watchdog re-saves every minute.
        if (keep?.ssid != _hotspot.value?.ssid && _nodes.value.any { it.wifiSaved != null }) {
            val list = _nodes.value.map { it.copy(wifiSaved = null) }
            writeList("nodes", list) { it.toJson() }; _nodes.value = list
        }
        p.edit().apply {
            if (keep == null) remove("hotspot")
            else putString("hotspot", JSONObject().put("ssid", keep.ssid).put("pass", keep.pass).put("direct", keep.direct).toString())
        }.apply()
        _hotspot.value = keep
    }

    /**
     * Records the Master's own car network as the hotspot, unless the parent has typed a hotspot in. One
     * check-and-set under the same lock as [setHotspot], so a group that finishes coming up just as the parent
     * switches to a typed hotspot can never overwrite what they typed. True when it is recorded.
     */
    @Synchronized
    fun adoptDirectHotspot(h: Hotspot): Boolean {
        val cur = _hotspot.value
        if (cur != null && !cur.direct) return false
        if (cur == h.copy(direct = true)) return true
        setHotspot(h.copy(direct = true))
        return true
    }

    private val _carNetOn = MutableStateFlow(p.getBoolean("carNetOn", true))
    /** The parent's car network switch (Settings, Tablets). On by default, so an update changes nothing until it is used. */
    val carNetOn: StateFlow<Boolean> = _carNetOn
    @Synchronized
    fun setCarNetOn(v: Boolean) {
        // Durable first (a tiny write), then publish: nothing reacts to a value that could be lost. A failed write keeps the old value.
        if (p.edit().putBoolean("carNetOn", v).commit()) _carNetOn.value = v
    }

    private fun readHotspot(): Hotspot? = try {
        p.getString("hotspot", null)?.let { JSONObject(it) }
            ?.let { Hotspot(it.getString("ssid"), it.optString("pass"), it.optBoolean("direct", false)) }?.takeIf { it.ssid.isNotEmpty() }
    } catch (e: Exception) { null }

    /**
     * The passphrase of the network this Master hosts: 16 characters of a-z and 0-9, made once and kept. It survives
     * [resetAll] on purpose: a reset Master that made a new one would leave every Node holding a network that no
     * longer exists. Never logged.
     */
    @Synchronized
    fun carNetPass(): String = p.getString(KEY_CARNET, null)?.takeIf { it.length == CARNET_PASS_LEN }
        ?: newCarNetPass(rnd).also { p.edit().putString(KEY_CARNET, it).apply() }

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

    private val _cameras = MutableStateFlow(readList("cameras") { Camera.from(it) })
    val cameras: StateFlow<List<Camera>> = _cameras
    @Synchronized fun saveCamera(c: Camera) {
        val cur = _cameras.value
        val list = if (cur.any { it.id == c.id }) cur.map { if (it.id == c.id) c else it } else cur + c
        writeList("cameras", list) { it.toJson() }; _cameras.value = list
    }
    @Synchronized fun removeCamera(id: String) {
        val list = _cameras.value.filterNot { it.id == id }
        writeList("cameras", list) { it.toJson() }; _cameras.value = list
    }

    // ---- Master: tablet cameras ----
    private var _camArmed: Set<String> = readStrings("camArmed").toSet()
    /** Tablets whose camera the parent has switched on in Cameras. */
    val camArmed: Set<String> get() = _camArmed
    fun setCamArmed(v: Set<String>) { writeStrings("camArmed", v.toList()); _camArmed = v }

    private val _camRotation = MutableStateFlow(readCamRotation())
    /** Extra quarter turns the parent gave a tablet's picture, by node id (0/90/180/270). */
    val camRotation: StateFlow<Map<String, Int>> = _camRotation
    @Synchronized fun setCamRotation(nodeId: String, degrees: Int) {
        val map = _camRotation.value + (nodeId to ((degrees % 360) + 360) % 360)
        p.edit().putString("camRotation", JSONObject(map as Map<*, *>).toString()).apply(); _camRotation.value = map
    }
    private fun readCamRotation(): Map<String, Int> = try {
        val o = JSONObject(p.getString("camRotation", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.optInt(it) }
    } catch (e: Exception) { emptyMap() }

    private val _camRtspHidden = MutableStateFlow(readStrings("camRtspHidden").toSet())
    /** RTSP cameras the parent has taken off the Cameras stage, by camera id. */
    val camRtspHidden: StateFlow<Set<String>> = _camRtspHidden
    fun setCamRtspHidden(v: Set<String>) { writeStrings("camRtspHidden", v.toList()); _camRtspHidden.value = v }

    private val _camPrefs = MutableStateFlow(readCamPrefs())
    /** Picture size, auto-off and transition lengths for Cameras, set in Settings, Cameras. */
    val camPrefs: StateFlow<CamPrefs> = _camPrefs
    fun setCamPrefs(v: CamPrefs) {
        p.edit().putString("camPrefs", JSONObject().put("profile", v.profile).put("maxMin", v.maxMin)
            .put("layoutMs", v.layoutMs).put("fadeMs", v.fadeMs).toString()).apply()
        _camPrefs.value = v
    }
    private fun readCamPrefs(): CamPrefs = try {
        val o = JSONObject(p.getString("camPrefs", "{}") ?: "{}")
        val d = CamPrefs()
        CamPrefs(
            profile = o.optString("profile", d.profile).takeIf { it in setOf("low", "normal", "high") } ?: d.profile,
            maxMin = o.optInt("maxMin", d.maxMin).takeIf { it in setOf(5, 10, 30, 60) } ?: d.maxMin,
            layoutMs = o.optInt("layoutMs", d.layoutMs).coerceIn(0, 2000),
            fadeMs = o.optInt("fadeMs", d.fadeMs).coerceIn(0, 2000),
        )
    } catch (e: Exception) { CamPrefs() }

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

    private val _pausedMsg = MutableStateFlow(p.getString("pausedMsg", null))
    /** Non-null while the parent has paused everyone; holds the message. On the Node it is what the wall shows, on the Master what is re-sent. */
    val pausedMsg: StateFlow<String?> = _pausedMsg
    fun setPausedMsg(v: String?) { p.edit().putString("pausedMsg", v).apply(); _pausedMsg.value = v }

    private val _pausedBy = MutableStateFlow(p.getString("pausedBy", null))
    /** Node only: the parent's display name shown under the pause message. */
    val pausedBy: StateFlow<String?> = _pausedBy
    fun setPausedBy(v: String?) { p.edit().putString("pausedBy", v).apply(); _pausedBy.value = v }

    /** Node only: the id of the Master that paused everyone. Only that Master (or the PIN, or a forced resume) lifts the pause. */
    fun pausedOwner(): String? = p.getString("pausedOwner", null)
    fun setPausedOwner(v: String?) { p.edit().putString("pausedOwner", v).apply() }

    /** The alarm volume as it was before Ring raised it, or null when nothing is waiting to be put back. Written to disk before the volume is raised. */
    fun savedAlarmVolume(): Int? = if (p.contains("alarmVolSaved")) p.getInt("alarmVolSaved", 0) else null
    fun setSavedAlarmVolume(v: Int?): Boolean {
        val e = p.edit()
        if (v == null) e.remove("alarmVolSaved") else e.putInt("alarmVolSaved", v)
        return e.commit()
    }

    private val _bedtime = MutableStateFlow(p.getLong("bedtimeAt", 0))
    /** Wall-clock millis when the bedtime timer turns the screen off, 0 when unset. */
    val bedtimeAt: StateFlow<Long> = _bedtime
    fun setBedtimeAt(v: Long) { p.edit().putLong("bedtimeAt", v).apply(); _bedtime.value = v }

    private val _kidLockDone = MutableStateFlow(p.getBoolean("kidLockDone", false))
    /** Start kid mode worked once on this tablet. From then on the kid lock repair screen needs the parent PIN. */
    val kidLockDone: StateFlow<Boolean> = _kidLockDone
    fun setKidLockDone(v: Boolean) { p.edit().putBoolean("kidLockDone", v).commit(); _kidLockDone.value = v }

    /** A grown-up switched remote control on at least once here. Only then may an update's lost switch be put back silently. */
    fun remoteControlEverOn(): Boolean = p.getBoolean("remoteControlEverOn", false)
    fun setRemoteControlEverOn() { if (!remoteControlEverOn()) p.edit().putBoolean("remoteControlEverOn", true).commit() }

    /**
     * First start of the version that added the kid lock PIN gate: a tablet that is already a Node was set up before the
     * gate existed, so it counts as locked. Keyed by a stored number, not by the flag being absent. Returns true if it ran.
     */
    fun migrateKidLock(): Boolean {
        if (p.getInt(KEY_MIGRATION, 0) >= MIGRATION_KID_LOCK) return false
        val e = p.edit()
        if (_role.value == Role.NODE) { e.putBoolean("kidLockDone", true) }
        e.putInt(KEY_MIGRATION, MIGRATION_KID_LOCK).commit()
        if (_role.value == Role.NODE) _kidLockDone.value = true
        return true
    }

    private val _landscape = MutableStateFlow(p.getBoolean("landscapeLock", false))
    /** The parent locked this kid tablet to landscape. Kept across reboots. */
    val landscapeLock: StateFlow<Boolean> = _landscape
    /** True when the choice reached the disk; only then does the app act on it. */
    fun setLandscapeLock(v: Boolean): Boolean {
        val ok = p.edit().putBoolean("landscapeLock", v).commit()
        if (ok) _landscape.value = v
        return ok
    }

    /** The system rotation settings as they were before the landscape lock changed them, or null when it has not. */
    fun savedRotation(): com.nbaltas.nickos.node.Rotation? =
        if (p.contains("rotSavedAccel")) com.nbaltas.nickos.node.Rotation(p.getInt("rotSavedAccel", 1), p.getInt("rotSavedUser", 0)) else null
    /** True only when the change reached the disk: the landscape lock changes nothing on the tablet without that. */
    fun setSavedRotation(r: com.nbaltas.nickos.node.Rotation?): Boolean {
        val e = p.edit()
        if (r == null) e.remove("rotSavedAccel").remove("rotSavedUser") else e.putInt("rotSavedAccel", r.accelerometer).putInt("rotSavedUser", r.user)
        return e.commit()
    }

    private val _powerWake = MutableStateFlow(p.getBoolean("powerWakeOnCharge", false))
    /** Wake the screen when power is plugged in. The Master's own choice, or on a Node the one the parent sent. */
    val powerWakeOnCharge: StateFlow<Boolean> = _powerWake
    private val _powerOffMin = MutableStateFlow(p.getInt("powerBatteryOffMin", 0))
    /** Minutes of idle before the screen goes off while not charging; 0 is off. */
    val powerBatteryOffMin: StateFlow<Int> = _powerOffMin
    /** True when the choice reached the disk; only then does the app act on it. */
    fun setPower(wake: Boolean, offMin: Int): Boolean {
        val ok = p.edit().putBoolean("powerWakeOnCharge", wake).putInt("powerBatteryOffMin", offMin).commit()
        if (ok) { _powerWake.value = wake; _powerOffMin.value = offMin }
        return ok
    }

    /** The tablet's own screen off timeout (milliseconds) as it was before the battery timer changed it, or null when it has not. */
    fun savedScreenTimeout(): Int? = durableTimeout
    // SharedPreferences updates its memory even when commit() fails, so what counts is only what a commit confirmed.
    @Volatile private var durableTimeout: Int? = if (p.contains("powerSavedTimeout")) p.getInt("powerSavedTimeout", 0) else null
    /** True only when the change reached the disk: the timer changes nothing on the tablet without that. A failed removal leaves the copy present. */
    @Synchronized
    fun setSavedScreenTimeout(ms: Int?): Boolean {
        val e = p.edit()
        if (ms == null) e.remove("powerSavedTimeout") else e.putInt("powerSavedTimeout", ms)
        val ok = e.commit()
        if (ok) durableTimeout = ms
        else durableTimeout.let { d -> p.edit().apply { if (d == null) remove("powerSavedTimeout") else putInt("powerSavedTimeout", d) }.apply() }
        return ok
    }

    private val _resume = MutableStateFlow(readList("resume") { ResumeCard.from(it) })
    val resume: StateFlow<List<ResumeCard>> = _resume
    fun setResume(v: List<ResumeCard>) { writeList("resume", v) { it.toJson() }; _resume.value = v }

    @Synchronized
    fun resetAll() {
        val carnet = p.getString(KEY_CARNET, null)
        // A rotation restore that has not finished is the tablet's own setting, not NickOS data: it survives a reset and is retried.
        val rotation = savedRotation()
        val alarmVolume = savedAlarmVolume() // likewise the tablet's own alarm volume
        val screenTimeout = savedScreenTimeout()
        val migration = p.getInt(KEY_MIGRATION, 0)
        p.edit().clear().putString("deviceId", deviceId).apply {
            if (migration > 0) putInt(KEY_MIGRATION, migration)
            if (carnet != null) putString(KEY_CARNET, carnet)
            if (alarmVolume != null) putInt("alarmVolSaved", alarmVolume)
            if (rotation != null) putInt("rotSavedAccel", rotation.accelerometer).putInt("rotSavedUser", rotation.user)
            if (screenTimeout != null) putInt("powerSavedTimeout", screenTimeout)
        }.apply()
        _powerWake.value = false; _powerOffMin.value = 0
        _masterHomeOptIn.value = false; _landscape.value = false; _kidLockDone.value = false
        _role.value = Role.NONE; _name.value = ""; _color.value = 0; _avatar.value = "initial"
        _pin.value = DEFAULT_PIN; _nodes.value = emptyList(); _hotspot.value = null; _carNetOn.value = true; _jellyfin.value = null; _quickApps.value = emptyList()
        _masters.value = emptyList(); _allowed.value = emptyList(); _lockedApp.value = null; _locked.value = null
        _pausedMsg.value = null; _pausedBy.value = null
        _bedtime.value = 0; _resume.value = emptyList(); _theme.value = "DARK"; _cameras.value = emptyList()
        _camArmed = emptySet(); _camRotation.value = emptyMap(); _camRtspHidden.value = emptySet(); _camPrefs.value = CamPrefs()
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
        private const val KEY_CARNET = "carnet"
        private const val KEY_MIGRATION = "migration"
        const val MIGRATION_KID_LOCK = 1
        const val CARNET_PASS_LEN = 16
        private const val CARNET_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private val rnd = SecureRandom()
        fun newCarNetPass(r: SecureRandom = rnd): String = String(CharArray(CARNET_PASS_LEN) { CARNET_ALPHABET[r.nextInt(CARNET_ALPHABET.length)] })
        fun randomHex(bytes: Int) = ByteArray(bytes).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}
