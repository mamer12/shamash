# Match Centre

- MC-01 Matches of the profile's competitions for today + 6 days (ESPN, PRV-11), the user's teams first then by
  kick-off; live and finished-today included; ≤ 16 cards in the Matches row.
- MC-02 A match card: both crests (transparent, fit), short names under them, the kick-off time (or "2 - 1" score)
  between, the day ("Today", "Tomorrow", weekday) or live minute in red below, the competition at the top; the user's
  team's matches get a gold edge.
- MC-03 Match day: when a user's team plays within 14 h (or is live), the carousel's first item is a match billboard:
  crests large on the right, kick-off or score between, "MATCH DAY" / "LIVE" label, "Watch on <app>" (TOD by default).
- MC-04 Over any app (needs "display over other apps", granted during the switch via ADB): 10 minutes before a user's
  team kicks off, a reminder card (crests, "Starts in 10 min · <app>") for 15 s at the top-right.
- MC-05 While that match is live: a compact score bubble at the top-right, refreshed every 60 s; when a score changes a
  gold "GOAL!" card for 10 s; at full time the final score for 30 s, then nothing.
- MC-06 The overlay never takes focus or touches (FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE) and runs as a foreground
  service only between 12 min before kick-off and the end; an alarm wakes it.

## More sports, where to watch, alerts (additions)
- MC-07 Competitions grow beyond soccer. `Sport` = SOCCER, F1, TENNIS, BASKETBALL (NBA), CRICKET. Each competition is
  data (`id`, sport, ESPN path, display name) in a catalog list; onboarding and Settings show chips grouped by sport
  (ONB-04 extended). ESPN paths follow `sports/<sport>/<league>/scoreboard` (racing/f1, tennis/atp|wta,
  basketball/nba, cricket/<series>); the same day-range calls as PRV-11.
- MC-08 `Match` generalizes without breaking soccer: non-team events use `Event(title, venue, sessions)`:
  F1 weekend sessions (FP/Quali/Sprint/Race) are matches with `home/away` replaced by the session name and flag;
  tennis shows two players (surname, seed) and set score; NBA/cricket show team crests and score text ("112-108",
  "186/4 (18.2)"). Parsers per sport are separate files; an unknown shape yields no card (fail soft).
- MC-09 "My teams" extends to "My follows": teams, players (tennis), drivers/F1 as a follow of "F1" itself.
  `Profile.teams` stays (migration: same set); `follows: Set<Follow(kind, name)>` adds players and sports.
- MC-10 Where to watch this match: a `BroadcastMap` of data (competition -> list of app packages by region, e.g.
  region SA: beIN, Shahid; user-editable) chooses the app. The card button "Watch on <app>" opens the first
  installed app that the user subscribes to (else the first installed, else a "no app" note), through the Opener
  with `OpenTarget.App`/`AppPage`. Replaces the fixed TOD default of MC-03. The map ships as a small JSON asset the
  user can override (companion CMP-09 later).
- MC-11 Per-team alerts: for each follow the user sets Off / Kick-off / Goals / All (default Kick-off, as MC-04).
  Alerts of other follows never show. Priority: a live match of an "All" follow can replace the bubble of a
  lower-priority one (no two bubbles).
- MC-12 Quiet hours: a time range (can cross midnight) and optional days in which MC-04/05 overlays are suppressed
  (state keeps updating, nothing shows); a live match flagged "Always" (star) ignores quiet hours. Also off when the
  active profile is Kids and the alert is not the kid's follow.
- MC-13 Goal-only mode: the live bubble is not shown; only the gold GOAL card (and the final score) appears. Detect
  change by score difference between polls; for non-goal sports (tennis, F1) the mode reads as "key moments only":
  set won, lead change, race finish. Same overlay rules as MC-06.
- MC-14 Per-sport polling: 60 s live (as MC-05), 10 min when a follow plays within 3 h, else the HOME-14 3 h; F1
  weekend 30 s during sessions. Battery/CPU budget unchanged (performance.md).
- MC-15 Strings en+ar for sports, session names, alert levels; Arabic match text mirrors (crests swap sides in RTL).
- MC-16 Privacy: follows and alert settings are local; no schedule is sent anywhere but ESPN's public endpoints.
- Tests: `MC_08` parser fixtures per sport, `MC_10` broadcast choice, `MC_11` priority, `MC_12` quiet-hours window
  across midnight, `MC_13` change detection.
