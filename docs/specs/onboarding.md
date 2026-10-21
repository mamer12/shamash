# Onboarding (first run)

- ONB-01 On first launch (no saved profile) Shamash shows setup; after "Done" it never shows again unless the user
  picks "Redo setup" in Settings.
- ONB-02 Steps, each one screen, D-pad friendly, every step except Welcome skippable with "Skip":
  1. Welcome. 2. About you: first name, city (city is geocoded to latitude/longitude via Open-Meteo geocoding; the
  first match is used and shown for confirmation). 3. Your apps. 4. Your teams and competitions. 5. What you like.
  6. (Removed: the TMDB key and YouTube OAuth client are build-time values from local.properties, see ARCH and the README; no key screen on the TV.)
  7. Make Shamash your home screen (explains what changes and how to undo; can be done later from Settings).
- ONB-03 "Your apps" lists the TV's launchable apps with their TV banners, known streaming services first;
  selecting marks "I subscribe". At least one must be selectable with the D-pad only.
- ONB-04 Teams are free text (comma separated) matched case-insensitively against team names; competitions are chips
  (ESPN codes: uefa.champions, uefa.europa, eng.1, esp.1, ita.1, ger.1, fra.1, ksa.1).
- ONB-05 Genres are chips (Action, Comedy, Drama, Thriller, Sci-Fi, Animation, Documentary, Romance, Horror, Family,
  Anime, Arabic, Turkish, Korean, Sports).
- ONB-06 Answers are saved in DataStore as one `Profile`; Settings shows the same screens to change any answer.
- ONB-07 Language follows the TV (English or Arabic; Arabic lays out right-to-left).
