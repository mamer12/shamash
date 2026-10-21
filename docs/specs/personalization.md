# Personalization

All local; nothing leaves the TV. Per profile (PRF-07); off for Guest (PRF-11).

## Signals
- PER-01 A `Signal(profileId, itemKey, providerId, kind: OPEN|FINISH|SKIP|LIKE|DISLIKE|MUTE|LESS_LIKE_THIS, at,
  hourOfDay, genres)` is recorded locally, capped at 2000 per profile, oldest dropped, 180 days max.
- PER-02 FINISH = progress >= 90% (95% for Cinemana CIN-04); SKIP = opened and left under 2 min or dismissed from a
  card; OPEN when launched. Providers without progress give OPEN only. Trakt/Simkl imports add FINISH (PEX-26).
- PER-03 Time-of-day: each signal keeps the HOME-06 moment; the picker for the moment row (HOME-08) uses genres that
  were FINISHed in the same moment, falling back to all.
- PER-04 A "Clear my history and signals" action in Settings deletes signals, ratings and mutes for the profile.

## Weights
- PER-05 Provider weight and row weight: `w = clamp(1 + 0.15*finishes - 0.1*skips, 0.5, 2.0)` over 30 days (a pure
  function). They reorder only rows with no user order (LAY-03) and mix the carousel (LAY-12); pinned or user-ordered
  rows are never moved. The user can switch "Learn my order" off.
- PER-06 Genre profile reuses CIN-11 weights, generalized: selected +2, My List +1, started +1, finished +2, love +3,
  like +2, dislike -3, "less like this" -2 on its genres; computed in `domain` (`TasteProfile`).

## Not for you
- PER-07 On any card, a context action (long OK) offers: Add to My List, **Not for you** (hide this title everywhere
  for the profile) and **Less like this** (hide + lower its genres, PER-06). Both are undoable from Settings ->
  Muted (list with "Show again").
- PER-08 Mutes apply through `ContentGate` (PRF-12): search, carousel, rows, moment rows and My List. A muted title
  is never re-recommended even after a provider re-keys it; matching key = provider id + id, else normalized title +
  year + kind.

## Where to watch
- PER-09 For a TMDB title the "Open" target is chosen from `watch/providers` for the profile's region: the user's
  subscribed services first, in the order Flatrate > Free/Ads > Rent > Buy, ties by the user's provider weight
  (PER-05); apps not installed are skipped; if none match the best non-subscribed is shown labeled "Not in your
  apps". The chosen app is named on the button ("Open in <app>").
- PER-10 Long OK on a title page shows all options ranked the same way (<= 6).

## Explainers
- PER-11 Every recommended row (not Popular/Top 10 lists, not user feeds) carries a one-line reason
  `Reason(kind, argRes/argText)`: "Because you watched <title>", "Because you like <genre>", "Popular with your
  <service>", "From a channel you follow", "Because you finished <title> this morning". Shown under the row title,
  <= 60 chars, en+ar. Providers must return a reason with each such shelf; a shelf without one gets none (nothing
  fabricated, no "% match").
- PER-12 Tapping "Why?" (long OK -> Why am I seeing this) shows the signals used (<= 3) and a Not-for-you shortcut.

## One My List
- PER-13 A single `MyList` in `domain` (`itemKey, providerId, title, kind, artUrl, open: OpenTarget, addedAt`), per
  profile, replacing per-provider lists on Home. Cinemana's list (CIN-01/07) is mirrored: add/remove from either
  side stays in sync through `RefreshableProvider.changes`; the add-on keeps working alone if the core list is gone.
- PER-14 Add from any card (PER-07), title page, search results; Trakt/Simkl watchlist can be merged in (PEX-26).
  Row "My List" (newest first, <= 40), dedupes by PER-08 key. Opening an item follows ARCH-12.
- PER-15 Removing a provider keeps its My List entries but shows them as unavailable and offers "Remove".

## Edge cases
- PER-16 With fewer than 5 signals, no weighting is applied (defaults). Clock changes never rewrite old signals.
- PER-17 Storage is a small JSON file per profile (<= 300 KB), written atomically; corrupt file = start empty.
- PER-18 Privacy: signals are in the encrypted backup only when the user ticks "include history" (CMP-14).
- Tests: `PER_05` weight function, `PER_09` ranking with fixtures, `PER_08` mute keys, `PER_13` mirrored list sync,
  `PER_11` no reason = no line.
