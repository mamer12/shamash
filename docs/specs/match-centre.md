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
