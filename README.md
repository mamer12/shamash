# Shamash

**An open-source home screen for Android TV and Google TV that shapes itself around you, not around ads.**

<p align="center"><a href="docs/media/shamash.mp4"><img src="docs/media/showcase.gif" width="800" alt="Shamash showcase"></a></p>
<p align="center"><sub>▶ <a href="docs/media/shamash.mp4">Watch the full 84-second showcase</a> (made with code: <a href="video/">video/</a>)</sub></p>

Shamash (the Mesopotamian sun god, giver of light) replaces your TV's launcher. On first run it asks what you watch,
which apps you pay for, your teams and your tastes, and the whole home screen follows those answers.

Works on Android TV / Google TV 9 and newer (TVs and boxes). Built for and first being tried on a TCL Google TV
(Android 11); reports from other TVs are welcome.

## Why switch

| The TV's stock home screen | Shamash |
|---|---|
| Ads and promoted apps on the first screen | Your apps and what's on *your* services; the TV maker's promo apps turned off |
| Fixed rows you can't change | Hide, pin, rename and reorder every row; presets; rows that change with the time of day |
| One profile for the whole family | Profiles with a PIN each, a Kids profile with content levels, daily limits and bedtime |
| Closed | Open source (MIT): read it, fork it, add your own sources |
| Hard to undo changes | Nothing is uninstalled; one press of **Original** brings the TV's own home screen back |

## Features

- **Home and layout**: a full-screen carousel of what's worth watching on your services, a greeting and a moment
  row (*Tonight*, *Before bed*...) that follow the time of day, Continue watching, Top 10, Popular on each of your
  apps. A row editor (hide, pin, rename, reorder), layout presets and a *Quick start* in setup.
- **Profiles and parental controls**: a profile picker, a 4-digit PIN per profile, Kids and Guest profiles, content
  levels (All / Family / Kids) checked on every title from every source, an allow-list of apps, daily limits and
  bedtime.
- **Search**: one search across your services, Cinemana and YouTube; voice search with the remote's mic, in English
  and Arabic.
- **Match Centre**: football, F1, tennis, NBA and cricket. Follow teams, players or drivers; a match-day billboard,
  a reminder before kick-off and a live score **over any app**, alerts per follow, quiet hours, goal-only mode, and
  where each match is on in your region.
- **Phone companion**: open a page on your phone (same Wi-Fi) to send a link or a title to the TV, type for the TV
  (keys too), edit rows, teams and feeds, queue titles, and back up or restore.
- **Add-ons** (each off until you turn it on): Cinemana (with Watchme+ installed it comes on by itself and titles
  open there), Plex, Jellyfin / Emby, IPTV (M3U + XMLTV guide), My feed (RSS, Atom, JSON), podcasts, radio,
  Twitch / Kick live, Trakt / Simkl, and *Today* widgets (headlines and prices).
- **Made for you**: recommendations from what you open and like, a one-line reason on recommended rows,
  *Not for you* on any card, and one My List across sources.
- **Backup**: one encrypted file (passphrase), exported and imported on the TV or the phone.
- **Accessibility**: larger text, high contrast, reduce motion, TalkBack descriptions on every card.
- **Arabic**: the whole app in English and Arabic, with a right-to-left layout.

## Install with Claude (easiest)

You don't need to know Android tools: [Claude Code](https://claude.com/claude-code) can do the computer side for you
and tell you what to press on the TV.

1. Install Claude Code on your computer (macOS, Linux, or Windows with WSL).
2. Get Shamash and open Claude in it:

   ```bash
   git clone https://github.com/mamer12/shamash.git   # or your fork
   cd shamash
   claude
   ```

3. Say, for example: **"Install Shamash on my TV at 192.168.1.50"** (your TV's IP address is in the TV's network
   settings), or type the slash command **`/install-tv 192.168.1.50`**.

Claude follows [CLAUDE.md](CLAUDE.md): it checks and installs the tools, builds the app, connects to the TV and
installs it. You accept the prompts on the TV and make the final switch yourself. Your keys never go in the chat.

