# Home screen

## Layout
- HOME-01 A top bar: on the left round buttons Search, Inputs, TV Settings; tabs Home · Discover · Apps centered; on
  the right the time and, below it, the date, weather and city.
- HOME-02 Home tab: a full-screen **carousel** (billboard) of featured items: backdrop photo darkened toward the
  bottom-left, provider name in caps, title (≤ 2 lines), meta line, overview (≤ 3 lines), an "Open in <app>" button,
  and dots. It advances every 9 s; ←/→ on the button changes item; the change crossfades (≤ 700 ms).
- HOME-03 Under the billboard, the user's apps row (subscribed apps first, then other pinned apps) sits low on the
  screen and does not move when focused. ↓ scrolls into the rows below, the billboard fades and dims.
- HOME-04 Home rows, in order, each hidden when empty: Matches (see match-centre), the moment row, Continue watching
  (movies and series), Continue watching on YouTube, Recommended for you (YouTube), upcoming on TOD, Cinemana rows
  (add-on), one "Popular on <app>" per subscribed app with TMDB data.
- HOME-05 Discover tab: Top 10 (posters with large rank numbers) and "Popular on <app>" rows for every TMDB service
  the TV has. Apps tab: Recent apps, All apps, Inputs, Settings (Shamash settings, TV settings, phone remote,
  YouTube sign-in).

## Moments
- HOME-06 The moment is MORNING 05–12, AFTERNOON 12–17, EVENING 17–23, NIGHT 23–05 (TV clock).
- HOME-07 The greeting "Good morning/afternoon/evening, <name>" (no name → no comma) shows above the billboard.
- HOME-08 The moment row ("This morning", "This afternoon", "Tonight", "Before bed") holds, in order: matches of the
  user's teams within the next 14 h, the first unfinished movie/series, one personal pick, one YouTube pick
  (morning: YouTube first), each title once.
- HOME-09 At NIGHT the background dims by 35% more.

## Carousel content
- HOME-10 Featured items mix providers one at a time: the user's team's match (today/live) first, then in turns
  Cinemana featured (add-on), YouTube recommended, Apple TV / subscribed services' popular titles, TOD upcoming
  matches; only items with wide art; at most 16.

## Continue watching
- HOME-11 Items opened from Shamash are recorded (newest first, max 20, 30 days); providers with their own progress
  (Cinemana add-on) supply theirs with a progress bar. A title appears once.

## Focus and motion
- HOME-12 Focus: the focused card grows 8% with a white 3 dp border; springs, no jumps on fast presses; Back goes up
  to the tabs, then Home, then the apps row.
- HOME-13 Opening an item uses the Opener (architecture ARCH-12). Apps, inputs, settings open directly.
- HOME-14 Data refresh intervals: TOD 1 h, Cinemana 2 h, YouTube recommendations 3 h, TMDB 6 h, weather 30 min,
  matches 3 h (1 min while one of the user's teams plays). Cached data shows instantly at start.
- HOME-15 When Shamash is the home screen and the user returns Home from another app, that app's process is closed to
  free memory unless it is one of the user's "keep running" apps (default: Netflix, YouTube).
