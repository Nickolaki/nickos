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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Send
import com.nbaltas.nickos.master.DlState
import com.nbaltas.nickos.master.JellyfinDownloads
import com.nbaltas.nickos.master.Progress
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
import com.nbaltas.nickos.ui.theme.Solid
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.ProgressBar
import com.nbaltas.nickos.ui.theme.Segmented
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import com.nbaltas.nickos.ui.theme.TabletParentMetrics
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalDensity
import com.nbaltas.nickos.ui.theme.PhoneMetrics
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.disp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.nbaltas.nickos.ui.node.screenNow
import kotlinx.coroutines.delay

private sealed interface Detail {
    data class Series(val id: String) : Detail
    data class Movie(val id: String) : Detail
    data class All(val title: String, val items: List<JfItem>, val landscape: Boolean) : Detail
}

private val DetailSaver = androidx.compose.runtime.saveable.Saver<Detail?, String>(
    save = { d -> when (d) { is Detail.Series -> "s${d.id}"; is Detail.Movie -> "m${d.id}"; is Detail.All -> "a${if (d.landscape) 1 else 0}${d.title}"; else -> "" } },
    restore = { s ->
        when {
            s.startsWith("s") -> Detail.Series(s.substring(1))
            s.startsWith("m") -> Detail.Movie(s.substring(1))
            s.startsWith("a") && s.length > 2 -> Detail.All(s.substring(2), emptyList(), s[1] == '1')
            else -> null
        }
    },
)

/** Browse the tablet's Jellyfin user's library and play things on that tablet. */
@Composable
fun WatchTab(v: NodeView, onPlayed: () -> Unit, onSettings: () -> Unit) {
    val app = App.get(LocalContext.current)
    val account by app.store.jellyfin.collectAsState()
    val jf = remember(account) { app.master.jellyfin() }
    if (jf == null) {
        CenterMessage("Connect Jellyfin", "Add your Jellyfin server in Settings to browse shows and movies and play them on ${v.node.name}.") {
            NickButton("Open Settings", onSettings, fullOnPhone(), style = BtnStyle.PRIMARY, big = bigButtons())
        }
        return
    }
    val uid = v.node.jfUserId
    if (uid == null) { UserPicker(v, jf); return }

    val play: (JfItem, Boolean) -> Unit = { item, fromStart ->
        if (!v.online) app.master.toastOffline(v.node.name)
        else {
            app.master.playOn(v.node.id, item.id, if (fromStart) 0 else item.posTicks, item.displayTitle)
            onPlayed()
        }
    }
    JellyfinBrowser(jf, uid, v.node.id, "${v.node.jfUserName ?: "Their"}'s library", onPick = null, v = v, play = play)
}

/**
 * The Jellyfin library: home rows, movies, shows, history, search and a show's episodes. With [onPick]
 * a tap on a movie or an episode hands it over instead of playing it. Without, [play] controls the
 * tablet [v]. [key] keeps the browsing place per tablet. [ownerLabel] is shown next to the tabs.
 */
@Composable
fun JellyfinBrowser(
    jf: Jellyfin, uid: String, key: String, ownerLabel: String?, onPick: ((JfItem) -> Unit)?,
    v: NodeView? = null, play: ((JfItem, Boolean) -> Unit)? = null,
) {
    // Saveable, so the Watch tab keeps its place when the parent visits another tab. A "See all" list is kept as its title and fetched again.
    var detail by rememberSaveable(key, stateSaver = DetailSaver) { mutableStateOf<Detail?>(null) }
    var backTo by rememberSaveable(key, stateSaver = DetailSaver) { mutableStateOf<Detail?>(null) }
    BackHandler(enabled = detail != null) { detail = backTo.also { backTo = null } }

    val doPlay: (JfItem, Boolean) -> Unit = { item, fromStart -> if (onPick != null) onPick(item) else play?.invoke(item, fromStart) }
    val open: (JfItem) -> Unit = { item ->
        if (onPick != null && !item.isSeries) onPick(item)
        else {
            backTo = detail.takeIf { it is Detail.All }
            detail = when {
                item.isSeries -> Detail.Series(item.id)
                item.isEpisode && item.seriesId != null -> Detail.Series(item.seriesId)
                else -> Detail.Movie(item.id)
            }
        }
    }

    when (val d = detail) {
        is Detail.Series -> SeriesDetail(v, onPick != null, jf, uid, d.id, onBack = { detail = backTo.also { backTo = null } }, play = doPlay)
        is Detail.Movie -> MovieDetail(v!!, jf, uid, d.id, onBack = { detail = backTo.also { backTo = null } }, play = doPlay)
        is Detail.All -> {
            val all by produceState(d.items, d.title) {
                if (d.items.isEmpty()) value = try { seeAllItems(jf, uid, d.title) } catch (e: Exception) { emptyList() }
            }
            if (LocalPhone.current) Column(Modifier.fillMaxSize().padding(start = PhoneMetrics.gutter, end = PhoneMetrics.gutter, top = PhoneMetrics.gutter), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
                LibraryChip { detail = null }
                Title(d.title, 32)
                ItemGrid(all, jf, d.landscape, open)
            } else if (LocalParentTablet.current) Column(Modifier.fillMaxSize().padding(top = TabletParentMetrics.contentTop), verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap)) {
                TabletBackChip("Library") { detail = null }
                Title(d.title, 32)
                ItemGrid(all, jf, d.landscape, open)
            } else Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 26.dp)) {
                Chip(Modifier.clip(RoundedCornerShape(22.dp)).clickable { detail = null }) {
                    Icon(Lucide.ChevronLeft, null, tint = C.text, modifier = Modifier.size(18.dp))
                    Text("Library", style = body(16.sp, 600), color = C.text)
                }
                Title(d.title, 32, Modifier.padding(vertical = 18.dp))
                ItemGrid(all, jf, d.landscape, open)
            }
        }
        null -> LibraryHome(key, ownerLabel, jf, uid, open, onSeeAll = { t, items, land -> detail = Detail.All(t, items, land) })
    }
}

/** The items behind a home row's "See all", fetched again after the state was restored. */
private suspend fun seeAllItems(jf: Jellyfin, uid: String, title: String): List<JfItem> = when (title) {
    "Continue Watching" -> jf.resume(uid, 20)
    "Next Up" -> jf.nextUp(uid, 20)
    "Movies" -> jf.library(uid, "Movie", "DateCreated", desc = true, limit = 30)
    "Shows" -> jf.library(uid, "Series", "DateCreated", desc = true, limit = 30)
    else -> emptyList()
}

fun com.nbaltas.nickos.master.MasterRuntime.toastOffline(name: String) = say("$name is offline")

@Composable
private fun LibraryHome(key: String, ownerLabel: String?, jf: Jellyfin, uid: String, open: (JfItem) -> Unit, onSeeAll: (String, List<JfItem>, Boolean) -> Unit) {
    var mode by rememberSaveable(key) { mutableIntStateOf(0) }
    var query by rememberSaveable(key) { mutableStateOf("") }
    val parentPhone = LocalPhone.current
    val edge = if (parentPhone) PhoneMetrics.gutter else 40.dp
    if (LocalParentTablet.current) {
        // The page is already in the content cap: no side padding here. The categories are one group (Home / Movies / Shows / History);
        // search is 52 high and at most 320, on the right from I = 960 and a full-width row of its own below that.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth.value >= 960f
            val categories: @Composable () -> Unit = {
                Segmented(listOf("Home" to null, "Movies" to null, "Shows" to null, "History" to null), mode, { mode = it; query = "" })
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap)) {
                ownerLabel?.let { Muted(it, 14, weight = 600, maxLines = 2) }
                if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    categories()
                    Spacer(Modifier.weight(1f))
                    SearchBox(query, { query = it }, Modifier.width(320.dp))
                } else {
                    categories()
                    SearchBox(query, { query = it }, Modifier.fillMaxWidth())
                }
                when {
                    query.isNotBlank() -> {
                        val results by produceState<List<JfItem>?>(null, query) {
                            value = null
                            delay(350)
                            value = try { jf.search(uid, query.trim()) } catch (e: Exception) { emptyList() }
                        }
                        when {
                            results == null -> Muted("Searching…", 18)
                            results!!.isEmpty() -> Muted("Nothing called \"$query\" in this library.", 18)
                            else -> ItemGrid(results!!, jf, false, open)
                        }
                    }
                    mode == 1 -> LoadGrid(jf, uid, "Movie", open)
                    mode == 2 -> LoadGrid(jf, uid, "Series", open)
                    mode == 3 -> HistoryList(jf, uid, open)
                    else -> HomeRows(jf, uid, open, onSeeAll)
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(top = if (parentPhone) 12.dp else 26.dp), verticalArrangement = Arrangement.spacedBy(if (parentPhone) PhoneMetrics.cardGap else 26.dp)) {
        // Phone width: the tabs fill the row, so search gets a full-width row of its own.
        val phone = LocalConfiguration.current.screenWidthDp < 600
        if (parentPhone) {
            // Four equal tabs across the full width, History included; whose library it is goes on its own line.
            ownerLabel?.let { Muted(it, 14, Modifier.padding(horizontal = edge), weight = 600, maxLines = 1) }
            Segmented(listOf("Home" to null, "Movies" to null, "Shows" to null, "History" to null), mode, { mode = it; query = "" }, Modifier.padding(horizontal = edge).fillMaxWidth())
        } else Row(Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Segmented(listOf("Home" to null, "Movies" to null, "Shows" to null, "History" to null), mode, { mode = it; query = "" }, height = 52.dp)
            if (ownerLabel != null) Muted(ownerLabel, 16, weight = 600, maxLines = 1)
            if (!phone) {
                Spacer(Modifier.weight(1f))
                SearchBox(query, { query = it }, Modifier.widthIn(max = 420.dp).weight(1f, fill = false))
            }
        }
        if (parentPhone) SearchBox(query, { query = it }, Modifier.padding(horizontal = edge).fillMaxWidth())
        else if (phone) SearchBox(query, { query = it }, Modifier.padding(horizontal = 40.dp).fillMaxWidth())
        when {
            query.isNotBlank() -> {
                val results by produceState<List<JfItem>?>(null, query) {
                    value = null
                    delay(350)
                    value = try { jf.search(uid, query.trim()) } catch (e: Exception) { emptyList() }
                }
                Box(Modifier.padding(horizontal = edge)) {
                    when {
                        results == null -> Muted("Searching…", 18)
                        results!!.isEmpty() -> Muted("Nothing called \"$query\" in this library.", 18)
                        else -> ItemGrid(results!!, jf, false, open)
                    }
                }
            }
            mode == 1 -> LoadGrid(jf, uid, "Movie", open)
            mode == 2 -> LoadGrid(jf, uid, "Series", open)
            mode == 3 -> HistoryList(jf, uid, open)
            else -> HomeRows(jf, uid, open, onSeeAll)
        }
    }
}

