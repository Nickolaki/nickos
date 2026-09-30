package com.nbaltas.nickos.ui.master

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.X
import com.nbaltas.nickos.App
import com.nbaltas.nickos.jellyfin.JfItem
import com.nbaltas.nickos.jellyfin.JfUser
import com.nbaltas.nickos.jellyfin.Jellyfin
import com.nbaltas.nickos.ui.theme.Artwork
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.ProgressBar
import com.nbaltas.nickos.ui.theme.Segmented
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import kotlinx.coroutines.delay

private sealed interface Detail {
    data class Series(val id: String) : Detail
    data class Movie(val id: String) : Detail
    data class All(val title: String, val items: List<JfItem>, val landscape: Boolean) : Detail
}

/** Browse the tablet's Jellyfin user's library and play things on that tablet. */
@Composable
fun WatchTab(v: NodeView, onPlayed: () -> Unit, onSettings: () -> Unit) {
    val app = App.get(LocalContext.current)
    val account by app.store.jellyfin.collectAsState()
    val jf = remember(account) { app.master.jellyfin() }
    if (jf == null) {
        CenterMessage("Connect Jellyfin", "Add your Jellyfin server in Settings to browse shows and movies and play them on ${v.node.name}.") {
            NickButton("Open Settings", onSettings, style = BtnStyle.PRIMARY, big = true)
        }
        return
    }
    val uid = v.node.jfUserId
    if (uid == null) { UserPicker(v, jf); return }

    var detail by remember(v.node.id) { mutableStateOf<Detail?>(null) }
    var backTo by remember(v.node.id) { mutableStateOf<Detail?>(null) }
    BackHandler(enabled = detail != null) { detail = backTo.also { backTo = null } }

    val play: (JfItem, Boolean) -> Unit = { item, fromStart ->
        if (!v.online) app.master.toastOffline(v.node.name)
        else {
            app.master.playOn(v.node.id, item.id, if (fromStart) 0 else item.posTicks, item.displayTitle)
            onPlayed()
        }
    }
    val open: (JfItem) -> Unit = { item ->
        backTo = detail.takeIf { it is Detail.All }
        detail = when {
            item.isSeries -> Detail.Series(item.id)
            item.isEpisode && item.seriesId != null -> Detail.Series(item.seriesId)
            else -> Detail.Movie(item.id)
        }
    }

    when (val d = detail) {
        is Detail.Series -> SeriesDetail(v, jf, uid, d.id, onBack = { detail = backTo.also { backTo = null } }, play = play)
        is Detail.Movie -> MovieDetail(v, jf, uid, d.id, onBack = { detail = backTo.also { backTo = null } }, play = play)
        is Detail.All -> Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 26.dp)) {
            Chip(Modifier.clip(RoundedCornerShape(22.dp)).clickable { detail = null }) {
                Icon(Lucide.ChevronLeft, null, tint = C.text, modifier = Modifier.size(18.dp))
                Text("Library", style = body(16.sp, 600), color = C.text)
            }
            Title(d.title, 32, Modifier.padding(vertical = 18.dp))
            ItemGrid(d.items, jf, d.landscape, open)
        }
        null -> LibraryHome(v, jf, uid, open, onSeeAll = { t, items, land -> detail = Detail.All(t, items, land) })
    }
}

fun com.nbaltas.nickos.master.MasterRuntime.toastOffline(name: String) = say("$name is offline")

@Composable
private fun LibraryHome(v: NodeView, jf: Jellyfin, uid: String, open: (JfItem) -> Unit, onSeeAll: (String, List<JfItem>, Boolean) -> Unit) {
    var mode by remember(v.node.id) { mutableIntStateOf(0) }
    var query by remember(v.node.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
        Row(Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Segmented(listOf("Home" to null, "Movies" to null, "Shows" to null), mode, { mode = it; query = "" }, height = 52.dp)
            Muted("${v.node.jfUserName ?: "Their"}'s library", 16, weight = 600, maxLines = 1)
            Spacer(Modifier.weight(1f))
            SearchBox(query, { query = it }, Modifier.widthIn(max = 420.dp).weight(1f, fill = false))
        }
        when {
            query.isNotBlank() -> {
                val results by produceState<List<JfItem>?>(null, query) {
                    value = null
                    delay(350)
                    value = try { jf.search(uid, query.trim()) } catch (e: Exception) { emptyList() }
                }
                Box(Modifier.padding(horizontal = 40.dp)) {
                    when {
                        results == null -> Muted("Searching…", 18)
                        results!!.isEmpty() -> Muted("Nothing called \"$query\" in this library.", 18)
                        else -> ItemGrid(results!!, jf, false, open)
                    }
                }
            }
            mode == 1 -> LoadGrid(jf, uid, "Movie", open)
            mode == 2 -> LoadGrid(jf, uid, "Series", open)
            else -> HomeRows(jf, uid, open, onSeeAll)
        }
    }
}

