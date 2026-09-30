package com.nbaltas.nickos.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Contrast
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.Unlink
import com.nbaltas.nickos.App
import com.nbaltas.nickos.BuildConfig
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.jellyfin.JfUser
import com.nbaltas.nickos.ui.setup.JellyfinForm
import com.nbaltas.nickos.ui.setup.rotate90
import com.nbaltas.nickos.ui.theme.Avatar
import com.nbaltas.nickos.ui.theme.BackButton
import com.nbaltas.nickos.ui.theme.BtnStyle
import com.nbaltas.nickos.ui.theme.C
import com.nbaltas.nickos.ui.theme.Chip
import com.nbaltas.nickos.ui.theme.Dot
import com.nbaltas.nickos.ui.theme.DarkColors
import com.nbaltas.nickos.ui.theme.Keypad
import com.nbaltas.nickos.ui.theme.LightColors
import com.nbaltas.nickos.ui.theme.Muted
import com.nbaltas.nickos.ui.theme.NickButton
import com.nbaltas.nickos.ui.theme.NickLogo
import com.nbaltas.nickos.ui.theme.PinDots
import com.nbaltas.nickos.ui.theme.Title
import com.nbaltas.nickos.ui.theme.body
import com.nbaltas.nickos.ui.theme.dashed
import com.nbaltas.nickos.ui.theme.disp
import com.nbaltas.nickos.ui.theme.tap

private val sections = listOf<Pair<String, ImageVector>>(
    "Linked tablets" to Lucide.Tablet, "Jellyfin server" to Lucide.Server, "Parent PIN" to Lucide.KeyRound,
    "Theme" to Lucide.Contrast, "About" to Lucide.Info,
)

@Composable
fun SettingsScreen(section: Int, onSection: (Int) -> Unit, onBack: () -> Unit, onAdd: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelW = if (maxWidth < 1000.dp) 250.dp else 320.dp
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(panelW).fillMaxHeight().background(C.panel).padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BackButton(onBack)
                    Title("Settings", 34, maxLines = 1)
                }
                sections.forEachIndexed { i, (label, icon) ->
                    val on = i == section
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(18.dp)).background(if (on) C.surface2 else Color.Transparent)
                            .clickable { onSection(i) }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(icon, null, tint = if (on) C.accent else C.muted, modifier = Modifier.size(22.dp).then(if (i == 0) Modifier.rotate90() else Modifier))
                        Text(label, style = body(19.sp, 700), color = if (on) C.text else C.muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("NickOS ${BuildConfig.VERSION_NAME}", style = body(15.sp), color = C.muted, modifier = Modifier.padding(horizontal = 18.dp))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    0 -> TabletsSection(onAdd)
                    1 -> JellyfinSection()
                    2 -> PinSection()
                    3 -> ThemeSection()
                    else -> AboutSection()
                }
            }
        }
    }
}

@Composable
private fun SectionPage(title: String, action: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Title(title, 46, Modifier.weight(1f), maxLines = 1)
            action()
        }
        content()
    }
}

// ---------------- Linked tablets ----------------

@Composable
private fun TabletsSection(onAdd: () -> Unit) {
    val app = App.get(LocalContext.current)
    val views = rememberNodeViews()
    val name by app.store.name.collectAsState()
    var renaming by remember { mutableStateOf<String?>(null) } // node id, or "" for this tablet
    var confirmUnlink by remember { mutableStateOf<String?>(null) }
    SectionPage("Linked tablets", { NickButton("Add tablet", onAdd, style = BtnStyle.PRIMARY, leading = Lucide.Plus) }) {
        Row(
            Modifier.fillMaxWidth().border(2.dp, C.line, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                Icon(Lucide.Tablet, null, tint = C.onAccent, modifier = Modifier.size(26.dp).rotate90())
            }
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Parent's Tablet" }, style = body(20.sp, 800), color = C.text)
                Muted("This tablet · Master", 16)
            }
            NickButton("Rename", { renaming = "" }, Modifier.height(52.dp), leading = Lucide.Pencil)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            views.forEach { v ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(RoundedCornerShape(24.dp)).background(C.surface).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Avatar(v.node.name, v.node.color, v.node.avatar, 60.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(v.node.name, style = body(22.sp, 800), color = C.text, maxLines = 1)
                        StatusLine(v, 16, 10.dp, if (v.online) "Online" else statusText(v))
                    }
                    Text(v.battery?.let { "$it%" } ?: "", style = disp(28.sp), color = if (v.online) C.text else C.muted, modifier = Modifier.width(90.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    NickButton("Rename", { renaming = v.node.id }, Modifier.height(52.dp), leading = Lucide.Pencil)
                    NickButton(if (confirmUnlink == v.node.id) "Sure?" else "Unlink", {
                        if (confirmUnlink == v.node.id) { app.master.unlink(v.node.id); confirmUnlink = null } else confirmUnlink = v.node.id
                    }, Modifier.height(52.dp), BtnStyle.DANGER, leading = Lucide.Unlink)
                }
            }
            if (views.isEmpty()) Muted("No tablets linked yet.", 18)
        }
    }
    renaming?.let { id ->
        if (id.isEmpty()) RenameDialog(name.ifBlank { "Parent's Tablet" }, "Rename this tablet") { n -> renaming = null; if (n != null) app.store.setName(n) }
        else views.firstOrNull { it.node.id == id }?.let { v -> RenameDialog(v.node.name) { n -> renaming = null; if (n != null) app.master.rename(id, n) } }
    }
}

