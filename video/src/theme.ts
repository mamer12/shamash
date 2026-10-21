import {loadFont as loadInter} from '@remotion/google-fonts/Inter';
import {loadFont as loadArabic} from '@remotion/google-fonts/NotoSansArabic';
import {spring} from 'remotion';

// Fonts are fetched from Google Fonts at render time (needs network).
const inter = loadInter('normal', {weights: ['400', '500', '600', '700', '800', '900'], subsets: ['latin']});
const arabic = loadArabic('normal', {weights: ['400', '500', '600', '700'], subsets: ['arabic']});

export const FPS = 30;

export const fonts = {
  sans: `${inter.fontFamily}, sans-serif`,
  arabic: `${arabic.fontFamily}, ${inter.fontFamily}, sans-serif`,
  mono: `ui-monospace, "DejaVu Sans Mono", monospace`,
};

/** Shamash's palette, from app/src/main/java/io/shamash/ui/Theme.kt (night blue + the Sun's gold). */
export const C = {
  night: '#0A0C1E',
  dusk: '#14172E',
  card: '#1B1F3A',
  mine: '#1A2440',
  gold: '#F4C450',
  dawn: '#FFE28C',
  ink: '#F5F0E6',
  dim: 'rgba(245,240,230,0.70)',
  faint: 'rgba(245,240,230,0.40)',
  line: 'rgba(245,240,230,0.12)',
  live: '#FF453A',
  goldGlow: 'rgba(244,196,80,0.55)',
};

export const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/** A smooth, non-bouncy spring that starts at `delay`. */
export const ease = (frame: number, delay = 0, durationInFrames?: number) =>
  spring({frame: frame - delay, fps: FPS, config: {damping: 200}, durationInFrames});

/** A lively spring with a little overshoot, for things that pop in. */
export const pop = (frame: number, delay = 0) =>
  spring({frame: frame - delay, fps: FPS, config: {damping: 14, stiffness: 160, mass: 0.7}});