@Composable
private fun SearchBox(q: String, onQ: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.height(62.dp).clip(RoundedCornerShape(31.dp)).background(C.surface).border(2.dp, C.line, RoundedCornerShape(31.dp)).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Lucide.Search, null, tint = C.muted, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text("Search shows and movies", style = body(19.sp), color = C.muted, maxLines = 1)
            BasicTextField(q, onQ, singleLine = true, textStyle = body(19.sp).copy(color = C.text), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth())
        }
        if (q.isNotEmpty()) Icon(Lucide.X, "Clear", tint = C.muted, modifier = Modifier.size(22.dp).clickable { onQ("") })
    }
}

@Composable
private fun HomeRows(jf: Jellyfin, uid: String, open: (JfItem) -> Unit, onSeeAll: (String, List<JfItem>, Boolean) -> Unit) {
    val data by produceState<List<Triple<String, List<JfItem>, Boolean>>?>(null, uid) {
        value = try {
            listOf(
                Triple("Continue Watching", jf.resume(uid, 20), true),
                Triple("Next Up", jf.nextUp(uid, 20), true),
                Triple("Movies", jf.library(uid, "Movie", "DateCreated", desc = true, limit = 30), false),
                Triple("Shows", jf.library(uid, "Series", "DateCreated", desc = true, limit = 30), false),
            ).filter { it.second.isNotEmpty() }
        } catch (e: Exception) { emptyList() }
    }
    val rows = data
    when {
        rows == null -> Muted("Loading the library…", 18, Modifier.padding(horizontal = 40.dp))
        rows.isEmpty() -> Muted("Couldn't load this library. Check the Jellyfin server in Settings.", 18, Modifier.padding(horizontal = 40.dp))
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            items(rows, key = { it.first }) { (title, items, landscape) ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Title(title, 26)
                        Text("See all", style = body(16.sp, 600), color = C.muted, modifier = Modifier.clickable { onSeeAll(title, items, landscape) })
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(items, key = { it.id }) { item ->
                            if (landscape) LandscapeCard(item, jf, title == "Next Up") { open(item) }
                            else PosterCard(item, jf) { open(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadGrid(jf: Jellyfin, uid: String, type: String, open: (JfItem) -> Unit) {
    val items by produceState<List<JfItem>?>(null, uid, type) {
        value = try { jf.library(uid, type, limit = 1000) } catch (e: Exception) { emptyList() }
    }
    Box(Modifier.padding(horizontal = 40.dp)) {
        if (items == null) Muted("Loading…", 18) else ItemGrid(items!!, jf, false, open)
    }
}

@Composable
private fun ItemGrid(items: List<JfItem>, jf: Jellyfin, landscape: Boolean, open: (JfItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (landscape) 264.dp else 150.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        items(items, key = { it.id }) { item ->
            if (landscape) LandscapeCard(item, jf, false) { open(item) } else PosterCard(item, jf) { open(item) }
        }
    }
}

@Composable
private fun LandscapeCard(item: JfItem, jf: Jellyfin, nextUp: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(264.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            Artwork(jf.landscape(item), item.displayTitle, Modifier.size(264.dp, 148.dp), radius = 18.dp, titleSize = 24)
            if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart).padding(12.dp), height = 6.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.displayTitle, style = body(18.sp, 700), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                nextUp && item.isEpisode -> "${item.epCode} — ${item.name}"
                item.isEpisode -> "${item.epCode} · ${minutesText(item.leftMin)} left"
                item.inProgress -> "${minutesText(item.leftMin)} left"
                else -> item.year?.toString() ?: ""
            }
            Muted(sub, 15, maxLines = 1)
        }
    }
}

@Composable
private fun PosterCard(item: JfItem, jf: Jellyfin, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick)) {
        Artwork(jf.poster(item, 450), item.displayTitle, Modifier.size(150.dp, 225.dp), radius = 16.dp, titleSize = 22)
        if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart).padding(10.dp), height = 6.dp)
    }
}

// ---------------- Series ----------------

@Composable
private fun SeriesDetail(v: NodeView, jf: Jellyfin, uid: String, id: String, onBack: () -> Unit, play: (JfItem, Boolean) -> Unit) {
    val series by produceState<JfItem?>(null, id) { value = try { jf.item(uid, id) } catch (e: Exception) { null } }
    // What the big button plays: the episode in progress, else Next Up.
    val target by produceState<JfItem?>(null, id) {
        value = try { jf.resume(uid, 1, parentId = id).firstOrNull() ?: jf.nextUp(uid, 1, seriesId = id).firstOrNull() } catch (e: Exception) { null }
    }
    val seasons by produceState<List<JfItem>>(emptyList(), id) { value = try { jf.seasons(uid, id) } catch (e: Exception) { emptyList() } }
    var season by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(target, seasons) { if (season == null && seasons.isNotEmpty()) season = target?.seasonId?.takeIf { s -> seasons.any { it.id == s } } ?: seasons.first().id }
    val episodes by produceState<List<JfItem>?>(null, id, season) {
        value = null
        val s = season ?: return@produceState
        value = try { jf.episodes(uid, id, seasonId = s) } catch (e: Exception) { emptyList() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 1000.dp
        val left: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                LibraryChip(onBack)
                val s = series
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Artwork(s?.let { jf.poster(it, 500) }, s?.name ?: "", Modifier.size(170.dp, 255.dp), radius = 18.dp, titleSize = 26)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Title(s?.name ?: "", 44, maxLines = 3)
                        Muted(listOfNotNull("Series", seasons.size.takeIf { it > 0 }?.let { "$it season${if (it == 1) "" else "s"}" }, s?.rating).joinToString(" · "), 17, weight = 600)
                        s?.overview?.let { Muted(it, 17, maxLines = 6) }
                    }
                }
                target?.let { t ->
                    val sub = if (t.inProgress) "${t.epCode} · ${minutesText(t.leftMin)} left" else "${t.epCode} · ${t.name}"
                    NickButton(
                        "${if (t.inProgress) "Resume" else "Play"} on ${v.node.name}", { play(t, false) },
                        Modifier.fillMaxWidth(), BtnStyle.PRIMARY, big = true, leading = Lucide.Play, sub = sub,
                    )
                    if (t.inProgress) NickButton("Play episode from start", { play(t, true) }, Modifier.fillMaxWidth().height(64.dp), leading = Lucide.RotateCcw)
                }
            }
        }
        val right: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (seasons.size > 1) {
                    Box(Modifier.fillMaxWidth()) {
                        LazyRow { item { Segmented(seasons.map { (it.name) to null }, seasons.indexOfFirst { it.id == season }.coerceAtLeast(0), { season = seasons[it].id }, height = 52.dp) } }
                    }
                }
                val eps = episodes
                if (eps == null) Muted("Loading episodes…", 18)
                else Column(Modifier.then(if (narrow) Modifier else Modifier.verticalScroll(rememberScrollState())), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    eps.forEach { e -> EpisodeRow(e, jf, highlight = e.id == target?.id && e.inProgress) { play(e, false) } }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 32.dp, top = 28.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                left(Modifier.width(470.dp).verticalScroll(rememberScrollState())); right(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EpisodeRow(e: JfItem, jf: Jellyfin, highlight: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(20.dp)).background(if (highlight) C.surface2 else Color.Transparent)
            .clickable(onClick = onClick).padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box {
            Artwork(jf.landscape(e, 300), "", Modifier.size(144.dp, 81.dp), radius = 12.dp, showTitle = false)
            when {
                e.played -> ProgressBar(1f, Modifier.align(Alignment.BottomStart).padding(8.dp), height = 5.dp, fill = Color.White.copy(alpha = 0.8f))
                e.inProgress -> ProgressBar(e.progress, Modifier.align(Alignment.BottomStart).padding(8.dp), height = 5.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${e.index?.let { "$it. " } ?: ""}${e.name}", style = body(19.sp, 700), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Muted(if (e.inProgress) "${minutesText(e.runMin)} · ${minutesText(e.leftMin)} left" else minutesText(e.runMin), 15)
        }
        when {
            e.inProgress -> Chip(height = 40.dp, bg = C.accent) {
                Icon(Lucide.Play, null, tint = C.onAccent, modifier = Modifier.size(14.dp))
                Text("Resume", style = body(16.sp, 700), color = C.onAccent)
            }
            e.played -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Lucide.Check, null, tint = C.muted, modifier = Modifier.size(18.dp))
                Text("Watched", style = body(15.sp, 600), color = C.muted)
            }
            else -> Box(Modifier.size(48.dp).clip(CircleShape).background(C.surface2), contentAlignment = Alignment.Center) {
                Icon(Lucide.Play, "Play", tint = C.text, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LibraryChip(onBack: () -> Unit) {
    Chip(Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onBack)) {
        Icon(Lucide.ChevronLeft, null, tint = C.text, modifier = Modifier.size(18.dp))
        Text("Library", style = body(16.sp, 600), color = C.text)
    }
}

// ---------------- Movie ----------------

@Composable
private fun MovieDetail(v: NodeView, jf: Jellyfin, uid: String, id: String, onBack: () -> Unit, play: (JfItem, Boolean) -> Unit) {
    val movie by produceState<JfItem?>(null, id) { value = try { jf.item(uid, id) } catch (e: Exception) { null } }
    val m = movie
    BoxWithConstraints(Modifier.fillMaxSize()) {
        m?.let { jf.backdrop(it) }?.let { AsyncImage(it, null, Modifier.fillMaxSize().alpha(0.45f), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.3f to C.bg, 1f to C.bg.copy(alpha = 0.6f))))
        val narrow = maxWidth < 1000.dp
        val posterCol: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LibraryChip(onBack)
                Artwork(m?.let { jf.poster(it, 630) }, m?.name ?: "", Modifier.size(if (narrow) 200.dp else 280.dp, if (narrow) 300.dp else 420.dp), radius = 22.dp, titleSize = 40)
            }
        }
        val info: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (m == null) { Muted("Loading…", 18); return@Column }
                Title(m.name, 72, lineHeight = 0.95f, maxLines = 3)
                Muted(listOfNotNull("Movie", m.rating, m.runMin.takeIf { it > 0 }?.let { minutesText(it) }).joinToString(" · "), 19, weight = 600)
                m.overview?.let { Text(it, style = body(20.sp, 500, 1.5f), color = if (C.isDark) Color(0xFFD5DAE3) else C.text, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                if (m.inProgress) Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressBar(m.progress, height = 8.dp)
                    Muted("${minutesText(m.leftMin)} left", 16, weight = 600)
                }
                Column(Modifier.padding(top = 12.dp).widthIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NickButton(
                        "${if (m.inProgress) "Resume" else "Play"} on ${v.node.name}", { play(m, false) }, Modifier.fillMaxWidth(), BtnStyle.PRIMARY,
                        big = true, leading = Lucide.Play, sub = if (m.inProgress) "from ${fmtTicks(m.posTicks)}" else null,
                    )
                    if (m.inProgress) NickButton("Play from start", { play(m, true) }, Modifier.fillMaxWidth(), big = true, leading = Lucide.RotateCcw)
                }
            }
        }
        if (narrow) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                posterCol(); info(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                posterCol(); info(Modifier.padding(top = 64.dp).widthIn(max = 560.dp))
            }
        }
    }
}

