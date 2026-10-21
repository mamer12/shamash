# Profiles and parental controls

Replaces the single `Profile` of ONB-06 with a list of profiles; the existing `Profile` data class stays the unit
every provider already reads (ARCH-10 contract unchanged: providers receive the active `Profile`).

## Model
- PRF-01 `ProfileId` is a random UUID string. `Profile` gains: `id`, `kind` (ADULT, KIDS, GUEST), `avatarColor`,
  `pinHash` (nullable), `layout` (LAY-01..), `limits` (PRF-12), `keyMode` (SHARED or OWN), `muted` (PER-05),
  `feeds` (PEX-20). Defaults keep today's behavior.
- PRF-02 Storage: per-profile DataStore file `profile_<id>`; a small `profiles` store holds the list, the active id,
  `lastUsed`, and the owner id. Secrets stay in the `secret_` namespace, namespaced `secret_<profileId>_<key>` for OWN
  keys and `secret_shared_<key>` for shared ones.
- PRF-03 Exactly one OWNER (the first profile, an ADULT). The owner cannot be deleted or demoted; others can.
  Max 6 profiles.

## Picker
- PRF-04 On launch of Home with 2+ profiles the picker shows (avatar color + name, "Guest", "Add profile"). With one
  profile and no Guest it is skipped. Setting "Ask on launch" can be turned off to reopen the last profile; the
  switch lives in the top bar (the name chip) either way.
- PRF-05 Optional PIN (4 digits, D-pad entry) per profile; stored as salted hash (PBKDF2), never plain. Five wrong
  entries lock that profile for 5 minutes (doubles on repeat). Forgotten PIN: the owner's PIN resets another
  profile's; the owner's PIN resets only by "Redo setup" after the TV's own confirmation.
- PRF-06 Switching profile reloads rows, carousel, matches, teams, apps and moment rules from that profile; the
  companion (CMP) and overlays (MC-04) follow the active profile's teams. Cached provider data keyed by provider and
  region is shared; anything keyed by account or key is keyed by profile (PRF-09).

## Per-profile and shared
- PRF-07 Per profile: name, city/region, subscriptions, teams, competitions, genres, layout, rows, visible apps,
  content level, limits, history, My List (PER-14), ratings and mutes.
- PRF-08 New profile flow reuses onboarding screens (ONB-02) with "Copy from <profile>" as the first choice.
- PRF-09 Keys (TMDB, YouTube sign-in, provider credentials): `SHARED` uses the owner's, `OWN` asks the profile's own.
  YouTube/Trakt sign-ins are always per profile (they are accounts). Own-server credentials (PEX-04) default SHARED.
- PRF-10 Kids: name + avatar only; Settings, Inputs, TV Settings, phone remote, row editor, apps outside the allow
  list, "Redo setup", add-on switches and profile switching to ADULT without PIN are all unreachable. Search is
  limited by content level. Leaving Kids needs the owner's PIN (or, with no PIN set, a one-time confirmation hold of
  OK for 3 s; the setup nudges the owner to set a PIN).
- PRF-11 Guest: nothing is saved (no history, ratings, progress, personalization signals); no access to others'
  accounts; content level KIDS or FAMILY chosen by the owner (default FAMILY); cleared on leaving or after 4 h idle.

## Parental controls
- PRF-12 `ContentLevel` ALL/FAMILY/KIDS (existing) applies to every provider: a provider must filter its own
  results with it (Cinemana CIN-08, TMDB certification, YouTube `safeSearch=strict` for KIDS, user feeds PEX-21,
  Plex/Jellyfin ratings). Items with unknown rating are hidden when level is not ALL. A central `ContentGate` in
  domain drops any `MediaItem` that still violates the level after the provider (defense in depth), including in
  search, carousel, moment rows, Continue watching and My List.
- PRF-13 Apps: KIDS and FAMILY use an allow-list of apps (owner picks from installed apps; default empty for KIDS =
  only the Kids rows). Opening a blocked app by deep link, search or companion send is refused with a toast.
- PRF-14 Time limits: `daily minutes` per profile (0 = none) counted from time the profile is active while the
  screen is on (Home plus apps in the foreground that Shamash can see through its own overlay service; if the usage
  cannot be measured the limit falls back to "Shamash-session time" and Settings says so). Warning at 5 min left;
  at 0 a lock screen shows (en+ar) and only the owner PIN unlocks.
- PRF-15 Bedtime: per day-of-week window (e.g. 21:00-06:30). Inside it the profile lock screen shows; ends by itself.
  Clock changes by the user do not bypass it: the owner PIN is needed to edit limits; time comes from the TV clock
  and a limit check also runs on network time when available.
- PRF-16 Limits and levels are editable only by the owner after PIN (if set). Lowering restrictions on a KIDS profile
  always asks the PIN.

## Migration
- PRF-17 On first start of the new version: if a single `Profile` exists, it becomes the OWNER, `kind=ADULT`, keys
  `SHARED`, layout = Cinema preset with current row order; no picker (one profile). Existing DataStore keys are read
  once and re-saved under the new store; the old store is deleted only after the new one read back equal. Failure
  keeps the old store and runs in legacy mode.
- PRF-18 Defaults for new fields never change existing behavior (contentLevel keeps its stored value, keepRunning too).

## Strings, privacy, tests
- PRF-19 en+ar strings: picker, PIN entry, lock screens, limits and bedtime, Guest, Kids notices. Lock screens are
  RTL-aware (LAY-17).
- PRF-20 PIN hashes, limits and avatar are local; exported only in encrypted backups (CMP-14). No analytics.
- Tests: `PRF_12` ContentGate table (all kinds, unknown rating), `PRF_15` bedtime across midnight, `PRF_17`
  migration from a v1 store fixture, `PRF_05` lockout timing, `PRF_11` guest records nothing.
