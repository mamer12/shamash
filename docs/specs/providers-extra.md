# Extra providers (add-ons)

Same shape as Cinemana (PRV-13, PRV-14): each add-on is one package `data/<id>` (+ `feature/<id>` if it has UI),
off by default, listed in `Sources.providers` by one registration line, deletable without breaking the build.

## Shared contracts
- PEX-01 `AddOn` registration: `AddOn(id, nameRes, descRes, requires: List<CredentialField>, create: (Deps) -> Provider?)`.
  `Sources` holds a list of `AddOn`s; one line per add-on. `create` returns null when not configured (fail soft).
- PEX-02 Enable flags live in the profile's `addons: Set<String>` (replaces `cinemanaAddon`, migrated by PRF-17).
- PEX-03 `ProviderCredentials` (domain): `fields: List<CredentialField(key, labelRes, kind: URL|TEXT|SECRET|TOKEN,
  required, hintRes)>`, `suspend get(key)`, `set(key, value?)`. Backed by the `secret_` store (PRF-09). Reading
  never logs values; `toString()` of credentials redacts.
- PEX-04 Own-server credentials (Plex token, Jellyfin/Emby user token, M3U URL with username in the query, XMLTV
  URL) are stored on the device only, via PEX-03; requests carrying them bypass the HTTP disk cache (ARCH-06); the
  logs show host only; images from the server are fetched with the token in a header where the server allows it,
  otherwise the URL is never cached or logged; backups exclude them (CMP-14).
- PEX-05 Settings UI contract (one reusable screen, `ProviderSettingsPanel`): title, one-line description, enable
  switch, credential fields from PEX-03 (on-screen keyboard or phone entry via companion CMP-08), "Test connection"
  (`Provider.test(): Result` with a human message, no secret echoed), "Remove" (clears credentials and rows), and the
  **row sources** list (PEX-06).
- PEX-06 Row source contract: a provider exposes `rowSources(): List<RowSource(id, titleRes|titleText, default on)>`
  (what rows it can offer); the panel shows each with on/off and an optional rename (LAY-02). Rows reach Home as
  `Shelf`s with `id = "<providerId>:<sourceId>"` (LAY-01). Max 12 sources per provider; each source fetch <= 8 s.
- PEX-07 Every provider: `featured` needs wide art, honors ContentLevel (PRF-12; Unknown rating hidden when not ALL),
  fails soft (ARCH-05), 2 h refresh, items open through the Opener with `OpenTarget` (ARCH-11).
- PEX-08 Add-ons are listed in Settings -> Add-ons with an "Add-on authoring guide" link (PLT-04).

## Media servers
- PEX-10 Plex: sign-in by PIN (`plex.tv/api/v2/pins`, show code, poll) then server list; or a direct URL + token.
  Rows: Continue watching (on deck), Recently added, libraries (<= 6). Opens in the Plex app if installed
  (`OpenTarget.AppPage`), else the item's own page in the server's web UI is not used (no in-app player in v1).
- PEX-11 Jellyfin / Emby: URL + username + password -> access token (`/Users/AuthenticateByName`); the password is not
  stored, only the token. Rows: Continue watching (resume), Next up, Latest per library. Opens the matching app.
- PEX-12 Local/private hosts (RFC 1918, `.local`) are allowed only for these providers (cleartext allowed per
  host with an explicit "this server uses http" switch via `network_security_config`); public hosts must be https.
- PEX-13 Library artwork over 1920 px is requested resized (`maxWidth`); no full-size images in memory.

## IPTV
- PEX-14 M3U: playlist URL (http/https), parsed streaming (no full load of > 20 MB): `#EXTINF` attrs `tvg-id`,
  `tvg-logo`, `group-title`; <= 5000 channels kept, extras ignored. Rows: Favorites (user marks, per profile),
  one per group (<= 8 shown, user picks). Channel opens in the user's IPTV player app (package chosen once) via
  ACTION_VIEW `video/*`; Shamash has no player for it.
- PEX-15 XMLTV EPG: optional URL (gz ok), streamed parse, only channels in favorites and now/next kept, <= 5 MB held;
  "Now" and "Next" in the card subtitle. Refresh 6 h. Bad XML = skip silently.
- PEX-16 The playlist URL is a secret (it often contains credentials): never logged or shown after entry (masked).

## My feed (no code)
- PEX-20 A `UserFeed(id, title, url, kind: RSS|ATOM|JSON, mapping?, style, level)` per profile; max 10. The user adds
  one by URL (typed on the phone via CMP-08) and picks a card style (WIDE, VIDEO, POSTER).
- PEX-21 RSS/Atom: title, link, `media:thumbnail`/`enclosure`/first `<img>` as art, `pubDate`; JSON: a mapping of
  JSON-pointer paths for `items`, `title`, `link`, `image`, `subtitle`, `date` (a small form; "Try it" shows the
  first 3 mapped items). Links open via ACTION_VIEW (YouTube links -> YouTube per CMP-04). Items without art use a
  generic tile. Feed level: the user sets All/Family/Kids; for KIDS profiles only feeds marked "kids safe" show.
- PEX-22 Safety: https only (http needs per-feed switch); size <= 1 MB, 6 s timeout, XML parser with DTD and external
  entities disabled; text is plain, never HTML-rendered; feeds with auth headers store them as secrets (PEX-03).

## Audio and live
- PEX-23 Podcasts: RSS with `<enclosure>`; rows: subscriptions (user list or OPML import via the phone page),
  new episodes. Opens in the user's podcast app (ACTION_VIEW audio) else a plain audio player is not built in v1.
- PEX-24 Radio: Radio Browser (`all.api.radio-browser.info`) by country/tag; favorites per profile; opens in a radio
  app via ACTION_VIEW.
- PEX-25 Twitch / Kick live: public follows-by-username list (no sign-in in v1); a row "Live now" from the public
  status endpoints; opens the app page `twitch://stream/<login>` or `kick.com/<slug>`; offline streamers hidden.

## Trackers
- PEX-26 Trakt / Simkl sync: OAuth device code (same flow as PRV-06, own client id/secret entered like YouTube's).
  Imports watched and watchlist into My List and signals (PER-14, PER-02); optional write-back of "finished" (off by
  default). Tokens are secrets, per profile; `invalid_grant` signs out. Never sync Guest or Kids (PRF-10, PRF-11).

## Widgets
- PEX-27 News and prices as optional cards (a card-shaped row "Today": up to 6 tiles): news = feeds from PEX-20 (headline
  only); prices = a user list of symbols (crypto from CoinGecko public API, FX from a public rate endpoint);
  refresh 15 min, last good value kept 2 h then shown stale-dimmed. Each tile type is removable. No auth required.

## Strings, tests
- PEX-30 en+ar strings for every add-on name, description, credential label, hint, test messages.
- PEX-31 Removal: deleting an add-on's folders and its registration line compiles and passes tests (CI check).
- Tests: `PEX_14` M3U fixtures (BOM, missing attrs, 10k lines), `PEX_15` XMLTV, `PEX_21` RSS/Atom/JSON mapping,
  `PEX_22` XXE rejected, `PEX_04` no secret in logs/cache (log capture), `PEX_10` PIN poll, `PEX_26` token handling.
