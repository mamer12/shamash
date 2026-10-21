import React from 'react';
import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {OUTRO} from '../captions';
import {Backdrop, Scene} from '../components/Scene';
import {SunMark} from '../components/SunMark';
import {C, ease, fonts} from '../theme';

export const OUTRO_DUR = 180;

export const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const a = ease(frame, 14, 20);
  const b = ease(frame, 26, 20);
  const c = ease(frame, 40, 20);
  return (
    <Scene dur={OUTRO_DUR}>
      <Backdrop />
      <AbsoluteFill style={{background: 'radial-gradient(ellipse 50% 40% at 50% 26%, rgba(244,196,80,0.18), transparent 70%)'}} />
      <div style={{position: 'absolute', top: 110, left: 0, right: 0, display: 'flex', justifyContent: 'center'}}>
        <SunMark size={250} frame={frame + 10} spin={frame * 0.2} />
      </div>
      <div style={{position: 'absolute', top: 420, left: 0, right: 0, textAlign: 'center', fontFamily: fonts.sans, fontWeight: 800, fontSize: 104, color: C.ink, letterSpacing: -2, opacity: a, transform: `translateY(${(1 - a) * 30}px)`}}>
        {OUTRO.en}
      </div>
      <div dir="rtl" lang="ar" style={{position: 'absolute', top: 560, left: 0, right: 0, textAlign: 'center', fontFamily: fonts.arabic, fontWeight: 700, fontSize: 62, lineHeight: 1.5, color: C.dawn, opacity: b, transform: `translateY(${(1 - b) * 30}px)`}}>
        {OUTRO.ar}
      </div>
      <div style={{position: 'absolute', top: 720, left: 0, right: 0, display: 'flex', justifyContent: 'center', opacity: c, transform: `scale(${0.92 + 0.08 * c})`}}>
        <div style={{padding: '22px 52px', borderRadius: 60, border: `3px solid ${C.gold}`, background: 'rgba(244,196,80,0.08)', fontFamily: fonts.mono, fontSize: 50, fontWeight: 700, color: C.gold, boxShadow: `0 0 50px ${C.goldGlow.replace('0.55', '0.3')}`}}>
          {OUTRO.url}
        </div>
      </div>
      <div style={{position: 'absolute', top: 880, left: 0, right: 0, textAlign: 'center', fontFamily: fonts.sans, fontSize: 30, color: C.dim, opacity: c}}>
        Open source · MIT · Android TV and Google TV 9+
      </div>
    </Scene>
  );
};
