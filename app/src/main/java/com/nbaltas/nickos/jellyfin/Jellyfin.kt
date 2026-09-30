package com.nbaltas.nickos.jellyfin

import com.nbaltas.nickos.BuildConfig
import com.nbaltas.nickos.data.JellyfinAccount
import com.nbaltas.nickos.data.optStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class JfUser(val id: String, val name: String)

data class JfItem(
    val id: String,
    val name: String,
    val type: String,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    val index: Int?,
    val season: Int?,
    val runTicks: Long,
    val posTicks: Long,
    val played: Boolean,
    val overview: String?,
    val year: Int?,
    val rating: String?,
    val childCount: Int?,
    val hasPrimary: Boolean,
    val hasThumb: Boolean,
    val hasBackdrop: Boolean,
    val parentThumbId: String?,
    val parentBackdropId: String?,
) {
    val isEpisode get() = type == "Episode"
    val isSeries get() = type == "Series"
    val isMovie get() = type == "Movie"
    val runMin get() = (runTicks / TICKS_PER_MIN).toInt()
    val leftMin get() = ((runTicks - posTicks).coerceAtLeast(0) / TICKS_PER_MIN).toInt()
    val progress get() = if (runTicks > 0) posTicks.toFloat() / runTicks else 0f
    val inProgress get() = posTicks > 0 && !played

    /** Card title: the show name for episodes, else the item's own name. */
    val displayTitle get() = if (isEpisode) seriesName ?: name else name

    /** "S2 E5" */
    val epCode get() = if (season != null && index != null) "S$season E$index" else ""

    companion object {
        const val TICKS_PER_SEC = 10_000_000L
        const val TICKS_PER_MIN = 600_000_000L

        fun from(o: JSONObject): JfItem {
            val ud = o.optJSONObject("UserData")
            val tags = o.optJSONObject("ImageTags")
            return JfItem(
                id = o.getString("Id"),
                name = o.optString("Name"),
                type = o.optString("Type"),
                seriesId = o.optStr("SeriesId"),
                seriesName = o.optStr("SeriesName"),
                seasonId = o.optStr("SeasonId"),
                index = if (o.has("IndexNumber")) o.optInt("IndexNumber") else null,
                season = if (o.has("ParentIndexNumber")) o.optInt("ParentIndexNumber") else null,
                runTicks = o.optLong("RunTimeTicks"),
                posTicks = ud?.optLong("PlaybackPositionTicks") ?: 0,
                played = ud?.optBoolean("Played") ?: false,
                overview = o.optStr("Overview"),
                year = if (o.has("ProductionYear")) o.optInt("ProductionYear") else null,
                rating = o.optStr("OfficialRating"),
                childCount = if (o.has("ChildCount")) o.optInt("ChildCount") else null,
                hasPrimary = tags?.has("Primary") == true,
                hasThumb = tags?.has("Thumb") == true,
                hasBackdrop = (o.optJSONArray("BackdropImageTags")?.length() ?: 0) > 0,
                parentThumbId = o.optStr("ParentThumbItemId"),
                parentBackdropId = o.optStr("ParentBackdropItemId"),
            )
        }
    }
}

data class JfSession(
    val id: String,
    val userId: String?,
    val deviceName: String,
    val client: String,
    val remote: Boolean,
    val item: JfItem?,
    val posTicks: Long,
    val paused: Boolean,
    val lastActivity: String,
)

class JellyfinException(msg: String) : Exception(msg)

/**
 * Jellyfin REST client for the Master. Jellyfin 12 rejects X-Emby-Token, so every call sends one
 * combined `Authorization: MediaBrowser ..., Token="..."` header.
 */
class Jellyfin(val account: JellyfinAccount, private val deviceName: String, private val deviceId: String) {
    private val base = account.url.trimEnd('/')

    // ---- images ----
    fun poster(item: JfItem, h: Int = 450): String? = when {
        item.isEpisode && item.seriesId != null -> img(item.seriesId, "Primary", h)
        item.hasPrimary -> img(item.id, "Primary", h)
        else -> null
    }

