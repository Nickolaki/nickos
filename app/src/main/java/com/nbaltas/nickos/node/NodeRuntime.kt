package com.nbaltas.nickos.node

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.nbaltas.nickos.BuildConfig
import com.nbaltas.nickos.cam.CamMessages
import com.nbaltas.nickos.cam.CamProfile
import com.nbaltas.nickos.cam.NodeCam
import com.nbaltas.nickos.data.LinkedMaster
import com.nbaltas.nickos.data.ResumeCard
import com.nbaltas.nickos.data.Store
import com.nbaltas.nickos.link.AcceptGate
import com.nbaltas.nickos.link.Conn
import com.nbaltas.nickos.link.Handshake
import com.nbaltas.nickos.link.LanAddress
import com.nbaltas.nickos.link.LinkFlags
import com.nbaltas.nickos.link.PairGate
import com.nbaltas.nickos.link.NetMessages
import com.nbaltas.nickos.link.NetWatch
import com.nbaltas.nickos.link.Wire
import com.nbaltas.nickos.remote.RemoteMessages
import com.nbaltas.nickos.remote.ScreenGate
import com.nbaltas.nickos.talk.TalkBannerPolicy
import com.nbaltas.nickos.talk.TalkGate
import com.nbaltas.nickos.talk.TalkIndicator
import com.nbaltas.nickos.talk.TalkMessages
import com.nbaltas.nickos.talk.TalkPlayer
import com.nbaltas.nickos.talk.TalkReceiver
import com.nbaltas.nickos.talk.TalkText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList

/** What [Advertiser] needs from NSD, so the sequencing can be tested with a fake. */
interface AdvertGate {
    /** Starts a NEW registration (a reused listener throws) and reports once whether it took. Returns its handle. */
    fun register(port: Int, onResult: (Boolean) -> Unit): Any
    /** Stops [handle]; [onDone] runs when the system confirms or refuses. */
    fun unregister(handle: Any, onDone: () -> Unit)
}

/** The real thing: one fresh listener per registration. */
class NsdGate(private val ctx: Context, private val store: Store) : AdvertGate {
    private val nsd get() = ctx.getSystemService(NsdManager::class.java)

    override fun register(port: Int, onResult: (Boolean) -> Unit): Any {
        val info = NsdServiceInfo().apply {
            serviceName = Wire.serviceName(store.deviceId)
            serviceType = Wire.SERVICE_TYPE
            this.port = port
        }
        val l = Reg(port, onResult)
        try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { Log.w("NickOS.Node", "nsd unavailable", e); onResult(false) }
        return l
    }

    override fun unregister(handle: Any, onDone: () -> Unit) {
        val l = handle as Reg
        l.done = onDone
        try { nsd.unregisterService(l) } catch (e: Exception) { onDone() }
    }

    private class Reg(val port: Int, val onResult: (Boolean) -> Unit) : NsdManager.RegistrationListener {
        @Volatile var done: (() -> Unit)? = null
        override fun onRegistrationFailed(si: NsdServiceInfo, code: Int) { Log.w("NickOS.Node", "nsd register failed $code"); onResult(false) }
        override fun onUnregistrationFailed(si: NsdServiceInfo, code: Int) { done?.invoke() }
        override fun onServiceRegistered(si: NsdServiceInfo) { Log.i("NickOS.Node", "advertising ${si.serviceName}:$port"); onResult(true) }
        override fun onServiceUnregistered(si: NsdServiceInfo) { done?.invoke() }
    }
}

/**
 * Keeps this Node's mDNS record alive across network changes. [refresh] unregisters, WAITS for the system to
 * confirm (registering the same service name while the old one is still going makes Android rename the new one),
 * then registers with a brand new listener. Races it handles:
 *  - A burst of refreshes runs one at a time, and refreshes queued behind a running one collapse into one.
 *  - [stop] during a refresh: nothing registers after the unregister ack.
 *  - A registration that fails right after a network change (the daemon is still restarting) is retried.
 */
