package com.nbaltas.nickos.ui.node

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nbaltas.nickos.node.PlayerUi
import com.nbaltas.nickos.sync.SyncState
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.NodePalette
import com.nbaltas.nickos.ui.theme.disp
import com.nbaltas.nickos.ui.theme.tap
import kotlinx.coroutines.delay

/**
 * The Node's player screen while Watch together has this tablet in front. [player] is nullable
 * so the screenshot test can render the overlays without a real ExoPlayer. [parent] is already
 * resolved (`parentName(masterName)`) by the caller.
 */
@Composable
fun TogetherScreen(ui: PlayerUi, player: Player?, parent: String, onHold: () -> Unit) {
    val ctx = LocalContext.current
    var tapMessage by remember { mutableStateOf(false) }

    // The kiosk's own back handling must never leave this screen.
    BackHandler {}

    DisposableEffect(Unit) {
        val activity = ctx as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(tapMessage) {
        if (tapMessage) {
            delay(2500)
            tapMessage = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .tap { if (ui.state == SyncState.PLAYING) tapMessage = true },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            update = { it.player = player },
        )

        val message = when {
            tapMessage -> if (ui.group <= 1) "$parent is playing this for you" else "$parent is playing this on everyone's tablets"
            ui.state == SyncState.LOADING || ui.state == SyncState.READY -> "Getting ${ui.title} ready..."
            ui.state == SyncState.PAUSED || ui.state == SyncState.LOCKED -> "Paused by $parent"
            ui.state == SyncState.BUFFERING -> "Catching up..."
            ui.state == SyncState.ERROR -> if (ui.err == "missing") "This movie isn't on this tablet" else "This movie won't play here"
            ui.state == SyncState.ENDED -> "The end"
            else -> null
        }

        if (message != null) {
            Box(Modifier.fillMaxWidth().padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.widthIn(max = 640.dp)
                        .shadow(24.dp, RoundedCornerShape(32.dp))
                        .clip(RoundedCornerShape(32.dp)).background(NodePalette.toastBg)
                        .padding(horizontal = 26.dp, vertical = 22.dp),
                ) {
                    Text(message, style = disp(28.sp), color = C.onAccent, textAlign = TextAlign.Center)
                }
            }
        }

        // Hidden parent exit: a long hold on the top-left corner, no hint drawn.
        Box(Modifier.size(120.dp).hiddenHold(onHold = onHold))
    }
}
