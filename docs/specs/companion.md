# Phone companion

- CMP-01 While Shamash runs, an HTTP server listens on port 8765 on the TV's local network.
- CMP-02 `GET /` serves one mobile page: a PIN field (asked once, then kept in the phone's localStorage), a text box,
  a Send button and the result message. `?text=` / `?url=` pre-fill the box (for the phone's share target).
- CMP-03 `POST /send` (form: `pin`, `text`) with a wrong PIN answers 403. The 4-digit PIN is random on first run and
  shown in Settings → Phone remote with the address.
- CMP-04 Handling: a YouTube link (watch, youtu.be, shorts, live) opens that video in YouTube; a TOD link opens TOD;
  other http(s) links open with ACTION_VIEW (the matching app if one handles it); anything else opens Search with
  that text (SRCH-04). The TV shows a short toast "From your phone: …".
- CMP-05 Bodies over 4 KB are cut; one request per connection; the server never blocks the main thread.
