# NickOS setup

NickOS is one app with two roles. Install the same APK on every tablet.

- **Master**: the parent's tablet. Sees and controls the linked tablets.
- **Node**: a kid's tablet. Locked-down home screen with only the apps the parent allows.

Both tablets must be on the same WiFi (home or the car router). No internet is needed.

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
# Lets the Master see the tablet's front camera
adb shell pm grant com.nbaltas.nickos android.permission.CAMERA
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

## Kid tablet cameras

The Master's Cameras screen shows the kids' tablets' front cameras next to any RTSP cameras you added. Tap a tablet's chip to turn its camera on; tap again to turn it off. The layout changes by itself as cameras come and go (one, two side by side, a grid, or a spotlight with a strip); tap a picture to make it the spotlight.

A tablet camera only runs while the Cameras screen is open on the Master, and it turns off by itself after the time you pick in Settings, Cameras (10 minutes unless you change it). A tablet that gets warm drops to a smaller picture; one that gets too hot turns its camera off for two minutes. Both are shown on the tile.

Kid tablets with the kid lock get camera access by themselves on most tablets. If a tile says "Camera permission needed", open the grown-up menu on that tablet (hold the avatar, parent PIN) and tap Allow camera, or run the adb line above. Android shows its own small camera dot on the kid's tablet while the camera is on; NickOS adds nothing else there. There is no sound: the microphone is never used.
## Movies without a server (Watch together)

NickOS now has its own movie library, so a Jellyfin server is optional. It works with ordinary video files (MP4, MKV) in the **Movies** or **Download** folder of any tablet, the parent's tablet included. Copy files there with the Files app, a USB cable, or a browser download. Jellyfin's own downloads can't be used: the Jellyfin app keeps them in a private folder other apps can't read.

On the Master: tap the TV icon in the top bar. You see every movie and show on every tablet, and which tablets have each one.

- **Send** a movie to a kid's tablet: open the movie, tap Send next to the tablet (or "Send to all"). The copy goes over your WiFi from whichever tablet has the file, usually the parent's. It carries on by itself if the WiFi drops or the tablet sleeps, and it checks the copy is complete at the end. Do this at home; then the movie is on the tablet for the car.
- **Play** a movie on one tablet or several at once: tick the tablets, Play. Pause, skip, next episode, stop and volume act on all of them together, and each tablet also has its own volume slider. A tablet that comes online late joins at the right spot. Shows are grouped when the filename has an episode code like `S02E05`.

Kid tablets with the kid lock get video access by themselves. Without the kid lock, open the grown-up menu on that tablet (hold the avatar, parent PIN) and tap **Allow videos** once. Kid tablets on an older NickOS show "Update needed" until they are updated from Settings → About.

Jellyfin still works: open a tablet, Watch tab, "Jellyfin (legacy)".

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

## Screens and their saved pictures

`design/screens/*.png` is one picture per screen. The ordinary test run
(`.\gradlew.bat :app:testDebugUnitTest`) draws every screen again and **fails if one no longer
matches its picture**, naming the screen. The renders use a pinned clock (Mon 12 Oct 2026, 12:00),
so they do not change from run to run.

If a screen changed on purpose, re-record the pictures and commit them:

    .\gradlew.bat :app:recordRoborazziDebug

then look at the changed PNGs in `design/screens` before committing. The tolerance is
`CHANGE_THRESHOLD` in `DesignScreensTest.kt` (a tiny fraction of pixels, to ignore font
smoothing differences between computers).
