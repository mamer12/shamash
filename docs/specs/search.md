# Search

- SRCH-01 The Search screen has a text field (TV keyboard; voice through the keyboard's mic when the TV has a speech
  service) and result rows, one per provider: Cinemana (add-on), each subscribed service (TMDB `search/multi` +
  `watch/providers` for the region), YouTube (signed in).
- SRCH-02 Search runs 900 ms after typing stops, or at once on Enter; older searches are cancelled.
- SRCH-03 Ranking inside a row: exact title match first, then titles starting with the query, then the provider's
  order; case- and accent-insensitive; movies and series together.
- SRCH-04 When the screen is opened with a query (from the phone) or Enter is pressed, the keyboard closes and the
  first result gets focus.
- SRCH-05 Results open through the Opener (ARCH-12).
