# Platform (backup, add-on authoring, voice, accessibility, import presets)

## Backup and restore
- PLT-01 Settings -> Backup: Export and Import on the TV as well as the phone (CMP-13..15). TV flow writes the
  encrypted file to `Downloads/` (SAF picker) or serves it once to the phone page; Import reads from USB or the same.
- PLT-02 The file format is the same envelope as CMP-12; one `BackupCodec` in `domain` (pure, testable) used by both.
- PLT-03 Automatic local backup of non-secret layout and profiles to app storage before each migration (PRF-17),
  keeping the last 3; excluded from Android backup (ARCH-06).

## Add-on authoring
- PLT-04 `docs/addons/README.md` explains a provider in under 150 lines: the Provider contract, `AddOn` registration
  (PEX-01), credentials (PEX-03), row sources (PEX-06), fail-soft and level rules, a test checklist.
- PLT-05 `docs/addons/template/` is a compilable skeleton package (`data/example`, one fake provider reading a JSON
  fixture, one test, one registration line commented). It is built and tested by CI, so it cannot rot.
- PLT-06 Rules for an add-on: no network on the main thread, no secrets in logs, ContentLevel honored, strings en+ar,
  works when removed (PEX-31), spec file `docs/specs/addon-<id>.md` with its own IDs.
- PLT-07 Add-ons are source-level only (compiled in); there is no dynamic code loading in v1.

## Voice search
- PLT-08 The Search button and the remote's voice key start `RecognizerIntent` (or the TV's assistant search hand-off
  to `shamash://search?q=`); the recognized text fills the existing search (SRCH-04) and runs. No audio is stored or
  sent by Shamash; a recognizer missing gives a toast and the keyboard.
- PLT-09 `ACTION_SEARCH` / `SEARCH` intents from the TV launcher voice ("search Shamash for ...") are handled by the
  Search activity with the query; results obey PRF-12 and mutes (PER-08).
- PLT-10 Language: the recognizer gets the app language (en/ar); Arabic text is normalized (diacritics, alef forms)
  by the same function the search ranking uses.

## Accessibility
- PLT-11 Text scale: Normal / Large (1.15) / Larger (1.3) in Settings and respected from the system font scale;
  layouts (LAY-15) must not clip at Larger (1080p, 3-line overview limits adjust).
- PLT-12 High contrast: a theme flag (also follows the TV's setting when exposed): solid backgrounds behind text over
  art, 7:1 text contrast, thick white focus border (6 dp), no dimming gradients below 70% opacity.
- PLT-13 TalkBack: every focusable has a content description: cards "<title>, <kind>, <provider>, <progress>%";
  rows announce their title and the reason (PER-11); focus order follows the visual order and mirrors in RTL;
  the carousel announces "<n> of <m>". Decorative images are marked as such.
- PLT-14 Motion: "Reduce motion" turns off the expanding-tile growth animation, preview autoplay (HOME-20) and
  crossfade into instant swaps.
- PLT-15 No information by color alone (live = red + text "LIVE"; mine = gold edge + star).
- PLT-16 en+ar strings for all labels above; an automated check lists focusables without a description.

## First-run import presets
- PLT-17 Onboarding step "Quick start": "I use Netflix and YouTube", "I use Shahid and OSN+", "Sports first",
  "Just the basics" - each a data preset: subscriptions (by package, only those installed), teams empty, layout preset
  (LAY-07), add-ons on, keepRunning. Applying pre-fills the next screens and is fully editable.
- PLT-18 Presets are JSON assets (`assets/presets/*.json`), region-aware; a preset naming an app that is not
  installed ignores it silently. Users can import a preset file from backup (PLT-02).
- Tests: `PLT_02` codec round-trip, `PLT_17` preset applied against a fake installed-apps list, `PLT_16` a11y lint,
  `PLT_08` voice text to query (with Arabic normalization).
