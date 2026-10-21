import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {CAPTIONS} from '../captions';
import {Captions} from '../components/Captions';
import {FocusCard, focusAmount} from '../components/Focus';
import {Check} from '../components/Icons';
import {Backdrop, Scene} from '../components/Scene';
import {Label, ShamashScreen} from '../components/ShamashScreen';
import {SunMark} from '../components/SunMark';
import {TVFrame} from '../components/TVFrame';
import {C, clamp, ease, pop} from '../theme';

export const SETUP_DUR = 330;

const STEPS = ['Your name', 'Your city', 'Your apps', 'Your teams', 'What you like', 'Quick start'];
const STEP_LEN = 50;
const STEP_START = 15;

const typed = (text: string, frame: number, start: number, speed = 3) =>
  text.slice(0, Math.max(0, Math.floor((frame - start) / speed)));

const Field: React.FC<{text: string; caret: boolean}> = ({text, caret}) => (
  <div
    style={{
      width: 900,
      height: 110,
      borderRadius: 18,
      background: C.card,
      boxShadow: `0 0 0 4px ${C.gold}, 0 0 40px 6px ${C.goldGlow}`,
      display: 'flex',
      alignItems: 'center',
      padding: '0 40px',
      fontSize: 56,
      fontWeight: 600,
    }}
  >
    {text}
    {caret && <span style={{width: 4, height: 60, background: C.gold, marginLeft: 6}} />}
  </div>
);

const Tick: React.FC<{on: number}> = ({on}) => (
  <div
    style={{
      position: 'absolute',
      top: 14,
      right: 14,
      width: 44,
      height: 44,
      borderRadius: 22,
      background: C.gold,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      transform: `scale(${on})`,
    }}
  >
    <Check size={30} color={C.night} stroke={3} />
  </div>
);

const APPS: [string, string][] = [
  ['Nova', '#e5484d'],
  ['Orbit', '#3e63dd'],
  ['Reelbox', '#30a46c'],
  ['Kickoff TV', '#f76b15'],
  ['Cinemix', '#8e4ec6'],
  ['Tunes', '#12a594'],
];
const TEAMS: [string, string, string][] = [
  ['Red Lions', '#d13b3b', '#ffd27a'],
  ['Blue Falcons', '#2f5fd0', '#ffffff'],
  ['Tigris FC', '#1d8a6e', '#f4c450'],
  ['Desert Hawks', '#b5762a', '#1b1f3a'],
];
const TASTES = ['Thrillers', 'Comedy', 'Documentaries', 'Anime', 'Drama', 'Sci-fi', 'Football', 'Cooking', 'Kids'];
const PRESETS = ['Cinema', 'Sports first', 'Kids', 'Minimal'];
const PRESET_BG = [
  'linear-gradient(160deg, #3a1c71, #1b1f3a 70%)',
  'linear-gradient(160deg, #1d8a6e, #1b1f3a 70%)',
  'linear-gradient(160deg, #5b3cc4, #1fa2a8)',
  `linear-gradient(160deg, #2a2f52, ${C.card})`,
];

