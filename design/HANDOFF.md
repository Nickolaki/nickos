# NickOS — UI design handoff

NickOS is a custom Android launcher (home screen app) for family tablets. Each tablet has a role:

- **Master**: the parent's tablet (e.g. mounted in the car). Sees and controls linked tablets.
- **Node**: a kid's tablet, locked down. Shows only the apps the parent allows.

Tablets link over local WiFi. No internet is needed. It replaces Home Assistant + Fully Kiosk with something simple enough for friends and family to set up themselves.

## What's in this folder

| Path | What |
|---|---|
| `screens/*.png` | Rendered screenshot of every screen (1280×800 landscape; portrait screens 800×1280). **Source of truth for look.** |
| `html/*.html` | Static HTML/CSS of each screen. Open in a browser. Exact values (sizes, radii, colours) live here. |
| `HANDOFF.md` | This file. |

The screenshots use fallback system fonts. The intended fonts are listed under Typography below.

The HTML is a **visual reference, not code to ship**. Rebuild the UI natively (Jetpack Compose recommended).

## Screen inventory

### 1. First-run setup
| File | Screen |
|---|---|
| `Setup-Welcome` | Welcome, "Get started" |
| `Setup-Role` | "What is this tablet?": Master (I control) / Node (kid's tablet) |
| `Node-Name` | Node: name the tablet, pick colour (7) + avatar (initial or icon) |
| `Node-Code` | Node: big 6-digit pairing code, "Waiting for the Master tablet on this WiFi…" |
| `Master-AddCode` | Master: enter 6-digit code (boxes + big keypad) |
| `Master-Linked` | Master: success + preview card, "Add another" / "Continue" |
| `Master-Jellyfin` | Master: optional Jellyfin connect (address, username, password, Test connection), "Skip for now" / "Save & finish" |

### 2. Master home
| File | Screen |
|---|---|
| `Master-Home` | 2×2 grid of tablet cards + "Add tablet" card. Top bar: clock/date, quick apps, "Dad's Tablet · Master" chip, settings |
| `Master-Home-Empty` | Empty state: no tablets linked |
| `Master-Home-Portrait` | Same, single column |

Each tablet card shows the avatar, name, online dot, screen state, a **large battery %** (red under 25%) and one of three body states:
- **Playing**: poster, title, episode, progress bar, time left
- **In an app**: app icon + name, "Nothing playing"
- **Offline**: "Can't reach this tablet", last-seen time, last-known battery

Tapping a card opens Tablet Control.

### 3. Tablet control (full screen, 4 tabs)
Header: back, avatar + name + status, segmented tabs (Now Playing / Watch / Apps / Device), battery %.

| File | Screen |
|---|---|
| `Control-NowPlaying` | Backdrop, poster, title/episode, scrub bar, −10s / play-pause / +10s, Next episode, Stop. Right column: volume + brightness sliders (control the tablet itself), Up next |
| `Control-NothingPlaying` | Empty state: kid is in an app. "Bring back to home" / "Find something to watch" |
| `Control-Watch` | Kid's Jellyfin library: Home/Movies/Shows chips, search, Continue Watching, Next Up, Movies rows |
| `Control-Series` | Series details: Resume on [tablet], Play episode from start, season tabs, episode list (watched / in progress / unwatched) |
| `Control-Movie` | Movie details: Resume on [tablet], Play from start |
| `Control-Apps` | Grid of every installed app. Tap = open on tablet now. "Home" switch = allowed on home screen. "OPEN" badge on the current app. Buttons: Bring back to home, Lock to [current app] |
| `Control-Device` | Screen off/on, Lock now, bedtime timer (15/30/45/60 + live countdown + cancel), battery, volume, brightness, Restart Jellyfin, Rename, Unlink |
| `Control-Offline` | Empty state: tablet offline, tips, Try again |

### 4. Node (kid's tablet)
| File | Screen |
|---|---|
| `Node-Home` | "Hi Hailey", "Linked to Dad's tablet" chip, battery, clock. Big Jellyfin tile (2×2, poster art, "Keep watching…") + allowed app tiles |
| `Node-Home-Portrait` | Portrait version |
| `Node-PIN` | Hidden parent exit: 4-digit PIN pad over dimmed home |
| `Node-Locked` | "Screen locked by Dad" full-screen overlay |
| `Node-Bedtime` | "Bedtime in 5 minutes" toast with countdown |

**Hidden exit:** long-press the top-left corner (over the avatar) for 3 s to open the PIN pad. Nothing on screen hints at it. The Node has no settings, back or recents access.

### 5. Settings (Master)
Left nav + content pane: `Settings-Tablets` (list, rename, unlink, add), `Settings-Jellyfin` (server + **which Jellyfin user each tablet shows**), `Settings-PIN` (change the parent PIN, synced to all Nodes), `Settings-Theme` (Dark / Light / Match system), `Settings-About` (version, reset).

## Design tokens

### Colour (dark theme)
| Token | Hex | Use |
|---|---|---|
| bg | `#0B0E13` | Master background |
| surface | `#151A22` | Cards |
| surface2 | `#1E2430` | Buttons, inputs, keys |
| line | `#2A3140` | Borders, dashed empty states, slider track |
| text | `#EEF1F6` | Primary text |
| muted | `#A0A8B6` | Secondary text |
| accent | `#FFB547` | Primary buttons, active tab, progress, toggles on |
| onAccent | `#1C1305` | Text/icons on accent |
| online | `#3DDC84` | Online dot, success |
| offline | `#5A6273` | Offline dot |
| danger | `#FF8A7A` | Low battery, Unlink, Reset |
| nodeBg | `#121433` | Node home background |
| nodeSurface | `#1D2150` | Node Jellyfin tile |

Kid avatar colours (chosen at setup): `#FF7A59`, `#FFB547`, `#B5E550`, `#2EC4B6`, `#4DA8FF`, `#9B7BFF`, `#FF6FB5`. Text/icons on these use `#10131F`.

### Typography
- Display (titles, numbers, clock, battery %): **Bricolage Grotesque** 700–800
- Body/UI: **Figtree** 400–800
- Key sizes (px at 1280×800): clock 48, battery on cards 40, screen titles 46–56, card titles 28–32, body 17–21, eyebrow labels 13 uppercase with 0.12em tracking

### Shape and spacing
- Radii: cards 28–30, Node tiles 36, buttons 18 (big 22), app icons 28% of size, chips/pills fully rounded
- Screen padding 40 (portrait 32), grid gaps 20–24
- Touch targets: min 60 dp for buttons, 72 dp for primary, keypad keys 76–84 dp tall, slider thumb 38

### Icons
Rounded stroke icons at 2–2.2 stroke width (Lucide-style). Use a matching icon set.

## Behaviour notes
- Links run over local WiFi only (e.g. NSD/mDNS discovery + local socket/WebSocket). Pairing uses the 6-digit code shown on the Node.
- The Master shows live status for each Node: online, battery, screen on/off, foreground app, Jellyfin playback state and position.
- Volume/brightness sliders and the Device tab act on the **remote** tablet.
- Apps tab "Home" switch = allowlist for the Node home screen. "Lock to app" = kiosk/lock-task mode on that app.
- Bedtime timer: the Node shows a toast 5 min before, then the screen turns off.
- "Screen locked by parent" is a full-screen overlay that the kid can't dismiss.
- Placeholder content (show titles, names, `[VERSION]`) is sample data.
