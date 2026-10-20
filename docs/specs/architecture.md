# Architecture

## Layers (package `io.shamash`)

```
app/            Activity, navigation between onboarding / home / search / settings
ui/             design system: theme, typography, focus motion, shared components (Billboard, MediaRow, cards)
feature/<x>/    one package per capability: onboarding, home, search, matchcentre, companion, tvcontrol, screensaver,
                settings. UI (Compose) + a ViewModel-like state holder. Depends on domain, never on data internals.
domain/         models (MediaItem, Shelf, Match, TvApp, Profile), the Provider contract, pure logic
                (Moments, carousel mix, search ranking, link parsing). No Android framework imports except where noted.
data/           sources: http client, preferences (DataStore), TMDB, JustWatch, YouTube, TOD, ESPN, weather, the
                Cinemana add-on, installed apps, watch history. Implements domain contracts.
```

- ARCH-01 Dependencies point inward: `feature → domain ← data`. `domain` does not import `data` or `feature`.
- ARCH-02 One `ServiceLocator` (manual DI, no framework) builds the object graph once per process.
- ARCH-03 All I/O is `suspend` on `Dispatchers.IO`; UI state is `StateFlow`. No blocking calls on the main thread.
- ARCH-04 Network: one OkHttp client (disk cache 20 MB, 8 s connect / 15 s read timeouts), JSON with
  kotlinx.serialization (`ignoreUnknownKeys = true`). Images: Coil 3 with OkHttp, memory cache ≤ 20% of heap.
- ARCH-05 Every source fails soft: an error returns an empty result and is logged with tag `Shamash`; one failing
  provider never empties the home screen.
- ARCH-06 Secrets (TMDB key, YouTube client, YouTube sign-in) live in DataStore on the TV, entered in onboarding or
  settings, never in the repository. A fork works without any key (features needing one stay hidden).
- ARCH-07 Strings shown to the user live in `res/values/strings.xml` (English) with `values-ar` (Arabic); no
  user-facing text hard-coded in Kotlin.
- ARCH-08 Clean code: functions ≤ ~40 lines, files ≤ ~400 lines, names say intent, no dead code, comments only for
  the why.
- ARCH-09 Unit tests (JUnit 4, JVM) for domain logic and data parsers, using JSON fixtures under
  `app/src/test/resources/`. Test names reference spec IDs.

## The Provider contract

```kotlin
interface Provider {
    val id: String                 // "tmdb", "youtube", "tod", "cinemana", "espn"…
    val appPackage: String?        // the TV app it opens titles in, or null (TMDB opens in the title's service app)
    fun isEnabled(profile: Profile): Boolean
    suspend fun shelves(profile: Profile): List<Shelf>        // rows for the home screen
    suspend fun featured(profile: Profile): List<MediaItem>   // candidates for the carousel (wide art required)
    suspend fun search(query: String, profile: Profile): List<MediaItem>
}
```

- ARCH-10 `MediaItem` carries what any screen needs: `id`, `providerId`, `title`, `subtitle`, `overview`,
  `posterUrl`, `backdropUrl` (16:9, ≥ 1280 wide for the carousel), `appPackage`, `open: OpenTarget`,
  `progress` (0..1 or null), `startsAt` (matches), `kind` (MOVIE, SERIES, VIDEO, MATCH, LIVE).
- ARCH-11 `OpenTarget` is a sealed type: `AppPage(uri, package)` (deep link with fallback to the app),
  `Lookup(tmdbId, kind, package)` (resolve a deep link at open time via JustWatch), `App(package)`,
  `Video(youtubeId)`, `Internal(route)`.
- ARCH-12 Opening goes through one `Opener` that tries the most specific target and falls back to launching the app;
  it records the item in watch history (except providers that keep their own progress).