// ---------------- setup helpers ----------------

@Composable
private fun UserPicker(v: NodeView, jf: Jellyfin) {
    val app = App.get(LocalContext.current)
    val users by produceState<List<JfUser>?>(null) { value = try { jf.users() } catch (e: Exception) { emptyList() } }
    CenterMessage("Whose library is this?", "Pick the Jellyfin user ${v.node.name} is signed in as. Watch shows their shows and Continue Watching.") {
        val list = users
        when {
            list == null -> Muted("Loading users…", 18)
            list.isEmpty() -> Muted("Couldn't load users from Jellyfin.", 18)
            else -> Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface).clickable {
                            app.store.updateNode(v.node.id) { it.copy(jfUserId = u.id, jfUserName = u.name) }
                            app.master.refreshResume(v.node.id)
                        }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Avatar(u.name, 4, "initial", 44.dp)
                        Text(u.name, style = body(20.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                        Icon(Lucide.ChevronRight, null, tint = C.muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CenterMessage(title: String, text: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 80.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(112.dp).clip(RoundedCornerShape(31.dp)).background(C.surface2), contentAlignment = Alignment.Center) {
            Icon(Lucide.Server, null, tint = C.accent, modifier = Modifier.size(48.dp))
        }
        Title(title, 44, lineHeight = 1.05f)
        Muted(text, 20, Modifier.widthIn(max = 640.dp))
        content()
    }
}

