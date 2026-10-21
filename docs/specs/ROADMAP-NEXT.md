# Roadmap: next build (dependency-ordered waves)

Rule: a wave starts when the previous wave's tests are green. Inside a wave, agents own disjoint files. Specs
listed are the source of truth; code and tests carry `// SPEC: <ID>`.

## Shared files (one owner each, per wave)
| File | Owner rule |
|---|---|
| `domain/Models.kt` (Profile, MediaItem, Follow, RowPref...) | Wave 0 agent only; later waves add fields through a small PR to that owner, or new files (`domain/Layout.kt`, `domain/Signals.kt`) |
| `data/Preferences.kt`, `ProfileStore` | Wave 0 owner; later waves add stores in new files |
| `data/Sources.kt` (registration lines) | One agent per wave appends lines; merge order by wave |
| `feature/settings/SettingsScreen.kt`, `SettingsPanels.kt` | One owner per wave; panels live in new files per feature (`LayoutPanel.kt`, `ProfilesPanel.kt`...) |
| `res/values/strings.xml`, `values-ar/strings.xml` | One owner per wave; others send keys in their handback, or use new `strings_<feature>.xml` files (merged by Android) |
| `app/ServiceLocator.kt`, `ShamashApp.kt` (navigation) | Wave owner, one line additions |
| `feature/home/HomeModel.kt` | Home agent only (Waves 1-3 handed off in order) |

## Wave 0: foundations (sequential, one agent)
- Models: `Profile.id/kind/avatarColor/pinHash/keyMode/addons/follows/layout`, `RowPref`, `LayoutPreset`, `ContentGate`
  and `ProviderCredentials` + `AddOn` interfaces, `ContentLevel` unchanged.
- `ProfileStore` (PRF-01..03, PRF-17 migration with fixtures), `Preferences` reads the active profile.
- `ContentGate` (PRF-12) wired into the home pipeline, search, and Cinemana (re-verifies CIN-08).
- Tests: PRF_12, PRF_17, PRF_18; no UI change except none.

## Wave 1: parallel (disjoint files)
- A. Profiles UI: picker, PIN, Kids/Guest locks, limits, bedtime (PRF-04..16). Files: `feature/profiles/*`,
  `ProfilesPanel.kt`, `app/ShamashApp.kt` entry.
- B. Layout: row editor, presets, onboarding preset step, moment rules, visual choices (LAY-01..21). Files:
  `feature/layout/*`, `domain/Layout.kt`, `domain/Moments.kt` (rules), `HomeModel.kt` (ordering).
- C. Provider contract: `ProviderSettingsPanel`, `AddOn` list, credential store (PEX-01..08), migrate Cinemana to
  the registration line. Files: `feature/settings/ProviderPanel.kt`, `data/AddOns.kt`, `Sources.kt`.
- D. Platform small items: accessibility (PLT-11..16), voice (PLT-08..10). Files: `ui/*` semantics, `feature/search/*`.
  (A11y touches shared `ui/` composables: coordinate with B who owns row/card layout; D works after B's cards merge.)
- Profile/Models changes consumed: Wave 0 only. Strings: each agent a separate `strings_<x>.xml`.

## Wave 2: parallel
- E. Providers batch 1: media servers and IPTV (PEX-10..16), My feed (PEX-20..22). Files: `data/plex`,
  `data/jellyfin`, `data/iptv`, `data/feed`, registration lines (one owner).
- F. Personalization (PER-01..18) incl. One My List (needs C's row sources, B's rows). Files: `domain/Signals.kt`,
  `domain/TasteProfile.kt`, `data/SignalStore.kt`, `feature/home` Why/Not-for-you actions (Home owner).
- G. Match Centre extensions (MC-07..16). Files: `data/espn/*` per sport, `feature/matchcentre/*`,
  `assets/broadcast.json`. Models: `Follow`, `Event`.
- H. Backup codec (PLT-01..03, CMP-12..15 core) pure `domain/BackupCodec.kt` + tests (no server code yet).

## Wave 3: parallel
- I. Companion additions (CMP-06..16): server endpoints and the phone page; depends on B, C, E, H.
- J. Providers batch 2: podcasts, radio, Twitch/Kick, Trakt/Simkl, widgets (PEX-23..27). Trakt needs F (signals,
  My List).
- K. Platform: add-on authoring guide and template (PLT-04..07), first-run presets (PLT-17..18, depends on B),
  Kids preset content (LAY-07).

## Wave 4: hardening
- Cross-feature audit: every `MediaItem` path through `ContentGate`; no secret in logs/cache grep test; RTL pass
  on Home, picker, editors; performance budgets (performance.md); TV smoke test (the user's, by hand).

## Test plan
- JVM unit tests (ARCH-09) named after IDs, fixtures under `src/test/resources/<feature>/`. Per wave: all new IDs
  have at least one test; `./gradlew test` green before the next wave.
- Required fixtures: v1 profile store (PRF_17), M3U/XMLTV/RSS/Atom/JSON feeds, ESPN per sport, Plex/Jellyfin sample
  responses (redacted), backup envelope v1.
- Security tests: no secrets in logs or HTTP cache (PEX_04), XXE rejected (PEX_22), companion auth/lockout on new
  endpoints (CMP_06), backup tamper/atomic import (CMP_14/15), CIN_08 level on every Cinemana request.
- UI: Compose focus-order tests for picker and row editor; screenshot checks LTR and RTL; manual TV checklist
  per wave (D-pad only, Back behavior HOME-12).
- Conflicts: the Wave 0 owner reviews any Model change; new fields default so old stores load (PRF-18).
