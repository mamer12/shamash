# Layout (rows, presets, rules, look)

All of it is user preference, stored per profile (PRF-02), with nothing about one person's tastes in code.

## Row model
- LAY-01 Every Home row has a stable `rowId` (`<providerId>:<shelfId>`, e.g. `tmdb:popular-netflix`, `matches`,
  `moment`, `continue`). A `RowPref(rowId, hidden, pinned, title?, order)` is saved per profile. Unknown rowIds in
  saved prefs are kept (a provider may come back) but ignored when drawing.
- LAY-02 Row editor (Settings -> Home layout, also reachable with the Menu key on a Home row: a long OK press there is
  the card menu, PER-07, and row titles take no focus): list of every row the
  profile could show, each with: move up/down (D-pad, no drag), Hide/Show, Rename (on-screen keyboard, <= 40 chars,
  empty restores the default), Pin to top. Changes apply live on Home and are saved on leaving the editor.
- LAY-03 Ordering: pinned rows first (by `order`), then the rest by `order`, then rows with no pref in the default
  HOME-04 order after the ordered ones. A new row from a newly enabled provider appears at the end, never silently
  reordering existing rows.
- LAY-04 Hidden rows are not fetched when no other consumer needs them (carousel still may use a provider's
  `featured`, controlled by LAY-12). A row that is empty stays hidden even if pinned (HOME-04 rule wins).
- LAY-05 "Reset layout" restores the preset's order, names and visibility (asks to confirm); it never touches
  provider keys or subscriptions.
- LAY-06 Renames are user text: shown as-is, not translated; defaults stay in `strings.xml` en + ar (ARCH-07).

## Presets
- LAY-07 A `LayoutPreset` is data (`id`, ordered `rowId` list with hidden flags, default carousel mix, default
  moment rules, card size, tab set), not code branches. Shipped: **Cinema** (continue, picks, services, Top 10),
  **Sports** (matches, live and upcoming first, team news if a feed exists, then continue), **Kids** (kids rows
  only: continue, Cinemana, YouTube picks and My feed rows, all behind the Kids gate PRF-12; large cards, no matches row, no search-by-voice results beyond the level, see PRF-10), **Minimal** (apps row,
  continue watching, one pick row, no billboard autoplay: `Layout.autoplay` false, a switch under Look).
- LAY-08 Onboarding gains a "How should Home look?" step after "What you like" (ONB-02 step 5b): four cards with a
  one-line description, skippable (default Cinema). Choosing applies the preset to the profile once; later edits via
  the row editor win and are never overwritten by preset updates.
- LAY-09 Presets reference rowIds that may not exist (provider off): those entries are skipped silently.

## Moment rules (user-defined)
- LAY-10 A `MomentRule(id, days: set of 1..7, from: HH:mm, to: HH:mm, emphasis: RowRef or RowTag, label?)`. When
  several match, the first in list order wins; a range may cross midnight ("23:00-05:00"). Rules use the TV clock and
  the profile's week start. Built-in HOME-06 moments remain the fallback when no rule matches.
- LAY-11 "Emphasis" moves the target row to just under the billboard and (optionally) replaces the moment row's
  title with `label`. Rule editor: pick days (chips), two time pickers, target row from a list. Max 8 rules.
- LAY-12 Carousel mix (HOME-10) is a per-profile ordered list of source tags with on/off; rules may swap the
  mix ("Friday evening: matches first"). Defaults reproduce HOME-10.
- LAY-13 Invalid rules (empty days, from == to) are rejected in the editor with a message and never saved.

## Visual choices
- LAY-14 Accent: 8 preset colors plus the TV-friendly rule that text on the accent keeps contrast >= 4.5:1 (else the
  accent is darkened/lightened for text only). Default is the current gold.
- LAY-15 Card size S / M / L scales row cards 0.85 / 1.0 / 1.2 and the number of cards visible; HOME-16..19 growth
  math scales with it; performance budgets (performance.md) still hold at L.
- LAY-16 Clock: 12 h / 24 h / follow TV (default); date: weekday+day / numeric. Weather: Celsius / Fahrenheit /
  region default (PRV-12), and can be hidden. Show seconds is not offered.
- LAY-17 RTL-first: when the TV language is Arabic (ONB-07) or the user forces "Arabic layout", the whole Home mirrors
  (top bar, row start edge, focus order, carousel dots, arrows). Numbers stay Western digits unless the user picks
  Arabic-Indic. Nothing in the layout code hard-codes left/right: use start/end.
- LAY-18 Strings: new labels in `values/strings.xml` and `values-ar/strings.xml`: row editor, presets (names and
  one-line descriptions), rule editor, accent names, sizes, clock formats.

## Edge cases and privacy
- LAY-19 Fail soft: corrupt layout JSON falls back to the preset default and logs `Shamash` without content.
- LAY-20 Layout prefs contain no secrets and are exported with the profile (CMP-12).
- LAY-21 A Kids profile (PRF-10) cannot open the row editor; presets for it are chosen by the owner only.

## Tests
`LAY_03` ordering pure function, `LAY_10` rules incl. midnight crossing and ties, `LAY_09` missing rows, `LAY_17`
mirrored order, preset JSON fixtures under `src/test/resources/layout/`.
