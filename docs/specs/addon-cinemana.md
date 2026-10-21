# Cinemana add-on (optional, off by default)

The add-on is a full "Netflix-style" app for Cinemana's catalog inside Shamash, enabled from Settings. Everything in
it lives under `data/cinemana` and `feature/cinemana`; deleting both folders and its line in `Sources` removes it.

- CIN-01 Browse: a top bar (Home, Movies, Series, My List, Search, Settings), a billboard from Cinemana's banner feed,
  rows from its groups and "new" lists, expanding tiles with in-tile previews (HOME-16..20), Top 10 movies/series
  (TMDB trending matched to Cinemana by name and year), "Top picks for you" and "Because you watched <title>"
  (genre profile from watch history + My List; TMDB recommendations of the latest title watched).
- CIN-02 Rows mode: the focused title's wide picture fills the right side, dimmed: horizontal gradient from opaque to
  40% width then ~65%→45% alpha at the right, plus a bottom fade from 40% to 85% of the height. The focused row sits
  near the top (y = 170 at 1920 scale).
- CIN-03 Title page: action column (Play/Resume, My List, Trailer), Episodes panel (seasons, episode progress), More
  Like This, year · length · rating; series open their root.
- CIN-04 Player: ExoPlayer-free MediaPlayer with a range-request streamer (parallel 2 MB ranges, first and last chunk
  first) for fast starts; subtitles (SRT, Arabic/English, Netflix style); speed 0.5–2×; skip intro/credits countdown to
  the next episode; resume position saved every 5 s; counts as started after 3 s; finished at 95%.
- CIN-05 Loader: appears only after 250 ms of loading, eases in/out; a red arc on a faint ring with a soft dark disc,
  sweep 30–260° over a 1.5 s cycle, turning 320°/s; the title art before the first frame fades out over 450 ms.
- CIN-06 Overlay redraws: invalidate once more after elements disappear, and every frame while anything animates
  (no frozen mid-fade frames).
- CIN-07 Continue watching, My List and recommendations feed Shamash's Home rows (HOME-04, HOME-11).
- CIN-08 Family filter (all / family / kids) applies to every Cinemana request.
- CIN-09 Ratings: the title page offers Love / Like / Dislike (one at a time, again to clear). Genres weigh love 3,
  like 2, dislike −3; disliked titles are never recommended; recommendations rebuild after a rating changes.
- CIN-10 Browse Home order: Continue watching → Top picks for you → the first "Because you…" row → Top 10 movies →
  Top 10 series → the other "Because you…" rows → My List → the rest. Up to 3 "Because you loved / watched / liked
  <title>" rows, seeded by loved titles first, then watched, then liked.
- CIN-11 Top picks mixes the genre profile with TMDB recommendations of up to 3 loved (+4) or liked (+2.5) titles
  (when there's a TMDB key). No "% match" anywhere.

## Integration and edge cases

- CIN-04 Range responses support bounded, open-ended and suffix ranges, HEAD, and 416 for invalid ranges.
  The streamer binds only to loopback, validates upstream Content-Range and byte counts, bounds memory and workers,
  and cancels downloads and closes sockets when the player leaves. No video data enters the HTTP disk cache.
  Subtitles accept UTF-8, UTF-16 BOM and legacy Windows-1256 Arabic. A missing subtitle never prevents playback.
- CIN-07 Title links use `shamash://cinemana/title/<id>`; playback uses `shamash://cinemana/play/<id>`.
  A nonnegative `at` on either link starts playback at that position, including zero. Series titles resolve to
  their root; progress remains per episode, with one Continue watching entry per series. Progress under 3 seconds
  is not history; 95% completion resets resume. Local list and rating changes immediately refresh recommendations.
- CIN-08 Detail, episode, file and subtitle requests carry the same level as browse and search. Stored titles are
  revalidated against the selected level before display or playback; unknown ratings are hidden in restricted modes.
- CIN-09 Tapping the selected rating clears it. Ratings belong to the series root. Preference updates are serialized
  and persisted on I/O threads; corrupt local records fail soft.
- CIN-11 Genre scores include selected genres (+2), My List (+1), started history (+1), completed history (+2),
  and ratings (love +3, like +2, dislike -3). TMDB boosts add +4 / +2.5 for up to three loved/liked seeds.
  Matching uses normalized title plus year (within one year) and media kind. Dislikes are excluded from every
  recommendation row, including trending and More like this.
