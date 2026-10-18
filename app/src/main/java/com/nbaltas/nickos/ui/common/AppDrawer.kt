package com.nbaltas.nickos.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.nbaltas.nickos.ui.theme.BackButton
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.LocalParentTablet
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.ParentContentCap
import com.nbaltas.nickos.ui.theme.TabletParentMetrics
import androidx.compose.foundation.layout.heightIn
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.toSmallBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalApp(val pkg: String, val label: String, val icon: android.graphics.Bitmap)

/**
 * All apps on this tablet. Not in the design handoff; uses the Apps-tab tile language.
 * [pinned] + [onLongPress] let the Master pin apps to its top bar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    pinned: List<String> = emptyList(),
    onLongPress: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val apps by produceState<List<LocalApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { LocalApp(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm).toSmallBitmap()) }
                .sortedBy { it.label.lowercase() }
        }
    }
    if (LocalParentTablet.current) {
        // Parent tablet: 80dp header, content capped at 1152, adaptive cells of at least 128 with a 56dp icon and a two-line label.
        Column(Modifier.fillMaxSize().background(C.bg)) {
            ParentContentCap { _ ->
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(TabletParentMetrics.headerHeight),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TabletParentMetrics.relatedGap),
                    ) {
                        BackButton(onBack)
                        Title("All apps", 40, Modifier.weight(1f))
                        if (onLongPress != null) Muted("Hold an app to pin it to the top bar", 14, Modifier.weight(1f, fill = false))
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(128.dp),
                        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 16.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(apps.orEmpty(), key = { it.pkg }) { a ->
                            Column(
                                Modifier.heightIn(min = 124.dp).clip(RoundedCornerShape(TabletParentMetrics.cardRadius)).background(C.surface)
                                    .combinedClickable(onClick = { onOpen(a.pkg) }, onLongClick = onLongPress?.let { { it(a.pkg) } })
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            ) {
                                Box {
                                    Image(a.icon.asImageBitmap(), null, Modifier.size(56.dp))
                                    if (a.pkg in pinned) Box(
                                        Modifier.align(Alignment.TopEnd).size(22.dp).clip(RoundedCornerShape(11.dp)).background(C.accent),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Lucide.Pin, null, tint = C.onAccent, modifier = Modifier.size(12.dp)) }
                                }
                                Text(a.label, style = body(15.sp, 700, 20f / 15f), color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(C.bg)) {
        Row(
            Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BackButton(onBack)
            Title("All apps", 40, Modifier.weight(1f))
            if (onLongPress != null) Muted("Hold an app to pin it to the top bar", 17)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps.orEmpty(), key = { it.pkg }) { a ->
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(C.surface)
                        .combinedClickable(onClick = { onOpen(a.pkg) }, onLongClick = onLongPress?.let { { it(a.pkg) } })
                        .padding(vertical = 22.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box {
                        Image(a.icon.asImageBitmap(), null, Modifier.size(76.dp))
                        if (a.pkg in pinned) Box(
                            Modifier.align(Alignment.TopEnd).size(26.dp).clip(RoundedCornerShape(13.dp)).background(C.accent),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Lucide.Pin, null, tint = C.onAccent, modifier = Modifier.size(14.dp)) }
                    }
                    Text(a.label, style = body(17.sp, 700), color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
