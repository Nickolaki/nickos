package com.nbaltas.nickos.ui.master

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.semantics.Role
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.PhoneHeader
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.master.DlState
import com.nbaltas.nickos.master.JellyfinDownloads
import com.nbaltas.nickos.node.LocalVideos
import com.nbaltas.nickos.sync.JfTogether
import com.nbaltas.nickos.sync.JfVideo
import com.nbaltas.nickos.sync.JobState
import com.nbaltas.nickos.sync.StreamCodec
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
fun MovieThumb(bitmap: Bitmap?, title: String, modifier: Modifier, radius: Dp = 18.dp, titleSize: Int = 18, showTitle: Boolean = true, art: String? = null) {
    Box(modifier.clip(RoundedCornerShape(radius))) {
        Artwork(art, title, Modifier.fillMaxSize(), radius = radius, titleSize = titleSize, showTitle = showTitle && bitmap == null && art == null)
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
    // "Save to tablets instead" from the remote opens the save for that one tablet while the others keep watching.
    var saveInstead by remember { mutableStateOf<Pair<JfItem, String>?>(null) }
    if (session != null && saveInstead == null) {
        val scope = rememberCoroutineScope()
        val account = app.store.jellyfin.collectAsState().value
        TogetherRemote(onBack) { f ->
            val jfc = master.jellyfin()
            if (jfc != null && account != null) scope.launch {
                sync.dismissFailure(f.id)
                runCatching { jfc.item(account.userId, f.fid.substringAfterLast(':')) }.onSuccess { saveInstead = it to f.id }
            }
        }
        return
    }
    saveInstead?.let { (item, id) ->
        val nodes = rememberNodeViews()
        val library = remember { sync.library() }
        JfPickPanel(item, nodes, library, master, onBack = { saveInstead = null }, saveFor = id)
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
    var openJf by remember { mutableStateOf<JfItem?>(null) }
    val jfAccount by app.store.jellyfin.collectAsState()
    val jf = remember(jfAccount) { master.jellyfin() }

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

    openJf?.let { pick ->
        JfPickPanel(pick, nodes, library, master, onBack = { openJf = null })
        return
    }

    val phone = LocalPhone.current
    val edge = if (phone) PhoneMetrics.gutter else 40.dp
    Column(Modifier.fillMaxSize()) {
        if (phone) PhoneHeader("Watch together", onBack) { IconCircle(Lucide.RefreshCw, { sync.rescan() }, label = "Look again") }
        else Row(Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Spacer(Modifier.width(20.dp))
            Title("Watch together", 40, Modifier.weight(1f))
            IconCircle(Lucide.RefreshCw, { sync.rescan() })
        }
        // A streamed movie that no tablet could play ended the session: say why, and offer the save.
        val streamFails by sync.failed.collectAsState()
        val failScope = rememberCoroutineScope()
        streamFails.values.forEach { f ->
            StreamFailRow(f, sync.kid(f.id), Modifier.padding(horizontal = edge, vertical = 6.dp)) {
                val account = jfAccount
                if (jf != null && account != null) failScope.launch {
                    sync.dismissFailure(f.id)
                    runCatching { jf.item(account.userId, f.fid.substringAfterLast(':')) }.onSuccess { saveInstead = it to f.id }
                } else sync.dismissFailure(f.id)
            }
        }
        // Phone: 56dp pills in a row that scrolls, 16dp from the edge; the next pill peeks out at the end.
        LazyRow(
            if (phone) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            contentPadding = if (phone) PaddingValues(horizontal = edge) else PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val label = TogetherText.chip(true, true, Library.SELF in scanning, selfPerm, Library.ownedBy(library, Library.SELF).size, 0)
                Chip(Modifier.clickable {
                    if (label == "No video access") videosLauncher.launch(LocalVideos.permissions().toTypedArray())
                }, height = if (phone) 56.dp else 44.dp) {
                    Icon(Lucide.Tablet, null, tint = C.text, modifier = Modifier.size(if (phone) 24.dp else 28.dp))
                    Column { Text("This tablet", style = body(16.sp, 700), color = C.text, maxLines = 1); Muted(label, 15, maxLines = if (phone) 1 else 3) }
                }
            }
            items(nodes) { n ->
                val capable = n.status?.supportsSync == true
                val label = TogetherText.chip(n.online, capable, n.node.id in scanning, true, Library.ownedBy(library, n.node.id).size, transfer.downloading(n.node.id))
                Chip(Modifier.clickable {
                    when (label) {
                        "Update needed" -> onSettings(5)
                        "No video access" -> master.say("On ${n.node.name}, hold the avatar, enter the PIN, then tap Allow videos")
                        else -> {}
                    }
                }, height = if (phone) 56.dp else 44.dp) {
                    Avatar(n.node.name, n.node.color, n.node.avatar, if (phone) 32.dp else 28.dp)
                    Column { Text(n.node.name, style = body(16.sp, 700), color = C.text, maxLines = 1); Muted(label, 15, maxLines = if (phone) 1 else 3) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Segmented(
            listOf("Movies" to null, "Shows" to null, "Jellyfin" to null), segment,
            { segment = it; openShow = null },
            Modifier.padding(horizontal = edge),
        )
        Spacer(Modifier.height(12.dp))

        val onlineNodes = nodes.filter { it.online }
        val capableOnline = onlineNodes.filter { it.status?.supportsSync == true }
        val selfHasNone = Library.ownedBy(library, Library.SELF).isEmpty()
        val scanningCount = scanning.size

        when {
            !selfPerm -> CenterMessage("Let NickOS see your videos", "Movies on this tablet are what you send to the kids' tablets. Allow video access once.", titleLines = 3) {
                NickButton("Allow videos on this tablet", { videosLauncher.launch(LocalVideos.permissions().toTypedArray()) }, fullOnPhone(), style = BtnStyle.PRIMARY, big = !LocalPhone.current)
            }
            segment == 2 && jf == null -> CenterMessage("Connect Jellyfin", "Add your Jellyfin server in Settings to pick a movie or show and watch it together.") {
                NickButton("Open Settings", { onSettings(1) }, fullOnPhone(), style = BtnStyle.PRIMARY, big = !LocalPhone.current)
            }
            segment == 2 -> JellyfinBrowser(jf!!, jfAccount!!.userId, "together", null, onPick = { openJf = it })
            onlineNodes.isEmpty() && selfHasNone -> CenterMessage("No tablets online", "Turn the kids' tablets on and connect them to this WiFi. Movies on them, and on this tablet, show up here.") {
                NickButton("All tablets", onBack, fullOnPhone(), style = BtnStyle.GHOST, big = !LocalPhone.current)
            }
            onlineNodes.isNotEmpty() && capableOnline.isEmpty() -> CenterMessage("Update the kids' tablets", "Watch together needs the latest NickOS on each tablet. Update them from Settings, then come back.") {
                NickButton("Open Settings", { onSettings(5) }, fullOnPhone(), style = BtnStyle.PRIMARY, big = !LocalPhone.current)
            }
            else -> {
                val visible = if (segment == 0) Library.movies(library) else null
                val shows = if (segment == 1) Library.shows(library) else emptyList()
                // Phone: the empty state and the list pad themselves with the 16dp gutter, so this column adds none.
                Column(Modifier.fillMaxSize().padding(horizontal = if (phone) 0.dp else edge)) {
                    if (scanningCount > 0) Muted("Looking for movies on $scanningCount tablets...", 18, Modifier.padding(start = if (phone) edge else 0.dp, end = if (phone) edge else 0.dp, bottom = 12.dp))
                    when {
                        segment == 0 && visible!!.isEmpty() && scanningCount == 0 -> CenterMessage("No movies yet", "Copy MP4 or MKV files into the Movies or Download folder on this tablet or any kid's tablet. From here you can send a movie to the other tablets and play it on all of them at once.") {
                            NickButton("Look again", { sync.rescan() }, fullOnPhone(), style = BtnStyle.PRIMARY, big = !LocalPhone.current, leading = Lucide.RefreshCw)
                        }
                        segment == 1 && openShow == null && shows.isEmpty() && scanningCount == 0 -> CenterMessage("No shows yet", "Copy episodes into a folder named after the show, inside Movies or Download on this tablet, or send them from another tablet.") {
                            NickButton("Look again", { sync.rescan() }, fullOnPhone(), style = BtnStyle.PRIMARY, big = !LocalPhone.current, leading = Lucide.RefreshCw)
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
    if (LocalPhone.current) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = PhoneMetrics.gutter, end = PhoneMetrics.gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.fid }) { item ->
                val avail = TogetherText.availability(item.owners, expected, order, name)
                val jobs = transfer.cardJobs(item.fid)
                PhoneMediaRow(
                    art = { MovieThumb(thumbs[item.fid], item.title, it, radius = PhoneMetrics.nestedRadius, showTitle = false) },
                    title = item.title, onClick = { onOpen(item) },
                ) {
                    Muted(if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown", 14)
                    Text(avail.text, style = body(14.sp, 600, 1.3f), color = if (avail.all) C.online else C.muted)
                    TogetherText.jobLine(jobs)?.let { Text(it, style = body(14.sp, 600, 1.3f), color = C.accent) }
                }
            }
        }
        return
    }
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
    if (LocalPhone.current) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = PhoneMetrics.gutter, end = PhoneMetrics.gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(shows, key = { it.name }) { show ->
                PhoneMediaRow(
                    art = { MovieThumb(show.episodes.firstOrNull()?.let { thumbs[it.fid] }, show.name, it, radius = PhoneMetrics.nestedRadius, showTitle = false) },
                    title = show.name, onClick = { onOpen(show) },
                ) { Muted("${show.episodes.size} episodes", 14) }
            }
        }
        return
    }
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

    if (LocalPhone.current) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PhoneMetrics.gutter), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
            PhoneBackChip(backLabel, onBack)
            PhoneMediaHeader(
                art = { MovieThumb(thumb, item.title, it, radius = PhoneMetrics.nestedRadius, showTitle = false) },
                eyebrow = epLabel, title = item.title,
                meta = if (item.durMs > 0) minutesText((item.durMs / 60_000).toInt()) else "Length unknown",
            )
            nodes.forEach { n ->
                val has = n.node.id in item.owners
                val capable = n.status?.supportsSync == true
                val job = transfer.jobFor(n.node.id, item.fid)
                val jobState = job?.let { JobState.of(it.state) }
                val running = job != null && jobState != JobState.FAILED && jobState != JobState.DONE && jobState != JobState.CANCELLED
                PhoneDeviceRow(
                    n,
                    status = when {
                        !capable -> "Update needed"
                        running || job != null && jobState == JobState.FAILED -> null
                        has && n.online -> "Ready"
                        has -> "Offline"
                        n.online -> if (sendBlocked != null) sendBlocked else "Not on this tablet"
                        else -> "Offline"
                    },
                    top = {
                        if (capable && !running && !(job != null && jobState == JobState.FAILED)) when {
                            has && n.online -> NickSwitch(n.node.id in selected) { on -> selected = if (on) selected + n.node.id else selected - n.node.id }
                            has -> NickSwitch(on = false, enabled = false) {}
                            n.online && sendBlocked == null -> NickButton("Send", { transfer.send(item.fid, setOf(n.node.id)) }, style = BtnStyle.SECONDARY)
                        }
                    },
                ) {
                    if (capable && job != null && jobState == JobState.FAILED) {
                        Text(TogetherText.jobError(job.err, job.size), style = body(14.sp, 500, 1.3f), color = C.danger)
                        NickButton("Try again", { transfer.retry(item.fid, n.node.id) }, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY)
                    } else if (capable && running) {
                        PhoneProgressLine(TogetherText.percent(job!!.done, job.size) / 100f, TogetherText.jobProgress(job)) { transfer.cancel(item.fid, n.node.id) }
                    }
                }
            }
            Spacer(Modifier.height(PhoneMetrics.relatedGap))
            val k = selected.size
            val playLabel = if (k == 1) "Play on ${nodes.firstOrNull { it.node.id == selected.first() }?.node?.name ?: sync.kid(selected.first())}" else "Play on $k tablets"
            NickButton(playLabel, { sync.start(item.fid, item.title, item.durMs, selected) }, Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Solid.Play, enabled = k > 0)
            val j = lacking.size
            if (j > 0) NickButton("Send to $j tablet${if (j == 1) "" else "s"}", { transfer.send(item.fid, lacking) }, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY, leading = Lucide.Send, enabled = sendBlocked == null)
        }
        return
    }

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
                    has && !n.online -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NickSwitch(on = false, enabled = false) {}
                        Muted("Offline", 15)
                    }
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
        val playLabel = if (k == 1) "Play on ${nodes.firstOrNull { it.node.id == selected.first() }?.node?.name ?: sync.kid(selected.first())}" else "Play on $k tablets"
        NickButton(playLabel, { sync.start(item.fid, item.title, item.durMs, selected) }, style = BtnStyle.PRIMARY, big = !LocalPhone.current, leading = Solid.Play, enabled = k > 0)
        Spacer(Modifier.height(12.dp))
        val j = lacking.size
        if (j > 0) NickButton("Send to $j tablet${if (j == 1) "" else "s"}", { transfer.send(item.fid, lacking) }, style = BtnStyle.SECONDARY, big = !LocalPhone.current, leading = Lucide.Send, enabled = sendBlocked == null)
    }
}

/** One tablet's line in the Getting ready panel. [fraction] is null when there is no bar to show. */
internal class JfRow(val id: String, val text: String, val fraction: Float?, val ready: Boolean, val failed: Boolean, val error: String? = null)

/** What the Getting ready panel shows. [saveFraction] is the parent's save progress, null when it is not saving. */
internal class JfPrep(val saveFraction: Float?, val saveError: String?, val rows: List<JfRow>, val stuck: String? = null)

/** A tablet that could not stream this movie. [canSave] false: saving would not help (it cannot play this one). */
internal class JfStreamFail(val id: String, val text: String, val canSave: Boolean)

/**
 * The "Stream now" half of the pick panel. [count] is the real number of tablets that can stream now,
 * [label] says so ("Stream now on 2 tablets (Leo can't stream yet)").
 */
internal class JfStreamUi(
    val count: Int, val label: String, val onStream: () -> Unit,
    val failures: List<JfStreamFail> = emptyList(), val onSaveInstead: (String) -> Unit = {},
    /** "Leo's tablet gets a converted stream (...)", or null when every tablet plays the file directly. */
    val convertedLine: String? = null,
)

/** Pick a Jellyfin movie or episode, choose the tablets, save it, send it, and start it on all of them in sync. */
@Composable
internal fun JfPickPanel(
    item: JfItem, nodes: List<NodeView>, library: List<LibraryItem>, master: com.nbaltas.nickos.master.MasterRuntime, onBack: () -> Unit,
    /** "Save to tablets instead": only this tablet, saving starts at once, and a group already playing is left alone. */
    saveFor: String? = null,
) {
    val sync = master.sync
    val transfer = master.transfer
    val downloads = master.jfDownloads
    val dlMap by downloads.progress.collectAsState()
    @Suppress("UNUSED_VARIABLE") val jobsMap by transfer.jobs.collectAsState()
    val dl = dlMap[item.id]
    val lib = JellyfinDownloads.inLibrary(item, library, dl?.fid)
    val owners = lib?.owners.orEmpty()
    val online = nodes.filter { it.online }.map { it.node.id }.toSet()
    val nameOf: (String) -> String = { id -> nodes.firstOrNull { it.node.id == id }?.node?.name ?: sync.kid(id) }
    var selected by remember(item.id) {
        mutableStateOf(if (saveFor != null) setOf(saveFor) else nodes.filter { it.online && it.status?.supportsSync == true }.map { it.node.id }.toSet())
    }
    val failures by sync.failed.collectAsState()
    var preparing by remember(item.id) { mutableStateOf(false) }
    var started by remember(item.id) { mutableStateOf(false) }
    var note by remember(item.id) { mutableStateOf<String?>(null) }
    var stalled by remember(item.id) { mutableStateOf(false) }

    var attempt by remember(item.id) { mutableStateOf(0) }

    // Any progress in saving, sending or ownership resets the 60 s clock. Nothing for that long: say who is still awaited.
    val progressSig = listOf(dl?.bytes ?: 0L, dl?.fid, owners, selected.map { id -> lib?.let { transfer.jobFor(id, it.fid)?.done } ?: 0L })
    LaunchedEffect(progressSig) {
        stalled = JfTogether.showStallPrompt(stalled, progressResumed = true, timedOut = false)
    }
    LaunchedEffect(preparing, started, progressSig, attempt) {
        if (preparing && !started) {
            delay(JfTogether.STALL_MS)
            stalled = JfTogether.showStallPrompt(stalled, progressResumed = false, timedOut = true)
        }
    }

    // Start the moment every tablet still online owns the file. Only once per tap, and never wait for an offline tablet.
    LaunchedEffect(preparing, started, selected, owners, online) {
        if (!preparing || started) return@LaunchedEffect
        val step = JfTogether.step(selected, online, owners, started)
        if (step.dropped.isNotEmpty()) {
            selected = selected - step.dropped
            note = JfTogether.droppedNote(step.dropped, selected.isNotEmpty(), nameOf)
            if (selected.isEmpty()) preparing = false
            return@LaunchedEffect
        }
        val go = step.start
        // Saving for one tablet that could not stream: the others are watching, so this does not restart them.
        if (go != null && lib != null && saveFor != null && sync.session.value != null) {
            started = true
            preparing = false
            note = "Saved on ${nameOf(saveFor)}'s tablet"
            return@LaunchedEffect
        }
        if (go != null && lib != null) {
            started = true
            sync.start(lib.fid, item.displayTitle, lib.durMs.takeIf { it > 0 } ?: (item.runTicks / 10_000), go,
                art = master.jellyfin()?.let { it.landscape(item) ?: it.poster(item) })
        }
    }

    val usable = JfTogether.usable(owners, online)
    val resolving = dl?.state == DlState.DONE && lib == null
    val stuck = JfTogether.stuck(
        selected, owners, online,
        saving = dl?.active == true || resolving,
        saveFailed = dl?.state == DlState.FAILED, saveCancelled = dl?.state == DlState.CANCELLED,
    ) ?: if (stalled) JfTogether.stallText(nodes.filter { it.node.id in selected && it.node.id !in owners }.map { it.node.name }) else null
    val prep = if (!preparing) null else JfPrep(
        saveFraction = dl?.takeIf { it.active }?.fraction,
        saveError = dl?.takeIf { it.state == DlState.FAILED && usable.isEmpty() }?.err,
        stuck = stuck,
        rows = nodes.filter { it.node.id in selected }.map { n ->
            val job = lib?.let { transfer.jobFor(n.node.id, it.fid) }
            val failed = job != null && JobState.of(job.state) == JobState.FAILED
            JfRow(
                n.node.id, JfTogether.rowText(n.node.id in owners, job),
                fraction = job?.takeIf { !failed }?.let { TogetherText.percent(it.done, it.size) / 100f },
                ready = n.node.id in owners, failed = failed, error = job?.takeIf { failed }?.let { TogetherText.jobError(it.err, it.size) },
            )
        },
    )
    fun beginSave(sel: Set<String>) {
        note = null
        downloads.clear(item.id)
        preparing = true
        when (JfTogether.plan(sel, owners, online)) {
            JfTogether.Plan.START_NOW -> {}
            JfTogether.Plan.SEND -> lib?.let { transfer.send(it.fid, sel) } ?: downloads.save(item, sel)
            JfTogether.Plan.SAVE -> downloads.save(item, sel)
        }
    }
    if (saveFor != null) LaunchedEffect(item.id, saveFor) { beginSave(setOf(saveFor)) }

    // A tablet can be streamed to only when it is online, on a sealed link and says "stream1". The label counts the real ones.
    val account by App.get(LocalContext.current).store.jellyfin.collectAsState()
    val client = remember(account) { master.jellyfin() }
    // The movie's video stream (one GET), to know which tablets cannot decode the file. Unknown until it arrives: the file as before.
    var video by remember(item.id, account) { mutableStateOf<JfVideo?>(null) }
    LaunchedEffect(item.id, client, account) {
        val a = account
        if (client != null && a != null) video = runCatching { client.video(a.userId, item.id) }.getOrNull()
    }
    val (streamOk, streamNo) = JfTogether.streamSplit(selected) { id ->
        nodes.firstOrNull { it.node.id == id }?.let { it.online && it.status?.supportsStream == true && master.isSealed(id) } == true
    }
    val decs = streamOk.associateWith { id -> nodes.firstOrNull { it.node.id == id }?.status?.dec }
    val stream = if (client == null) null else JfStreamUi(
        count = streamOk.size,
        label = JfTogether.streamLabel(streamOk.size, streamNo.map(nameOf)),
        convertedLine = StreamCodec.convertedLine(StreamCodec.converted(video, decs).map { sync.kid(it) }),
        onStream = {
            note = null
            sync.start(
                client.streamFid(item), item.displayTitle, item.runTicks / 10_000, streamOk, client.authedStreamUrl(item),
                art = client.landscape(item) ?: client.poster(item), nodeUrls = client.streamUrls(item, video, decs),
            )
        },
        failures = failures.values.filter { it.fid == client.streamFid(item) }.map { f ->
            JfStreamFail(f.id, TogetherText.streamError(nameOf(f.id), f.code), f.canSaveInstead)
        },
        onSaveInstead = { id ->
            sync.dismissFailure(id)
            selected = setOf(id)
            beginSave(setOf(id))
        },
    )
    JfPickContent(
        item = item, nodes = nodes, selected = selected, onToggle = { id, on -> selected = if (on) selected + id else selected - id },
        prep = prep, note = note, onBack = onBack, stream = stream,
        art = client?.let { it.landscape(item) ?: it.poster(item) },
        onPrimary = { beginSave(selected) },
        onRetryRow = { id -> attempt++; lib?.let { transfer.retry(it.fid, id) } },
        onRetrySave = {
            // Plan again from what is true now: send from an owner that is online, else save to this tablet.
            note = null
            attempt++
            downloads.clear(item.id)
            when (JfTogether.plan(selected, owners, online)) {
                JfTogether.Plan.START_NOW -> {}
                JfTogether.Plan.SEND -> lib?.let { transfer.send(it.fid, selected) } ?: downloads.save(item, selected)
                JfTogether.Plan.SAVE -> downloads.save(item, selected)
            }
        },
        onCancel = {
            preparing = false
            // Also after the save reports DONE: this stops the send that is waiting for the copy to show up.
            downloads.cancel(item.id)
            (lib?.fid ?: dl?.fid)?.let { fid -> selected.forEach { transfer.cancel(fid, it) } }
        },
    )
}

@Composable
internal fun JfPickContent(
    item: JfItem, nodes: List<NodeView>, selected: Set<String>, onToggle: (String, Boolean) -> Unit,
    prep: JfPrep?, note: String?, onBack: () -> Unit, onPrimary: () -> Unit,
    onRetryRow: (String) -> Unit = {}, onRetrySave: () -> Unit = {}, onCancel: () -> Unit = {},
    stream: JfStreamUi? = null,
    /** The Jellyfin artwork for the poster; null draws the placeholder. */
    art: String? = null,
) {
    if (LocalPhone.current) {
        PhoneJfPickContent(item, nodes, selected, onToggle, prep, note, onBack, onPrimary, onRetryRow, onRetrySave, onCancel, stream, art)
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp)) {
        Chip(Modifier.clickable(onClick = onBack)) {
            Icon(Lucide.ChevronLeft, null, tint = C.text, modifier = Modifier.size(18.dp))
            Text("Jellyfin", style = body(16.sp, 700), color = C.text)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MovieThumb(null, item.displayTitle, Modifier.size(400.dp, 225.dp), showTitle = false, art = art)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.isEpisode) EyebrowText("${item.epCode} · ${item.name}")
                Title(item.displayTitle, 44, lineHeight = 1.05f)
                Muted(if (item.runMin > 0) minutesText(item.runMin) else "Length unknown", 18)
            }
        }
        Spacer(Modifier.height(28.dp))
        if (prep == null) {
            nodes.forEach { n ->
                val capable = n.status?.supportsSync == true
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Avatar(n.node.name, n.node.color, n.node.avatar, 44.dp)
                    Text(n.node.name, style = body(19.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                    when {
                        !capable -> Muted("Update needed", 15)
                        !n.online -> Muted("Offline", 15)
                        else -> NickSwitch(n.node.id in selected) { on -> onToggle(n.node.id, on) }
                    }
                }
            }
            note?.let { Muted(it, 16, Modifier.padding(top = 8.dp)) }
            stream?.failures?.forEach { f ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(f.text, style = body(15.sp, 500, 1.3f), color = C.danger)
                    if (f.canSave) NickButton(TogetherText.SAVE_INSTEAD, { stream.onSaveInstead(f.id) }, style = BtnStyle.SECONDARY)
                }
            }
            Spacer(Modifier.height(20.dp))
            val k = selected.size
            if (stream != null) {
                NickButton(
                    if (k == 0) "Pick a tablet" else if (stream.count == 0) "No tablet can stream yet" else stream.label, stream.onStream,
                    style = BtnStyle.PRIMARY, big = !LocalPhone.current, leading = Solid.Play, enabled = k > 0 && stream.count > 0,
                )
                Spacer(Modifier.height(12.dp))
                NickButton(
                    "Save to tablets", onPrimary,
                    style = BtnStyle.SECONDARY, big = !LocalPhone.current, leading = Lucide.Tablet, enabled = k > 0,
                )
                Muted(TogetherText.STREAM_HINT, 15, Modifier.padding(top = 10.dp))
                stream.convertedLine?.let { Muted(it, 15, Modifier.padding(top = 4.dp)) }
            } else NickButton(
                if (k == 0) "Pick a tablet" else "Watch together on $k tablet${if (k == 1) "" else "s"}", onPrimary,
                style = BtnStyle.PRIMARY, big = !LocalPhone.current, leading = Solid.Play, enabled = k > 0,
            )
        } else {
            Title("Getting ready", 30)
            Spacer(Modifier.height(12.dp))
            if (prep.saveFraction != null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Lucide.Tablet, null, tint = C.text, modifier = Modifier.size(44.dp))
                    Text("Saving to this tablet", style = body(19.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                    ProgressBar(prep.saveFraction, Modifier.width(200.dp))
                    Muted("${(prep.saveFraction * 100).toInt()}%", 15)
                }
            }
            (prep.saveError ?: prep.stuck)?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(it, style = body(15.sp, 500, 1.3f), color = if (prep.saveError != null) C.danger else C.text)
                    NickButton("Try again", onRetrySave, style = BtnStyle.SECONDARY)
                }
            }
            prep.rows.forEach { r ->
                val n = nodes.firstOrNull { it.node.id == r.id } ?: return@forEach
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Avatar(n.node.name, n.node.color, n.node.avatar, 44.dp)
                    Text(n.node.name, style = body(19.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                    if (r.failed) {
                        Text(r.error ?: r.text, style = body(15.sp, 500, 1.3f), color = C.danger)
                        NickButton("Try again", { onRetryRow(r.id) }, style = BtnStyle.SECONDARY)
                    } else {
                        if (r.fraction != null && !r.ready) ProgressBar(r.fraction, Modifier.width(200.dp))
                        Muted(r.text, 15)
                    }
                }
            }
            note?.let { Muted(it, 16, Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(20.dp))
            NickButton("Cancel", onCancel, style = BtnStyle.SECONDARY, big = !LocalPhone.current, leading = Lucide.X)
        }
    }
}

