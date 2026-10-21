import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import type {Caption} from '../captions';
import {C, clamp, ease, fonts} from '../theme';

/** The caption band at the bottom: an English line, and its Arabic line (right to left) under it. */
export const Captions: React.FC<{items: Caption[]}> = ({items}) => {
  const frame = useCurrentFrame();
  const cur = items.find((c) => frame >= c.from && frame < c.to);
  if (!cur) return null;
  const local = frame - cur.from;
  const len = cur.to - cur.from;
  const inP = ease(local, 0, 16);
  const out = interpolate(local, [len - 10, len], [1, 0], clamp);
  const opacity = Math.min(inP, out);
  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        right: 0,
        bottom: 34,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 4,
        opacity,
        transform: `translateY(${(1 - inP) * 18}px)`,
      }}
    >
      <div
        style={{
          fontFamily: fonts.sans,
          fontWeight: 700,
          fontSize: 46,
          lineHeight: 1.25,
          color: C.ink,
          letterSpacing: -0.5,
          textAlign: 'center',
          maxWidth: 1760,
          textShadow: '0 2px 18px rgba(0,0,0,0.6)',
        }}
      >
        {cur.en}
      </div>
      <div
        dir="rtl"
        lang="ar"
        style={{
          fontFamily: fonts.arabic,
          fontWeight: 600,
          fontSize: 36,
          lineHeight: 1.6,
          color: C.dawn,
          textAlign: 'center',
          maxWidth: 1760,
          textShadow: '0 2px 18px rgba(0,0,0,0.6)',
        }}
      >
        {cur.ar}
      </div>
    </div>
  );
};