// ---------------- Jellyfin ----------------

@Composable
private fun JellyfinSection() {
    val app = App.get(LocalContext.current)
    val account by app.store.jellyfin.collectAsState()
    val nodes by app.store.nodes.collectAsState()
    var picking by remember { mutableStateOf<LinkedNode?>(null) }
    var formKey by remember { mutableStateOf(0) }
    SectionPage("Jellyfin server", {
        if (account != null) NickButton("Disconnect", { app.store.setJellyfin(null); formKey++ }, style = BtnStyle.DANGER)
    }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Dot(if (account != null) C.online else C.offline)
                Text(if (account != null) "Connected" else "Not connected", style = body(18.sp, 700), color = if (account != null) C.online else C.muted)
            }
            androidx.compose.runtime.key(formKey, account?.token) {
                JellyfinForm(onSaved = {}, initial = account, footer = { save, saving ->
                    if (account == null) NickButton(if (saving) "Saving…" else "Save", save, style = BtnStyle.PRIMARY, enabled = !saving)
                    else NickButton(if (saving) "Saving…" else "Save changes", save, enabled = !saving)
                })
            }
        }
        if (account != null && nodes.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(28.dp)) {
                Text("Whose library each tablet shows", style = body(20.sp, 800), color = C.text)
                Muted("Watch browses this Jellyfin user's library on each tablet.", 16)
                Spacer(Modifier.height(10.dp))
                nodes.forEach { n ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    Row(Modifier.fillMaxWidth().height(76.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(n.name, n.color, n.avatar, 44.dp)
                        Text(n.name, style = body(20.sp, 700), color = C.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Chip(Modifier.clip(RoundedCornerShape(26.dp)).clickable { picking = n }, height = 52.dp) {
                            Text("Jellyfin user: ${n.jfUserName ?: "Choose"}", style = body(17.sp, 700), color = C.text)
                            Icon(Lucide.ChevronRight, null, tint = C.text, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
    picking?.let { n ->
        val jf = remember(account) { app.master.jellyfin() }
        val users by produceState<List<JfUser>?>(null, jf) { value = try { jf?.users() } catch (e: Exception) { emptyList() } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).tap { picking = null }, contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(24.dp).clip(RoundedCornerShape(30.dp)).background(C.surface).tap {}.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Title("${n.name} shows…", 32)
                Spacer(Modifier.height(6.dp))
                val list = users
                if (list == null) Muted("Loading users…", 18)
                list?.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (u.id == n.jfUserId) C.surface2 else Color.Transparent)
                            .clickable {
                                app.store.updateNode(n.id) { it.copy(jfUserId = u.id, jfUserName = u.name) }
                                app.master.refreshResume(n.id)
                                picking = null
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Avatar(u.name, 4, "initial", 40.dp)
                        Text(u.name, style = body(19.sp, 700), color = C.text, modifier = Modifier.weight(1f))
                        if (u.id == n.jfUserId) Icon(Lucide.Check, null, tint = C.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---------------- PIN ----------------

@Composable
private fun PinSection() {
    val app = App.get(LocalContext.current)
    var first by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    SectionPage("Parent PIN") {
        BoxWithConstraints {
            val narrow = maxWidth < 800.dp
            val text: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Used to leave kid mode on every Node tablet.", style = body(21.sp, 500, 1.45f), color = C.text)
                    Muted("Changing it here updates all linked tablets.", 17)
                    message?.let { (ok, m) -> Text(m, style = body(18.sp, 700), color = if (ok) C.online else C.danger) }
                }
            }
            val pad: @Composable () -> Unit = {
                Column(
                    Modifier.widthIn(max = 484.dp).fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(C.surface).padding(horizontal = 32.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Text(if (first == null) "Enter a new 4-digit PIN" else "Enter it again", style = body(20.sp, 700), color = C.text)
                    PinDots(entered.length)
                    Keypad(
                        onDigit = { d ->
                            if (entered.length < 4) entered += d
                            if (entered.length == 4) {
                                val f = first
                                if (f == null) { first = entered; entered = ""; message = null }
                                else if (f == entered) { app.store.setPin(entered); first = null; entered = ""; message = true to "PIN changed on this tablet and every linked tablet." }
                                else { first = null; entered = ""; message = false to "Those didn't match. Start again." }
                            }
                        },
                        onDelete = { entered = entered.dropLast(1) },
                        modifier = Modifier.width(356.dp),
                        keyHeight = 72.dp,
                    )
                }
            }
            if (narrow) Column(verticalArrangement = Arrangement.spacedBy(28.dp)) { text(Modifier.fillMaxWidth()); pad() }
            else Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) { text(Modifier.weight(1f)); pad() }
        }
    }
}

// ---------------- Theme ----------------

@Composable
private fun ThemeSection() {
    val app = App.get(LocalContext.current)
    val theme by app.store.theme.collectAsState()
    SectionPage("Theme") {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf("DARK" to "Dark", "LIGHT" to "Light", "SYSTEM" to "Match system").forEach { (key, label) ->
                val on = theme == key
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(C.surface).border(3.dp, if (on) C.accent else C.line, RoundedCornerShape(28.dp))
                        .clickable { app.store.setTheme(key) }.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ThemePreview(key)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = body(21.sp, 800), color = C.text, modifier = Modifier.weight(1f), maxLines = 1)
                        if (on) Box(Modifier.size(32.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                            Icon(Lucide.Check, null, tint = C.onAccent, modifier = Modifier.size(18.dp))
                        } else Box(Modifier.size(32.dp).border(3.dp, C.line, CircleShape))
                    }
                }
            }
        }
        Muted("Kid tablets keep their own colour. Theme applies to this tablet.", 17)
    }
}

@Composable
private fun ThemePreview(key: String) {
    Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp))) {
        Row(Modifier.fillMaxSize()) {
            if (key != "LIGHT") Box(Modifier.weight(1f).fillMaxHeight().background(DarkColors.bg))
            if (key != "DARK") Box(Modifier.weight(1f).fillMaxHeight().background(LightColors.bg))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { c ->
                        val light = key == "LIGHT" || (key == "SYSTEM" && c == 1)
                        val tile = when {
                            key == "SYSTEM" && !light -> Color(0xFF3A3D44)
                            key == "SYSTEM" -> Color(0xFFC4C1BA)
                            light -> Color.White
                            else -> DarkColors.surface2
                        }
                        val m = Modifier.weight(1f).fillMaxHeight()
                        if (r == 1 && c == 1) Box(m.dashed(if (light) Color(0xFFC07A00) else C.accent, 12.dp, 2.dp))
                        else Box(m.clip(RoundedCornerShape(12.dp)).background(tile))
                    }
                }
            }
        }
    }
}