/** One tablet that could not stream: why, and "Save to tablets instead" when saving would help. */
@Composable
internal fun StreamFailRow(f: com.nbaltas.nickos.master.StreamFail, who: String, modifier: Modifier = Modifier, onSave: () -> Unit) {
    if (LocalPhone.current) {
        // The reason gets the full width and wraps; the save button is a full-width row under it.
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
            Text(TogetherText.streamError(who, f.code), style = body(14.sp, 500, 1.3f), color = C.danger)
            if (f.canSaveInstead) NickButton(TogetherText.SAVE_INSTEAD, onSave, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY)
        }
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(TogetherText.streamError(who, f.code), style = body(15.sp, 500, 1.3f), color = C.danger)
        if (f.canSaveInstead) NickButton(TogetherText.SAVE_INSTEAD, onSave, style = BtnStyle.SECONDARY)
    }
}

@Composable
internal fun TogetherRemote(onBack: () -> Unit, onSaveInstead: (com.nbaltas.nickos.master.StreamFail) -> Unit = {}) {
    val app = App.get(LocalContext.current)
    val master = app.master
    val sync = master.sync
    val session by sync.session.collectAsState()
    val s = session ?: return onBack()
    if (LocalPhone.current) {
        PhoneGroupRemote(null, rememberScrollState(), onBack, onSaveInstead)
        return
    }
    val states by sync.states.collectAsState()
    val waiting by sync.waiting.collectAsState()
    val streamFails by sync.failed.collectAsState()
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
            val bg = thumbs[s.fid]
            if (bg != null) Image(bg.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
            else if (s.art != null) Artwork(s.art, s.title, Modifier.fillMaxSize().alpha(0.35f), radius = 0.dp, showTitle = false)
        }
        val content = @Composable {
            Column(Modifier.padding(40.dp)) {
                BackButton(onBack)
                Spacer(Modifier.height(20.dp))
                // The poster sits left of the title block, as on the tablet pick panel.
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MovieThumb(thumbs[s.fid], s.title, Modifier.height(if (narrow) 160.dp else 225.dp).aspectRatio(16f / 9f), showTitle = false, art = s.art)
                    Column(Modifier.weight(1f)) {
                        EyebrowText(if (epLabel != null) "Watching together · $epLabel" else "Watching together", C.accent)
                        Title(s.title, if (narrow) 40 else 64, lineHeight = 1.0f)
                        Muted("On $names", 22, Modifier.padding(top = 6.dp))
                    }
                }
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
                waiting?.let { Text(it, style = body(18.sp, 700), color = C.accent, modifier = Modifier.padding(top = 8.dp)) }
                streamFails.values.filter { it.fid == s.fid }.forEach { f ->
                    StreamFailRow(f, sync.kid(f.id), Modifier.padding(top = 8.dp)) { onSaveInstead(f) }
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
                    NickButton(if (nodes.size <= 1) "Stop" else "Stop for everyone", { sync.stop() }, style = BtnStyle.SECONDARY, leading = Solid.Stop)
                }
            }
        }
        val side = @Composable {
            Column(Modifier.width(360.dp).padding(end = 40.dp, top = 40.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val statuses = master.status.collectAsState().value
                val avgVol = nodes.mapNotNull { statuses[it.node.id]?.volume }.let { if (it.isEmpty()) 50 else it.average().toInt() }
                VolumeCard(if (nodes.size <= 1) "Volume" else "Everyone's volume", avgVol) { v -> nodes.filter { it.online }.forEach { master.setVolume(it.node.id, v) } }
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
        val bg = thumbs[s.fid]
        if (bg != null) Image(bg.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
        else if (s.art != null) Artwork(s.art, s.title, Modifier.fillMaxSize().alpha(0.35f), radius = 0.dp, showTitle = false)
        Column(Modifier.padding(32.dp)) {
            // The poster sits left of the title, as on the Watch together player: a local frame, else the Jellyfin artwork.
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                MovieThumb(bg, s.title, Modifier.height(135.dp).aspectRatio(16f / 9f), showTitle = false, art = s.art)
                Column(Modifier.weight(1f)) {
                    EyebrowText(if (epLabel != null) "Watching together · $epLabel" else "Watching together", C.accent)
                    Title(s.title, 40, lineHeight = 1.0f)
                }
            }
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
    val phone = LocalPhone.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(if (phone) PhoneMetrics.cardRadius else 26.dp)).background(C.surface).padding(if (phone) PhoneMetrics.cardPadding else 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Lucide.Volume2, null, tint = C.text, modifier = Modifier.size(20.dp))
            Text(label, style = body(17.sp, 700), color = C.text)
        }
        NickSlider(value / 100f, {}, { f -> onCommit((f * 100).toInt()) })
    }
}

