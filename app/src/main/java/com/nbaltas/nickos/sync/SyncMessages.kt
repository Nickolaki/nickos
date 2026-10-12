package com.nbaltas.nickos.sync

import com.nbaltas.nickos.data.optStr
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encode/decode for every message this feature adds to the existing line link (see the doc
 * comment on `link/Wire.kt`). Decoders return null on a missing or wrong-typed field, and a
 * malformed entry inside a list is skipped rather than failing the whole message, so a buggy or
 * half-written line from a peer never crashes the session loop.
 */
object SyncMessages {

    // ---- Master -> Node commands: {"t":"cmd","c":...} ----

    fun cmdPing(id: Int, t0: Long): JSONObject = cmd("ping").put("id", id).put("t0", t0)
    fun cmdVideos(): JSONObject = cmd("videos")
    /** [n] is how many tablets are in the session, so a Node can say "for you" or "on everyone's tablets". */
    fun cmdSync(fid: String, pos: Long, at: Long, title: String, n: Int = 0): JSONObject =
        cmd("sync").put("fid", fid).put("pos", pos).put("at", at).put("title", title).put("n", n)
    fun cmdSyncPause(): JSONObject = cmd("syncPause")
    fun cmdSyncStop(): JSONObject = cmd("syncStop")
    fun cmdServe(fid: String): JSONObject = cmd("serve").put("fid", fid)
    fun cmdFetch(fid: String, url: String, size: Long, title: String, name: String): JSONObject =
        cmd("fetch").put("fid", fid).put("url", url).put("size", size).put("title", title).put("name", name)
    fun cmdCancel(fid: String): JSONObject = cmd("cancel").put("fid", fid)
    fun cmdJobs(): JSONObject = cmd("jobs")

    private fun cmd(name: String): JSONObject = JSONObject().put("t", "cmd").put("c", name)

    data class SyncCmdMsg(val fid: String, val pos: Long, val at: Long, val title: String, val n: Int = 0)

    /** Decodes a `cmd` `sync` payload; null if `fid`, `pos`, `at` or `title` is missing. */
    fun decodeSyncCmd(o: JSONObject): SyncCmdMsg? = try {
        SyncCmdMsg(o.getString("fid"), o.getLong("pos"), o.getLong("at"), o.getString("title"), o.optInt("n"))
    } catch (e: Exception) { null }

    data class FetchCmdMsg(val fid: String, val url: String, val size: Long, val title: String, val name: String)
    fun decodeFetchCmd(o: JSONObject): FetchCmdMsg? = try {
        FetchCmdMsg(o.getString("fid"), o.getString("url"), o.getLong("size"), o.getString("title"), o.getString("name"))
    } catch (e: Exception) { null }

    // ---- Node -> Master ----

    data class Pong(val id: Int, val t0: Long, val t1: Long, val t2: Long)
    fun encodePong(id: Int, t0: Long, t1: Long, t2: Long): JSONObject =
        JSONObject().put("t", "pong").put("id", id).put("t0", t0).put("t1", t1).put("t2", t2)
    fun decodePong(o: JSONObject): Pong? = try {
        Pong(o.getInt("id"), o.getLong("t0"), o.getLong("t1"), o.getLong("t2"))
    } catch (e: Exception) { null }

    data class VideoEntry(val fid: String, val title: String, val name: String, val size: Long, val durMs: Long, val thumb: String? = null)
    data class VideosMsg(val perm: Boolean, val list: List<VideoEntry>)

    fun encodeVideos(perm: Boolean, list: List<VideoEntry>): JSONObject {
        val arr = JSONArray()
        for (v in list) {
            val e = JSONObject().put("fid", v.fid).put("title", v.title).put("name", v.name)
                .put("size", v.size).put("durMs", v.durMs)
            if (v.thumb != null) e.put("thumb", v.thumb)
            arr.put(e)
        }
        return JSONObject().put("t", "videos").put("perm", perm).put("list", arr)
    }

    fun decodeVideos(o: JSONObject): VideosMsg? = try {
        val perm = o.getBoolean("perm")
        val arr = o.getJSONArray("list")
        val list = mutableListOf<VideoEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            try {
                list.add(VideoEntry(e.getString("fid"), e.getString("title"), e.getString("name"), e.getLong("size"), e.getLong("durMs"), e.optStr("thumb")))
            } catch (_: Exception) { /* drop this entry, keep the rest */ }
        }
        VideosMsg(perm, list)
    } catch (e: Exception) { null }

    data class SyncStateMsg(val fid: String, val state: String, val pos: Long, val at: Long, val dur: Long, val rate: Float, val err: String?)

    fun encodeSyncState(fid: String, state: String, pos: Long, at: Long, dur: Long, rate: Float, err: String? = null): JSONObject {
        val o = JSONObject().put("t", "syncState").put("fid", fid).put("state", state)
            .put("pos", pos).put("at", at).put("dur", dur).put("rate", rate.toDouble())
        if (err != null) o.put("err", err)
        return o
    }

    fun decodeSyncState(o: JSONObject): SyncStateMsg? = try {
        SyncStateMsg(
            o.getString("fid"), o.getString("state"), o.getLong("pos"), o.getLong("at"), o.getLong("dur"),
            o.getDouble("rate").toFloat(), o.optStr("err"),
        )
    } catch (e: Exception) { null }

    data class ServingMsg(val fid: String, val ok: Boolean, val port: Int, val key: String?, val size: Long, val err: String?)

    fun encodeServing(fid: String, ok: Boolean, port: Int = 0, key: String? = null, size: Long = 0, err: String? = null): JSONObject {
        val o = JSONObject().put("t", "serving").put("fid", fid).put("ok", ok).put("port", port).put("size", size)
        if (key != null) o.put("key", key)
        if (err != null) o.put("err", err)
        return o
    }

    fun decodeServing(o: JSONObject): ServingMsg? = try {
        ServingMsg(o.getString("fid"), o.getBoolean("ok"), o.optInt("port"), o.optStr("key"), o.optLong("size"), o.optStr("err"))
    } catch (e: Exception) { null }

    data class JobEntry(val fid: String, val title: String, val state: String, val done: Long, val size: Long, val err: String? = null)
    data class JobsMsg(val list: List<JobEntry>)

    fun encodeJobs(list: List<JobEntry>): JSONObject {
        val arr = JSONArray()
        for (j in list) {
            val e = JSONObject().put("fid", j.fid).put("title", j.title).put("state", j.state).put("done", j.done).put("size", j.size)
            if (j.err != null) e.put("err", j.err)
            arr.put(e)
        }
        return JSONObject().put("t", "jobs").put("list", arr)
    }

    /** At most [max] entries are read; a longer list is cut, not rejected. */
    fun decodeJobs(o: JSONObject, max: Int = 200): JobsMsg? = try {
        val arr = o.getJSONArray("list")
        val list = mutableListOf<JobEntry>()
        for (i in 0 until minOf(arr.length(), max)) {
            val e = arr.optJSONObject(i) ?: continue
            try {
                list.add(JobEntry(e.getString("fid"), e.getString("title"), e.getString("state"), e.getLong("done"), e.getLong("size"), e.optStr("err")))
            } catch (_: Exception) { /* drop this entry, keep the rest */ }
        }
        JobsMsg(list)
    } catch (e: Exception) { null }
}
