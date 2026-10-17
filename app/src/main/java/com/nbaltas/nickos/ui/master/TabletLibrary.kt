package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Server
import com.nbaltas.nickos.App
import com.nbaltas.nickos.sync.JobState
import com.nbaltas.nickos.sync.Library
import com.nbaltas.nickos.sync.LibraryItem
import com.nbaltas.nickos.sync.TogetherText
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.EyebrowText
import com.nbaltas.nickos.ui.theme.IconCircle
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.ProgressBar
import com.nbaltas.nickos.ui.theme.Segmented
import com.nbaltas.nickos.ui.theme.Solid
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import kotlinx.coroutines.delay

/**
 * The NickOS library scoped to one tablet ([nodeId]): the same grid and pick panel as
 * [TogetherScreen] but filtered to what that tablet has, with legacy Jellyfin behind a toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabletLibrary(nodeId: String, v: NodeView, onPlayed: () -> Unit, onSettings: () -> Unit) {
    val app = App.get(LocalContext.current)
    val master = app.master
    val sync = master.sync
    val transfer = master.transfer
    val jfAccount by app.store.jellyfin.collectAsState()
    var legacy by rememberSaveable { mutableStateOf(false) }

    if (legacy) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(if (LocalPhone.current) PhoneMetrics.gutter else 20.dp)) {
                NickButton("NickOS library", { legacy = false }, fullOnPhone(), style = BtnStyle.GHOST)
            }
            WatchTab(v, onPlayed = onPlayed, onSettings = onSettings)
        }
        return
    }

    val videosMap by sync.videos.collectAsState()
    val thumbs by sync.thumbs.collectAsState()
    val library = remember(videosMap) { sync.library() }
    val mine = remember(library) { Library.ownedBy(library, nodeId) }
    var segment by rememberSaveable { mutableStateOf(0) }
    var openFid by rememberSaveable { mutableStateOf<String?>(null) }
    // The open movie is looked up in the current library every time, so one that was just deleted
    // closes the panel instead of leaving Play and Delete on screen.
    val openItem = openItemFor(mine, openFid)
    LaunchedEffect(openFid, openItem == null) { if (openFid != null && openItem == null) openFid = null }

    if (openItem != null) {
        TabletPickPanel(openItem, nodeId, thumbs[openItem.fid], sync, transfer, canDelete = v.status?.supportsDelete == true, onBack = { openFid = null })
        return
    }
    if (openFid != null) return // closing: the effect above clears it on the next frame

    if (LocalPhone.current) {
        val visible = if (segment == 0) Library.movies(mine) else mine.filter { it.episode != null }
        val count = if (segment == 0) visible.size else visible.mapNotNull { it.episode?.show?.lowercase() }.distinct().size
        // The header parts take the 16dp gutter themselves; the empty state and the list do their own, so nothing is padded twice.
        val gutter = Modifier.padding(horizontal = PhoneMetrics.gutter)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
            Spacer(Modifier.height(PhoneMetrics.relatedGap))
            // What is stored, in its own wrapping lines; the count and the Jellyfin button under the tabs, not beside them.
            EyebrowText(TogetherText.libraryHeader(Library.movies(mine).size, videosMap[nodeId]?.free ?: -1), modifier = gutter)
            Segmented(listOf("Movies" to null, "Shows" to null), segment, { segment = it }, gutter.fillMaxWidth())
            Muted("$count on ${v.node.name}", 14, gutter)
            if (jfAccount != null) {
                NickButton("Jellyfin library", { legacy = true }, gutter.fillMaxWidth().testTag("jellyfin-library"), style = BtnStyle.GHOST, leading = Lucide.Server)
            }
            if (visible.isEmpty()) {
                Box(Modifier.weight(1f)) {
                    CenterMessage(
                        if (segment == 0) "No movies yet" else "No shows yet",
                        if (segment == 0) "Copy MP4 or MKV files into the Movies or Download folder on this tablet, or send one from another tablet."
                        else "Copy episodes into a folder named after the show, inside Movies or Download on this tablet, or send them from another tablet.",
                    ) {
                        NickButton("Look again", { sync.rescan() }, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Lucide.RefreshCw)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = PhoneMetrics.gutter, end = PhoneMetrics.gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(visible, key = { it.fid }) { item ->
                        PhoneMediaRow(
                            art = { MovieThumb(thumbs[item.fid], item.title, it, radius = PhoneMetrics.nestedRadius, showTitle = false) },
                            title = item.title, onClick = { openFid = item.fid },
                        ) { Muted(if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown", 14) }
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        val visible = if (segment == 0) Library.movies(mine) else mine.filter { it.episode != null }
        val count = if (segment == 0) visible.size else visible.mapNotNull { it.episode?.show?.lowercase() }.distinct().size
        EyebrowText(TogetherText.libraryHeader(Library.movies(mine).size, videosMap[nodeId]?.free ?: -1))
        Spacer(Modifier.height(10.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Segmented(listOf("Movies" to null, "Shows" to null), segment, { segment = it })
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Muted("$count on ${v.node.name}", 15)
                if (jfAccount != null) {
                    NickButton("Jellyfin library", { legacy = true }, Modifier.testTag("jellyfin-library"), style = BtnStyle.GHOST, leading = Lucide.Server)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f)) {
                CenterMessage(
                    if (segment == 0) "No movies yet" else "No shows yet",
                    if (segment == 0) "Copy MP4 or MKV files into the Movies or Download folder on this tablet, or send one from another tablet."
                    else "Copy episodes into a folder named after the show, inside Movies or Download on this tablet, or send them from another tablet.",
                ) {
                    NickButton("Look again", { sync.rescan() }, style = BtnStyle.PRIMARY, big = true, leading = Lucide.RefreshCw)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(264.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(visible, key = { it.fid }) { item ->
                    Column(
                        Modifier.clickable { openFid = item.fid },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MovieThumb(thumbs[item.fid], item.title, Modifier.height(148.dp).fillMaxWidth())
                        Text(item.title, style = body(18.sp, 700), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Muted(if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown", 15)
                    }
                }
            }
        }
    }
}

/** The movie [fid] as this tablet's library has it now, or null when none is open or it is gone. */
internal fun openItemFor(mine: List<LibraryItem>, fid: String?): LibraryItem? = fid?.let { f -> mine.firstOrNull { it.fid == f } }

