# Shamash showcase video

An 84-second animated showcase of Shamash (1920x1080, 30 fps, no sound), made with
[Remotion](https://www.remotion.dev). Everything on screen is drawn with React and CSS: no screenshots, no
third-party logos, no real people. Captions are in English, with an Arabic (right-to-left) line under each.

## Requirements

- Node 20+
- Google Chrome at `/usr/bin/google-chrome` (set in `remotion.config.ts`; change the path for another system,
  or delete the line to let Remotion download its own headless Chrome)
- Network access while rendering: the fonts (Inter, Noto Sans Arabic) come from Google Fonts
- No ffmpeg needed: Remotion brings its own

```bash
cd video
npm install
```

## Preview and edit

```bash
npm run studio        # opens Remotion Studio in the browser, with a timeline and live reload
```

Where things are:

| What | File |
| --- | --- |
| Every caption (English + Arabic) and when it shows | `src/captions.ts` |
| Scene order | `src/Showcase.tsx` |
| Each scene, and its length (`..._DUR`, in frames; 30 = 1 s) | `src/scenes/*.tsx` |
| Colors and fonts (from the app's `ui/Theme.kt`) | `src/theme.ts` |
| The sun mark, TV frame, focus glow, icons, artwork | `src/components/` |

The story: `OldWay` (a cluttered stock home) → `MeetShamash` (the sun mark and wordmark) → `Setup` (onboarding) →
`Home` (billboard and rows, profiles with a Kids lock, the row editor, presets) → `OnePlace` (one search, live
scores over a video, your own servers, the phone as remote and queue) → `WhySwitch` (Old vs Shamash, and the way
back) → `Outro`.

Caption frames in `captions.ts` count from the start of their scene, so changing one scene's length doesn't move
the others' captions. The total length is the sum of the scenes' `_DUR` values.

## Render

```bash
npm run render        # out/shamash.mp4  (H.264, CRF 23)
npm run poster        # out/poster.png   (frame 1170: the Home screen)
npm run gif           # out/preview.gif  (8 s of the sun and wordmark reveal, small)
```

Single frames, to check a change: `npx remotion still Showcase out/check.png --frame=1200`.

`node_modules/` and `out/` are git-ignored.