// ---------------- phone ----------------

/** A button or field that fills the width on a phone and keeps its own size on a tablet. */
@Composable
internal fun fullOnPhone(): Modifier = if (LocalPhone.current) Modifier.fillMaxWidth() else Modifier

/** The "back to <where>" pill, at least 48dp high. */
@Composable
internal fun PhoneBackChip(label: String, onBack: () -> Unit) {
    Chip(Modifier.clickable(role = Role.Button, onClick = onBack), height = PhoneMetrics.minTarget) {
        Icon(Lucide.ChevronLeft, null, tint = C.text, modifier = Modifier.size(20.dp))
        Text(label, style = body(16.sp, 700), color = C.text, maxLines = 1)
    }
}

/**
 * A phone media header, stacked: the art 160dp high at 16:9, then the title (24, wraps at words, three lines) and
 * the meta line (14) under it. Nothing sits beside the title, so it can never be pushed off the right edge.
 */
@Composable
internal fun PhoneMediaHeader(
    art: @Composable (Modifier) -> Unit, eyebrow: String?, title: String, meta: String?,
    eyebrowColor: Color = C.muted, extra: @Composable ColumnScope.() -> Unit = {}, fullWidthArt: Boolean = false,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
        art(if (fullWidthArt) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.height(160.dp).aspectRatio(16f / 9f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            eyebrow?.let { EyebrowText(it, eyebrowColor) }
            PhoneMediaTitle(title)
            meta?.let { Text(it, style = body(14.sp, 500, 1.3f), color = C.muted) }
            extra()
        }
    }
}

