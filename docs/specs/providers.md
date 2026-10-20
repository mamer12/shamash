# Providers (content sources)

## TMDB (needs the user's TMDB key; hidden without)
- PRV-01 Watch-provider ids for the profile's region come from `/watch/providers/movie?watch_region=`; a provider
  name maps to a known service by name (Netflix, Apple TV — not "Apple TV Store" for flatrate —, Prime Video, Disney+,
  Max, OSN+, Shahid, TOD) and counts only if its app is installed.
- PRV-02 "Popular on <service>": `/discover/{movie,tv}` with `with_watch_providers=<ids>`,
  `with_watch_monetization_types=flatrate`, sorted by popularity; movies and shows interleaved; ≤ 20.
- PRV-03 Top 10: the most popular across the user's services (movies and shows), by TMDB popularity, distinct titles.
- PRV-04 Key format: a 32-hex v3 key is sent as `api_key`; a JWT (starts with `eyJ`) as `Authorization: Bearer`.

## JustWatch links
- PRV-05 At open time, a TMDB title with service Apple TV or Netflix resolves its page link via JustWatch's GraphQL
  (`popularTitles` with `searchQuery`, matched by `externalIds.tmdbId`, FLATRATE offer of that service →
  `standardWebURL`). Cached per session. No link → open the app.

## YouTube (TV sign-in, read-only)
- PRV-06 Device flow at `oauth2.googleapis.com/device/code` with scope `youtube.readonly`; the TV shows the code and
  `google.com/device`; polls `/token` at the given interval; stores the refresh token in DataStore; "Sign out" deletes
  it; `invalid_grant` on refresh signs out.
- PRV-07 "Recommended for you": seeds = the last 2 YouTube videos opened from Shamash + recent likes (≤ 4 seeds); per
  seed a `search` (type=video, ≤ 6); plus the newest uploads of up to 25 subscriptions (uploads playlist `UU…`, 2
  each); interleaved, minus liked/watched; ≤ 20; maxres thumbnail as wide art.
- PRV-08 Videos open in the YouTube app at `https://www.youtube.com/watch?v=<id>`.

## TOD
- PRV-09 Upcoming matches and new titles come from `https://www.tod.tv/en/{sports,movies,series}` requested with
  header `RSC: 1` and a browser User-Agent; the JSON object starting at `{"status":200,"data":{"railType":"slider"` is
  parsed; items with `matchStartTime` in the past more than 2 h are dropped; images
  `https://img.tod.tv/resources/images/link/{id}/{mediaSettingsId}/{timeStamp}/{x1}:{y1}:{x2}:{y2}/{W}x{H}/{name}`.
- PRV-10 TOD titles open the TOD app (its TV app takes no links).

## ESPN (Match Centre)
- PRV-11 `site.api.espn.com/apis/site/v2/sports/soccer/{league}/scoreboard?dates=YYYYMMDD`, one call per league per
  day for 7 days; state pre/in/post; scores, crests (`team.logo`), display clock.

## Weather
- PRV-12 Open-Meteo forecast (current temperature, weather code, day/night, sunrise/sunset); Celsius unless the
  region is US.

## Cinemana add-on (optional, off by default)
- PRV-13 Enabled only from Settings ("Cinemana add-on"); uses the public catalog
  (`cinemana.shabakaty.cc/api/android/…`: newlyVideosItems, videoGroups, AdvancedSearch); titles open the Cinemana
  app's launcher (no deep links) or a configured companion app; wide art from TMDB `/find/{imdb}`.
- PRV-14 The add-on is a single package `data.cinemana` that can be deleted without breaking the build of the rest
  (the registry lists it by id only).
