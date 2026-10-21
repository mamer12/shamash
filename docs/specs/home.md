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

## Expanding tiles (poster rows)
- HOME-16 In POSTER and RANKED rows the focused poster grows in place into a 16:9 tile (688×387 at 1920 scale); the
  cards after it slide right. The poster center-crops to the tile's current shape (no stretching) and crossfades to
  the item's wide art after ~60% of the growth; wide art fades in over 220 ms once loaded. The name label fades out as
  it grows; a white 4 px outline marks the tile.
- HOME-17 Each card has its own grow spring (response 0.34 s, damping 1): target 1 for the focused card, 0 for the
  others, stepped every frame; row scroll, horizontal scroll and growth share the 0.34 s pace. The focused tile is
  left-aligned at the margin; cards leaving on the left fade (alpha = clamp(1 − (margin − x)/(0.6 × step))).
- HOME-18 Rows below shift by the row's current growth × (tile height − poster height + info height − label room),
  driven by the actual growth so nothing overlaps.
- HOME-19 Under the tile: the name, then year · length · rating, then up to 3 lines of overview (fetched when the
  row's item has none); the info area animates on the same spring. No "% match".
- HOME-20 Preview: when the item's provider offers a preview stream (`PreviewSource`), after the focus rests 950 ms
  the video plays inside the tile (scaled 1.04× for overscan, sound 0.8, subtitles inside); the poster fades out over
  400 ms on the first rendered frame; after 12 s untouched it goes full screen, any key brings it back; OK opens the
  player at that second. A row rebuild with the same item keeps the preview playing.