    /** Landscape art for 16:9 cards: episode still, else thumb, else backdrop. */
    fun landscape(item: JfItem, w: Int = 560): String? = when {
        item.isEpisode && item.hasPrimary -> imgW(item.id, "Primary", w)
        item.hasThumb -> imgW(item.id, "Thumb", w)
        item.parentThumbId != null -> imgW(item.parentThumbId, "Thumb", w)
        item.hasBackdrop -> imgW(item.id, "Backdrop", w)
        item.parentBackdropId != null -> imgW(item.parentBackdropId, "Backdrop", w)
        item.hasPrimary -> imgW(item.id, "Primary", w)
        else -> null
    }

    fun backdrop(item: JfItem, w: Int = 1280): String? = when {
        item.hasBackdrop -> imgW(item.id, "Backdrop", w)
        item.parentBackdropId != null -> imgW(item.parentBackdropId, "Backdrop", w)
        item.seriesId != null -> imgW(item.seriesId, "Backdrop", w)
        else -> null
    }

    private fun img(id: String, type: String, h: Int) = "$base/Items/$id/Images/$type?fillHeight=$h&quality=90"
    private fun imgW(id: String, type: String, w: Int) = "$base/Items/$id/Images/$type?fillWidth=$w&quality=90"

    // ---- library ----
    suspend fun users(): List<JfUser> {
        if (!account.isAdmin) return listOf(JfUser(account.userId, account.username))
        val a = getArr("/Users")
        return (0 until a.length()).map { a.getJSONObject(it) }.map { JfUser(it.getString("Id"), it.getString("Name")) }
    }

    suspend fun resume(uid: String, limit: Int = 12, parentId: String? = null) =
        items("/Users/$uid/Items/Resume", "Limit" to limit, "MediaTypes" to "Video", "ParentId" to parentId)

    suspend fun nextUp(uid: String, limit: Int = 12, seriesId: String? = null) =
        items("/Shows/NextUp", "UserId" to uid, "Limit" to limit, "SeriesId" to seriesId)

    suspend fun library(uid: String, type: String, sortBy: String = "SortName", desc: Boolean = false, limit: Int = 200) =
        items(
            "/Users/$uid/Items", "IncludeItemTypes" to type, "Recursive" to true, "SortBy" to sortBy,
            "SortOrder" to if (desc) "Descending" else "Ascending", "Limit" to limit,
        )

    suspend fun search(uid: String, q: String) =
        items("/Users/$uid/Items", "SearchTerm" to q, "IncludeItemTypes" to "Movie,Series", "Recursive" to true, "Limit" to 60)

    suspend fun item(uid: String, id: String): JfItem = JfItem.from(get("/Users/$uid/Items/$id"))

    suspend fun seasons(uid: String, seriesId: String) = items("/Shows/$seriesId/Seasons", "UserId" to uid)

    suspend fun episodes(uid: String, seriesId: String, seasonId: String? = null, startItemId: String? = null, limit: Int? = null) =
        items("/Shows/$seriesId/Episodes", "UserId" to uid, "SeasonId" to seasonId, "StartItemId" to startItemId, "Limit" to limit)

    /** The episode after [episode], for "Up next" and the Next episode button. */
    suspend fun nextEpisode(uid: String, episode: JfItem): JfItem? {
        val sid = episode.seriesId ?: return null
        return episodes(uid, sid, startItemId = episode.id, limit = 2).getOrNull(1)
    }

    // ---- sessions / remote control ("Play On") ----
    suspend fun sessions(): List<JfSession> {
        val a = getArr("/Sessions", "ActiveWithinSeconds" to 900)
        return (0 until a.length()).map { a.getJSONObject(it) }.map { s ->
            val ps = s.optJSONObject("PlayState")
            JfSession(
                id = s.getString("Id"),
                userId = s.optStr("UserId"),
                deviceName = s.optString("DeviceName"),
                client = s.optString("Client"),
                remote = s.optBoolean("SupportsRemoteControl"),
                item = s.optJSONObject("NowPlayingItem")?.let { JfItem.from(it) },
                posTicks = ps?.optLong("PositionTicks") ?: 0,
                paused = ps?.optBoolean("IsPaused") ?: false,
                lastActivity = s.optString("LastActivityDate"),
            )
        }
    }

