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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.nbaltas.nickos.App
import com.nbaltas.nickos.remote.RemoteMessages
import com.nbaltas.nickos.remote.TouchMap
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.LocalPhone
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.disp

const val SCREEN_NEEDS_PERMISSION = "Can't share: allow notifications on this tablet"
const val SCREEN_NEEDS_CONSENT = "Turn on screen sharing on the tablet (grown-up menu)"
const val SCREEN_VIEW_ONLY = "View only. To touch the screen from here, turn on Let parents control this tablet in the grown-up menu."
const val SCREEN_SECOND_PARENT = "To watch from a second parent tablet, add it with Pair another parent tablet in the grown-up menu."

/** The Screen tab of Tablet Control: a start page, then the full screen viewer. */
@Composable
fun ScreenTab(v: NodeView) {
    val rt = App.get(LocalContext.current).master
    val ctl = rt.screen
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
            openSettingLabel = "Open setting on ${v.node.name}", onOpenSetting = { rt.askControl(id) },
            nodeName = v.node.name, maintenance = v.status?.maintenance == true, onLockNow = { rt.home(id) },
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
    openSettingLabel: String? = null,
    onOpenSetting: (() -> Unit)? = null,
    nodeName: String? = null,
    maintenance: Boolean = false,
    onLockNow: (() -> Unit)? = null,
) {
    val phone = LocalPhone.current
    Column(Modifier.fillMaxSize().background(Color.Black).then(if (phone) Modifier.navigationBarsPadding() else Modifier)) {
        if (phone && control && LocalDensity.current.fontScale >= 1.5f) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Large font: three labels cannot share one row without breaking words, so Stop gets its own row.
            NickButton("Stop", onStop, Modifier.fillMaxWidth().heightIn(min = 48.dp), style = BtnStyle.DANGER)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NickButton("Back", onBack, Modifier.weight(1f).heightIn(min = 48.dp), leading = Lucide.ArrowLeft)
                NickButton("Home", onHome, Modifier.weight(1f).heightIn(min = 48.dp), leading = Lucide.House)
            }
        } else if (phone) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Equal-weight 48dp buttons with 8dp gaps: "Home" is never clipped and Back never touches it.
            NickButton("Stop", onStop, Modifier.weight(1f).heightIn(min = 48.dp), style = BtnStyle.DANGER)
            if (control) {
                NickButton("Back", onBack, Modifier.weight(1f).heightIn(min = 48.dp), leading = Lucide.ArrowLeft)
                NickButton("Home", onHome, Modifier.weight(1f).heightIn(min = 48.dp), leading = Lucide.House)
            }
        } else Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                phone && lost -> ViewerState(nodeName ?: "Screen", "The picture stopped. Watch again to carry on.", "Watch again", onWatchAgain)
                phone && state == RemoteMessages.NEEDS_PERMISSION -> ViewerState("Screen sharing", SCREEN_NEEDS_PERMISSION)
                phone && (state == RemoteMessages.NEEDS_CONSENT || state == RemoteMessages.OFF && frame == null) ->
                    ViewerState("Screen sharing", if (nodeName != null) "Screen: tap Allow on $nodeName" else SCREEN_NEEDS_CONSENT)
                phone -> ViewerState(nodeName ?: "Screen", "Connecting")
                lost -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Muted("The picture stopped.", 20)
                    NickButton("Watch again", onWatchAgain, style = BtnStyle.PRIMARY)
                }
                state == RemoteMessages.NEEDS_PERMISSION -> Muted(SCREEN_NEEDS_PERMISSION, 20, Modifier.padding(32.dp), maxLines = 3)
                state == RemoteMessages.NEEDS_CONSENT || state == RemoteMessages.OFF && frame == null ->
                    Muted(if (nodeName != null) "Screen: tap Allow on $nodeName" else SCREEN_NEEDS_CONSENT, 20, Modifier.padding(32.dp), maxLines = 3)
                else -> Muted("Connecting", 20)
            }
        }
        // Two separate things: the picture (screen sharing allowed on the tablet) and touch (the accessibility switch).
        if (maintenance) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted("Settings open on ${nodeName ?: "the tablet"}", 14, Modifier.weight(1f), maxLines = 2)
            if (onLockNow != null) NickButton("Lock now", onLockNow, style = BtnStyle.DANGER)
        } else if (!control && phone) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The whole instruction at 14sp, then the 48dp action on its own row.
            Muted(if (nodeName != null) "Touch control: off. $SCREEN_VIEW_ONLY" else SCREEN_VIEW_ONLY, 14, Modifier.fillMaxWidth())
            if (onOpenSetting != null) NickButton(openSettingLabel ?: "Open setting", onOpenSetting, Modifier.fillMaxWidth().heightIn(min = 48.dp))
        } else if (!control) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted(if (nodeName != null) "Touch control: off. $SCREEN_VIEW_ONLY" else SCREEN_VIEW_ONLY, 14, Modifier.weight(1f), maxLines = 2)
            if (onOpenSetting != null) NickButton(openSettingLabel ?: "Open setting", onOpenSetting)
        }
    }
}

/** Phone: the viewer's no-picture states as one centred group, title 20, the full body at 16 and a 52dp action. */
@Composable
private fun ViewerState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = disp(20.sp, 800, 1.15f), color = C.text, textAlign = TextAlign.Center)
        Text(message, style = body(16.sp, 500, 1.4f), color = C.muted, textAlign = TextAlign.Center)
        if (action != null) NickButton(action, onAction, Modifier.fillMaxWidth().heightIn(min = 52.dp), style = BtnStyle.PRIMARY)
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