/** A compact list row for a movie or show: 112x72 art, a weighted title (18, two lines) and the lines under it (14). */
@Composable
internal fun PhoneMediaRow(art: @Composable (Modifier) -> Unit, title: String, onClick: () -> Unit, meta: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
    ) {
        art(Modifier.size(112.dp, 72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = body(18.sp, 700, 1.25f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            meta()
        }
    }
}

/**
 * One tablet in a pick or progress list, as a Column: avatar, weighted name (18) and status (14) on top with [top] at the end,
 * then [below] (a progress bar, an error) across the full width.
 */
@Composable
internal fun PhoneDeviceRowBase(
    avatar: @Composable () -> Unit, name: String, status: String?,
    top: @Composable RowScope.() -> Unit = {}, below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
        Row(Modifier.fillMaxWidth().heightIn(min = PhoneMetrics.minTarget), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
            avatar()
            Column(Modifier.weight(1f)) {
                Text(name, style = body(18.sp, 700, 1.25f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (status != null) Text(status, style = body(14.sp, 500, 1.3f), color = C.muted)
            }
            top()
        }
        below()
    }
}

@Composable
private fun PhoneDeviceRow(n: NodeView, status: String?, top: @Composable RowScope.() -> Unit = {}, below: @Composable ColumnScope.() -> Unit = {}) =
    PhoneDeviceRowBase({ Avatar(n.node.name, n.node.color, n.node.avatar, 32.dp) }, n.node.name, status, top, below)

/** A full-width 6dp bar, then the figures (14, tabular digits) with an optional 48dp cancel at the end. The bar eases over 150ms. */
@Composable
internal fun PhoneProgressLine(fraction: Float, text: String, onCancel: (() -> Unit)? = null) {
    val shown by animateFloatAsState(fraction, tween(150), label = "progress")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProgressBar(shown, Modifier.fillMaxWidth(), height = 6.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = body(14.sp, 600, 1.3f).copy(fontFeatureSettings = "tnum"), color = C.muted, modifier = Modifier.weight(1f))
            if (onCancel != null) IconCircle(Lucide.X, onCancel, label = "Cancel")
        }
    }
}

