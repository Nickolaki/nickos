package com.nbaltas.nickos.ui.master

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import com.nbaltas.nickos.App
import com.nbaltas.nickos.node.LocalVideos
import com.nbaltas.nickos.sync.JobState
import com.nbaltas.nickos.sync.Library
import com.nbaltas.nickos.sync.LibraryItem
import com.nbaltas.nickos.sync.Show
import com.nbaltas.nickos.sync.SyncMessages
import com.nbaltas.nickos.sync.TogetherText
import com.nbaltas.nickos.ui.node.rememberNow
import com.nbaltas.nickos.ui.theme.Artwork
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.BackButton
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Dot
import com.nbaltas.nickos.ui.theme.EyebrowText
import com.nbaltas.nickos.ui.theme.IconCircle
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickSlider
import com.nbaltas.nickos.ui.theme.NickSwitch
import com.nbaltas.nickos.ui.theme.ProgressBar
import com.nbaltas.nickos.ui.theme.Segmented
import com.nbaltas.nickos.ui.theme.Solid
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.disp

/** Same shape as `fmtTicks`, but over plain milliseconds. "18:02" or "1:32:05". */
fun fmtMs(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/**
 * Poster/thumbnail that can show a decoded Bitmap (Watch together's thumbs are never a URL):
 * [Artwork]'s placeholder underneath, the Bitmap and the title drawn above it when there is one.
 */
@Composable
fun MovieThumb(bitmap: Bitmap?, title: String, modifier: Modifier, radius: Dp = 18.dp, titleSize: Int = 18, showTitle: Boolean = true) {
    Box(modifier.clip(RoundedCornerShape(radius))) {
        Artwork(null, title, Modifier.fillMaxSize(), radius = radius, titleSize = titleSize, showTitle = showTitle && bitmap == null)
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (showTitle) {
                Text(
                    title,
                    style = disp(titleSize.sp, 800, 0.95f).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(0f, 2f), 14f)),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                )
            }
        }
    }
}

/** Every tablet id (and [Library.SELF]) that owns [item], named in a fixed order: "This tablet" first. */
fun availabilityFor(item: LibraryItem, order: List<String>, expected: Set<String>, name: (String) -> String): TogetherText.Availability =
    TogetherText.availability(item.owners, expected, order, name)

@Composable
fun TogetherScreen(onBack: () -> Unit, onSettings: (Int) -> Unit, initialSegment: Int = 0) {
    val app = App.get(LocalContext.current)
    val master = app.master
    val sync = master.sync
    val transfer = master.transfer
    val session by sync.session.collectAsState()
    if (session != null) {
        TogetherRemote(onBack)
        return
    }
    val nodes = rememberNodeViews()
    val videosMap by sync.videos.collectAsState()
    val scanning by sync.scanning.collectAsState()
    val jobsMap by transfer.jobs.collectAsState()
    val thumbs by sync.thumbs.collectAsState()
    val selfPerm = master.videos.hasPermission()
    val videosLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { sync.rescanSelf() }

    var segment by remember { mutableStateOf(initialSegment) }
    var openShow by remember { mutableStateOf<Show?>(null) }
    var openItem by remember { mutableStateOf<String?>(null) }

    val library = remember(videosMap) { sync.library() }
    val order = remember(nodes) { listOf(Library.SELF) + nodes.map { it.node.id } }
    val name: (String) -> String = { id -> if (id == Library.SELF) "This tablet" else nodes.firstOrNull { it.node.id == id }?.node?.name?.let { com.nbaltas.nickos.node.parentName(it) } ?: "A tablet" }
    val expected = remember(nodes) { setOf(Library.SELF) + nodes.filter { it.online && it.status?.supportsSync == true }.map { it.node.id } }

    openItem?.let { fid ->
        val item = library.firstOrNull { it.fid == fid }
        if (item != null) {
            PickPanel(
                item = item,
                thumb = thumbs[fid],
                nodes = nodes,
                sync = sync,
                transfer = transfer,
                backLabel = if (item.episode != null) item.episode.show else if (segment == 0) "Movies" else "Shows",
                onBack = { openItem = null },
            )
            return
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Spacer(Modifier.width(20.dp))
            Title("Watch together", 40, Modifier.weight(1f))
            IconCircle(Lucide.RefreshCw, { sync.rescan() })
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val label = TogetherText.chip(true, true, Library.SELF in scanning, selfPerm, Library.ownedBy(library, Library.SELF).size, 0)
                Chip(Modifier.clickable {
                    if (label == "No video access") videosLauncher.launch(LocalVideos.permissions().toTypedArray())
                }) {
                    Icon(Lucide.Tablet, null, tint = C.text, modifier = Modifier.size(28.dp))
                    Column { Text("This tablet", style = body(16.sp, 700), color = C.text); Muted(label, 15) }
                }
            }
            items(nodes) { n ->
                val capable = n.status?.supportsSync == true
                val label = TogetherText.chip(n.online, capable, n.node.id in scanning, true, Library.ownedBy(library, n.node.id).size, transfer.downloading(n.node.id))
                Chip(Modifier.clickable {
                    when (label) {
                        "Update needed" -> onSettings(4)
                        "No video access" -> master.say("On ${n.node.name}, hold the avatar, enter the PIN, then tap Allow videos")
                        else -> {}
                    }
                }) {
                    Avatar(n.node.name, n.node.color, n.node.avatar, 28.dp)
                    Column { Text(n.node.name, style = body(16.sp, 700), color = C.text, maxLines = 1); Muted(label, 15) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Segmented(
            listOf("Movies" to null, "Shows" to null), segment,
            { segment = it; openShow = null },
            Modifier.padding(horizontal = 40.dp),
        )
        Spacer(Modifier.height(12.dp))

        val onlineNodes = nodes.filter { it.online }
        val capableOnline = onlineNodes.filter { it.status?.supportsSync == true }
        val selfHasNone = Library.ownedBy(library, Library.SELF).isEmpty()
        val scanningCount = scanning.size

        when {
            !selfPerm -> CenterMessage("Let NickOS see your videos", "Movies on this tablet are what you send to the kids' tablets. Allow video access once.", titleLines = 3) {
                NickButton("Allow videos on this tablet", { videosLauncher.launch(LocalVideos.permissions().toTypedArray()) }, style = BtnStyle.PRIMARY, big = true)
            }
            onlineNodes.isEmpty() && selfHasNone -> CenterMessage("No tablets online", "Turn the kids' tablets on and connect them to this WiFi. Movies on them, and on this tablet, show up here.") {
                NickButton("All tablets", onBack, style = BtnStyle.GHOST, big = true)
            }
            onlineNodes.isNotEmpty() && capableOnline.isEmpty() -> CenterMessage("Update the kids' tablets", "Watch together needs the latest NickOS on each tablet. Update them from Settings, then come back.") {
                NickButton("Open Settings", { onSettings(4) }, style = BtnStyle.PRIMARY, big = true)
            }
            else -> {
                val visible = if (segment == 0) Library.movies(library) else null
                val shows = if (segment == 1) Library.shows(library) else emptyList()
                Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
                    if (scanningCount > 0) Muted("Looking for movies on $scanningCount tablets...", 18, Modifier.padding(bottom = 12.dp))
                    when {
                        segment == 0 && visible!!.isEmpty() && scanningCount == 0 -> CenterMessage("No movies yet", "Copy MP4 or MKV files into the Movies or Download folder on this tablet or any kid's tablet. From here you can send a movie to the other tablets and play it on all of them at once.") {
                            NickButton("Look again", { sync.rescan() }, style = BtnStyle.PRIMARY, big = true, leading = Lucide.RefreshCw)
                        }
                        segment == 1 && openShow == null && shows.isEmpty() && scanningCount == 0 -> CenterMessage("No movies yet", "Copy MP4 or MKV files into the Movies or Download folder on this tablet or any kid's tablet. From here you can send a movie to the other tablets and play it on all of them at once.") {
                            NickButton("Look again", { sync.rescan() }, style = BtnStyle.PRIMARY, big = true, leading = Lucide.RefreshCw)
                        }
                        segment == 0 -> LibraryGrid(visible!!, thumbs, nodes, order, expected, name, transfer) { openItem = it.fid }
                        openShow != null -> LibraryGrid(openShow!!.episodes, thumbs, nodes, order, expected, name, transfer) { openItem = it.fid }
                        else -> ShowGrid(shows, thumbs) { openShow = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryItem>,
    thumbs: Map<String, Bitmap>,
    nodes: List<NodeView>,
    order: List<String>,
    expected: Set<String>,
    name: (String) -> String,
    transfer: com.nbaltas.nickos.master.TransferController,
    onOpen: (LibraryItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(264.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.fid }) { item ->
            Column(Modifier.clickable { onOpen(item) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MovieThumb(thumbs[item.fid], item.title, Modifier.size(264.dp, 148.dp))
                Text(item.title, style = body(18.sp, 700), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown", 15)
                val avail = TogetherText.availability(item.owners, expected, order, name)
                Text(avail.text, style = body(15.sp, 600), color = if (avail.all) C.online else C.muted, maxLines = 1)
                val jobs = transfer.cardJobs(item.fid)
                TogetherText.jobLine(jobs)?.let { Text(it, style = body(15.sp, 600), color = C.accent, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun ShowGrid(shows: List<Show>, thumbs: Map<String, Bitmap>, onOpen: (Show) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(264.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shows, key = { it.name }) { show ->
            val first = show.episodes.firstOrNull()
            Column(Modifier.clickable { onOpen(show) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MovieThumb(first?.let { thumbs[it.fid] }, show.name, Modifier.size(264.dp, 148.dp))
                Text(show.name, style = body(18.sp, 700), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted("${show.episodes.size} episodes", 15)
            }
        }
    }
}

@Composable
internal fun PickPanel(
    item: LibraryItem,
    thumb: Bitmap?,
    nodes: List<NodeView>,
    sync: com.nbaltas.nickos.master.SyncController,
    transfer: com.nbaltas.nickos.master.TransferController,
    backLabel: String,
    onBack: () -> Unit,
) {
    val jobsMap by transfer.jobs.collectAsState()
    val videosMap by sync.videos.collectAsState()
    var selected by remember(item.fid) { mutableStateOf(nodes.filter { it.node.id in item.owners && it.online }.map { it.node.id }.toSet()) }
    val lacking = remember(item.fid, videosMap, jobsMap) { transfer.lacking(item.fid) }
    val sendBlocked = transfer.sendBlocked(item.fid)
    val epLabel = item.episode?.let { TogetherText.episodeLine(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp)) {
        Chip(Modifier.clickable(onClick = onBack)) {
            Icon(Lucide.ChevronLeft, null, tint = C.text, modifier = Modifier.size(18.dp))
            Text(backLabel, style = body(16.sp, 700), color = C.text)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MovieThumb(thumb, item.title, Modifier.size(400.dp, 225.dp), showTitle = false)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                epLabel?.let { EyebrowText(it) }
                Title(item.title, 44, lineHeight = 1.05f)
                Muted(if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown", 18)
            }
        }
        Spacer(Modifier.height(28.dp))
        nodes.forEach { n ->
            val has = n.node.id in item.owners
            val capable = n.status?.supportsSync == true
            val job = transfer.jobFor(n.node.id, item.fid)
            val jobState = job?.let { JobState.of(it.state) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Avatar(n.node.name, n.node.color, n.node.avatar, 44.dp)
                Text(n.node.name, style = body(19.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                when {
                    !capable -> Muted("Update needed", 15)
                    job != null && jobState == JobState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(TogetherText.jobError(job.err, job.size), style = body(15.sp, 500, 1.3f), color = C.danger)
                        NickButton("Try again", { transfer.retry(item.fid, n.node.id) }, style = BtnStyle.SECONDARY)
                    }
                    job != null && jobState != JobState.DONE && jobState != JobState.CANCELLED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressBar(TogetherText.percent(job.done, job.size) / 100f, Modifier.width(200.dp))
                        Muted(TogetherText.jobProgress(job), 15)
                        IconCircle(Lucide.X, { transfer.cancel(item.fid, n.node.id) }, size = 44.dp)
                    }
                    has && n.online -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NickSwitch(n.node.id in selected) { on -> selected = if (on) selected + n.node.id else selected - n.node.id }
                        Muted("Ready", 15)
                    }
                    has && !n.online -> Muted("Offline", 15)
                    !has && n.online -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (sendBlocked != null) Muted(sendBlocked, 15) else {
                            NickButton("Send", { transfer.send(item.fid, setOf(n.node.id)) }, style = BtnStyle.SECONDARY)
                            Muted("Not on this tablet", 15)
                        }
                    }
                    else -> Muted("Offline", 15)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        val k = selected.size
        val playLabel = if (k == 1) "Play on ${sync.kid(selected.first())}'s tablet" else "Play on $k tablets"
        NickButton(playLabel, { sync.start(item.fid, item.title, item.durMs, selected) }, style = BtnStyle.PRIMARY, big = true, leading = Solid.Play, enabled = k > 0)
        Spacer(Modifier.height(12.dp))
        val j = lacking.size
        NickButton("Send to $j tablets", { transfer.send(item.fid, lacking) }, style = BtnStyle.SECONDARY, big = true, leading = Lucide.Send, enabled = j > 0 && sendBlocked == null)
    }
}

@Composable
internal fun TogetherRemote(onBack: () -> Unit) {
    val app = App.get(LocalContext.current)
    val master = app.master
    val sync = master.sync
    val session by sync.session.collectAsState()
    val s = session ?: return onBack()
    val states by sync.states.collectAsState()
    val now = rememberNow(500)
    val pos = sync.expectedPos()
    val nodeViews = rememberNodeViews()
    val nodes = nodeViews.filter { it.node.id in s.nodes }
    val library = remember(s.fid) { sync.library() }
    val thumbs by sync.thumbs.collectAsState()
    val item = library.firstOrNull { it.fid == s.fid }
    val epLabel = item?.episode?.let { TogetherText.episodeLine(it) }
    val names = TogetherText.join(nodes.map { it.node.name })
    val next = sync.next()
    val starting = sync.starting()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 1000.dp
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
            thumbs[s.fid]?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f) }
        }
        val content = @Composable {
            Column(Modifier.padding(40.dp)) {
                BackButton(onBack)
                Spacer(Modifier.height(20.dp))
                EyebrowText(if (epLabel != null) "Watching together · $epLabel" else "Watching together", C.accent)
                Title(s.title, if (narrow) 40 else 64, lineHeight = 1.0f)
                Muted("On $names", 22, Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(24.dp))
                if (s.durMs > 0) {
                    NickSlider(
                        (pos.toFloat() / s.durMs).coerceIn(0f, 1f),
                        {}, { f -> sync.seek((f * s.durMs).toLong()) },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtMs(pos), style = body(18.sp, 700), color = C.muted)
                        Text(fmtMs(s.durMs), style = body(18.sp, 700), color = C.muted)
                    }
                } else {
                    ProgressBar(0f)
                    Text("--:--", style = body(18.sp, 700), color = C.muted)
                }
                if (starting > 0) Muted("Starting on $starting tablets...", 17, Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SkipButtonWatch(back = true) { sync.skip(-10_000) }
                    Box(
                        Modifier.size(104.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.accent).clickable { sync.toggle() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (s.playing) Solid.Pause else Solid.Play, null, tint = C.onAccent, modifier = Modifier.size(40.dp)) }
                    SkipButtonWatch(back = false) { sync.skip(10_000) }
                    if (item?.episode != null) NickButton("Next episode", { sync.playNext() }, style = BtnStyle.SECONDARY, leading = Solid.Next, enabled = next != null)
                    NickButton(if (nodes.size <= 1) "Stop" else "Stop for everyone", { sync.stop() }, style = BtnStyle.SECONDARY, leading = Solid.Stop)
                }
            }
        }
        val side = @Composable {
            Column(Modifier.width(360.dp).padding(end = 40.dp, top = 40.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val statuses = master.status.collectAsState().value
                val avgVol = nodes.mapNotNull { statuses[it.node.id]?.volume }.let { if (it.isEmpty()) 50 else it.average().toInt() }
                VolumeCard(if (nodes.size <= 1) "Volume" else "Everyone's volume", avgVol) { v -> nodes.forEach { master.setVolume(it.node.id, v) } }
                if (nodes.size > 1) {
                    EyebrowText("Tablets")
                    nodes.forEach { n ->
                        val st = states[n.node.id]
                        val line = sync.stateLine(n.node.id)
                        val inSync = sync.inSync(n.node.id)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.surface).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(n.node.name, n.node.color, n.node.avatar, 44.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(n.node.name, style = body(19.sp, 700), color = C.text)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (inSync) Dot(C.online, 8.dp)
                                        Muted(line, 15)
                                    }
                                }
                                if (nodes.size > 1) IconCircle(Lucide.X, { sync.remove(n.node.id) }, size = 44.dp)
                            }
                            NickSlider(
                                (statuses[n.node.id]?.volume ?: 50) / 100f,
                                {}, { f -> master.setVolume(n.node.id, (f * 100).toInt()) },
                                trackHeight = 10.dp, thumb = 28.dp,
                            )
                        }
                    }
                }
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content(); side() }
        } else {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                side()
            }
        }
    }
}

/** The remote scoped to one tablet (Control's Now Playing tab): no group volume, no tablet list. */
@Composable
fun TabletRemote(nodeId: String) {
    val app = App.get(LocalContext.current)
    val sync = app.master.sync
    val session by sync.session.collectAsState()
    val s = session ?: return
    if (nodeId !in s.nodes) return
    val pos = sync.expectedPos()
    val library = remember(s.fid) { sync.library() }
    val item = library.firstOrNull { it.fid == s.fid }
    val epLabel = item?.episode?.let { TogetherText.episodeLine(it) }
    val thumbs by sync.thumbs.collectAsState()
    val next = sync.next()

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
        thumbs[s.fid]?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f) }
        Column(Modifier.padding(32.dp)) {
            EyebrowText(if (epLabel != null) "Watching together · $epLabel" else "Watching together", C.accent)
            Title(s.title, 40, lineHeight = 1.0f)
            Spacer(Modifier.height(20.dp))
            if (s.durMs > 0) {
                NickSlider((pos.toFloat() / s.durMs).coerceIn(0f, 1f), {}, { f -> sync.seek((f * s.durMs).toLong()) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtMs(pos), style = body(18.sp, 700), color = C.muted)
                    Text(fmtMs(s.durMs), style = body(18.sp, 700), color = C.muted)
                }
            } else {
                ProgressBar(0f)
                Text("--:--", style = body(18.sp, 700), color = C.muted)
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SkipButtonWatch(back = true) { sync.skip(-10_000) }
                Box(
                    Modifier.size(104.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.accent).clickable { sync.toggle() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (s.playing) Solid.Pause else Solid.Play, null, tint = C.onAccent, modifier = Modifier.size(40.dp)) }
                SkipButtonWatch(back = false) { sync.skip(10_000) }
                if (item?.episode != null) NickButton("Next episode", { sync.playNext() }, style = BtnStyle.SECONDARY, leading = Solid.Next, enabled = next != null)
                NickButton("Stop", { sync.stop() }, style = BtnStyle.SECONDARY, leading = Solid.Stop)
            }
        }
    }
}

@Composable
private fun SkipButtonWatch(back: Boolean, secs: Int = 10, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (back) Lucide.RotateCcw else Lucide.RotateCw,
            if (back) "Back $secs seconds" else "Forward $secs seconds", tint = C.text, modifier = Modifier.size(34.dp),
        )
        Text("$secs", style = body(10.sp, 800), color = C.text, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun VolumeCard(label: String, value: Int, onCommit: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Lucide.Volume2, null, tint = C.text, modifier = Modifier.size(20.dp))
            Text(label, style = body(17.sp, 700), color = C.text)
        }
        NickSlider(value / 100f, {}, { f -> onCommit((f * 100).toInt()) })
    }
}