class Advertiser(
    private val scope: CoroutineScope,
    private val gate: AdvertGate,
    private val ackTimeoutMs: Long = 1500,
    private val resultTimeoutMs: Long = 5000,
    private val retryMs: Long = 2000,
    private val maxRetries: Int = 3,
) {
    private val lock = kotlinx.coroutines.sync.Mutex()
    private val queued = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var stopped = true
    private var port = 0
    private var current: Any? = null
    private var registeredIp: String? = null

    fun start(port: Int, ip: String? = null) {
        registeredIp = ip
        stopped = false
        scope.launch { lock.withLock { this@Advertiser.port = port; if (!stopped) register() } }
    }

    /** [ip] is the network identity now (interface, IPv4, gateway: [LanAddress.identity]); the same one as at the last registration means nothing to announce (null: always re-register). */
    fun refresh(ip: String? = null) {
        if (stopped || !queued.compareAndSet(false, true)) return
        scope.launch {
            lock.withLock {
                queued.set(false)
                if (stopped) return@withLock
                if (ip != null && ip == registeredIp && current != null) return@withLock
                unregister()
                registeredIp = ip
                if (!stopped) register()
            }
        }
    }

    fun stop() {
        stopped = true
        scope.launch { lock.withLock { unregister() } }
    }

    private suspend fun register() {
        repeat(maxRetries + 1) { attempt ->
            val result = kotlinx.coroutines.CompletableDeferred<Boolean>()
            val handle = gate.register(port) { result.complete(it) }
            val ok = withTimeoutOrNull(resultTimeoutMs) { result.await() }
            if (ok != false) { current = handle; return }   // registered, or no word yet: keep the handle so stop can undo it
            if (attempt < maxRetries) { delay(retryMs); if (stopped) return }
        }
    }

    private suspend fun unregister() {
        val h = current ?: return
        current = null
        val ack = kotlinx.coroutines.CompletableDeferred<Unit>()
        gate.unregister(h) { ack.complete(Unit) }
        withTimeoutOrNull(ackTimeoutMs) { ack.await() }
    }
}

/** "Dad's Tablet" → "Dad" for "Screen locked by Dad". */
fun parentName(masterName: String?): String {
    val n = masterName?.trim().orEmpty()
    return Regex("^(.+?)['’]s\\s+tablet$", RegexOption.IGNORE_CASE).find(n)?.groupValues?.get(1) ?: n.ifEmpty { "a grown-up" }
}

/**
 * The Node side of the link. Runs inside [NodeService] so the Master can still reach the tablet
 * while a kid is in Jellyfin or the screen is off.
 */
