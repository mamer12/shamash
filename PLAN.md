# Shamash — plan

Shamash (𒀭𒌓, the Mesopotamian Sun god, giver of light) is an open-source home screen for Android TV and Google TV.
It replaces the TV maker's launcher with one that is built around **you**: on first run it asks what you watch, which
apps you pay for, your teams and your tastes, then shapes the whole experience from those answers.

Written from scratch in Kotlin: every feature from the author's Free My TV / Watchme+ experiments is re-implemented
here, with nothing hard-coded to one person, country or app.

## Principles

- **Yours from the first minute.** No config files: a TV-friendly setup flow asks, and Settings can change any answer.
- **Generic.** Every service is a plug-in ("provider"); nothing assumes Netflix, a country, a team or a language.
- **Smooth on cheap TVs.** 2 GB RAM, Mali-class GPUs, Android 9+. Nothing allocated per frame; work off the main thread.
- **Always a way back.** Switching to Shamash is reversible in one press ("Original"); a watchdog restores the
  TV's launcher after repeated crashes.
- **Private.** Keys and sign-ins stay on the TV. No analytics.

## First run (onboarding)

1. Language and region (detected, confirmed).
2. City (weather, sunset look, prayer/clock widgets).
3. **Your apps**: the streaming apps found on the TV, each with "I subscribe" / "I don't" (subscriptions decide
   what the home screen recommends and where titles open).
4. Your teams and competitions (Match Centre).
5. What you like: genres and moods, movies vs series.
6. Optional sign-ins: TMDB key (recommendations), YouTube (read-only sign-in for TV), each skippable.
7. The switch: make Shamash the home screen (with the TV's own ADB, like Free My TV), with a clear explanation of
   what changes and how to undo it.

## Features

| Area | What | Prototype |
|---|---|---|
| Home | Featured carousel (full-screen photo, Open in…), time of day ("Good evening", Tonight row, night look) | HomeView, Moments |
| Home | Rows: Continue watching (movies/series, YouTube apart), Top 10, Popular per subscribed app, provider rows | Catalog, Watched |
| Home | Tabs Home · Discover · Apps; Search, Inputs, TV Settings buttons; apps row; recent apps | HomeView, HomeApps |
| Match Centre | Matches row with crests and live scores, match-day billboard, reminder and live score over any app, goal flash | Matches, MatchWatch |
| Search | One search across providers, exact matches first, opens titles on their own page | SearchActivity, Links |
| Phone | LAN page with PIN: send links/titles from the phone | Companion |
| Deep links | Apple TV / Netflix title pages (JustWatch), YouTube videos, provider pages | Links |
| YouTube | TV sign-in (device flow), "Recommended for you" | YouTubeAccount |
| TV control | Switch home/screensaver, turn promo apps off (never uninstall), remote app buttons, watchdog, Original mode | HomeMode, Adb, RemoteKeys, Watchdog |
| Screensaver | Photo screensaver with clock and weather | DreamView, Screensaver |
| Weather | Current and hourly | HomeData |
| Speed | Close apps on Home (keep favourites), tuned animations | HomeView |
| Optional add-on | Watchme+ (Cinemana client: browse, player, subtitles, continue watching) — **off by default** | Cinema*, Player*, Streamer |

## Architecture

```
app/                     the launcher (Compose for TV), onboarding, settings
core/                    design system (colors, type, focus motion, sounds), image cache, springs
data/                    preferences (DataStore), providers API, TMDB, JustWatch links, weather, matches, YouTube
providers/<name>/        one module per service (netflix, appletv, tod, youtube, osn, …; cinemana = optional)
system/                  ADB-on-device control: home switch, debloat lists per brand, remote keys, watchdog
```

- Kotlin 2.2, Jetpack Compose for TV (`androidx.tv:tv-material`), coroutines + Flow, DataStore.
- A **Provider** interface: `id`, `name`, `packageName`, `rows(prefs)`, `search(query)`, `open(item)`.
- Gradle (Kotlin DSL), AGP 9, minSdk 28, targetSdk 35.

## Roadmap

0. Skeleton that builds; PLAN, LICENSE. ← now
1. Core + onboarding + preferences (replaces config.properties).
2. Home screen (carousel, rows, tabs, apps) on providers: TMDB, YouTube, TOD, Apple TV/Netflix links.
3. TV control (switch, Original, debloat, remote keys, watchdog) — the riskiest part, ported carefully.
4. Match Centre (rows, billboard, overlays), Search, Phone companion.
5. Screensaver, weather, night look, speed (close apps on Home).
6. Optional Cinemana add-on.
7. Polish, docs, translations (en, ar, pt, es), release.
