# Phone companion

- CMP-01 After onboarding consent, an HTTP server listens on port 8765 bound only to the TV's site-local IPv4
  address. Without a LAN address it waits, then stops; startup can retry. It never binds all interfaces.
- CMP-02 `GET /` serves one mobile page: a PIN field (asked once, then kept in the phone's localStorage), a text box,
  a Send button and the result message. `?text=` / `?url=` pre-fill the box (for the phone's share target).
- CMP-03 `POST /send` (form: `pin`, `text`) with a wrong PIN answers 403. The 6-digit PIN is random on first run and
  shown in Settings → Phone remote with the address. Five failures across all clients lock sends for five minutes;
  repeated lockouts double the wait and the third rotates the PIN. Even a correct PIN is refused during lockout.
  Sends require `X-Shamash: 1`; Host must be the TV address and port. CORS preflights are refused.
- CMP-04 Handling: a YouTube link (watch, youtu.be, shorts, live) opens that video in YouTube; a TOD link opens TOD;
  other http(s) links open with ACTION_VIEW (the matching app if one handles it); anything else opens Search with
  that text (SRCH-04). The TV shows a short toast "From your phone: …".
- CMP-05 Bodies over 4 KB are cut; one request per connection; the server never blocks the main thread. Four
  workers and an eight-connection queue bound resource use; reads have a five-second absolute deadline. Unknown
  methods and paths are dropped before headers are read. The page has a fresh script nonce and a restrictive CSP.

## Phone page: editing, queue, backup (additions)
- CMP-06 The page works on the active profile (PRF-06) and shows its name; with PINs (PRF-05) the phone is asked for
  that profile's PIN to edit it. Kids and Guest profiles cannot be edited from the phone without the owner's PIN.
  All new endpoints keep CMP-03 rules (PIN, `X-Shamash: 1`, Host check, lockout) and CMP-05 limits (bodies <= 64 KB
  for edits, 1 MB for backup import).
- CMP-07 Rows: `GET /api/rows` lists the row editor model (LAY-02: id, title, hidden, pinned, order); `POST
  /api/rows` saves a full replacement list (validated: known fields only, <= 60 rows, titles <= 40 chars). Phone UI:
  up/down buttons, hide, rename, pin. The TV applies it live and shows a short toast.
- CMP-08 Text entry: a "Type for the TV" form for any TV text field that asks for it (teams, city, feed URL,
  server URL, credential fields, PEX-05): the TV shows a code of the field; the phone page lists pending fields.
  Secret fields are sent in the POST body only, never in the URL or the page, and are cleared from the phone page
  after submit. The TV never echoes them.
- CMP-09 Teams and competitions: `GET/POST /api/teams` (free text list, <= 20 names of <= 40 chars; competitions from
  the ESPN code list and MC-07 additions).
- CMP-10 Feeds (PEX-20): add, remove, "Try it" (the TV fetches and returns the first 3 mapped titles). URLs must be
  https (or http with the explicit flag); private-range URLs are accepted only for media servers (PEX-12), never for
  feeds, to avoid the phone turning the TV into a LAN scanner.
- CMP-11 Queue and share-to-TV: a "Queue" list on the TV per profile (<= 30). A link/title sent with "Add to queue"
  (instead of "Open now") is stored as a My List-style entry resolved through CMP-04 types; row "Queue" on Home
  (hidden when empty) and a Dismiss on each card. Sharing from the phone's share sheet (`?url=`) offers both buttons.
  Opening a queued item removes it.
- CMP-12 A backup contains: profiles (name, avatar, kind, content level, limits, apps, teams, competitions, genres,
  layout, moment rules, feeds, muted, My List, queue), add-on enable flags and row-source choices; optionally watch
  history and signals (PER-18); optionally secrets (CMP-14). Never: ADB keys, the companion PIN, PIN hashes unless
  the user ticks "include PINs".
- CMP-13 Export: `POST /api/backup/export` returns one file `shamash-backup-<date>.shamash` (JSON inside a versioned
  envelope `{"v":1,...}`). The TV asks for confirmation on screen before it creates one (so a stolen phone PIN does
  not export data silently).
- CMP-14 Encryption: always with a passphrase chosen on the TV or phone: PBKDF2-HMAC-SHA256 (>= 210k iterations,
  random 16-byte salt) -> AES-256-GCM, random 12-byte nonce; passphrase min 8 chars, never stored or logged. Secrets
  are excluded unless the user ticks "include keys and sign-ins"; sign-in refresh tokens are then included only for
  that explicit choice. An unencrypted export is not offered.
- CMP-15 Import: `POST /api/backup/import` (file + passphrase), validated: version, authenticated decryption, schema,
  sizes. Preview on the TV (profiles and counts) then "Merge" (add profiles that don't exist) or "Replace" (owner
  PIN needed); a failed import changes nothing (atomic swap). Unknown fields are ignored; newer `v` than known = refuse
  with a message. Wrong passphrase: 3 tries then 1-minute wait.
- CMP-16 Strings en+ar on the page (page follows the phone's language; RTL for Arabic). Page keeps the CSP nonce.
- Tests: `CMP_07` rows save validation, `CMP_08` secret never in response, `CMP_14` crypto round-trip and tamper
  detection, `CMP_15` atomic import failure leaves data intact, `CMP_10` private-range rejection.