@Composable
internal fun TabletPickPanel(
    item: LibraryItem,
    nodeId: String,
    thumb: android.graphics.Bitmap?,
    sync: com.nbaltas.nickos.master.SyncController,
    transfer: com.nbaltas.nickos.master.TransferController,
    canDelete: Boolean,
    onBack: () -> Unit,
) {
    val jobsMap by transfer.jobs.collectAsState()
    val deletingNow by transfer.deletingNow.collectAsState()
    // Two taps: the first arms the button, which goes back to idle after 4 s or when another movie opens.
    var confirmDelete by remember(item.fid) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) { if (confirmDelete) { delay(4000); confirmDelete = false } }
    val pendingDelete = "$nodeId:${item.fid}" in deletingNow
    val has = nodeId in item.owners
    val job = transfer.jobFor(nodeId, item.fid)
    val jobState = job?.let { JobState.of(it.state) }
    val others = transfer.cardJobs(item.fid).map { it.first }.let { TogetherText.join(it) }

    if (LocalPhone.current) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PhoneMetrics.gutter), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
            PhoneBackChip("Movies", onBack)
            PhoneMediaHeader(
                art = { MovieThumb(thumb, item.title, it, radius = PhoneMetrics.nestedRadius, showTitle = false) },
                eyebrow = item.episode?.let { TogetherText.episodeLine(it) }, title = item.title,
                meta = if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown",
            )
            when {
                has -> {
                    NickButton("Play", { sync.start(item.fid, item.title, item.durMs, setOf(nodeId)) }, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Solid.Play)
                    // Delete is its own action, set apart from Play.
                    Spacer(Modifier.height(PhoneMetrics.cardGap))
                    if (canDelete) {
                        NickButton(
                            if (pendingDelete) "Deleting..." else if (confirmDelete) "Tap again to delete" else "Delete from this tablet",
                            { if (confirmDelete) { confirmDelete = false; transfer.delete(item.fid, nodeId) } else confirmDelete = true },
                            Modifier.fillMaxWidth(), style = BtnStyle.DANGER, enabled = !pendingDelete,
                        )
                    } else {
                        Muted("Update this tablet to delete movies from here.", 14)
                    }
                }
                job != null && jobState == JobState.FAILED -> {
                    Text(TogetherText.jobError(job.err, job.size), style = body(14.sp, 500, 1.3f), color = C.danger)
                    NickButton("Try again", { transfer.retry(item.fid, nodeId) }, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY)
                }
                job != null && jobState != JobState.DONE && jobState != JobState.CANCELLED -> {
                    PhoneProgressLine(TogetherText.percent(job.done, job.size) / 100f, TogetherText.jobProgress(job))
                    NickButton("Stop copying", { transfer.cancel(item.fid, nodeId) }, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY)
                }
                else -> {
                    val blocked = transfer.sendBlocked(item.fid)
                    if (blocked != null) Muted(blocked, 14) else NickButton("Send to this tablet", { transfer.send(item.fid, setOf(nodeId)) }, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Lucide.Send)
                }
            }
            if (item.owners.any { it != nodeId }) {
                Spacer(Modifier.height(PhoneMetrics.relatedGap))
                EyebrowText("On other tablets")
                Muted(others.ifEmpty { "Already on another tablet" }, 14)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        NickButton("Movies", onBack, style = BtnStyle.GHOST)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // A fixed 16:9 size: without a width the picture stretched across the row and pushed the title off screen.
            MovieThumb(thumb, item.title, Modifier.height(225.dp).width(400.dp), showTitle = false)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.episode?.let { EyebrowText(TogetherText.episodeLine(it)) }
                Title(item.title, 40, lineHeight = 1.05f)
                Muted(if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown", 18)
            }
        }
        Spacer(Modifier.height(24.dp))
        when {
            has -> Column {
                NickButton("Play", { sync.start(item.fid, item.title, item.durMs, setOf(nodeId)) }, style = BtnStyle.PRIMARY, big = true, leading = Solid.Play)
                Spacer(Modifier.height(12.dp))
                if (canDelete) {
                    NickButton(
                        if (pendingDelete) "Deleting..." else if (confirmDelete) "Tap again to delete" else "Delete from this tablet",
                        { if (confirmDelete) { confirmDelete = false; transfer.delete(item.fid, nodeId) } else confirmDelete = true },
                        style = BtnStyle.DANGER, enabled = !pendingDelete,
                    )
                } else {
                    Muted("Update this tablet to delete movies from here.", 15)
                }
            }
            job != null && jobState == JobState.FAILED -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(TogetherText.jobError(job.err, job.size), style = body(15.sp, 500, 1.3f), color = C.danger)
                NickButton("Try again", { transfer.retry(item.fid, nodeId) }, style = BtnStyle.SECONDARY)
            }
            job != null && jobState != JobState.DONE && jobState != JobState.CANCELLED -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressBar(TogetherText.percent(job.done, job.size) / 100f)
                Muted(TogetherText.jobProgress(job), 15)
                NickButton("Stop copying", { transfer.cancel(item.fid, nodeId) }, style = BtnStyle.SECONDARY)
            }
            else -> {
                val blocked = transfer.sendBlocked(item.fid)
                if (blocked != null) Muted(blocked, 15) else NickButton("Send to this tablet", { transfer.send(item.fid, setOf(nodeId)) }, style = BtnStyle.PRIMARY, big = true, leading = Lucide.Send)
            }
        }
        if (item.owners.any { it != nodeId }) {
            Spacer(Modifier.height(20.dp))
            EyebrowText("On other tablets")
            Muted(others.ifEmpty { "Already on another tablet" }, 15)
        }
    }
}