    suspend fun playNow(sessionId: String, itemId: String, startTicks: Long = 0) =
        post("/Sessions/$sessionId/Playing", "PlayCommand" to "PlayNow", "ItemIds" to itemId, "StartPositionTicks" to startTicks)

    /** Pause | Unpause | PlayPause | Stop | Seek | NextTrack */
    suspend fun playstate(sessionId: String, cmd: String, seekTicks: Long? = null) =
        post("/Sessions/$sessionId/Playing/$cmd", "SeekPositionTicks" to seekTicks)

    // ---- http ----
    private suspend fun items(path: String, vararg q: Pair<String, Any?>): List<JfItem> {
        val params = q.toList() + ("Fields" to "Overview,ChildCount,ParentId") + ("EnableImageTypes" to "Primary,Backdrop,Thumb")
        val o = get(path, *params.toTypedArray())
        val a = o.optJSONArray("Items") ?: JSONArray()
        return (0 until a.length()).map { JfItem.from(a.getJSONObject(it)) }
    }

    private suspend fun get(path: String, vararg q: Pair<String, Any?>): JSONObject = JSONObject(call("GET", path, q.toList()))
    private suspend fun getArr(path: String, vararg q: Pair<String, Any?>): JSONArray = JSONArray(call("GET", path, q.toList()))
    private suspend fun post(path: String, vararg q: Pair<String, Any?>) { call("POST", path, q.toList()) }

    private suspend fun call(method: String, path: String, q: List<Pair<String, Any?>>): String = withContext(Dispatchers.IO) {
        val qs = q.filter { it.second != null }.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v.toString(), "UTF-8") }
        val url = base + path + if (qs.isNotEmpty()) "?$qs" else ""
        val req = Request.Builder().url(url)
            .header("Authorization", authHeader(deviceName, deviceId, account.token))
            .apply { if (method == "POST") post(ByteArray(0).toRequestBody(null)) }
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw JellyfinException("Jellyfin ${r.code} on $path")
            r.body?.string().orEmpty().ifEmpty { "{}" }
        }
    }

    companion object {
        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        fun authHeader(device: String, deviceId: String, token: String?): String {
            val safe = device.replace("\"", "'")
            return "MediaBrowser Client=\"NickOS\", Device=\"$safe\", DeviceId=\"nickos-$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\"" +
                (if (token != null) ", Token=\"$token\"" else "")
        }

        /** Signs in and returns the account to store, or throws with a message fit for the screen. */
        suspend fun login(url: String, username: String, password: String, device: String, deviceId: String): JellyfinAccount =
            withContext(Dispatchers.IO) {
                var base = url.trim().trimEnd('/')
                if (!base.startsWith("http")) base = "http://$base"
                val body = JSONObject().put("Username", username).put("Pw", password).toString()
                val req = Request.Builder().url("$base/Users/AuthenticateByName")
                    .header("Authorization", authHeader(device, deviceId, null))
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val text = try {
                    http.newCall(req).execute().use { r ->
                        if (r.code == 401) throw JellyfinException("Wrong username or password")
                        if (!r.isSuccessful) throw JellyfinException("Server answered ${r.code}")
                        r.body?.string().orEmpty()
                    }
                } catch (e: JellyfinException) { throw e } catch (e: Exception) {
                    throw JellyfinException("Can't reach the server")
                }
                val o = JSONObject(text)
                val user = o.getJSONObject("User")
                JellyfinAccount(
                    url = base, username = username, token = o.getString("AccessToken"), userId = user.getString("Id"),
                    isAdmin = user.optJSONObject("Policy")?.optBoolean("IsAdministrator") ?: false,
                )
            }
    }
}