class NodeRuntime(private val ctx: Context, private val store: Store) {
    val device = NodeDevice(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = CopyOnWriteArrayList<Pair<String, Conn>>()
    private var server: ServerSocket? = null

    /** True once the link server is up. Stays false on a tablet below Android 8 (see [NodeSupport]). */
    val listening: Boolean get() = server != null
    private val advertiser = Advertiser(scope, NsdGate(ctx, store))
    private var jobs = mutableListOf<Job>()

    /** Saves the car hotspot so Android joins it unaided; see [WifiJoin]. */
    val wifi = WifiJoin(ctx, { device.isDeviceOwner }, currentSsid = {
        @Suppress("DEPRECATION")
        val s = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo?.ssid
        s?.takeIf { it.isNotEmpty() && !it.contains("unknown ssid", ignoreCase = true) }
    })
    private var netWatch: NetWatch? = null
    private var approvalReceiver: android.content.BroadcastReceiver? = null

    /** Set by the Node activity while the kid home screen is showing. */
    @Volatile var homeVisible = false

    /** The "let the parent tablet change brightness?" card: remembers "Not now" and a Master's request. */
    val permAsk = PermissionAskState()

    /** After the parent PIN: kiosk and "lock to app" are paused until this time. */
    @Volatile var parentUntil = 0L
    val parentMode get() = System.currentTimeMillis() < parentUntil

    /** The emergency PIN prompt (3 second hold in the shield's top left corner) keeps the shield away while it is up. */
    val shieldSuspension = ShieldSuspension()
    private val _pinWanted = MutableStateFlow(false)
    /** True while the shield asked for the parent PIN pad: NodeApp shows it. */
    val pinWanted: StateFlow<Boolean> = _pinWanted

    /** True while the in-app Box is composed and NickOS is resumed: the only time "inapp" is truthful. */
    val boxActive = MutableStateFlow(false)
    /** True while a remote gesture is in flight: the overlay window and the in-app Box both step aside. */
    val injecting = MutableStateFlow(false)

    /** The prompt window ran out: forget the request, so the next corner hold reopens the PIN. */
    fun expirePin() { if (_pinWanted.value && !TouchShield.pinOpen(true, shieldSuspension.active(System.currentTimeMillis()))) _pinWanted.value = false }

    /** The touch lock is wanted right now: off by the parent, and not in parent mode, under a wall or during the PIN prompt. */
    fun shieldWanted(): Boolean = TouchShield.wanted(
        store.touchOff.value, parentMode, store.lockedBy.value != null, store.pausedMsg.value != null,
        shieldSuspension.active(System.currentTimeMillis()),
    )

    /** The hold finished: take every shield away, then open the existing parent PIN pad on the home screen. */
    fun requestShieldPin() {
        shieldSuspension.suspend(System.currentTimeMillis())
        _pinWanted.value = true
        overlay.refresh()
        device.goHome()
    }

    /** The PIN pad closed. A right PIN is parent mode (the shield stays away by itself); cancel or a wrong PIN puts the shield back at once. */
    fun shieldPinDone(entered: Boolean) {
        _pinWanted.value = false
        if (!entered) shieldSuspension.restore()
        overlay.refresh()
    }

    /** What is really in place, for the parent's switch: on (overlay), inapp (only the box inside NickOS) or none. */
    fun touchShieldWire(): String =
        NetMessages.effectiveShield(
            shieldWanted(), overlay.shieldAttached() || (injecting.value && device.canOverlay()),
            inAppBox = !device.canOverlay() && (boxActive.value || injecting.value),
        )

    /** Wrong-PIN lockout for the grown-up PIN pad. Memory only: see [PinGate]. */
    val pinGate = PinGate()

    /** The app a parent opened from the Master: the launcher-mode watchdog leaves it alone until Home. */
    @Volatile var openedByParent: String? = null

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode

    private val _paired = MutableSharedFlow<LinkedMaster>(extraBufferCapacity = 4)
    val paired: SharedFlow<LinkedMaster> = _paired

    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    /** Master ids currently connected. */
    val connected: StateFlow<Set<String>> = _connected

    /** Seconds until bedtime while the 5-minute warning should show, else null. */
    private val _bedtimeWarning = MutableStateFlow<Int?>(null)
    val bedtimeWarning: StateFlow<Int?> = _bedtimeWarning

    val overlay = NodeOverlay(ctx, store, this)

    /** "Dad is talking" while a parent speaks through push to talk; null otherwise. */
    private val _talkBanner = MutableStateFlow<String?>(null)
    val talkBanner: StateFlow<String?> = _talkBanner

    /** Push to talk: the state machine decides, [TalkPlayback] plays. Frames only count from a Master still linked here. */
    private val talkNotice = TalkNotice(ctx)
    private val talkIndicator = object : TalkIndicator {
        @Volatile private var noticeUp = false
        private fun way() = TalkBannerPolicy.way(device.canOverlay(), talkNotice.ready())
        override fun available() = way() != TalkBannerPolicy.Way.NONE
        /** True only when the banner window was really attached (or the home screen draws it), or the notification was posted. */
        override fun show(text: String): Boolean {
            _talkBanner.value = text
            return when (way()) {
                TalkBannerPolicy.Way.OVERLAY -> overlay.refreshTalkAndWait() || talkNotice.show(text).also { noticeUp = it }
                // The banner cannot be drawn over another app without the permission: say it with a loud notification instead.
                TalkBannerPolicy.Way.NOTICE -> talkNotice.show(text).also { noticeUp = it }
                TalkBannerPolicy.Way.NONE -> false
            }
        }
        /** Asked until the audio has drained. Looks at what is really on screen, and puts it back or switches to the notification if it is not. */
        override fun maintain(): Boolean {
            val text = _talkBanner.value ?: return false
            return when (way()) {
                TalkBannerPolicy.Way.OVERLAY -> overlay.talkOnScreen() || overlay.refreshTalkAndWait() || talkNotice.show(text).also { noticeUp = it }
                // The overlay was lost mid session: the notification takes over.
                TalkBannerPolicy.Way.NOTICE -> noticeUp || talkNotice.show(text).also { noticeUp = it }
                TalkBannerPolicy.Way.NONE -> false
            }
        }
        override fun clear() { _talkBanner.value = null; overlay.refresh(); talkNotice.clear(); noticeUp = false }
    }

    /** The status field `talk`: "ok", or "needs_permission" when this tablet would refuse talk audio (see [TalkIndicator.available]). */
    private fun talkStatus() = if (talkIndicator.available()) TalkMessages.STATUS_OK else TalkMessages.STATUS_NEEDS_PERMISSION

    /** The receiver is given the player at once, the player is told a failure through [talk]: see [talkPlayer]. */
    private val talkPlayer = TalkPlayer(
        newSpeaker = { AndroidSpeaker(ctx) }, indicator = talkIndicator,
        nameOf = { id -> TalkText.banner(store.masters.value.firstOrNull { it.id == id }?.name) },
        onFailed = { sid -> talk.sinkFailed(sid) }, log = { Log.w(TAG, it) },
    )
    private val talk: TalkReceiver = TalkReceiver(
        talkPlayer,
        linked = { id -> store.masters.value.any { it.id == id } },
        clock = System::currentTimeMillis,
    )

    /** The parent's "lock to landscape": see [LandscapeLock]. MainActivity follows the stored choice itself. */
    val landscape = LandscapeLock(AndroidRotationPort(ctx, store) { overlay.landscapeGuard(it) })
    fun applyLandscape() = landscape.apply(store.landscapeLock.value)

    /** Turns the lock off and gives the tablet's own rotation settings back (unlink, reset). */
    fun releaseLandscape() { store.setLandscapeLock(false); landscape.apply(false) }

    /** The parent's power settings (wake on charge, screen off on battery): see [PowerRunner]. */
    val powerRunner = com.nbaltas.nickos.power.PowerRunner(ctx, store)

    /** Watch together: the movie library, the synced player and the answers to its commands. */
    val together = NodeTogether(ctx, store, device, scope, { homeVisible }, ::broadcast)
    val videos get() = together.videos
    val syncPlayer get() = together.player

    /** The front camera, streamed to a Master that asks for it. */
    val cam = CamStreamer(ctx, this, device)
    fun broadcastCamState() = scope.launch { broadcast(cam.stateJson()) }

    /** The screen, streamed to a parent that asks for it once a grown-up has allowed it on this tablet. */
    val screen = ScreenStreamer(ctx, this)
    fun broadcastScreenState() = scope.launch {
        // Each Master hears only its own key, and only on a sealed link.
        sessions.forEach { (mid, c) -> c.send(screen.stateFor(mid, c.isSealed)) }
        broadcast(status())
    }

    @Synchronized
    fun start() {
        if (!NodeSupport.ok()) { Log.w("NickOS.Node", "this Android is too old to be a Node"); return }
        if (server != null) return
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(Wire.PORT)) }
        } catch (e: Exception) {
            ServerSocket(0)
        }
        server = ss
        Wire.multicastLock(ctx)
        advertiser.start(ss.localPort, LanAddress.ownIdentity(ctx))
        wifi.ensureOn()
        approvalReceiver = wifi.watchApproval()
        // Every network change: advertise again (the old mDNS record points at the old network), and turn WiFi
        // back on if it dropped. The ServerSocket binds the wildcard address, so it needs nothing.
        netWatch = NetWatch(ctx) { wifi.ensureOn(); wifi.refreshApproval(); advertiser.refresh(LanAddress.ownIdentity(ctx)) }.also { it.start() }
        jobs += scope.launch { acceptLoop(ss) }
        applyLandscape()
        overlay.refresh() // the touch lock comes back by itself after a reboot or an app update
        powerRunner.start()
        jobs += scope.launch { statusLoop() }
        jobs += scope.launch { bedtimeLoop() }
        jobs += scope.launch { while (isActive) { delay(200); try { talk.tick(); if (!talkPlayer.indicatorHolds()) { Log.w(TAG, "talk ended: the indicator was lost"); talk.stop() } } catch (e: Exception) { Log.w(TAG, "talk tick", e) } } }
        jobs += scope.launch { kioskLoop() }
        together.start()
        applyLockdown()
    }

    /**
     * The pairing window: the code lives 120 s, dies after five wrong tries, and works once (see [PairGate]). The UI
     * shows [pairingCode], so clearing the gate is what makes a stale code disappear.
     */
    private val pairGate = PairGate(onChange = { _pairingCode.value = it })

    fun openPairing(): String {
        val code = pairGate.open()
        scope.launch { delay(PairGate.TTL_MS + 100); pairGate.expireIfDue() }
        return code
    }

    fun closePairing() = pairGate.close()

    // ---- connections ----
    private val acceptGate = AcceptGate(max = 8, soTimeoutMs = 10_000)
    private val linkFlags = LinkFlags(ctx)

    private fun acceptLoop(ss: ServerSocket) {
        acceptGate.run(ss, { scope.isActive }) { s, ticket -> handle(Conn(s), ticket) }
    }

    private fun handle(c: Conn, ticket: AcceptGate.Ticket) {
        var masterId: String? = null
        try {
            val first = c.read(Conn.FIRST_LINE_MAX, Handshake.PRE_AUTH_MS) ?: return c.close()
            when (first.optString("t")) {
                // The Master's sweep asking who is here. No auth: the id is already broadcast by mDNS.
                "who" -> {
                    c.send(NetMessages.iam(store.deviceId, store.name.value)); return c.close()
                }
                "pair" -> {
                    if (first.optInt("v", 1) >= 2) {
                        val mid = Handshake.servePair(c, first, pairGate, ::status, { id, name, token ->
                            store.addMaster(LinkedMaster(id, name, token))
                            afterPaired()
                        }, nodeId = store.deviceId) ?: return
                        masterId = mid
                        linkFlags.setSec2(mid, true)
                    } else {
                        // A v1 Master (no sealed link yet) pairing a new Node: still the clear-text token, kept so an old
                        // Master can pair. It flips this Master's flag to "not sec2", and the gate applies all the same.
                        val code = pairGate.code()
                        val mid = first.optString("masterId")
                        if (code == null) { Handshake.refuse(c); return }
                        if (first.optString("code") != code || !Handshake.ID_RE.matches(mid)) {
                            pairGate.fail(); Thread.sleep(1000); Handshake.refuse(c); return
                        }
                        if (!pairGate.consume()) { Handshake.refuse(c); return }
                        val m = LinkedMaster(mid, first.optString("masterName", "Parent's Tablet"), Store.randomHex(16))
                        store.addMaster(m)
                        afterPaired()
                        first.optString("pin").takeIf { it.length == 4 }?.let { store.setPin(it) }
                        c.send(status().put("t", "paired").put("token", m.token).put("nodeId", store.deviceId))
                        masterId = m.id
                        linkFlags.setSec2(m.id, false)
                    }
                    store.masters.value.firstOrNull { it.id == masterId }?.let { _paired.tryEmit(it) }
                    // A pairing connection is never a command session: the Master reconnects with a full hello. Falling
                    // through here would let a relayed pairing request go on to send unsealed commands.
                    return c.close()
                }
                "hello" -> {
                    val mid = Handshake.nodeHello(c, first, { id -> store.masters.value.firstOrNull { it.id == id }?.token }, linkFlags)
                        ?: return c.close()
                    val m = store.masters.value.firstOrNull { it.id == mid } ?: return c.close()
                    first.optString("masterName").takeIf { it.isNotEmpty() && it != m.name }?.let { store.addMaster(m.copy(name = it)) }
                    // A Master that reached us FROM its car-network address proves this Node is on that network.
                    wifi.linkedFrom(c.socket.inetAddress?.hostAddress)
                    masterId = m.id
                }
                else -> return c.close()
            }
            ticket.release()
            c.socket.soTimeout = 0 // authenticated: the Master is silent between commands
            val id = masterId!!
            sessions.removeAll { (mid, old) -> (mid == id).also { if (it) old.close() } }
            sessions += id to c
            _connected.value = sessions.map { it.first }.toSet()
            c.send(status())
            c.send(cam.stateJson()) // a Master that reconnects learns the camera is already on
            c.send(screen.stateFor(id, c.isSealed))
            while (true) {
                val msg = c.read() ?: break
                // Screen start, stop and touch only on a sealed link, like talk: a legacy clear text session (v1) gets no key and no control.
                if (msg.optString("t") == "cmd" && RemoteMessages.isScreenCmd(msg.optString("c")) && !ScreenGate.allows(c.isSealed)) Log.w(TAG, "screen command refused on an unsealed link")
                else if (msg.optString("t") == "cmd") try { command(id, msg) } catch (e: Exception) { Log.w(TAG, "cmd ${msg.optString("c")} failed", e) }
                // Audio only hands a frame over here; nothing on this thread waits for the speaker.
                // And only on a sealed link: a legacy clear text session (v1) is refused talk.
                else if (TalkMessages.isTalk(msg)) {
                    if (!TalkGate.allows(c.isSealed)) Log.w(TAG, "talk refused on an unsealed link")
                    else try { talk.onMessage(id, msg) } catch (e: Exception) { Log.w(TAG, "talk failed", e) }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "connection ended: ${e.message}")
        } finally {
            c.close()
            sessions.removeAll { it.second === c }
            // Not when the same Master has already reconnected: its new link may be mid talk.
            masterId?.let { m -> if (sessions.none { it.first == m }) { talk.onMasterGone(m); screen.masterGone(m) } }
            _connected.value = sessions.map { it.first }.toSet()
            if (sessions.isEmpty()) {
                cam.stop(NodeCam.LINK) // no parent tablet left to look
                stopRing(RingMachine.Stop.LINK)
            }
        }
    }

    // ---- ring a lost tablet ----
    private val _ringing = MutableStateFlow(false)
    /** True while the alarm sounds; the overlay shows "Found me!" for it and the status reports it. */
    val ringing: StateFlow<Boolean> = _ringing

    /** Start, stop and timeout all go through this one lock, device effects included (see [RingController]). */
    private val ring = RingController(object : RingPort {
        override fun alarmVolume() = device.alarmVolume()
        override fun setAlarmVolume(v: Int) = device.setAlarmVolume(v)
        override fun saveVolumeBackup(v: Int): Boolean = store.setSavedAlarmVolume(v).also {
            if (!it) Log.w(TAG, "could not save the alarm volume; ringing at current volume")
        }
        override fun volumeBackup() = store.savedAlarmVolume()
        override fun clearVolumeBackup() { store.setSavedAlarmVolume(null) }
        override fun wake() = device.wake()
        override fun startAlarm(raiseVolume: Boolean) = device.startAlarm(raiseVolume)
        override fun stopAlarm() = device.stopAlarm()
        override fun shown(ringing: Boolean) { _ringing.value = ringing; overlay.refresh() }
    }, clock = { android.os.SystemClock.elapsedRealtime() }) // monotonic: a clock correction cannot stretch the 60 seconds

    private fun startRing() {
        val id = ring.start()
        if (id == 0L) return
        scope.launch {
            while (isActive && ring.isCurrent(id)) {
                delay(1000)
                if (ring.tick()) { pushStatus(); break }
            }
        }
    }

    /** The kid tapped the overlay, the parent tapped Stop, or the link dropped. Safe to call when not ringing. */
    fun stopRing(reason: RingMachine.Stop) {
        if (ring.stop(reason) && reason != RingMachine.Stop.LINK) pushStatus()
    }

    // ---- pause everyone ----
    private fun applyPause(p: NetMessages.PauseAll, masterId: String, parentName: String) {
        if (p.on) {
            // A Master re-sends this on every connect: pause the video only when the wall is new.
            if (store.pausedMsg.value == null) device.mediaKey("pause")
            store.setPausedOwner(masterId)
            store.setPausedBy(parentName)
            store.setPausedMsg(PauseAll.wallText(p.msg))
        } else if (PauseAll.mayClear(store.pausedOwner(), masterId, p.force)) {
            clearPause()
        } else Log.i(TAG, "pause belongs to another parent: resume ignored")
        overlay.refresh()
    }

    /** Lifts the wall on this tablet only: a right PIN on the wall, unlinking the last parent tablet, or a Master allowed to. */
    fun clearPause() {
        store.setPausedMsg(null)
        store.setPausedBy(null)
        store.setPausedOwner(null)
        overlay.refresh()
        pushStatus()
    }

    /** A fresh kid tablet starts with Jellyfin on its home screen, if it has it. */
    private fun afterPaired() {
        if (store.allowed.value.isEmpty()) device.jellyfinPackage()?.let { store.setAllowed(listOf(it)) }
    }

    fun broadcast(o: JSONObject) = sessions.forEach { it.second.send(o) }

    fun pushStatus() = scope.launch { broadcast(status()) }

    /** Kid tapped a "keep watching" poster: ask the Master(s) to start it in Jellyfin. */
    fun want(itemId: String) {
        device.jellyfinPackage()?.let { device.launch(it) }
        // Called from a tap: socket writes on the main thread throw, and Conn.send then closes the link.
        scope.launch { broadcast(JSONObject().put("t", "want").put("itemId", itemId)) }
    }

    /**
     * "Reset this tablet": tell the connected Masters first, so they unlink it instead of showing
     * it Offline forever. Best effort: a Master that's away has to unlink it by hand.
     */
    suspend fun sayGoodbye() = withContext(Dispatchers.IO) {
        withTimeoutOrNull(2000) { broadcast(JSONObject().put("t", "reset")) }
        delay(300)
    }

    // ---- commands from a Master ----
    private fun command(masterId: String, m: JSONObject) {
        val master = store.masters.value.firstOrNull { it.id == masterId }
        val c = m.getString("c")
        // Watch together answers the asking Master only, and never with a status broadcast (pings are frequent).
        if (together.command(c, m) { o -> sessions.firstOrNull { it.first == masterId }?.second?.send(o) }) return
        if (c in NodeTogether.LEAVES_PLAYER) syncPlayer.stop()
        when (c) {
            // The camera answers with its own camState, never with a status broadcast.
            "camStart" -> { CamMessages.decodeStart(m)?.let { cam.start(CamProfile.of(it.profile), it.maxMs, Store.randomHex(16)) }; return }
            "camStop" -> { cam.stop(NodeCam.STOPPED); return }
            // The screen answers with its own screenState too.
            "screenStart" -> { screen.start(masterId); return }
            "screenStop" -> { screen.stop(masterId); return }
            // Strict decode: a bad touch is dropped here, a good one is checked again by the streamer (live, this Master, 20 a second).
            "touch" -> { RemoteMessages.decodeTouch(m)?.let { screen.touch(masterId, it) }; return }
            // The parent tapped a greyed control: show the permission card (the screen decides if it can right now).
            NetMessages.ASK_PERMISSION -> { permAsk.masterAsks(); return }
            NetMessages.PAUSE_ALL -> NetMessages.decodePauseAll(m)?.let { applyPause(it, masterId, master?.name ?: "Parent's Tablet") }
            NetMessages.RING -> NetMessages.decodeRing(m)?.let { if (it) startRing() else stopRing(RingMachine.Stop.PARENT) }
            "media" -> device.mediaKey(m.getString("key"))
            "volume" -> device.setVolume(m.getInt("v"))
            "brightness" -> device.setBrightness(m.getInt("v"))
            "screen" -> if (m.getBoolean("on")) device.wake() else device.screenOff()
            "lock" -> {
                if (m.getBoolean("on")) {
                    store.setLockedBy(master?.name ?: "Parent's Tablet")
                    device.wake()
                } else store.setLockedBy(null)
                overlay.refresh()
            }
            "bedtime" -> {
                val min = m.optInt("minutes")
                store.setBedtimeAt(if (min > 0) System.currentTimeMillis() + min * 60_000L else 0)
                bedtimeBy = master?.name
            }
            "open" -> {
                val pkg = m.getString("pkg")
                if (store.lockedApp.value != null && store.lockedApp.value != pkg) store.setLockedApp(null)
                openedByParent = pkg
                applyLockdown(extra = pkg)
                device.launch(pkg)
            }
            "home" -> {
                openedByParent = null
                store.setLockedApp(null)
                applyLockdown()
                device.goHome()
            }
            "allow" -> {
                val pkg = m.getString("pkg")
                val cur = store.allowed.value
                store.setAllowed(if (m.getBoolean("on")) (cur - pkg) + pkg else cur - pkg)
                applyLockdown()
            }
            "lockApp" -> {
                val pkg = m.optString("pkg").ifEmpty { null }
                openedByParent = null
                store.setLockedApp(pkg)
                applyLockdown()
                if (pkg != null) device.launch(pkg) else device.goHome()
            }
            "rename" -> {
                m.optString("name").takeIf { it.isNotBlank() }?.let { store.setName(it) }
                if (m.has("color")) store.setColor(m.getInt("color"))
                m.optString("avatar").takeIf { it.isNotEmpty() }?.let { store.setAvatar(it) }
            }
            "pin" -> m.optString("pin").takeIf { it.length == 4 }?.let { store.setPin(it) }
            "resume" -> {
                val a = m.getJSONArray("cards")
                store.setResume((0 until a.length()).map { ResumeCard.from(a.getJSONObject(it)) })
            }
            "restart" -> m.optString("pkg").ifEmpty { device.jellyfinPackage() }?.let { device.restart(it) }
            "apps" -> sendApps()
            "update" -> scope.launch { com.nbaltas.nickos.update.Updater.updateIfNewer(ctx) }
            "unlink" -> {
                store.removeMaster(masterId); talk.onMasterGone(masterId); screen.masterGone(masterId)
                if (store.masters.value.isEmpty()) { releaseLandscape(); store.setTouchOff(false); overlay.refresh(); clearPause(); powerRunner.release(); screen.release() }
            }
            NetMessages.POWER -> NetMessages.decodePower(m)?.let { if (store.setPower(it.wake, it.offMin)) powerRunner.refresh() else Log.w(TAG, "could not save the power choice") }
            NetMessages.TOUCH -> NetMessages.parseTouch(m)?.let { if (store.setTouchOff(it)) overlay.refresh() else Log.w(TAG, "could not save the touch choice") }
            NetMessages.ORIENTATION ->NetMessages.decodeOrientation(m)?.let { if (store.setLandscapeLock(it)) applyLandscape() else Log.w(TAG, "could not save the landscape choice") }
            // Save (or with an empty ssid forget) the car hotspot. Never reconnects: Android picks the network.
            "wifi" -> NetMessages.decodeWifiCmd(m)?.let { wifi.save(it.ssid, it.pass, it.gw) }
        }
        broadcast(status())
    }

    @Volatile private var bedtimeBy: String? = null

    private fun sendApps() {
        val list = JSONArray()
        device.installedApps().forEach { a ->
            list.put(JSONObject().put("pkg", a.pkg).put("label", a.label).put("icon", device.iconBase64(a.pkg)))
        }
        broadcast(JSONObject().put("t", "apps").put("list", list))
    }

    /** Re-applies kiosk lockdown with the current allowlist (plus [extra], a one-off parent "open"). */
    fun applyLockdown(extra: String? = null) {
        device.applyLockdown(store.allowed.value + listOfNotNull(store.lockedApp.value, extra, device.jellyfinPackage()))
    }

    fun status(): JSONObject {
        val (bat, charging) = device.battery()
        val fg = device.foregroundPackage(homeVisible)
        return JSONObject()
            .put("t", "status")
            .put("name", store.name.value).put("color", store.color.value).put("avatar", store.avatar.value)
            .put("battery", bat).put("charging", charging).put("screenOn", device.screenOn)
            .put("volume", device.volume()).put("brightness", device.brightness())
            .put("fg", fg).put("fgLabel", fg?.let { device.label(it) })
            .put("allowed", JSONArray(store.allowed.value))
            .put("lockedApp", store.lockedApp.value).put("lockedBy", store.lockedBy.value)
            .put("bedtimeAt", store.bedtimeAt.value)
            .put("owner", device.isDeviceOwner).put("usage", device.hasUsageAccess()).put("overlay", device.canOverlay())
            .put("brightnessOk", device.brightnessOk).put("screenOffOk", device.screenOffOk)
            .put("talk", talkStatus())
            .put("screen", screen.statusWire()).put("control", screen.controlWire())
            .put("ringing", _ringing.value).put("paused", store.pausedMsg.value != null)
            .put("pausedOwner", store.pausedOwner())
            .put("jf", device.jellyfinPackage())
            .put("names", JSONArray(device.deviceNames()))
            .put("version", BuildConfig.VERSION_NAME)
            .put("caps", JSONArray(NodeTogether.CAPS + CamMessages.CAP + RemoteMessages.CAP + NetMessages.CAP + Handshake.CAP + TalkMessages.CAP + NetMessages.RING_CAP + NetMessages.PAUSE_CAP + NetMessages.HEARTBEAT_CAP + NetMessages.TOUCH_CAP))
            .put("touchOff", store.touchOff.value).put("touchShield", touchShieldWire())
            .put("wifi", NetMessages.encodeWifi(wifi.state()))
            .put("landscape", NetMessages.encodeLandscape(landscape.status(store.landscapeLock.value)))
            .put("power", NetMessages.encodePower(powerRunner.status()))
            .put("now", System.currentTimeMillis())
    }

    private suspend fun statusLoop() {
        var last = ""
        var lastSent = 0L
        while (scope.isActive) {
            delay(1500)
            // Also with no parent connected: the lock must hold after a reboot, and a permission may arrive later.
            applyLandscape()
            overlay.refresh() // parent mode runs out on its own: the shield has to come back then
            powerRunner.refresh()
            if (sessions.isEmpty()) continue
            val s = status()
            val key = s.toString().replace(Regex("\"now\":\\d+"), "")
            val now = System.currentTimeMillis()
            if (key != last || now - lastSent > 3_000) { // the Master's read timeout is 8s: three missed beats and it knows the link is dead
                broadcast(s); last = key; lastSent = now
            }
        }
    }

    /**
     * Launcher mode watchdog: a tablet that is not Device Owner has no lock task, so a kid who opens Settings is
     * sent back to NickOS. Device Owner tablets skip it entirely (nothing is read). Tag NickOS.Kiosk.
     */
    private suspend fun kioskLoop() {
        val watch = KioskWatch(
            inputs = {
                val owner = device.isDeviceOwner
                KioskWatch.Inputs(
                    deviceOwner = owner,
                    parentMode = parentMode,
                    homeVisible = homeVisible,
                    fg = if (owner) null else device.foregroundPackage(false),
                    allowed = store.allowed.value + listOfNotNull(store.lockedApp.value, openedByParent, device.jellyfinPackage()),
                    self = ctx.packageName,
                )
            },
            goHome = { device.goHome() },
            log = { Log.w("NickOS.Kiosk", it) },
        )
        while (scope.isActive) {
            delay(1000)
            try { watch.tick(System.currentTimeMillis()) } catch (e: Exception) { Log.w("NickOS.Kiosk", "tick", e) }
        }
    }

    private suspend fun bedtimeLoop() {
        while (scope.isActive) {
            delay(1000)
            val at = store.bedtimeAt.value
            if (at <= 0) { _bedtimeWarning.value = null; overlay.refresh(); continue }
            val left = ((at - System.currentTimeMillis()) / 1000).toInt()
            if (left <= 0) {
                _bedtimeWarning.value = null
                store.setBedtimeAt(0)
                store.setLockedBy(bedtimeBy ?: store.masters.value.firstOrNull()?.name ?: "Parent's Tablet")
                overlay.refresh()
                device.screenOff()
                broadcast(status())
            } else {
                _bedtimeWarning.value = if (left <= 300) left else null
            }
            overlay.refresh()
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }; jobs.clear()
        try { server?.close() } catch (_: Exception) {}
        server = null
        sessions.forEach { it.second.close() }; sessions.clear()
        advertiser.stop()
        netWatch?.stop(); netWatch = null
        approvalReceiver?.let { try { ctx.applicationContext.unregisterReceiver(it) } catch (_: Exception) {} }
        approvalReceiver = null
        together.release()
        talk.stop()
        cam.destroy()
        screen.release()
        overlay.hideAll()
        overlay.landscapeGuard(false)
        powerRunner.stop()
    }

    companion object { private const val TAG = "NickOS.Node" }
}
