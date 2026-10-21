import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {C, clamp} from '../theme';

/** Fades a scene in and out through the night background. */
export const Scene: React.FC<{dur: number; children: React.ReactNode}> = ({dur, children}) => {
  const f = useCurrentFrame();
  const o = interpolate(f, [0, 12, dur - 12, dur], [0, 1, 1, 0], clamp);
  return <AbsoluteFill style={{opacity: o}}>{children}</AbsoluteFill>;
};

/** The room behind every scene: night blue with a faint warm glow. */
export const Backdrop: React.FC = () => {
  const f = useCurrentFrame();
  const x = 50 + Math.sin(f / 90) * 8;
  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(ellipse 70% 60% at ${x}% 38%, rgba(244,196,80,0.10), transparent 70%), radial-gradient(ellipse 90% 70% at 50% 120%, #1a1f45, ${C.night} 70%)`,
      }}
    />
  );
};
