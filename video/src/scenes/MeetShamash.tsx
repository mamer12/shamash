import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {CAPTIONS} from '../captions';
import {Captions} from '../components/Captions';
import {Backdrop, Scene} from '../components/Scene';
import {SunMark} from '../components/SunMark';
import {C, clamp, ease, fonts} from '../theme';

export const MEET_DUR = 300;

export const MeetShamash: React.FC = () => {
  const frame = useCurrentFrame();
  const word = 'SHAMASH';
  const rise = ease(frame, 0, 60);
  const dawn = interpolate(frame, [0, 90], [0, 1], clamp);
  return (
    <Scene dur={MEET_DUR}>
      <Backdrop />
      {/* Sunrise light */}
      <AbsoluteFill
        style={{
          background: `radial-gradient(ellipse 55% 45% at 50% ${70 - dawn * 40}%, rgba(244,196,80,${0.28 * dawn}), transparent 70%)`,
        }}
      />
      <div
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          top: 70 + (1 - rise) * 120,
          display: 'flex',
          justifyContent: 'center',
        }}
      >
        <SunMark size={380} frame={frame} spin={frame * 0.15} />
      </div>
      <div
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          top: 500,
          display: 'flex',
          justifyContent: 'center',
          fontFamily: fonts.sans,
          fontWeight: 800,
          fontSize: 150,
          color: C.ink,
          letterSpacing: 26,
          paddingLeft: 26,
        }}
      >
        {word.split('').map((ch, i) => {
          const p = ease(frame, 48 + i * 4, 24);
          return (
            <span key={i} style={{opacity: p, transform: `translateY(${(1 - p) * 40}px)`, display: 'inline-block'}}>
              {ch}
            </span>
          );
        })}
      </div>
      <div
        dir="rtl"
        lang="ar"
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          top: 690,
          textAlign: 'center',
          fontFamily: fonts.arabic,
          fontWeight: 700,
          fontSize: 76,
          lineHeight: 1.4,
          color: C.gold,
          opacity: ease(frame, 85, 30),
        }}
      >
        شمش
      </div>
      <Captions items={CAPTIONS.meet} />
    </Scene>
  );
};
