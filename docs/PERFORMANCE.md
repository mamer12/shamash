# Performance

Shamash is meant to feel instant on cheap TVs. This page lists the targets, the design choices behind them (with the
files that implement them) and how to measure on your own TV. **The targets are budgets, not measurements**: the
table at the end is empty until people fill it in.

## Targets

From [specs/performance.md](specs/performance.md). Reference TV: TCL with a MediaTek MT5889, 2.7 GB RAM, Android 11.

| ID | Target |
|---|---|
| PERF-01 | Cold start to the first frame of Home ≤ 1.5 s; returning Home from an app ≤ 300 ms to a fully drawn screen |
| PERF-02 | Scrolling rows and moving focus: 90th-percentile frame ≤ 16 ms, janky frames ≤ 5% (`dumpsys gfxinfo`) |
| PERF-03 | Shamash's process ≤ 120 MB PSS after 10 minutes of browsing |
| PERF-04 | Images requested at the size they are drawn; the carousel uses ≤ 1280×720 |
| PERF-05 | Data arriving from several providers at once triggers one recomposition of the rows |

The release APK measured **4,614,612 bytes (about 4.6 MB)** when this page was written
(`app/build/outputs/apk/release/app-release.apk`).

## Design measures

Paths are under `app/src/main/java/io/shamash/` unless they start with `app/`.

| Measure | Where | Spec |
|---|---|---|
| Release build shrunk and optimized by R8 (`isMinifyEnabled = true`, `proguard-android-optimize.txt`) | `app/build.gradle.kts`, `app/proguard-rules.pro` | |
| Every image requested at its drawn size; one image loader with a memory cache capped at 20% of the heap | `ui/Images.kt` (`Picture`, `Images.request`) | PERF-04, ARCH-04 |
| The billboard photo at 1280×720; TMDB posters as `w342`, wide art as `w1280`; screensaver art 1280 wide | `ui/Images.kt` (`HERO_W`, `HERO_H`), `data/tmdb/TmdbJson.kt` (`TmdbImages`), `feature/screensaver/ShamashDream.kt` | PERF-04 |
| One home state: profile, history, loaded data, the clock and personal signals are combined, coalesced for 120 ms, de-duplicated and built on a background dispatcher into one immutable `HomeUi` | `feature/home/HomeModel.kt`, `feature/home/HomeUi.kt`; test `PERF_05` in `app/src/test/java/io/shamash/feature/home/HomeRowsTest.kt` | PERF-05 |
| The home state lives as long as the process: coming back Home shows the last state at once and refreshes only what is due | `feature/home/HomeModel.kt` | HOME-14 |
| Sources load only while Home is on screen (`resume` / `pause`) | `feature/home/HomeModel.kt` | |
| Rows you hid are not fetched, unless the carousel or Discover still needs that source | `domain/LayoutFetch.kt`, `HomeModel.fetchNeeded` | LAY-04 |
| One HTTP client for the whole app: a shared connection pool, a 20 MB disk cache, 8 s connect and 15 s read timeouts | `data/Http.kt` | ARCH-04 |
| Each source refreshes on its own interval: TOD 1 h, Cinemana 2 h, YouTube 3 h, TMDB 6 h, weather 30 min, matches 3 h (10 min when a follow plays within 3 h, 1 min live); add-on servers 2 h, IPTV and Twitch / Kick 5 min, Today widgets 15 min; an empty answer is retried after 10 min | `data/SourceKit.kt` (`Refresh`), `data/ServerProvider.kt`, `data/espn/Polling.kt`, each provider | HOME-14, MC-14 |
| One slow add-on row can't hold the others: 8 s per row, then it is left out | `data/ServerProvider.kt`, `domain/RowSources.kt` | PEX-06 |
| No network or disk on the main thread: all I/O is `suspend` on `Dispatchers.IO` | `data/Http.kt`, `data/ServerHttp.kt` | ARCH-03 |
| The app you leave is closed when you come back Home, except your *Keep running* apps (Netflix and YouTube by default) | `feature/home/AppCloser.kt` | HOME-15 |

## Measure it on your TV

Connect first (`adb connect <TV-IP>:5555`) and build the release APK: debug builds are much slower and don't count.

### Cold start (PERF-01)

```bash
adb shell am force-stop io.shamash
adb shell am start -W -n io.shamash/.MainActivity
```

Read `TotalTime` (ms) and check `LaunchState: COLD`. Run it 5 times and note the median. When Shamash is the home
screen, Android may bring it straight back after `force-stop`, so the next start isn't cold: measure in *Original*
mode, or right after a reboot with `adb logcat -d | grep "Displayed io.shamash"`.

### Back to Home from an app (PERF-01)

With Shamash as the home screen, open an app (YouTube, for example), wait until it plays, then:

```bash
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
```

Read `TotalTime`. Shamash doesn't report a separate "fully drawn" time, so this is the time to its first frame;
judge "fully drawn" by eye, or record it with `adb shell screenrecord /sdcard/home.mp4` and step through the frames.

### Smoothness (PERF-02)

```bash
adb shell dumpsys gfxinfo io.shamash reset
# now scroll the rows and move focus with the remote for about 30 seconds
adb shell dumpsys gfxinfo io.shamash | grep -E "Total frames rendered|Janky frames|90th percentile"
```

Targets: `90th percentile` ≤ 16 ms, `Janky frames` ≤ 5%.

### Memory (PERF-03)

Browse Home, Discover and a few rows for 10 minutes, then:

```bash
adb shell dumpsys meminfo io.shamash | grep -E "TOTAL"
```

Read the total PSS (in KB; 120 MB is about 122,880 KB).

### APK size

```bash
ls -l app/build/outputs/apk/release/app-release.apk
```

## Measured on

Add a row (a pull request is welcome). Note the commit you built (`git rev-parse --short HEAD`) and leave a cell
empty rather than guessing.

| TV or box | SoC | RAM | Android | Commit | Cold start | Back Home | p90 frame | Janky | PSS after 10 min | Date |
|---|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | | |
