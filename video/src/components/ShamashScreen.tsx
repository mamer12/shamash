import React from 'react';
import {AbsoluteFill} from 'remotion';
import {C, fonts} from '../theme';

/** Shamash's own screen background: night blue, warmer where the light comes from. */
export const ShamashScreen: React.FC<{children: React.ReactNode; glow?: string}> = ({children, glow = 'rgba(244,196,80,0.16)'}) => (
  <AbsoluteFill
    style={{
      background: `radial-gradient(ellipse 60% 55% at 12% 0%, ${glow}, transparent 70%), linear-gradient(180deg, ${C.dusk}, ${C.night})`,
      fontFamily: fonts.sans,
      color: C.ink,
    }}
  >
    {children}
  </AbsoluteFill>
);

/** A small gold pill label like the app's hero label. */
export const Label: React.FC<{children: React.ReactNode; style?: React.CSSProperties}> = ({children, style}) => (
  <div style={{color: C.gold, fontWeight: 700, fontSize: 24, letterSpacing: 4, textTransform: 'uppercase', ...style}}>{children}</div>
);