@Composable
private fun PhoneJfPickContent(
    item: JfItem, nodes: List<NodeView>, selected: Set<String>, onToggle: (String, Boolean) -> Unit,
    prep: JfPrep?, note: String?, onBack: () -> Unit, onPrimary: () -> Unit,
    onRetryRow: (String) -> Unit, onRetrySave: () -> Unit, onCancel: () -> Unit, stream: JfStreamUi?, art: String?,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PhoneMetrics.gutter), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
        PhoneBackChip("Jellyfin", onBack)
        PhoneMediaHeader(
            art = { MovieThumb(null, item.displayTitle, it, radius = PhoneMetrics.nestedRadius, showTitle = false, art = art) },
            eyebrow = if (item.isEpisode) "${item.epCode} · ${item.name}" else null, title = item.displayTitle,
            meta = if (item.runMin > 0) minutesText(item.runMin) else "Length unknown",
        )
        if (prep == null) {
            nodes.forEach { n ->
                val capable = n.status?.supportsSync == true
                PhoneDeviceRow(n, status = if (!capable) "Update needed" else if (!n.online) "Offline" else null, top = {
                    if (capable && n.online) NickSwitch(n.node.id in selected) { on -> onToggle(n.node.id, on) }
                })
            }
            note?.let { Muted(it, 14) }
            stream?.failures?.forEach { f ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
                    Text(f.text, style = body(14.sp, 500, 1.3f), color = C.danger)
                    if (f.canSave) NickButton(TogetherText.SAVE_INSTEAD, { stream.onSaveInstead(f.id) }, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY)
                }
            }
            Spacer(Modifier.height(PhoneMetrics.relatedGap))
            val k = selected.size
            if (stream != null) {
                NickButton(
                    if (k == 0) "Pick a tablet" else if (stream.count == 0) "No tablet can stream yet" else stream.label, stream.onStream,
                    Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Solid.Play, enabled = k > 0 && stream.count > 0,
                )
                NickButton("Save to tablets", onPrimary, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY, leading = Lucide.Tablet, enabled = k > 0)
                Muted(TogetherText.STREAM_HINT, 14)
                stream.convertedLine?.let { Muted(it, 14) }
            } else NickButton(
                if (k == 0) "Pick a tablet" else "Watch together on $k tablet${if (k == 1) "" else "s"}", onPrimary,
                Modifier.fillMaxWidth(), style = BtnStyle.PRIMARY, leading = Solid.Play, enabled = k > 0,
            )
        } else {
            Title("Getting ready", 40)
            if (prep.saveFraction != null) {
                PhoneDeviceRowBase({ Icon(Lucide.Tablet, null, tint = C.text, modifier = Modifier.size(32.dp)) }, "Saving to this tablet", null) {
                    PhoneProgressLine(prep.saveFraction, "${(prep.saveFraction * 100).toInt()}%")
                }
            }
            (prep.saveError ?: prep.stuck)?.let {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap)) {
                    Text(it, style = body(14.sp, 500, 1.3f), color = if (prep.saveError != null) C.danger else C.text)
                    NickButton("Try again", onRetrySave, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY)
                }
            }
            prep.rows.forEach { r ->
                val n = nodes.firstOrNull { it.node.id == r.id } ?: return@forEach
                val bar = r.fraction != null && !r.ready && !r.failed
                PhoneDeviceRow(n, status = if (r.failed || bar) null else r.text) {
                    if (r.failed) {
                        Text(r.error ?: r.text, style = body(14.sp, 500, 1.3f), color = C.danger)
                        NickButton("Try again", { onRetryRow(r.id) }, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY)
                    } else if (bar) PhoneProgressLine(r.fraction!!, r.text)
                }
            }
            note?.let { Muted(it, 14) }
            Spacer(Modifier.height(PhoneMetrics.relatedGap))
            NickButton("Cancel", onCancel, Modifier.fillMaxWidth(), style = BtnStyle.SECONDARY, leading = Lucide.X)
        }
    }
}