const StepBody: React.FC<{step: number; t: number}> = ({step, t}) => {
  switch (step) {
    case 0:
      return (
        <>
          <div style={{fontSize: 60, fontWeight: 700, marginBottom: 50}}>What's your first name?</div>
          <Field text={typed('Sam', t, 8, 5)} caret={Math.floor(t / 8) % 2 === 0} />
          <div style={{fontSize: 30, color: C.dim, marginTop: 34}}>So the TV can say good evening.</div>
        </>
      );
    case 1:
      return (
        <>
          <div style={{fontSize: 60, fontWeight: 700, marginBottom: 50}}>Where are you?</div>
          <Field text={typed('Baghdad', t, 4, 3)} caret={t < 28} />
          <div
            style={{
              marginTop: 34,
              display: 'inline-flex',
              gap: 16,
              alignItems: 'center',
              padding: '16px 28px',
              borderRadius: 40,
              background: C.mine,
              fontSize: 32,
              opacity: ease(t, 28, 12),
            }}
          >
            <span style={{color: C.gold}}>●</span> Baghdad, Iraq · 24°
          </div>
          <div style={{fontSize: 30, color: C.dim, marginTop: 30}}>For the weather and the evening look.</div>
        </>
      );
    case 2:
      return (
        <>
          <div style={{fontSize: 60, fontWeight: 700, marginBottom: 44}}>Which apps do you pay for?</div>
          <div style={{display: 'flex', flexWrap: 'wrap', gap: 30, width: 1000}}>
            {APPS.map(([name, color], i) => {
              const on = [0, 2, 3].includes(i) ? pop(t, 8 + [0, 2, 3].indexOf(i) * 10) : 0;
              return (
                <div
                  key={name}
                  style={{
                    position: 'relative',
                    width: 300,
                    height: 168,
                    borderRadius: 18,
                    background: `linear-gradient(135deg, ${color}, ${C.card})`,
                    display: 'flex',
                    alignItems: 'flex-end',
                    padding: 22,
                    fontSize: 36,
                    fontWeight: 700,
                    boxShadow: on > 0.5 ? `0 0 0 4px ${C.gold}` : 'none',
                  }}
                >
                  {name}
                  <Tick on={on} />
                </div>
              );
            })}
          </div>
        </>
      );
    case 3:
      return (
        <>
          <div style={{fontSize: 60, fontWeight: 700, marginBottom: 44}}>Your teams</div>
          <div style={{display: 'flex', gap: 34}}>
            {TEAMS.map(([name, a, b], i) => {
              const on = i === 0 || i === 2 ? pop(t, i === 0 ? 10 : 22) : 0;
              return (
                <div
                  key={name}
                  style={{
                    position: 'relative',
                    width: 220,
                    height: 280,
                    borderRadius: 18,
                    background: C.card,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 22,
                    boxShadow: on > 0.5 ? `0 0 0 4px ${C.gold}` : 'none',
                  }}
                >
                  <svg width={110} height={130} viewBox="0 0 100 120">
                    <path d="M50 4 L94 18 L90 70 Q80 102 50 116 Q20 102 10 70 L6 18 Z" fill={a} stroke={b} strokeWidth={5} />
                    <path d="M50 30 L60 52 L84 54 L66 70 L72 94 L50 81 L28 94 L34 70 L16 54 L40 52 Z" fill={b} opacity={0.9} />
                  </svg>
                  <div style={{fontSize: 28, fontWeight: 600}}>{name}</div>
                  <Tick on={on} />
                </div>
              );
            })}
          </div>
        </>
      );
    case 4:
      return (
        <>
          <div style={{fontSize: 60, fontWeight: 700, marginBottom: 44}}>What do you like?</div>
          <div style={{display: 'flex', flexWrap: 'wrap', gap: 22, width: 1020}}>
            {TASTES.map((name, i) => {
              const picks = [0, 2, 4, 6];
              const on = picks.includes(i) ? ease(t, 6 + picks.indexOf(i) * 8, 10) : 0;
              return (
                <div
                  key={name}
                  style={{
                    padding: '20px 36px',
                    borderRadius: 50,
                    fontSize: 34,
                    fontWeight: 600,
                    background: on > 0.5 ? C.gold : C.card,
                    color: on > 0.5 ? C.night : C.ink,
                    transform: `scale(${1 + 0.06 * Math.sin(Math.PI * on)})`,
                  }}
                >
                  {name}
                </div>
              );
            })}
          </div>
        </>
      );
    default:
      return (
        <>
          <div style={{fontSize: 60, fontWeight: 700, marginBottom: 44}}>Start from a preset</div>
          <div style={{display: 'flex', gap: 30}}>
            {PRESETS.map((name, i) => (
              <FocusCard
                key={name}
                focus={focusAmount(t, [[4, 0], [16, 1]], i)}
                w={220}
                h={260}
                radius={18}
                style={{background: PRESET_BG[i], display: 'flex', alignItems: 'flex-end', padding: 22}}
              >
                <div style={{fontSize: 32, fontWeight: 700}}>{name}</div>
                {i === 1 && <Tick on={pop(t, 26)} />}
              </FocusCard>
            ))}
          </div>
          <div
            style={{
              marginTop: 50,
              display: 'inline-block',
              padding: '24px 44px',
              borderRadius: 16,
              background: C.gold,
              color: C.night,
              fontSize: 34,
              fontWeight: 700,
              opacity: ease(t, 32, 12),
            }}
          >
            Make Shamash my home screen
          </div>
        </>
      );
  }
};

export const Setup: React.FC = () => {
  const frame = useCurrentFrame();
  const step = Math.min(STEPS.length - 1, Math.max(0, Math.floor((frame - STEP_START) / STEP_LEN)));
  const t = frame - STEP_START - step * STEP_LEN;
  const enter = ease(t, 0, 14);
  const progress = interpolate(frame, [STEP_START, STEP_START + STEPS.length * STEP_LEN], [0.03, 1], clamp);
  return (
    <Scene dur={SETUP_DUR}>
      <Backdrop />
      <TVFrame>
        <ShamashScreen>
          {/* Left: the steps */}
          <div style={{position: 'absolute', left: 110, top: 110, width: 520}}>
            <SunMark size={110} frame={200} glow={false} />
            <Label style={{marginTop: 30}}>Welcome</Label>
            <div style={{fontSize: 52, fontWeight: 700, marginTop: 10, lineHeight: 1.15}}>Let's make this TV yours</div>
            <div style={{marginTop: 50, display: 'flex', flexDirection: 'column', gap: 22}}>
              {STEPS.map((s, i) => (
                <div key={s} style={{display: 'flex', alignItems: 'center', gap: 22, fontSize: 32, color: i === step ? C.ink : i < step ? C.dim : C.faint}}>
                  <div
                    style={{
                      width: 44,
                      height: 44,
                      borderRadius: 22,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 22,
                      fontWeight: 700,
                      background: i < step ? C.gold : i === step ? 'transparent' : C.card,
                      border: i === step ? `3px solid ${C.gold}` : 'none',
                      color: i < step ? C.night : C.ink,
                    }}
                  >
                    {i < step ? <Check size={26} color={C.night} stroke={3} /> : i + 1}
                  </div>
                  <span style={{fontWeight: i === step ? 700 : 500}}>{s}</span>
                </div>
              ))}
            </div>
          </div>
          {/* Right: the current step */}
          <div
            style={{
              position: 'absolute',
              left: 720,
              top: 110,
              width: 1100,
              height: 780,
              borderRadius: 28,
              background: 'rgba(27,31,58,0.55)',
              border: `1px solid ${C.line}`,
              overflow: 'hidden',
            }}
          >
            <div style={{position: 'absolute', left: 0, top: 0, height: 8, width: `${progress * 100}%`, background: C.gold}} />
            <div
              style={{
                position: 'absolute',
                left: 70,
                top: 90,
                right: 40,
                opacity: enter,
                transform: `translateX(${(1 - enter) * 80}px)`,
              }}
            >
              <div style={{fontSize: 26, color: C.faint, marginBottom: 18}}>
                Step {step + 1} of {STEPS.length}
              </div>
              <StepBody step={step} t={t} />
            </div>
          </div>
        </ShamashScreen>
      </TVFrame>
      <Captions items={CAPTIONS.setup} />
    </Scene>
  );
};