@Composable
private fun SearchBox(q: String, onQ: (String) -> Unit, modifier: Modifier) {
    val phone = LocalPhone.current
    if (LocalParentTablet.current) {
        // 52 high (at least), radius 14, 1dp border, 16sp text; the clear glyph (20) sits in a 48x48 target.
        val shape = RoundedCornerShape(TabletParentMetrics.fieldRadius)
        Row(
            modifier.heightIn(min = TabletParentMetrics.fieldHeight).clip(shape).background(C.surface).border(1.dp, C.line, shape)
                .padding(start = 14.dp, end = if (q.isNotEmpty()) 2.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Lucide.Search, null, tint = C.muted, modifier = Modifier.size(TabletParentMetrics.iconSize))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Search shows and movies", style = body(16.sp, 500, 22f / 16), color = C.muted, maxLines = 1)
                BasicTextField(q, onQ, singleLine = true, textStyle = body(16.sp, 500, 22f / 16).copy(color = C.text), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth())
            }
            if (q.isNotEmpty()) Box(Modifier.size(TabletParentMetrics.minTarget).clip(CircleShape).clickable(role = Role.Button) { onQ("") }, contentAlignment = Alignment.Center) {
                Icon(Lucide.X, "Clear", tint = C.muted, modifier = Modifier.size(TabletParentMetrics.iconSize))
            }
        }
        return
    }
    val h = if (phone) 52.dp else 62.dp
    val text = if (phone) 16.sp else 19.sp
    Row(
        modifier.height(h).clip(RoundedCornerShape(h / 2)).background(C.surface).border(2.dp, C.line, RoundedCornerShape(h / 2)).padding(start = if (phone) 16.dp else 22.dp, end = if (phone) 4.dp else 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Lucide.Search, null, tint = C.muted, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text("Search shows and movies", style = body(text), color = C.muted, maxLines = 1)
            BasicTextField(q, onQ, singleLine = true, textStyle = body(text).copy(color = C.text), cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth())
        }
        if (q.isNotEmpty()) {
            if (phone) Box(Modifier.size(PhoneMetrics.minTarget).clip(CircleShape).clickable(role = Role.Button) { onQ("") }, contentAlignment = Alignment.Center) {
                Icon(Lucide.X, "Clear", tint = C.muted, modifier = Modifier.size(22.dp))
            } else Icon(Lucide.X, "Clear", tint = C.muted, modifier = Modifier.size(22.dp).clickable { onQ("") })
        }
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
    val phone = LocalPhone.current
    val edge = if (phone) PhoneMetrics.gutter else 40.dp
    when {
        rows == null -> Muted("Loading the library…", 18, Modifier.padding(horizontal = edge))
        rows.isEmpty() -> Muted("Couldn't load this library. Check the Jellyfin server in Settings.", 18, Modifier.padding(horizontal = edge))
        LocalParentTablet.current -> LazyColumn(contentPadding = PaddingValues(bottom = TabletParentMetrics.contentBottom), verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.sectionGap)) {
            items(rows, key = { it.first }) { (title, items, landscape) ->
                Column(verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap)) {
                    // Section title 20/24 (Title maps 26 to it); "See all" is a 48dp target with a 14sp label.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Title(title, 26, Modifier.weight(1f), maxLines = 1)
                        Box(
                            Modifier.heightIn(min = TabletParentMetrics.minTarget).clip(RoundedCornerShape(TabletParentMetrics.nestedRadius))
                                .clickable(role = Role.Button) { onSeeAll(title, items, landscape) }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("See all", style = body(14.sp, 700, 20f / 14), color = C.muted) }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(items, key = { it.id }) { item -> JfArtCard(item, jf, landscape, if (landscape) 200.dp else 144.dp, title == "Next Up") { open(item) } }
                    }
                }
            }
        }
        else -> LazyColumn(contentPadding = PaddingValues(bottom = if (phone) 24.dp else 32.dp), verticalArrangement = Arrangement.spacedBy(if (phone) PhoneMetrics.sectionGap else 26.dp)) {
            items(rows, key = { it.first }) { (title, items, landscape) ->
                Column(verticalArrangement = Arrangement.spacedBy(if (phone) PhoneMetrics.cardGap else 14.dp)) {
                    if (phone) Row(Modifier.padding(horizontal = edge), verticalAlignment = Alignment.CenterVertically) {
                        // The heading is 20; "See all" is a 48dp-high target at the end of the row.
                        Text(title, style = disp(20.sp, 800, 1.15f), color = C.text, modifier = Modifier.weight(1f))
                        Box(Modifier.heightIn(min = PhoneMetrics.minTarget).clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).clickable(role = Role.Button) { onSeeAll(title, items, landscape) }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text("See all", style = body(14.sp, 700), color = C.muted)
                        }
                    } else Row(Modifier.padding(horizontal = 40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Title(title, 26)
                        Text("See all", style = body(16.sp, 600), color = C.muted, modifier = Modifier.clickable { onSeeAll(title, items, landscape) })
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = edge), horizontalArrangement = Arrangement.spacedBy(if (phone) PhoneMetrics.cardGap else 20.dp)) {
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
    Box(Modifier.padding(horizontal = if (LocalPhone.current) PhoneMetrics.gutter else if (LocalParentTablet.current) 0.dp else 40.dp)) {
        if (items == null) Muted("Loading…", 18) else ItemGrid(items!!, jf, false, open)
    }
}

/** What a compact phone row says under the title: the episode, or the year and length. */
private fun phoneMeta(item: JfItem): String = when {
    item.isEpisode -> "${item.epCode} · ${item.name}"
    item.isSeries -> listOfNotNull("Series", item.year?.toString()).joinToString(" · ")
    else -> listOfNotNull(item.year?.toString(), item.runMin.takeIf { it > 0 }?.let { minutesText(it) }).joinToString(" · ")
}

@Composable
private fun ItemGrid(items: List<JfItem>, jf: Jellyfin, landscape: Boolean, open: (JfItem) -> Unit) {
    if (LocalPhone.current) {
        // Compact rows: a 112x72 picture with a weighted title and a meta line, so a screen shows many titles.
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = { it.id }) { item ->
                PhoneMediaRow(
                    art = { m ->
                        Box(m.clip(RoundedCornerShape(PhoneMetrics.nestedRadius))) {
                            Artwork(jf.landscape(item, 300), item.displayTitle, Modifier.fillMaxSize(), radius = PhoneMetrics.nestedRadius, showTitle = false)
                            if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
                        }
                    },
                    title = item.displayTitle, onClick = { open(item) },
                ) { Muted(phoneMeta(item), 14) }
            }
        }
        return
    }
    if (LocalParentTablet.current) {
        // Whole columns of 200-260 landscape or 136-176 poster art; a short row keeps its width instead of stretching.
        if (landscape) TabletArtGrid(items, key = { it.id }, minArt = 200f, maxArt = 260f) { item, w -> JfArtCard(item, jf, true, w, false) { open(item) } }
        else TabletArtGrid(items, key = { it.id }, minArt = 136f, maxArt = 176f) { item, w -> JfArtCard(item, jf, false, w, false) { open(item) } }
        return
    }
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
    if (LocalPhone.current) {
        // 200x112. The progress bar is the bottom 4dp of the picture and the title is under it, so the bar never covers text.
        Column(Modifier.width(200.dp).clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(200.dp, 112.dp).clip(RoundedCornerShape(PhoneMetrics.nestedRadius))) {
                Artwork(jf.landscape(item), item.displayTitle, Modifier.fillMaxSize(), radius = PhoneMetrics.nestedRadius, showTitle = false)
                if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.displayTitle, style = body(16.sp, 700, 1.25f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = when {
                    nextUp && item.isEpisode -> "${item.epCode} · ${item.name}"
                    item.isEpisode -> "${item.epCode} · ${minutesText(item.leftMin)} left"
                    item.inProgress -> "${minutesText(item.leftMin)} left"
                    else -> item.year?.toString() ?: ""
                }
                Muted(sub, 14, maxLines = 2)
            }
        }
        return
    }
    Column(Modifier.width(264.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            Artwork(jf.landscape(item), item.displayTitle, Modifier.size(264.dp, 148.dp), radius = 18.dp, titleSize = 24)
            if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart).padding(12.dp), height = 6.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.displayTitle, style = body(18.sp, 700), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                nextUp && item.isEpisode -> "${item.epCode} · ${item.name}"
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
    if (LocalPhone.current) {
        // Smaller poster; the bar is the bottom 4dp of the picture, below the title the placeholder art draws.
        Box(Modifier.size(120.dp, 180.dp).clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).clickable(role = Role.Button, onClick = onClick)) {
            Artwork(jf.poster(item, 450), item.displayTitle, Modifier.fillMaxSize(), radius = PhoneMetrics.nestedRadius, titleSize = 18)
            if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
        }
        return
    }
    Box(Modifier.size(150.dp, 225.dp).clickable(onClick = onClick)) {
        Artwork(jf.poster(item, 450), item.displayTitle, Modifier.size(150.dp, 225.dp), radius = 16.dp, titleSize = 22)
        if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart).padding(10.dp), height = 6.dp)
    }
}

/**
 * A Jellyfin picture card on a parent tablet: art [w] wide (16:9, or 2:3 for a poster) and, for landscape art, the title and a line
 * under it. The title is never drawn on the picture as well.
 */