/**
 * The Together remote on a phone, shared by the Together screen ([nodeId] null: the whole group, with its volume and the
 * tablet list) and Control's Now Playing tab for one tablet ([nodeId] set: just the transport). A stacked media header,
 * the seek bar, a reserved status slot so the controls never jump, then play 72 / skip 56 and an equal Next + Stop row.
 */
@Composable
internal fun PhoneGroupRemote(
    nodeId: String?, scroll: ScrollState, onBack: (() -> Unit)? = null,
    onSaveInstead: (com.nbaltas.nickos.master.StreamFail) -> Unit = {},
) {
    val app = App.get(LocalContext.current)
    val master = app.master
    val sync = master.sync
    val session by sync.session.collectAsState()
    val s = session ?: return
    if (nodeId != null && nodeId !in s.nodes) return
    val states by sync.states.collectAsState()
    val waiting by sync.waiting.collectAsState()
    val streamFails by sync.failed.collectAsState()
    rememberNow(500)
    val pos = sync.expectedPos()
    val nodes = rememberNodeViews().filter { it.node.id in s.nodes }
    val library = remember(s.fid) { sync.library() }
    val thumbs by sync.thumbs.collectAsState()
    val item = library.firstOrNull { it.fid == s.fid }
    val epLabel = item?.episode?.let { TogetherText.episodeLine(it) }
    val next = sync.next()
    val starting = sync.starting()
    val bg = thumbs[s.fid]

    Box(Modifier.fillMaxSize()) {
        // A plain dark gradient: a blurred copy of the poster behind the content read as a shape.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.bg.copy(alpha = 0.92f), 1f to C.bg)))
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(PhoneMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
        ) {
            if (onBack != null) BackButton(onBack)
            PhoneMediaHeader(
                art = { MovieThumb(bg, s.title, it, radius = PhoneMetrics.nestedRadius, showTitle = false, art = s.art) },
                eyebrow = if (epLabel != null) "Watching together · $epLabel" else "Watching together", eyebrowColor = C.accent,
                title = s.title, meta = if (nodeId == null) "On ${TogetherText.join(nodes.map { it.node.name })}" else null,
                fullWidthArt = true,
            )
            // One line tall, kept under the title (not between the times and the buttons) so it leaves no gap above the transport row.
            Column(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalArrangement = Arrangement.Center) {
                if (starting > 0) Text("Starting on $starting tablets...", style = body(16.sp, 500, 1.3f), color = C.muted)
                waiting?.let { Text(it, style = body(16.sp, 700, 1.3f), color = C.accent) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (s.durMs > 0) {
                    NickSlider((pos.toFloat() / s.durMs).coerceIn(0f, 1f), {}, { f -> sync.seek((f * s.durMs).toLong()) })
                    PhoneTimes(fmtMs(pos), fmtMs(s.durMs))
                } else {
                    Box(Modifier.fillMaxWidth().height(PhoneMetrics.minTarget), contentAlignment = Alignment.Center) { ProgressBar(0f, height = 8.dp) }
                    PhoneTimes("--:--", "--:--")
                }
            }
            streamFails.values.filter { it.fid == s.fid }.forEach { f ->
                StreamFailRow(f, sync.kid(f.id)) { onSaveInstead(f) }
            }
            PhoneTransport(playing = s.playing, backSecs = 10, fwdSecs = 10, onBack = { sync.skip(-10_000) }, onToggle = { sync.toggle() }, onForward = { sync.skip(10_000) })
            PhoneNextStop(
                showNext = item?.episode != null, nextEnabled = next != null, onNext = { sync.playNext() },
                stopLabel = if (nodeId == null && nodes.size > 1) "Stop for everyone" else "Stop", onStop = { sync.stop() },
            )
            if (nodeId == null) {
                val statuses = master.status.collectAsState().value
                val avgVol = nodes.mapNotNull { statuses[it.node.id]?.volume }.let { if (it.isEmpty()) 50 else it.average().toInt() }
                VolumeCard(if (nodes.size <= 1) "Volume" else "Everyone's volume", avgVol) { v -> nodes.filter { it.online }.forEach { master.setVolume(it.node.id, v) } }
                if (nodes.size > 1) {
                    EyebrowText("Tablets")
                    nodes.forEach { n ->
                        val line = sync.stateLine(n.node.id)
                        val inSync = sync.inSync(n.node.id)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface).padding(PhoneMetrics.cardPadding),
                            verticalArrangement = Arrangement.spacedBy(PhoneMetrics.relatedGap),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
                                Avatar(n.node.name, n.node.color, n.node.avatar, 32.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(n.node.name, style = body(18.sp, 700, 1.25f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (inSync) Dot(C.online, 8.dp)
                                        Muted(line, 14)
                                    }
                                }
                                IconCircle(Lucide.X, { sync.remove(n.node.id) }, label = "Remove ${n.node.name}")
                            }
                            NickSlider(
                                (statuses[n.node.id]?.volume ?: 50) / 100f,
                                {}, { f -> master.setVolume(n.node.id, (f * 100).toInt()) },
                            )
                        }
                    }
                }
            }
        }
    }
}
