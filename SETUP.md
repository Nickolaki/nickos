# NickOS setup

NickOS is one app with two roles. Install the same APK on every tablet.

- **Master**: the parent's tablet. Sees and controls the linked tablets.
- **Node**: a kid's tablet. Locked-down home screen with only the apps the parent allows.

Both tablets must be on the same WiFi (home or the car router). No internet is needed.

## Easiest: QR setup (new or factory-reset kid tablet, no computer)

1. On the parent tablet: **Add tablet → "Brand-new or reset tablet? Set it up with a QR code"**.
   Optionally type your WiFi into that screen so the kid tablet joins it by itself.
2. On the kid tablet's very first "Welcome" screen, tap the same empty spot **6 times**.
   A QR scanner opens (older Android may ask for WiFi first).
3. Scan the code. Android downloads NickOS from the update server and switches the kid lock on.
4. NickOS opens straight at "Name this tablet". Then pair it with its 6-digit code as usual.

The APK comes from `https://nickflix-config.nbaltas.org/nickos/nickos-latest.apk` (public on
purpose: it holds no secrets). This only works on a tablet with no accounts yet, which is
Android's rule for kid lock, same as the adb route below.

## 1. Kid tablet (Node), the adb way

1. Install NickOS, open it, pick **Node**, name the tablet, then leave the 6-digit code on screen.
2. When Android asks, make NickOS the **Home app** ("Always").
3. Install the **Jellyfin** app (`org.jellyfin.mobile`) and sign it in as the kid's Jellyfin user.
   Leave its player on the default **Integrated** player: it plays more file types directly. From the
   Master you get play/pause/stop and skip (back 10 s, forward 30 s) but no dragging to a spot.
   Jellyfin's **Web player** setting gives full scrubbing from the Master, at the cost of codec support.

### Kid lock (one time, over adb)

Without this step NickOS is just a home screen, and the Home or Recents buttons get a kid out.
With it, NickOS uses Android's own kiosk mode (the same thing Fully Kiosk Plus uses).

Android only allows it when **no Google or other account is signed in** on the tablet. Either
remove the accounts first (Settings → Accounts) and add them back afterwards, or do it on a
freshly reset tablet.

```sh
adb shell dpm set-device-owner com.nbaltas.nickos/.node.AdminReceiver
```

Then grant the permissions that make the rest work:

```sh
# "Screen locked" and bedtime warnings on top of Jellyfin and other apps
adb shell appops set com.nbaltas.nickos SYSTEM_ALERT_WINDOW allow
# Lets the Master see which app is open
adb shell appops set com.nbaltas.nickos GET_USAGE_STATS allow
# Brightness control (only needed if the kid lock step above was skipped)
adb shell appops set com.nbaltas.nickos WRITE_SETTINGS allow
adb shell pm grant com.nbaltas.nickos android.permission.POST_NOTIFICATIONS
```

### Getting out

On the kid tablet, hold the avatar (top left) for 3 seconds, then enter the parent PIN (default
**0000**, change it on the Master under Settings → Parent PIN). The grown-up menu has
**Reset this tablet**, which switches the kid lock off and returns the tablet to normal.
While Device Owner is set, NickOS can't be uninstalled until you use that button.

## 2. Parent tablet (Master)

1. Install NickOS, open it, pick **Master**.
2. Type the 6-digit code from the kid's tablet.
3. Optionally connect Jellyfin (server address, username, password). An admin login lets the
   Master see every tablet's playback and choose which Jellyfin user each tablet shows.
4. Make NickOS the Home app if you want it as the tablet's launcher.

Rename the Master under Settings → Linked tablets (it shows up on kid tablets as
"Linked to Dad's tablet" and "Screen locked by Dad").

## Updates

The parent tablet checks for a new NickOS every 6 hours and shows an **Update** pill on its home
screen. Settings → About lists every kid tablet's version with an **Update** button: kid-locked
tablets install silently, others show Android's own Install prompt. Nothing updates by itself.

Publishing a build (on the server): bump `versionCode` + `versionName` in `app/build.gradle.kts`,
commit, build the release, then copy it into nickflix-config's data dir as
`releases/nickos-latest.apk` and write `version-nickos.json` (`versionCode`, `versionName`,
`notes`). Check with `curl localhost:3301/nickos/version`.

## How the pieces talk

- The Node listens on TCP port **47470** and advertises itself over mDNS as `NickOS-<id>`
  (`_nickos._tcp`). The Master finds it, pairs with the 6-digit code, and keeps one connection
  open. Messages are one JSON object per line.
- Playback uses Jellyfin's own remote control ("Play On"): the Master asks the Jellyfin server
  to play an item on the kid tablet's Jellyfin session. The session is matched by the tablet's
  device name, then by the tablet's Jellyfin user.
- Volume, brightness, screen, lock and bedtime act on the tablet itself, not through Jellyfin.