// ---------------- About ----------------

@Composable
private fun AboutSection() {
    val app = App.get(LocalContext.current)
    val name by app.store.name.collectAsState()
    val nodes by app.store.nodes.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    SectionPage("About") {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(32.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            NickLogo(96.dp)
            Column(Modifier.weight(1f)) {
                Title("NickOS", 40)
                Muted("Version ${BuildConfig.VERSION_NAME}", 18)
                updateMsg?.let { Muted(it, 16) }
            }
            NickButton("Check for updates", { updateMsg = "Updates aren't set up yet. Install new versions by hand for now." }, leading = Lucide.RefreshCw)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.surface).padding(horizontal = 28.dp, vertical = 8.dp)) {
            InfoRow("This tablet", "${name.ifBlank { "Parent's Tablet" }} · Master")
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            InfoRow("Linked tablets", nodes.size.toString())
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            InfoRow("Connection", "Local WiFi only")
        }
        NickButton(if (confirmReset) "Tap again: forget every tablet and start over" else "Reset this tablet", {
            if (confirmReset) app.store.resetAll() else confirmReset = true
        }, style = BtnStyle.DANGER)
    }
}

@Composable
private fun InfoRow(k: String, v: String, h: Dp = 64.dp) {
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically) {
        Text(k, style = body(19.sp), color = C.muted, modifier = Modifier.weight(1f))
        Text(v, style = body(19.sp, 700), color = C.text)
    }
}