@Composable
private fun JfArtCard(item: JfItem, jf: Jellyfin, landscape: Boolean, w: androidx.compose.ui.unit.Dp, nextUp: Boolean, onClick: () -> Unit) {
    val radius = TabletParentMetrics.mediaRadius
    if (!landscape) {
        Box(Modifier.size(w, w * 1.5f).clip(RoundedCornerShape(radius)).clickable(role = Role.Button, onClick = onClick)) {
            Artwork(jf.poster(item, 450), item.displayTitle, Modifier.fillMaxSize(), radius = radius)
            if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
        }
        return
    }
    Column(Modifier.width(w).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(radius))) {
            Artwork(jf.landscape(item), item.displayTitle, Modifier.fillMaxSize(), radius = radius, showTitle = false)
            if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.displayTitle, style = body(16.sp, 700, 22f / 16), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = when {
                nextUp && item.isEpisode -> "${item.epCode} · ${item.name}"
                item.isEpisode -> "${item.epCode} · ${minutesText(item.leftMin)} left"
                item.inProgress -> "${minutesText(item.leftMin)} left"
                else -> item.year?.toString() ?: ""
            }
            Muted(sub, 14, maxLines = 2)
        }
    }
}

// ---------------- History ----------------

@Composable
private fun HistoryList(jf: Jellyfin, uid: String, open: (JfItem) -> Unit) {
    val items by produceState<List<JfItem>?>(null, uid) { value = try { jf.history(uid) } catch (e: Exception) { emptyList() } }
    val list = items
    val phone = LocalPhone.current
    when {
        list == null -> Muted("Loading…", 18, Modifier.padding(horizontal = if (phone) PhoneMetrics.gutter else 40.dp))
        list.isEmpty() -> Muted("Nothing watched yet.", 18, Modifier.padding(horizontal = if (phone) PhoneMetrics.gutter else 40.dp))
        LocalParentTablet.current -> {
            // 112x63 art, 16/22 title, 14/20 status, rows at least 88 high with 12 padding, 48dp actions.
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.Instant.ofEpochMilli(screenNow()).atZone(zone).toLocalDate()
            fun whenOf(i: JfItem) = try { java.time.Instant.parse(i.lastPlayed).atZone(zone) } catch (e: Exception) { null }
            val groups = list.groupBy { whenOf(it)?.toLocalDate() }
            LazyColumn(contentPadding = PaddingValues(bottom = TabletParentMetrics.contentBottom), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                groups.forEach { (day, dayItems) ->
                    item(key = "h-$day") {
                        val label = when (day) {
                            null -> "Earlier"
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM"))
                        }
                        Title(label, 24, Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(dayItems, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(TabletParentMetrics.mediaRadius))
                                .clickable(role = Role.Button) { open(item) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(TabletParentMetrics.nestedRadius))) {
                                Artwork(jf.landscape(item, 300), item.displayTitle, Modifier.fillMaxSize(), radius = TabletParentMetrics.nestedRadius, showTitle = false)
                                if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.displayTitle, style = body(16.sp, 700, 22f / 16), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Muted(if (item.isEpisode) episodeLine(item) else listOfNotNull("Movie", item.year?.toString()).joinToString(" · "), 14, maxLines = 2)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                whenOf(item)?.let { Text(it.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), style = body(14.sp, 700, 20f / 14), color = C.text) }
                                Muted(if (item.inProgress) "${minutesText(item.leftMin)} left" else "Watched", 14)
                            }
                        }
                    }
                }
            }
        }
        phone -> {
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.Instant.ofEpochMilli(screenNow()).atZone(zone).toLocalDate()
            fun whenOf(i: JfItem) = try { java.time.Instant.parse(i.lastPlayed).atZone(zone) } catch (e: Exception) { null }
            val groups = list.groupBy { whenOf(it)?.toLocalDate() }
            LazyColumn(contentPadding = PaddingValues(start = PhoneMetrics.gutter, end = PhoneMetrics.gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                groups.forEach { (day, dayItems) ->
                    item(key = "h-$day") {
                        val label = when (day) {
                            null -> "Earlier"
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM"))
                        }
                        Text(label, style = disp(20.sp, 800, 1.15f), color = C.text, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(dayItems, key = { it.id }) { item ->
                        PhoneMediaRow(
                            art = { m ->
                                Box(m.clip(RoundedCornerShape(PhoneMetrics.nestedRadius))) {
                                    Artwork(jf.landscape(item, 300), item.displayTitle, Modifier.fillMaxSize(), radius = PhoneMetrics.nestedRadius, showTitle = false)
                                    if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
                                }
                            },
                            title = item.displayTitle, onClick = { open(item) },
                        ) {
                            Muted(if (item.isEpisode) episodeLine(item) else listOfNotNull("Movie", item.year?.toString()).joinToString(" · "), 14)
                            val at = whenOf(item)?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                            Muted(listOfNotNull(at, if (item.inProgress) "${minutesText(item.leftMin)} left" else "Watched").joinToString(" · "), 14)
                        }
                    }
                }
            }
        }
        else -> {
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.Instant.ofEpochMilli(screenNow()).atZone(zone).toLocalDate()
            fun whenOf(i: JfItem) = try { java.time.Instant.parse(i.lastPlayed).atZone(zone) } catch (e: Exception) { null }
            val groups = list.groupBy { whenOf(it)?.toLocalDate() }
            LazyColumn(contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { (day, dayItems) ->
                    item(key = "h-$day") {
                        val label = when (day) {
                            null -> "Earlier"
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM"))
                        }
                        Title(label, 24, Modifier.padding(top = 14.dp, bottom = 6.dp))
                    }
                    items(dayItems, key = { it.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { open(item) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            // Sized: the progress bar fills its width and would stretch an unsized Box across the row.
                            Box(Modifier.size(144.dp, 81.dp)) {
                                Artwork(jf.landscape(item, 300), item.displayTitle, Modifier.size(144.dp, 81.dp), radius = 12.dp, titleSize = 14)
                                if (item.inProgress) ProgressBar(item.progress, Modifier.align(Alignment.BottomStart).padding(8.dp), height = 5.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.displayTitle, style = body(19.sp, 700), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Muted(if (item.isEpisode) episodeLine(item) else listOfNotNull("Movie", item.year?.toString()).joinToString(" · "), 15, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                whenOf(item)?.let { Text(it.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), style = body(16.sp, 700), color = C.text) }
                                Muted(if (item.inProgress) "${minutesText(item.leftMin)} left" else "Watched", 15)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Series ----------------

@Composable
private fun SeriesDetail(v: NodeView?, pick: Boolean, jf: Jellyfin, uid: String, id: String, onBack: () -> Unit, play: (JfItem, Boolean) -> Unit) {
    val series by produceState<JfItem?>(null, id) { value = try { jf.item(uid, id) } catch (e: Exception) { null } }
    // What the big button plays: the episode in progress, else Next Up.
    val target by produceState<JfItem?>(null, id) {
        value = try { jf.resume(uid, 1, parentId = id).firstOrNull() ?: jf.nextUp(uid, 1, seriesId = id).firstOrNull() } catch (e: Exception) { null }
    }
    val seasons by produceState<List<JfItem>>(emptyList(), id) { value = try { jf.seasons(uid, id) } catch (e: Exception) { emptyList() } }
    var season by rememberSaveable(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(target, seasons) { if (season == null && seasons.isNotEmpty()) season = target?.seasonId?.takeIf { s -> seasons.any { it.id == s } } ?: seasons.first().id }
    val episodes by produceState<List<JfItem>?>(null, id, season) {
        value = null
        val s = season ?: return@produceState
        value = try { jf.episodes(uid, id, seasonId = s) } catch (e: Exception) { emptyList() }
    }

    val app = App.get(LocalContext.current)
    val downloads = app.master.jfDownloads
    val dl by downloads.progress.collectAsState()
    val videosMap by app.master.sync.videos.collectAsState()
    val lib = remember(videosMap) { app.master.sync.library() }

    if (LocalPhone.current) {
        var expanded by rememberSaveable(id) { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PhoneMetrics.gutter), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
            LibraryChip(onBack)
            val s = series
            Row(horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.gutter)) {
                Artwork(s?.let { jf.poster(it, 500) }, s?.name ?: "", Modifier.size(96.dp, 144.dp), radius = PhoneMetrics.nestedRadius, showTitle = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PhoneMediaTitle(s?.name ?: "")
                    Muted(listOfNotNull("Series", seasons.size.takeIf { it > 0 }?.let { "$it season${if (it == 1) "" else "s"}" }, s?.rating).joinToString(" · "), 14, weight = 600)
                }
            }
            target?.let { t ->
                val sub = if (t.inProgress) "${t.epCode} · ${minutesText(t.leftMin)} left" else "${t.epCode} · ${t.name}"
                NickButton(
                    if (pick) "Pick this episode" else "${if (t.inProgress) "Resume" else "Play"} on ${v?.node?.name}", { play(t, false) },
                    Modifier.fillMaxWidth(), BtnStyle.PRIMARY, leading = Solid.Play, sub = sub,
                )
                if (t.inProgress && !pick) NickButton("Play episode from start", { play(t, true) }, Modifier.fillMaxWidth(), leading = Lucide.RotateCcw)
            }
            s?.overview?.let {
                Text(
                    it, style = body(16.sp, 500, 1.4f), color = if (C.isDark) Color(0xFFD5DAE3) else C.text,
                    maxLines = if (expanded) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                )
            }
            if (seasons.size > 1) {
                // Scrollable 48dp season tabs.
                Row(Modifier.fillMaxWidth().selectableGroup().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { se ->
                        val on = se.id == season
                        Box(
                            Modifier.heightIn(min = PhoneMetrics.minTarget).clip(RoundedCornerShape(PhoneMetrics.fieldRadius)).background(if (on) C.accent else C.surface)
                                .selectable(selected = on, role = Role.Tab) { season = se.id }.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(se.name, style = body(14.sp, 700), color = if (on) C.onAccent else C.muted, maxLines = 1) }
                    }
                }
            }
            val eps = episodes
            if (eps == null) Muted("Loading episodes…", 16)
            else {
                val todo = eps.filter { e -> !JellyfinDownloads.onTablet(e, lib) && dl[e.id]?.let { it.active || it.state == DlState.DONE } != true }
                if (todo.isNotEmpty() && !pick) NickButton(
                    "Save season", { downloads.saveAll(todo) }, Modifier.fillMaxWidth(), leading = Lucide.Download,
                    sub = "${todo.size} episode${if (todo.size == 1) "" else "s"} to this tablet",
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    eps.forEach { e ->
                        EpisodeRow(
                            e, jf, highlight = e.id == target?.id && e.inProgress,
                            dl = dl[e.id], onTablet = JellyfinDownloads.onTablet(e, lib),
                            save = { downloads.save(e) }, cancel = { downloads.cancel(e.id) }, showSave = !pick,
                        ) { play(e, false) }
                    }
                }
            }
        }
        return
    }

    if (LocalParentTablet.current) {
        // Two panes from I = 960 at normal font scale (a 320 pane with the art, title and the big button; the episodes fill the rest);
        // below that, one scrolling page. The page is already inside the content cap.
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val two = maxWidth.value >= 960f && fontScale < 1.5f
            val seasonsLine = listOfNotNull("Series", seasons.size.takeIf { it > 0 }?.let { "$it season${if (it == 1) "" else "s"}" }, series?.rating).joinToString(" · ")
            val left: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TabletBackChip("Library", onBack)
                    val s = series
                    val art: @Composable () -> Unit = { Artwork(s?.let { jf.poster(it, 500) }, s?.name ?: "", Modifier.size(160.dp, 240.dp), radius = TabletParentMetrics.mediaRadius, showTitle = false) }
                    val titles: @Composable (Modifier) -> Unit = { m ->
                        Column(m, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.labelGap)) {
                            Title(s?.name ?: "", 44, maxLines = 3)
                            Muted(seasonsLine, 17, weight = 600)
                            s?.overview?.let { Muted(it, 16, Modifier.widthIn(max = 560.dp), maxLines = 6) }
                        }
                    }
                    if (two) { art(); titles(Modifier.fillMaxWidth()) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { art(); titles(Modifier.weight(1f)) }
                    target?.let { t ->
                        val sub = if (t.inProgress) "${t.epCode} · ${minutesText(t.leftMin)} left" else "${t.epCode} · ${t.name}"
                        Column(Modifier.widthIn(max = 400.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NickButton(
                                if (pick) "Pick this episode" else "${if (t.inProgress) "Resume" else "Play"} on ${v?.node?.name}", { play(t, false) },
                                Modifier.fillMaxWidth(), BtnStyle.PRIMARY, leading = Solid.Play, sub = sub,
                            )
                            if (t.inProgress && !pick) NickButton("Play episode from start", { play(t, true) }, Modifier.fillMaxWidth(), leading = Lucide.RotateCcw)
                        }
                    }
                }
            }
            val right: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap)) {
                    if (seasons.size > 1) {
                        Box(Modifier.fillMaxWidth()) {
                            LazyRow { item { Segmented(seasons.map { (it.name) to null }, seasons.indexOfFirst { it.id == season }.coerceAtLeast(0), { season = seasons[it].id }) } }
                        }
                    }
                    val eps = episodes
                    if (eps == null) Muted("Loading episodes…", 18)
                    else Column(Modifier.then(if (two) Modifier.verticalScroll(rememberScrollState()) else Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val todo = eps.filter { e -> !JellyfinDownloads.onTablet(e, lib) && dl[e.id]?.let { it.active || it.state == DlState.DONE } != true }
                        if (todo.isNotEmpty() && !pick) NickButton(
                            "Save season", { downloads.saveAll(todo) }, Modifier.widthIn(max = 320.dp).padding(bottom = 8.dp), leading = Lucide.Download,
                            sub = "${todo.size} episode${if (todo.size == 1) "" else "s"} to this tablet",
                        )
                        eps.forEach { e ->
                            EpisodeRow(
                                e, jf, highlight = e.id == target?.id && e.inProgress,
                                dl = dl[e.id], onTablet = JellyfinDownloads.onTablet(e, lib),
                                save = { downloads.save(e) }, cancel = { downloads.cancel(e.id) }, showSave = !pick,
                            ) { play(e, false) }
                        }
                        Spacer(Modifier.height(TabletParentMetrics.contentBottom))
                    }
                }
            }
            if (two) Row(Modifier.fillMaxSize().padding(top = TabletParentMetrics.contentTop), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                left(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())); right(Modifier.weight(1f).fillMaxHeight())
            } else Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
                verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.sectionGap),
            ) { left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth()) }
        }
        return
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
                        if (pick) "Pick this episode" else "${if (t.inProgress) "Resume" else "Play"} on ${v?.node?.name}", { play(t, false) },
                        Modifier.fillMaxWidth(), BtnStyle.PRIMARY, big = bigButtons(), leading = Solid.Play, sub = sub,
                    )
                    if (t.inProgress && !pick) NickButton("Play episode from start", { play(t, true) }, Modifier.fillMaxWidth().height(64.dp), leading = Lucide.RotateCcw)
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
                    val todo = eps.filter { e -> !JellyfinDownloads.onTablet(e, lib) && dl[e.id]?.let { it.active || it.state == DlState.DONE } != true }
                    if (todo.isNotEmpty() && !pick) NickButton(
                        "Save season", { downloads.saveAll(todo) }, Modifier.widthIn(max = 320.dp), leading = Lucide.Download,
                        sub = "${todo.size} episode${if (todo.size == 1) "" else "s"} to this tablet",
                    )
                    eps.forEach { e ->
                        EpisodeRow(
                            e, jf, highlight = e.id == target?.id && e.inProgress,
                            dl = dl[e.id], onTablet = JellyfinDownloads.onTablet(e, lib),
                            save = { downloads.save(e) }, cancel = { downloads.cancel(e.id) }, showSave = !pick,
                        ) { play(e, false) }
                    }
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
private fun EpisodeRow(
    e: JfItem, jf: Jellyfin, highlight: Boolean, dl: Progress?, onTablet: Boolean, save: () -> Unit, cancel: () -> Unit, showSave: Boolean = true, onClick: () -> Unit,
) {
    if (LocalPhone.current) {
        // A 96x54 picture, the title and its lines weighted in the middle, a 48dp play circle at the end. Saving sits under the text.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(PhoneMetrics.nestedRadius)).background(if (highlight) C.surface2 else Color.Transparent)
                .clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap),
        ) {
            Box(Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(8.dp))) {
                Artwork(jf.landscape(e, 300), "", Modifier.fillMaxSize(), radius = 8.dp, showTitle = false)
                when {
                    e.played -> ProgressBar(1f, Modifier.align(Alignment.BottomStart), height = 4.dp, fill = Color.White.copy(alpha = 0.8f))
                    e.inProgress -> ProgressBar(e.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${e.index?.let { "$it. " } ?: ""}${e.name}", style = body(16.sp, 700, 1.25f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Muted(if (e.inProgress) "${minutesText(e.runMin)} · ${minutesText(e.leftMin)} left" else if (e.played) "${minutesText(e.runMin)} · Watched" else minutesText(e.runMin), 14)
                if (showSave) Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        dl?.active == true -> {
                            Text(if (dl.total > 0) "Saving ${(dl.fraction * 100).toInt()}%" else "Saving", style = body(14.sp, 600).copy(fontFeatureSettings = "tnum"), color = C.muted, modifier = Modifier.weight(1f))
                            Box(Modifier.size(PhoneMetrics.minTarget).clip(CircleShape).clickable(role = Role.Button, onClick = cancel), contentAlignment = Alignment.Center) {
                                Icon(Lucide.X, "Cancel saving", tint = C.text, modifier = Modifier.size(20.dp))
                            }
                        }
                        onTablet || dl?.state == DlState.DONE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Lucide.Check, null, tint = C.accent, modifier = Modifier.size(18.dp))
                            Text("On this tablet", style = body(14.sp, 600), color = C.muted)
                        }
                        else -> {
                            Text(if (dl?.state == DlState.FAILED) "Couldn't save" else "Save to this tablet", style = body(14.sp, 600), color = C.muted, modifier = Modifier.weight(1f))
                            Box(Modifier.size(PhoneMetrics.minTarget).clip(CircleShape).clickable(role = Role.Button, onClick = save), contentAlignment = Alignment.Center) {
                                Icon(Lucide.Download, "Save to this tablet", tint = C.text, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            Box(Modifier.size(PhoneMetrics.minTarget).clip(CircleShape).background(if (e.inProgress) C.accent else C.surface2), contentAlignment = Alignment.Center) {
                Icon(Solid.Play, if (e.inProgress) "Resume" else "Play", tint = if (e.inProgress) C.onAccent else C.text, modifier = Modifier.size(18.dp))
            }
        }
        return
    }
    if (LocalParentTablet.current) {
        // 112x63 art, 16/22 title, 14/20 lines, at least 88 high, 12 padding and gaps, 48dp actions.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(TabletParentMetrics.mediaRadius)).background(if (highlight) C.surface2 else Color.Transparent)
                .clickable(role = Role.Button, onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(112.dp, 63.dp).clip(RoundedCornerShape(TabletParentMetrics.nestedRadius))) {
                Artwork(jf.landscape(e, 300), "", Modifier.fillMaxSize(), radius = TabletParentMetrics.nestedRadius, showTitle = false)
                when {
                    e.played -> ProgressBar(1f, Modifier.align(Alignment.BottomStart), height = 4.dp, fill = Color.White.copy(alpha = 0.8f))
                    e.inProgress -> ProgressBar(e.progress, Modifier.align(Alignment.BottomStart), height = 4.dp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${e.index?.let { "$it. " } ?: ""}${e.name}", style = body(16.sp, 700, 22f / 16), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Muted(if (e.inProgress) "${minutesText(e.runMin)} · ${minutesText(e.leftMin)} left" else if (e.played) "${minutesText(e.runMin)} · Watched" else minutesText(e.runMin), 14)
            }
            if (showSave) when {
                dl?.active == true -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (dl.total > 0) "${(dl.fraction * 100).toInt()}%" else "Saving", style = body(14.sp, 700, 20f / 14).copy(fontFeatureSettings = "tnum"), color = C.muted)
                    Box(Modifier.size(TabletParentMetrics.minTarget).clip(CircleShape).background(C.surface2).clickable(role = Role.Button, onClick = cancel), contentAlignment = Alignment.Center) {
                        Icon(Lucide.X, "Cancel saving", tint = C.text, modifier = Modifier.size(TabletParentMetrics.iconSize))
                    }
                }
                onTablet || dl?.state == DlState.DONE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Lucide.Check, null, tint = C.accent, modifier = Modifier.size(18.dp))
                    Text("On this tablet", style = body(14.sp, 600, 20f / 14), color = C.muted)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (dl?.state == DlState.FAILED) Text("Couldn't save", style = body(14.sp, 600, 20f / 14), color = C.muted)
                    Box(Modifier.size(TabletParentMetrics.minTarget).clip(CircleShape).background(C.surface2).clickable(role = Role.Button, onClick = save), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Download, "Save to this tablet", tint = C.text, modifier = Modifier.size(TabletParentMetrics.iconSize))
                    }
                }
            }
            when {
                e.inProgress -> Chip(height = TabletParentMetrics.minTarget, bg = C.accent) {
                    Icon(Solid.Play, null, tint = C.onAccent, modifier = Modifier.size(14.dp))
                    Text("Resume", style = body(16.sp, 700, 1.25f), color = C.onAccent)
                }
                e.played -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Lucide.Check, null, tint = C.muted, modifier = Modifier.size(18.dp))
                    Text("Watched", style = body(14.sp, 600, 20f / 14), color = C.muted)
                }
                else -> Box(Modifier.size(TabletParentMetrics.minTarget).clip(CircleShape).background(C.surface2), contentAlignment = Alignment.Center) {
                    Icon(Solid.Play, "Play", tint = C.text, modifier = Modifier.size(18.dp))
                }
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(20.dp)).background(if (highlight) C.surface2 else Color.Transparent)
            .clickable(onClick = onClick).padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(144.dp, 81.dp)) {
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
        if (showSave) when {
            dl?.active == true -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (dl.total > 0) "${(dl.fraction * 100).toInt()}%" else "Saving", style = body(15.sp, 700), color = C.muted)
                Box(Modifier.size(44.dp).clip(CircleShape).background(C.surface2).clickable(onClick = cancel), contentAlignment = Alignment.Center) {
                    Icon(Lucide.X, "Cancel saving", tint = C.text, modifier = Modifier.size(18.dp))
                }
            }
            onTablet || dl?.state == DlState.DONE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Lucide.Check, null, tint = C.accent, modifier = Modifier.size(18.dp))
                Text("On this tablet", style = body(15.sp, 600), color = C.muted)
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dl?.state == DlState.FAILED) Text("Couldn't save", style = body(15.sp, 600), color = C.muted)
                Box(Modifier.size(44.dp).clip(CircleShape).background(C.surface2).clickable(onClick = save), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Download, "Save to this tablet", tint = C.text, modifier = Modifier.size(18.dp))
                }
            }
        }
        when {
            e.inProgress -> Chip(height = 40.dp, bg = C.accent) {
                Icon(Solid.Play, null, tint = C.onAccent, modifier = Modifier.size(14.dp))
                Text("Resume", style = body(16.sp, 700), color = C.onAccent)
            }
            e.played -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Lucide.Check, null, tint = C.muted, modifier = Modifier.size(18.dp))
                Text("Watched", style = body(15.sp, 600), color = C.muted)
            }
            else -> Box(Modifier.size(48.dp).clip(CircleShape).background(C.surface2), contentAlignment = Alignment.Center) {
                Icon(Solid.Play, "Play", tint = C.text, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LibraryChip(onBack: () -> Unit) {
    if (LocalPhone.current) { PhoneBackChip("Library", onBack); return }
    if (LocalParentTablet.current) { TabletBackChip("Library", onBack); return }
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
    val app = App.get(LocalContext.current)
    val downloads = app.master.jfDownloads
    val dl by downloads.progress.collectAsState()
    val videosMap by app.master.sync.videos.collectAsState()
    val lib = remember(videosMap) { app.master.sync.library() }
    if (LocalPhone.current) {
        Box(Modifier.fillMaxSize()) {
            m?.let { jf.backdrop(it) }?.let { AsyncImage(it, null, Modifier.fillMaxSize().alpha(0.3f), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.bg.copy(alpha = 0.6f), 0.5f to C.bg.copy(alpha = 0.92f), 1f to C.bg)))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PhoneMetrics.gutter), verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap)) {
                LibraryChip(onBack)
                if (m == null) { Muted("Loading…", 16); return@Column }
                // The poster has no title drawn on it: the title is the one next to it.
                Row(horizontalArrangement = Arrangement.spacedBy(PhoneMetrics.gutter)) {
                    Artwork(jf.poster(m, 400), m.name, Modifier.size(96.dp, 144.dp), radius = PhoneMetrics.nestedRadius, showTitle = false)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PhoneMediaTitle(m.name)
                        Muted(listOfNotNull("Movie", m.rating, m.runMin.takeIf { it > 0 }?.let { minutesText(it) }).joinToString(" · "), 14, weight = 600)
                        if (m.inProgress) Muted("${minutesText(m.leftMin)} left", 14, weight = 600)
                    }
                }
                NickButton(
                    "${if (m.inProgress) "Resume" else "Play"} on ${v.node.name}", { play(m, false) }, Modifier.fillMaxWidth(), BtnStyle.PRIMARY,
                    leading = Solid.Play, sub = if (m.inProgress) "from ${fmtTicks(m.posTicks)}" else null,
                )
                if (m.inProgress) NickButton("Play from start", { play(m, true) }, Modifier.fillMaxWidth(), leading = Lucide.RotateCcw)
                if (m.inProgress) ProgressBar(m.progress, height = 6.dp)
                m.overview?.let { Text(it, style = body(16.sp, 500, 1.4f), color = if (C.isDark) Color(0xFFD5DAE3) else C.text, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                SaveActions(
                    v.node.name, dl[m.id], JellyfinDownloads.saved(m, lib) != null,
                    save = { downloads.save(m) }, saveSend = { downloads.save(m, setOf(v.node.id)) },
                    cancel = { downloads.cancel(m.id) },
                    send = { JellyfinDownloads.saved(m, lib)?.let { app.master.transfer.send(it.fid, setOf(v.node.id)) } },
                )
            }
        }
        return
    }
    if (LocalParentTablet.current) {
        // Poster 144x216 beside the title (36) and 16sp meta; the save / play column is at most 400 wide (52 primary, 48 secondary).
        // No backdrop: the page sits in the content cap and a cropped backdrop would show a hard edge.
        val stackPoster = LocalDensity.current.fontScale >= 1.5f
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = TabletParentMetrics.contentTop, bottom = TabletParentMetrics.contentBottom),
            verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.sectionGap),
        ) {
            LibraryChip(onBack)
            if (m == null) { Muted("Loading…", 18); return@Column }
            val poster: @Composable () -> Unit = { Artwork(jf.poster(m, 400), m.name, Modifier.size(144.dp, 216.dp), radius = TabletParentMetrics.mediaRadius, showTitle = false) }
            val info: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap)) {
                    Title(m.name, 72, maxLines = 3)
                    Muted(listOfNotNull("Movie", m.rating, m.runMin.takeIf { it > 0 }?.let { minutesText(it) }).joinToString(" · "), 19, weight = 600)
                    m.overview?.let { Muted(it, 20, Modifier.widthIn(max = 560.dp), maxLines = 6) }
                    if (m.inProgress) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProgressBar(m.progress, height = 6.dp)
                        Muted("${minutesText(m.leftMin)} left", 16, weight = 600)
                    }
                }
            }
            if (stackPoster) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { poster(); info(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { poster(); info(Modifier.weight(1f)) }
            Column(Modifier.widthIn(max = 400.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NickButton(
                    "${if (m.inProgress) "Resume" else "Play"} on ${v.node.name}", { play(m, false) }, Modifier.fillMaxWidth(), BtnStyle.PRIMARY,
                    leading = Solid.Play, sub = if (m.inProgress) "from ${fmtTicks(m.posTicks)}" else null,
                )
                if (m.inProgress) NickButton("Play from start", { play(m, true) }, Modifier.fillMaxWidth(), leading = Lucide.RotateCcw)
                SaveActions(
                    v.node.name, dl[m.id], JellyfinDownloads.saved(m, lib) != null,
                    save = { downloads.save(m) }, saveSend = { downloads.save(m, setOf(v.node.id)) },
                    cancel = { downloads.cancel(m.id) },
                    send = { JellyfinDownloads.saved(m, lib)?.let { app.master.transfer.send(it.fid, setOf(v.node.id)) } },
                )
            }
        }
        return
    }
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
                        big = bigButtons(), leading = Solid.Play, sub = if (m.inProgress) "from ${fmtTicks(m.posTicks)}" else null,
                    )
                    if (m.inProgress) NickButton("Play from start", { play(m, true) }, Modifier.fillMaxWidth(), big = bigButtons(), leading = Lucide.RotateCcw)
                    SaveActions(
                        v.node.name, dl[m.id], JellyfinDownloads.saved(m, lib) != null,
                        save = { downloads.save(m) }, saveSend = { downloads.save(m, setOf(v.node.id)) },
                        cancel = { downloads.cancel(m.id) },
                        send = { JellyfinDownloads.saved(m, lib)?.let { app.master.transfer.send(it.fid, setOf(v.node.id)) } },
                    )
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

