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
