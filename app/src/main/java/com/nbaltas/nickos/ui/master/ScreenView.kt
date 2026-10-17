package com.nbaltas.nickos.ui.master

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.nbaltas.nickos.App
import com.nbaltas.nickos.remote.RemoteMessages
import com.nbaltas.nickos.remote.TouchMap
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.Title

const val SCREEN_NEEDS_PERMISSION = "Can't share: allow notifications on this tablet"
const val SCREEN_NEEDS_CONSENT = "Turn on screen sharing on the tablet (grown-up menu)"
const val SCREEN_VIEW_ONLY = "View only. To touch the screen from here, turn on Let parents control this tablet in the grown-up menu."
const val SCREEN_SECOND_PARENT = "To watch from a second parent tablet, add it with Pair another parent tablet in the grown-up menu."

/** The Screen tab of Tablet Control: a start page, then the full screen viewer. */
@Composable
fun ScreenTab(v: NodeView) {
    val ctl = App.get(LocalContext.current).master.screen
    val watching by ctl.watching.collectAsState()
    val states by ctl.states.collectAsState()
    val frame by ctl.frame.collectAsState()
    val lost by ctl.lost.collectAsState()
    val id = v.node.id
    val state = states[id]?.state ?: v.status?.screen ?: RemoteMessages.OFF
    val control = (states[id]?.control ?: v.status?.control) == RemoteMessages.CONTROL_READY
    when {
        v.status?.supportsScreen != true -> ScreenStart("This tablet needs the latest NickOS before it can share its screen.", null, null)
        watching != id -> ScreenStart(null, "Watch this screen") { ctl.watch(id) }
        else -> ScreenViewer(
            frame = frame, state = state, control = control, lost = lost,
            onStop = { ctl.unwatch() }, onWatchAgain = { ctl.watch(id) },
            onBack = { ctl.back() }, onHome = { ctl.home() },
            onTap = { x, y -> ctl.tap(x, y) }, onSwipe = { x1, y1, x2, y2, ms -> ctl.swipe(x1, y1, x2, y2, ms) },
        )
    }
}

@Composable
internal fun ScreenStart(problem: String?, button: String?, onWatch: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Title("Screen", 32, maxLines = 1)
            Muted(problem ?: "See what this tablet shows right now, and tap or swipe it if you turned that on.", 18, Modifier.fillMaxWidth(), maxLines = 4)
            if (button != null && onWatch != null) NickButton(button, onWatch, style = BtnStyle.PRIMARY, big = true, leading = Lucide.Monitor)
            if (problem == null) Muted(SCREEN_SECOND_PARENT, 15, Modifier.fillMaxWidth(), maxLines = 3)
        }
    }
}

/**
 * The picture of the kid tablet's screen, kept whole and centred. A tap on it is a tap there, a drag is a swipe, and
 * Back and Home are buttons. Without [control] it is view only.
 */
@Composable
internal fun ScreenViewer(
    frame: Bitmap?,
    state: String,
    control: Boolean,
    lost: Boolean,
    onStop: () -> Unit,
    onWatchAgain: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onTap: (Double, Double) -> Unit,
    onSwipe: (Double, Double, Double, Double, Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NickButton("Stop", onStop, style = BtnStyle.DANGER)
            Box(Modifier.weight(1f))
            if (control) {
                NickButton("Back", onBack, leading = Lucide.ArrowLeft)
                NickButton("Home", onHome, leading = Lucide.House)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val shown = frame
            when {
                shown != null && !lost -> Picture(shown, control, onTap, onSwipe)
                lost -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Muted("The picture stopped.", 20)
                    NickButton("Watch again", onWatchAgain, style = BtnStyle.PRIMARY)
                }
                state == RemoteMessages.NEEDS_PERMISSION -> Muted(SCREEN_NEEDS_PERMISSION, 20, Modifier.padding(32.dp), maxLines = 3)
                state == RemoteMessages.NEEDS_CONSENT || state == RemoteMessages.OFF && frame == null -> Muted(SCREEN_NEEDS_CONSENT, 20, Modifier.padding(32.dp), maxLines = 3)
                else -> Muted("Connecting", 20)
            }
        }
        if (!control) Muted(SCREEN_VIEW_ONLY, 14, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 2)
    }
}

@Composable
private fun Picture(
    bmp: Bitmap,
    control: Boolean,
    onTap: (Double, Double) -> Unit,
    onSwipe: (Double, Double, Double, Double, Long) -> Unit,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    // The newest picture's size, read when a touch ends: the tablet may have turned since the touch began.
    val shape by rememberUpdatedState(bmp.width to bmp.height)
    val tap by rememberUpdatedState(onTap)
    val swipe by rememberUpdatedState(onSwipe)
    fun norm(p: Offset) = TouchMap.toNorm(p.x, p.y, box.width.toFloat(), box.height.toFloat(), shape.first, shape.second)
    var mod = Modifier.fillMaxSize().onSizeChanged { box = it }
    if (control) {
        mod = mod
            .pointerInput(Unit) { detectTapGestures { p -> norm(p)?.let { tap(it.x, it.y) } } }
            .pointerInput(Unit) {
                var from = Offset.Zero
                var to = Offset.Zero
                var at = 0L
                detectDragGestures(
                    onDragStart = { from = it; to = it; at = System.currentTimeMillis() },
                    onDrag = { change, _ -> to = change.position },
                    onDragEnd = {
                        val a = norm(from)
                        val b = norm(to)
                        val ms = (System.currentTimeMillis() - at).coerceIn(RemoteMessages.MIN_SWIPE_MS, RemoteMessages.MAX_SWIPE_MS)
                        if (a != null && b != null) swipe(a.x, a.y, b.x, b.y, ms)
                    },
                )
            }
    }
    Image(bmp.asImageBitmap(), "The tablet's screen", mod, contentScale = ContentScale.Fit)
}
