# Shamash specifications

Shamash is built spec-first: each capability below is a list of numbered requirements. Code and tests point back to
them (`// SPEC: HOME-04`, test names like `HOME_04 rows follow subscriptions`), and a change in behavior starts as a
change here.

| Spec | Capability |
|---|---|
| [architecture.md](architecture.md) | Layers, modules, the Provider contract, rules every part follows |
| [onboarding.md](onboarding.md) | First run: profile, subscriptions, teams, tastes, keys, the switch |
| [home.md](home.md) | The home screen: carousel, moments, rows, tabs, apps, focus and motion |
| [providers.md](providers.md) | Content sources: TMDB, JustWatch links, YouTube, TOD, ESPN, weather, Cinemana add-on |
| [match-centre.md](match-centre.md) | Matches row, match-day billboard, reminders and live score; more sports, where to watch, alerts |
| [search.md](search.md) | One search across providers |
| [companion.md](companion.md) | The phone page: send links, edit rows/teams/feeds, queue, encrypted backup |
| [tv-control.md](tv-control.md) | Becoming the home screen, Original mode, promo apps, remote buttons, watchdog |
| [screensaver.md](screensaver.md) | The screensaver |
| [addon-cinemana.md](addon-cinemana.md) | The optional Cinemana add-on: browse, title page, player |
| [performance.md](performance.md) | Budgets for cheap TVs |
| [layout.md](layout.md) | Row editor, layout presets, moment rules, visual choices, RTL-first (LAY) |
| [profiles.md](profiles.md) | Profiles, PIN, Kids and Guest, parental controls, migration (PRF) |
| [providers-extra.md](providers-extra.md) | Plex, Jellyfin/Emby, IPTV, My feed, podcasts, radio, Twitch/Kick, Trakt/Simkl, widgets, shared provider settings contract (PEX) |
| [personalization.md](personalization.md) | Signals, weights, Not for you, where to watch, explainers, one My List (PER) |
| [platform.md](platform.md) | Backup, add-on authoring, voice search, accessibility, first-run presets (PLT) |
| [ROADMAP-NEXT.md](ROADMAP-NEXT.md) | Dependency-ordered build waves, file ownership, test plan |

Requirement words: **must** (tested or verified on a TV), **should** (default behavior, may be skipped with a reason).
