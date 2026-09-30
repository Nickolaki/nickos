# NickOS setup

NickOS is one app with two roles. Install the same APK on every tablet.

- **Master**: the parent's tablet. Sees and controls the linked tablets.
- **Node**: a kid's tablet. Locked-down home screen with only the apps the parent allows.

Both tablets must be on the same WiFi (home or the car router). No internet is needed.

## 1. Kid tablet (Node)

1. Install NickOS, open it, pick **Node**, name the tablet, then leave the 6-digit code on screen.
2. When Android asks, make NickOS the **Home app** ("Always").
3. Install the **Jellyfin** app (`org.jellyfin.mobile`) and sign it in as the kid's Jellyfin user.

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

## How the pieces talk

- The Node listens on TCP port **47470** and advertises itself over mDNS as `NickOS-<id>`
  (`_nickos._tcp`). The Master finds it, pairs with the 6-digit code, and keeps one connection
  open. Messages are one JSON object per line.
- Playback uses Jellyfin's own remote control ("Play On"): the Master asks the Jellyfin server
  to play an item on the kid tablet's Jellyfin session. The session is matched by the tablet's
  device name, then by the tablet's Jellyfin user.
- Volume, brightness, screen, lock and bedtime act on the tablet itself, not through Jellyfin.