## Install it yourself

Short version below; step by step, updating and uninstalling are in **[docs/INSTALL.md](docs/INSTALL.md)**.

You need a JDK 17 or newer, the Android SDK (platform 36) and `adb`.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties      # where your Android SDK is
./gradlew assembleRelease                                # app/build/outputs/apk/release/app-release.apk
```

To sign with your own key (recommended: updates must be signed with the same key), add to `local.properties`
(git-ignored, never commit it):

```properties
signing.storeFile=/path/to/your.keystore
signing.storePassword=…
signing.keyAlias=…
signing.keyPassword=…
```

Without these, the release build is signed with your computer's debug key.

On the TV: **Settings › System › About › Android TV OS build**, press OK 7 times, then **Settings › System ›
Developer options › USB debugging: On** (and **Network debugging** if your TV shows it). Then:

```bash
adb connect <TV-IP>:5555      # the TV asks "Allow USB debugging?": accept and tick "Always allow"
adb install -r app/build/outputs/apk/release/app-release.apk
```

Open **Shamash** from the TV's apps. Setup asks, one screen at a time (all skippable but the welcome): about you,
*Quick start*, your apps, your teams, what you like, how Home should look, optional keys, and finally
**Make Shamash your home screen**. The TV asks once more to allow debugging: that's Shamash using the TV's own
debugging connection to make the switch.

## The way back

- **Settings › Home screen › Original** turns back on everything Shamash turned off and brings back the TV's own
  home screen and screensaver.
- **Watchdog**: if Shamash crashes 3 times within 5 minutes, the TV goes back to Original by itself.
- The switch only turns apps off (`pm disable-user`); it never uninstalls anything, never touches picture, sound,
  inputs, the tuner, the remote, updates or the Play Store, and never turns off an app that saved your Wi-Fi.

## Optional keys

Free, and only yours. Shamash works without them (the parts that need one stay hidden). Put them in the
git-ignored `local.properties` before building; the build bakes them in, so there's no key screen on the TV:

```properties
tmdb.key=...
youtube.clientId=...
youtube.clientSecret=...
```

(CI can use `SHAMASH_TMDB_KEY`, `SHAMASH_YOUTUBE_CLIENT_ID`, `SHAMASH_YOUTUBE_CLIENT_SECRET` instead.) An APK built
with your keys carries them, so don't publish it.

| Key | What it adds | Where to get it |
|---|---|---|
| **TMDB API key** | The carousel, Top 10, Popular on your apps, search across services | themoviedb.org › Settings › API › Create |
| **YouTube OAuth client** (ID + secret) | Sign in to YouTube: Recommended for you, YouTube search | console.cloud.google.com › enable *YouTube Data API v3* › OAuth consent screen (External, add yourself as a test user) › Credentials › OAuth client ID › *TVs and Limited Input devices* |

Then **Settings › YouTube › Sign in**: the TV shows a code to enter at google.com/device on your phone.

**Phone remote**: **Settings › Phone remote** shows an address like `http://192.168.1.20:8765` and a 6-digit PIN.
Open it on your phone (same Wi-Fi), enter the PIN once, and add the page to your phone's home screen.

## Performance

Shamash is built for cheap TVs. The budgets in [docs/specs/performance.md](docs/specs/performance.md), set for a
TCL with a MediaTek MT5889, 2.7 GB RAM and Android 11:

| ID | Target |
|---|---|
| PERF-01 | Cold start to the first frame of Home ≤ 1.5 s; back Home from an app ≤ 300 ms |
| PERF-02 | Scrolling and moving focus: 90th-percentile frame ≤ 16 ms, janky frames ≤ 5% |
| PERF-03 | ≤ 120 MB PSS after 10 minutes of browsing |
| PERF-04 | Images requested at the size they are drawn; the carousel at ≤ 1280×720 |
| PERF-05 | Data from several sources at once causes one recomposition of the rows |

