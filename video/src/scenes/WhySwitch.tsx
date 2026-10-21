import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {CAPTIONS} from '../captions';
import {Captions} from '../components/Captions';
import {Check} from '../components/Icons';
import {Backdrop, Scene} from '../components/Scene';
import {C, clamp, ease, fonts, pop} from '../theme';

export const WHY_DUR = 330;

const ROWS: {old: string; oldAr: string; neu: string; neuAr: string}[] = [
  {old: 'Ads and sponsored rows', oldAr: 'إعلانات وصفوف مموّلة', neu: 'No ads', neuAr: 'بلا إعلانات'},
  {old: 'Fixed rows', oldAr: 'صفوف ثابتة', neu: 'Rows you choose', neuAr: 'صفوف تختارها أنت'},
  {old: 'One profile', oldAr: 'ملف واحد', neu: 'Family profiles + Kids lock', neuAr: 'ملفات للعائلة وقفل للأطفال'},
  {old: 'Closed', oldAr: 'مغلق', neu: 'Open source (MIT)', neuAr: 'مفتوح المصدر'},
];

const LEFT = 210;
const COL_W = 730;
const GAP = 40;
const TOP = 140;
const ROW_H = 126;
const ROW_GAP = 22;

const Cell: React.FC<{en: string; ar: string; good: boolean; p: number; strike: number}> = ({en, ar, good, p, strike}) => (
  <div
    style={{
      position: 'relative',
      width: COL_W,
      height: ROW_H,
      borderRadius: 20,
      background: good ? C.mine : 'rgba(27,31,58,0.55)',
      border: good ? `2px solid ${C.gold}` : `1px solid ${C.line}`,
      boxShadow: good ? `0 0 ${40 * p}px ${C.goldGlow.replace('0.55', '0.25')}` : 'none',
      display: 'flex',
      alignItems: 'center',
      padding: '0 34px',
      gap: 24,
      opacity: p,
      transform: `translateX(${(1 - p) * (good ? 60 : -60)}px)`,
    }}
  >
    <div style={{width: 46, height: 46, borderRadius: 23, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: good ? C.gold : 'rgba(255,69,58,0.18)'}}>
      {good ? <Check size={30} color={C.night} stroke={3} /> : <span style={{color: C.live, fontSize: 30, fontWeight: 800, lineHeight: 1}}>×</span>}
    </div>
    <div style={{flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 2, minWidth: 0}}>
      <div style={{position: 'relative', fontFamily: fonts.sans, fontSize: 36, fontWeight: good ? 700 : 500, color: good ? C.ink : C.dim, whiteSpace: 'nowrap'}}>
        {en}
        {!good && <div style={{position: 'absolute', left: 0, top: '52%', height: 3, width: `${strike * 100}%`, background: C.live}} />}
      </div>
      <div dir="rtl" lang="ar" style={{fontFamily: fonts.arabic, fontSize: 26, lineHeight: 1.5, fontWeight: 500, color: good ? C.dawn : C.faint, whiteSpace: 'nowrap', textAlign: 'left'}}>
        {ar}
      </div>
    </div>
  </div>
);

const OriginalToggle: React.FC<{frame: number}> = ({frame}) => {
  const p = ease(frame, 190, 18);
  // Press: Shamash -> Original -> Shamash, to show the way back is one press.
  const toOriginal = ease(frame, 225, 12) - ease(frame, 275, 12);
  const knob = toOriginal;
  return (
    <div style={{position: 'absolute', left: 0, right: 0, top: 745, display: 'flex', justifyContent: 'center', opacity: p, transform: `translateY(${(1 - p) * 20}px)`}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 28, padding: '18px 34px', borderRadius: 50, background: C.card, border: `1px solid ${C.line}`, fontFamily: fonts.sans}}>
        <div style={{fontSize: 30, color: C.dim}}>Home screen</div>
        <div style={{position: 'relative', width: 420, height: 64, borderRadius: 32, background: C.night}}>
          <div style={{position: 'absolute', top: 4, left: 4 + knob * 206, width: 206, height: 56, borderRadius: 28, background: C.gold, boxShadow: `0 0 30px ${C.goldGlow}`}} />
          <div style={{position: 'absolute', inset: 0, display: 'flex', fontSize: 28, fontWeight: 700}}>
            <div style={{flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: knob < 0.5 ? C.night : C.dim}}>Shamash</div>
            <div style={{flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: knob >= 0.5 ? C.night : C.dim}}>Original</div>
          </div>
        </div>
      </div>
    </div>
  );
};

export const WhySwitch: React.FC = () => {
  const frame = useCurrentFrame();
  const head = ease(frame, 6, 18);
  return (
    <Scene dur={WHY_DUR}>
      <Backdrop />
      <div style={{position: 'absolute', top: 62, left: LEFT, width: COL_W, fontFamily: fonts.sans, fontSize: 30, fontWeight: 700, letterSpacing: 4, color: C.faint, opacity: head}}>STOCK HOME</div>
      <div style={{position: 'absolute', top: 62, left: LEFT + COL_W + GAP, width: COL_W, fontFamily: fonts.sans, fontSize: 30, fontWeight: 700, letterSpacing: 4, color: C.gold, opacity: head}}>SHAMASH</div>
      {ROWS.map((r, i) => {
        const delay = 20 + i * 26;
        const pOld = ease(frame, delay, 16);
        const pNew = pop(frame, delay + 10);
        const strike = interpolate(frame, [delay + 12, delay + 24], [0, 1], clamp);
        return (
          <div key={r.old} style={{position: 'absolute', left: LEFT, top: TOP + i * (ROW_H + ROW_GAP), display: 'flex', gap: GAP}}>
            <Cell en={r.old} ar={r.oldAr} good={false} p={pOld} strike={strike} />
            <Cell en={r.neu} ar={r.neuAr} good p={Math.min(1, pNew)} strike={0} />
          </div>
        );
      })}
      <OriginalToggle frame={frame} />
      <Captions items={CAPTIONS.why} />
    </Scene>
  );
};