/**
 * Save a Jellyfin movie onto the parent's tablet, and send it on. Shows the two save buttons, the
 * progress while it downloads, or "On this tablet" with a Send button once it is here.
 */
@Composable
internal fun SaveActions(
    tablet: String,
    progress: Progress?,
    onTablet: Boolean,
    save: () -> Unit,
    saveSend: () -> Unit,
    cancel: () -> Unit,
    send: () -> Unit,
) {
    // Parent tablet: a 4dp bar, 14/20 status, an 18dp check, a 48dp Cancel.
    val parentTablet = LocalParentTablet.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            progress?.active == true -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressBar(progress.fraction, height = if (parentTablet) 4.dp else 8.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val label = if (progress.state == DlState.QUEUED) "Waiting to save" else "Saving to this tablet"
                    Muted(if (progress.state == DlState.DOWNLOADING && progress.total > 0) "$label · ${(progress.fraction * 100).toInt()}%" else label, if (parentTablet) 14 else 16, Modifier.weight(1f), weight = 600)
                    NickButton("Cancel", cancel, leading = Lucide.X)
                }
            }
            onTablet || progress?.state == DlState.DONE -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Lucide.Check, null, tint = C.accent, modifier = Modifier.size(if (parentTablet) 18.dp else 20.dp))
                    Text("On this tablet", style = if (parentTablet) body(14.sp, 700, 20f / 14) else body(17.sp, 700), color = C.text)
                }
                if (onTablet) NickButton("Send to $tablet", send, Modifier.fillMaxWidth(), big = bigButtons(), leading = Lucide.Send)
            }
            else -> {
                if (progress?.state == DlState.FAILED) Muted(progress.err ?: "Couldn't save it", 15, weight = 600)
                NickButton("Save to this tablet", save, Modifier.fillMaxWidth(), big = bigButtons(), leading = Lucide.Download)
                NickButton("Save and send to $tablet", saveSend, Modifier.fillMaxWidth(), big = bigButtons(), leading = Lucide.Send)
            }
        }
    }
}