How it's designed to get there: an R8-minified release build; images loaded at their drawn size with a memory cache
capped at 20% of the heap; all home data combined off the main thread into one state; one HTTP client with a 20 MB
disk cache and per-source refresh intervals (TMDB 6 h, YouTube 3 h, weather 30 min...); all network and disk work in
the background; rows you hid aren't fetched; the app you leave is closed when you come back Home (except the ones
you keep running). The release APK is about **4.6 MB**.

These are targets and design choices, not numbers measured on your TV. To measure on yours (`am start -W`,
`dumpsys gfxinfo io.shamash`, `dumpsys meminfo io.shamash`) and share the results, see
**[docs/PERFORMANCE.md](docs/PERFORMANCE.md)**.

## Privacy and security

- **No account, no analytics, no telemetry.** Shamash talks only to the services whose content it shows (TMDB,
  JustWatch, YouTube, TOD, ESPN, Open-Meteo, and the add-ons you turn on).
- **Keys and sign-ins stay on the TV**: never in the source, excluded from Android's cloud backup and device
  transfer, never written to logs, and requests that carry them skip the HTTP disk cache.
- **Phone page**: only on your home network, behind a 6-digit PIN; wrong PINs lock it for a while.
- **Backups are always encrypted** (AES-256-GCM with your passphrase); keys and sign-ins are left out unless you
  tick the box to include them.
- Voice search uses the TV's own recognizer; Shamash doesn't store or send audio.

## Add-ons

Every add-on is off until you turn it on in **Settings › Add-ons** (Cinemana also comes on when the Watchme+ app is
installed). Each has its fields, *Test connection*, the rows it offers and *Remove*. To write your own, or to
delete one from your fork, read the [add-on guide](docs/ADDONS.md).

## Troubleshooting

| Problem | Fix |
|---|---|
| "Allow USB debugging?" never appears | Turn USB debugging off and on, then `adb connect` again |
| The switch says it can't reach the TV's debugging | Developer options › USB / Network debugging must be on |
| The switch stops and talks about Wi-Fi | The TV's first setup saved your Wi-Fi: forget the network in the TV's settings, join it again, switch again |
| Pressing Home asks which home screen to use | Another launcher app is installed: uninstall it, or pick Shamash › *Always* |
| No carousel or Popular rows | Add `tmdb.key` to `local.properties`, rebuild and reinstall |
| `adb install` fails with a signature error | The installed copy was signed with another key: see [docs/INSTALL.md](docs/INSTALL.md#updating) |
| The remote's Netflix / YouTube buttons stopped (TCL) | Shamash re-checks about a minute after a reboot or an update; if not, Settings › Home screen › *Use Shamash* again |
| Slow full start (TV logo, Google TV animation) | That's the TV booting after a power cut; the remote's power button is instant on |

To see what the switch did: `adb logcat -s Shamash`.

## For developers

Shamash is **spec-driven**: every capability is a list of numbered requirements in [docs/specs](docs/specs/README.md),
and code and tests point back to them (`// SPEC: HOME-04`, tests named like `` `LAY_04 ...` ``). Change behavior by
changing the spec first.

Kotlin and Jetpack Compose for TV, no DI framework. Layers point inward, `feature → domain ← data`: `domain` holds
the models, the `Provider` contract and pure logic; `data` the sources (HTTP, DataStore, TMDB, YouTube, ESPN,
add-ons); `feature` one package per capability; `ui` the design system; `app` the activity shell, the `Opener` and
the `ServiceLocator`. Every source fails soft (an error is an empty row, never an empty home screen), and all
user-facing text lives in English and Arabic string files.

```bash
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew assembleRelease        # the release APK
```

- New content source: implement `domain.Provider` and list it in `data/Sources.kt`; optional ones are add-ons
  ([docs/ADDONS.md](docs/ADDONS.md)).
- New TV brand: add its promo apps to `feature/tvcontrol/BrandProfiles.kt`, one reason per line.
- The showcase video is code too (Remotion): see [video/README.md](video/README.md) to edit and re-render it.

## License

MIT, see [LICENSE](LICENSE).