// ---------------- setup helpers ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserPicker(v: NodeView, jf: Jellyfin) {
    val app = App.get(LocalContext.current)
    val users by produceState<List<JfUser>?>(null) { value = try { jf.users() } catch (e: Exception) { emptyList() } }
    CenterMessage("Whose library is this?", "Pick the Jellyfin user ${v.node.name} is signed in as. Watch shows their shows and Continue Watching.") {
        val list = users
        when {
            list == null -> Muted("Loading users…", 18)
            list.isEmpty() -> Muted("Couldn't load users from Jellyfin.", 18)
            LocalParentTablet.current -> FlowRow(
                Modifier.widthIn(max = 560.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Tiles at least 96 wide with a 48dp avatar and a 16sp name; the whole tile is the (>= 48dp) target.
                list.forEach { u ->
                    Column(
                        Modifier.widthIn(min = 96.dp).heightIn(min = TabletParentMetrics.minTarget).clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface)
                            .clickable(role = Role.Button) {
                                app.store.updateNode(v.node.id) { it.copy(jfUserId = u.id, jfUserName = u.name) }
                                app.master.refreshResume(v.node.id)
                            }.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Avatar(u.name, 4, "initial", 48.dp)
                        Text(u.name, style = body(16.sp, 700, 22f / 16), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            else -> Column(Modifier.widthIn(max = 520.dp).then(fullOnPhone()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
fun CenterMessage(title: String, text: String, titleLines: Int = 2, content: @Composable () -> Unit) {
    if (LocalPhone.current) {
        // Phone: the full width less the 16dp gutters. A 64dp icon, a 24 title, a 16 body that is never cut with an ellipsis, then the action.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PhoneMetrics.gutter),
            verticalArrangement = Arrangement.spacedBy(PhoneMetrics.cardGap, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(PhoneMetrics.cardRadius)).background(C.surface2), contentAlignment = Alignment.Center) {
                Icon(Lucide.Server, null, tint = C.accent, modifier = Modifier.size(32.dp))
            }
            Title(title, 44, maxLines = 4)
            Muted(text, 16)
            Spacer(Modifier.height(PhoneMetrics.relatedGap))
            content()
        }
        return
    }
    if (LocalParentTablet.current) {
        // One centred group at most 560 wide: a 64 icon, a 28/32 title, 16/23 prose and the (52) action; scrolls on a short screen.
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = TabletParentMetrics.contentBottom),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TabletParentMetrics.sectionGap, Alignment.CenterVertically),
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface2), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Server, null, tint = C.accent, modifier = Modifier.size(32.dp))
                }
                Text(title, style = disp(28.sp, 800, 32f / 28), color = C.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(text, style = body(16.sp, 500, 23f / 16), color = C.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                content()
            }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 80.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(112.dp).clip(RoundedCornerShape(31.dp)).background(C.surface2), contentAlignment = Alignment.Center) {
            Icon(Lucide.Server, null, tint = C.accent, modifier = Modifier.size(48.dp))
        }
        Title(title, 44, lineHeight = 1.05f, maxLines = titleLines)
        Muted(text, 20, Modifier.widthIn(max = 640.dp))
        content()
    }
}

