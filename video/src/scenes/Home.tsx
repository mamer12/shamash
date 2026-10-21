import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {CAPTIONS} from '../captions';
import {Art, TITLES} from '../components/Art';
import {Captions} from '../components/Captions';
import {FocusCard, focusAmount, Schedule} from '../components/Focus';
import {Check, Eye, EyeOff, Handle, Lock, Moon, Pin, Play, Plus} from '../components/Icons';
import {Backdrop, Scene} from '../components/Scene';
import {Label, ShamashScreen} from '../components/ShamashScreen';
import {SunMark} from '../components/SunMark';
import {TVFrame} from '../components/TVFrame';
import {C, clamp, ease, pop} from '../theme';

export const HOME_DUR = 480;
const PART = 120;

const currentIndex = (t: number, s: Schedule) => s.filter(([at]) => t >= at).pop()?.[1] ?? 0;

/* ---------- a) The home screen ---------- */

const CARDS = [0, 4, 2, 6, 8];
const HERO_META = ['Thriller · 2h 4m · 4K', 'Drama · 6 episodes', 'Sci-fi · 1h 52m', 'Comedy · 1h 38m', 'Mystery · Series'];
const homeFocus: Schedule = [
  [14, 0],
  [42, 1],
  [70, 2],
  [96, 3],
];

export const TopBar: React.FC<{tab?: number}> = ({tab = 0}) => (
  <div style={{position: 'absolute', top: 40, left: 96, right: 96, height: 80, display: 'flex', alignItems: 'center', zIndex: 5}}>
    <SunMark size={54} frame={200} glow={false} />
    <div style={{fontSize: 38, fontWeight: 500, marginLeft: 20}}>Good evening, Sam</div>
    <div style={{display: 'flex', gap: 46, marginLeft: 90, fontSize: 30, fontWeight: 600}}>
      {['Home', 'Movies', 'Shows', 'Sports', 'Apps'].map((t, i) => (
        <div key={t} style={{color: i === tab ? C.ink : C.dim, borderBottom: i === tab ? `4px solid ${C.gold}` : '4px solid transparent', paddingBottom: 6}}>
          {t}
        </div>
      ))}
    </div>
    <div style={{marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 18}}>
      <Moon size={34} color={C.dawn} />
      <div style={{fontSize: 30, color: C.dim}}>24°</div>
      <div style={{fontSize: 58, fontWeight: 300, marginLeft: 16}}>21:30</div>
    </div>
  </div>
);

const HomeScreen: React.FC<{t: number}> = ({t}) => {
  const cur = currentIndex(t, homeFocus);
  const titleIn = ease(t - (homeFocus.find(([, i]) => i === cur)?.[0] ?? 0), 0, 12);
  const rowShift = interpolate(t, [96, 118], [0, -120], clamp);
  return (
    <ShamashScreen>
      {/* Billboard follows the focused card */}
      <div style={{position: 'absolute', inset: 0, height: 760}}>
        <Art seed={CARDS[0]} drift={t * 0.05} />
        {CARDS.slice(1).map((seed, k) => (
          <Art key={seed} seed={seed} drift={t * 0.05} style={{opacity: Math.min(1, focusAmount(t, homeFocus, k + 1))}} />
        ))}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: `linear-gradient(90deg, ${C.night} 0%, rgba(10,12,30,0.85) 35%, rgba(10,12,30,0) 72%), linear-gradient(0deg, ${C.night} 0%, rgba(10,12,30,0) 40%)`,
          }}
        />
      </div>
      <TopBar />
      <div style={{position: 'absolute', left: 96, top: 230, width: 900, opacity: titleIn, transform: `translateY(${(1 - titleIn) * 16}px)`}}>
        <Label>Tonight on your apps</Label>
        <div style={{fontSize: 96, fontWeight: 800, lineHeight: 1.05, marginTop: 14}}>{TITLES[CARDS[cur]]}</div>
        <div style={{fontSize: 30, color: C.dim, marginTop: 18}}>{HERO_META[cur]}</div>
        <div style={{fontSize: 28, color: C.dim, marginTop: 16, lineHeight: 1.45}}>
          Picked for you from what you watch, on the apps you pay for.
        </div>
      </div>
      {/* The buttons step aside as the rows scroll up into their place */}
      <div style={{position: 'absolute', left: 96, top: 560, display: 'flex', gap: 22, opacity: interpolate(t, [94, 106], [1, 0], clamp)}}>
        <div style={{display: 'flex', alignItems: 'center', gap: 12, padding: '18px 38px', borderRadius: 14, background: C.gold, color: C.night, fontSize: 30, fontWeight: 700}}>
          <Play size={28} color={C.night} /> Play
        </div>
        <div style={{display: 'flex', alignItems: 'center', gap: 12, padding: '18px 38px', borderRadius: 14, background: 'rgba(245,240,230,0.14)', fontSize: 30, fontWeight: 600}}>
          <Plus size={28} color={C.ink} /> My List
        </div>
      </div>
      {/* Rows */}
      <div style={{position: 'absolute', left: 96, top: 690 + rowShift}}>
        <div style={{fontSize: 32, fontWeight: 600, marginBottom: 22}}>Continue watching</div>
        <div style={{display: 'flex', gap: 28}}>
          {CARDS.map((seed, i) => (
            <FocusCard key={seed} focus={focusAmount(t, homeFocus, i)} w={392} h={220}>
              <Art seed={seed} />
              <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(0deg, rgba(0,0,0,0.65), transparent 55%)'}} />
              <div style={{position: 'absolute', left: 20, bottom: 26, fontSize: 26, fontWeight: 600}}>{TITLES[seed]}</div>
              <div style={{position: 'absolute', left: 20, right: 20, bottom: 12, height: 6, borderRadius: 3, background: 'rgba(255,255,255,0.25)'}}>
                <div style={{width: `${25 + ((seed * 23) % 60)}%`, height: 6, borderRadius: 3, background: C.gold}} />
              </div>
            </FocusCard>
          ))}
        </div>
        <div style={{fontSize: 32, fontWeight: 600, margin: '44px 0 22px'}}>Top 10 on your apps</div>
        <div style={{display: 'flex', gap: 28}}>
          {[1, 3, 5, 7, 9, 0, 2].map((seed, i) => (
            <div key={seed} style={{display: 'flex', alignItems: 'flex-end'}}>
              <div style={{fontSize: 200, fontWeight: 900, lineHeight: 0.8, color: 'transparent', WebkitTextStroke: `3px ${C.faint}`, marginRight: -24}}>{i + 1}</div>
              <div style={{position: 'relative', width: 150, height: 225, borderRadius: 14, overflow: 'hidden'}}>
                <Art seed={seed} />
              </div>
            </div>
          ))}
        </div>
      </div>
    </ShamashScreen>
  );
};

/* ---------- b) Profiles and the Kids lock ---------- */

const PROFILES: {name: string; bg: string; lock?: boolean}[] = [
  {name: 'Sam', bg: `linear-gradient(135deg, ${C.dawn}, ${C.gold})`},
  {name: 'Noor', bg: 'linear-gradient(135deg, #8ec5ff, #3e63dd)'},
  {name: 'Kids', bg: 'linear-gradient(135deg, #b8f5c8, #30a46c)', lock: true},
  {name: 'Guest', bg: 'linear-gradient(135deg, #8a8fa8, #44485e)'},
];
const profileFocus: Schedule = [
  [8, 0],
  [28, 1],
  [48, 2],
];

const Profiles: React.FC<{t: number}> = ({t}) => {
  const pinIn = ease(t, 60, 14);
  const unlocked = pop(t, 94);
  return (
    <ShamashScreen>
      <div style={{position: 'absolute', top: 180, left: 0, right: 0, textAlign: 'center', fontSize: 70, fontWeight: 700}}>Who's watching?</div>
      <div style={{position: 'absolute', top: 340, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 90}}>
        {PROFILES.map((p, i) => (
          <div key={p.name} style={{display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 28}}>
            <FocusCard focus={focusAmount(t, profileFocus, i)} w={230} h={230} radius={115} style={{background: p.bg, overflow: 'visible'}}>
              <div style={{position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 100, fontWeight: 800, color: 'rgba(10,12,30,0.75)'}}>
                {p.name[0]}
              </div>
              {p.lock && (
                <div
                  style={{
                    position: 'absolute',
                    right: -6,
                    bottom: -6,
                    width: 76,
                    height: 76,
                    borderRadius: 38,
                    background: unlocked > 0.5 ? C.gold : C.night,
                    border: `4px solid ${C.gold}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {unlocked > 0.5 ? <Check size={40} color={C.night} stroke={3} /> : <Lock size={38} color={C.gold} />}
                </div>
              )}
            </FocusCard>
            <div style={{fontSize: 36, fontWeight: 600, color: focusAmount(t, profileFocus, i) > 0.5 ? C.ink : C.dim}}>{p.name}</div>
          </div>
        ))}
      </div>
      <div style={{position: 'absolute', top: 760, left: 0, right: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 30, opacity: pinIn, transform: `translateY(${(1 - pinIn) * 30}px)`}}>
        <div style={{fontSize: 34, color: C.dim}}>{unlocked > 0.5 ? 'Kids: family-safe titles only, bedtime at 20:30' : 'Enter the PIN for Kids'}</div>
        <div style={{display: 'flex', gap: 30}}>
          {[0, 1, 2, 3].map((d) => {
            const fill = pop(t, 66 + d * 7);
            return (
              <div key={d} style={{width: 70, height: 84, borderRadius: 14, background: C.card, border: `3px solid ${t >= 66 + d * 7 ? C.gold : C.line}`, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                <div style={{width: 24, height: 24, borderRadius: 12, background: C.gold, transform: `scale(${t >= 66 + d * 7 ? fill : 0})`}} />
              </div>
            );
          })}
        </div>
      </div>
    </ShamashScreen>
  );
};

/* ---------- c) The row editor ---------- */

const ROWS = ['Continue watching', 'Tonight', 'Top 10 on your apps', 'Popular on Orbit', 'Matches', 'My List'];
const editFocus: Schedule = [
  [6, 4],
  [48, 3],
  [76, 0],
];

const rowLayout = (t: number) => {
  const move = ease(t, 16, 26); // Matches moves from 5th to 2nd
  const hide = ease(t, 56, 16); // Popular on Orbit hidden
  const base = [0, 1, 2, 3, 4, 5];
  const pos = base.map((i) => {
    if (i === 4) return interpolate(move, [0, 1], [4, 1]);
    if (i >= 1 && i <= 3) return i + move;
    return i;
  });
  return {pos, hide, pinned: pop(t, 82)};
};

const RowEditor: React.FC<{t: number}> = ({t}) => {
  const {pos, hide, pinned} = rowLayout(t);
  const H = 104;
  // The preview collapses the hidden row.
  const previewPos = pos.map((p, i) => p - (pos[3] < p ? hide : 0) - (i === 3 ? 0 : 0));
  return (
    <ShamashScreen>
      <div style={{position: 'absolute', left: 140, top: 110}}>
        <Label>Settings · Home</Label>
        <div style={{fontSize: 64, fontWeight: 700, marginTop: 10}}>Edit your rows</div>
      </div>
      <div style={{position: 'absolute', left: 140, top: 290, width: 980, height: 6 * H}}>
        {ROWS.map((name, i) => {
          const f = focusAmount(t, editFocus, i);
          const hidden = i === 3 ? hide : 0;
          const isPinned = i === 0 && pinned > 0.5;
          return (
            <div key={name} style={{position: 'absolute', left: 0, top: pos[i] * H, width: 980}}>
              <FocusCard focus={f} w={980} h={H - 16} radius={16} style={{background: C.card, display: 'flex', alignItems: 'center', padding: '0 30px', gap: 26, opacity: 1 - hidden * 0.55}}>
                <Handle size={34} color={C.faint} />
                <div style={{fontSize: 34, fontWeight: 600, flex: 1}}>{name}</div>
                {i === 3 && hidden > 0.5 && <div style={{fontSize: 26, color: C.dim}}>Hidden</div>}
                {isPinned && <div style={{fontSize: 26, color: C.gold}}>Pinned</div>}
                <div style={{transform: i === 0 ? `scale(${1 + 0.3 * Math.sin(Math.PI * Math.min(1, pinned))})` : undefined}}>
                  <Pin size={34} color={isPinned ? C.gold : C.faint} filled={isPinned} />
                </div>
                {i === 3 && hidden > 0.5 ? <EyeOff size={34} color={C.dim} /> : <Eye size={34} color={C.faint} />}
              </FocusCard>
            </div>
          );
        })}
      </div>
      {/* Live preview */}
      <div style={{position: 'absolute', left: 1220, top: 290, width: 560, height: 6 * H - 16, borderRadius: 20, background: 'rgba(10,12,30,0.7)', border: `1px solid ${C.line}`, overflow: 'hidden'}}>
        <div style={{position: 'absolute', left: 24, top: 18, fontSize: 22, color: C.faint, letterSpacing: 2}}>PREVIEW</div>
        {ROWS.map((name, i) => {
          const hidden = i === 3 ? hide : 0;
          return (
            <div key={name} style={{position: 'absolute', left: 24, top: 64 + previewPos[i] * 90, opacity: 1 - hidden}}>
              <div style={{fontSize: 18, color: C.dim, marginBottom: 8}}>{name}</div>
              <div style={{display: 'flex', gap: 10}}>
                {[0, 1, 2, 3].map((k) => (
                  <div key={k} style={{position: 'relative', width: 116, height: 52, borderRadius: 6, overflow: 'hidden'}}>
                    <Art seed={i * 3 + k} />
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </ShamashScreen>
  );
};

/* ---------- d) Layout presets ---------- */

const PRESETS = ['Cinema', 'Sports', 'Kids', 'Minimal'];
const presetFocus: Schedule = [
  [4, 0],
  [32, 1],
  [60, 2],
  [88, 3],
];

const Crest: React.FC<{color: string; size?: number}> = ({color, size = 90}) => (
  <svg width={size} height={size * 1.2} viewBox="0 0 100 120">
    <path d="M50 4 L94 18 L90 70 Q80 102 50 116 Q20 102 10 70 L6 18 Z" fill={color} stroke="#fff" strokeWidth={4} />
  </svg>
);

const CinemaLayout: React.FC = () => (
  <AbsoluteFill style={{background: C.night}}>
    <div style={{position: 'absolute', inset: 0, height: 480}}>
      <Art seed={5} />
      <div style={{position: 'absolute', inset: 0, background: `linear-gradient(90deg, ${C.night}, transparent 70%), linear-gradient(0deg, ${C.night}, transparent 40%)`}} />
    </div>
    <div style={{position: 'absolute', left: 60, top: 140}}>
      <Label>Cinema</Label>
      <div style={{fontSize: 80, fontWeight: 800, marginTop: 8}}>Blue Hour</div>
    </div>
    <div style={{position: 'absolute', left: 60, top: 470, display: 'flex', gap: 22}}>
      {[1, 2, 3, 4, 6, 7, 8, 9].map((s) => (
        <div key={s} style={{position: 'relative', width: 160, height: 240, borderRadius: 12, overflow: 'hidden'}}>
          <Art seed={s} />
        </div>
      ))}
    </div>
  </AbsoluteFill>
);

const SportsLayout: React.FC<{t: number}> = ({t}) => (
  <AbsoluteFill style={{background: 'linear-gradient(180deg, #0d2a22, #0a0c1e)'}}>
    <div style={{position: 'absolute', left: 0, right: 0, top: 70, display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 70}}>
      <Crest color="#d13b3b" size={120} />
      <div style={{textAlign: 'center'}}>
        <div style={{display: 'inline-flex', alignItems: 'center', gap: 10, padding: '6px 18px', borderRadius: 20, background: C.live, fontSize: 24, fontWeight: 800}}>
          <span style={{width: 12, height: 12, borderRadius: 6, background: '#fff', opacity: 0.5 + 0.5 * Math.sin(t / 3)}} /> LIVE 67'
        </div>
        <div style={{fontSize: 130, fontWeight: 800, marginTop: 6}}>2 – 1</div>
        <div style={{fontSize: 28, color: C.dim}}>Red Lions vs Blue Falcons</div>
      </div>
      <Crest color="#2f5fd0" size={120} />
    </div>
    <div style={{position: 'absolute', left: 60, top: 470, fontSize: 30, fontWeight: 600}}>Your matches</div>
    <div style={{position: 'absolute', left: 60, top: 530, display: 'flex', gap: 24}}>
      {[['Tigris FC', '1 – 0', "34'"], ['Desert Hawks', '20:45', 'Tonight'], ['Red Lions', 'Sat', '18:00'], ['Grand Prix', 'Sun', '15:00']].map(([a, b, c]) => (
        <div key={a} style={{width: 340, height: 180, borderRadius: 16, background: 'rgba(27,31,58,0.85)', padding: 24}}>
          <div style={{fontSize: 26, color: C.dim}}>{a}</div>
          <div style={{fontSize: 56, fontWeight: 800, marginTop: 8}}>{b}</div>
          <div style={{fontSize: 24, color: C.gold}}>{c}</div>
        </div>
      ))}
    </div>
  </AbsoluteFill>
);

const KidsLayout: React.FC<{t: number}> = ({t}) => (
  <AbsoluteFill style={{background: 'linear-gradient(160deg, #5b3cc4, #1fa2a8)'}}>
    <div style={{position: 'absolute', left: 60, top: 60, display: 'flex', alignItems: 'center', gap: 16, fontSize: 50, fontWeight: 800}}>
      <Lock size={44} color="#fff" /> Kids
    </div>
    <div style={{position: 'absolute', left: 60, top: 180, display: 'flex', gap: 40}}>
      {['#ffcf5c', '#ff8fab', '#7ae582'].map((c, i) => (
        <div key={c} style={{position: 'relative', width: 440, height: 440, borderRadius: 60, background: c, overflow: 'hidden', transform: `translateY(${Math.sin(t / 8 + i) * 8}px)`}}>
          <div style={{position: 'absolute', left: 90, top: 90, width: 260, height: 260, borderRadius: 130, background: 'rgba(255,255,255,0.45)'}} />
          <div style={{position: 'absolute', left: 150, top: 170, width: 34, height: 34, borderRadius: 17, background: '#1b1f3a'}} />
          <div style={{position: 'absolute', left: 256, top: 170, width: 34, height: 34, borderRadius: 17, background: '#1b1f3a'}} />
          <div style={{position: 'absolute', left: 170, top: 240, width: 100, height: 50, borderBottom: '10px solid #1b1f3a', borderRadius: '0 0 60px 60px'}} />
        </div>
      ))}
    </div>
  </AbsoluteFill>
);

const MinimalLayout: React.FC = () => (
  <AbsoluteFill style={{background: C.night, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center'}}>
    <div style={{fontSize: 200, fontWeight: 200, letterSpacing: -4}}>21:30</div>
    <div style={{fontSize: 34, color: C.dim, marginTop: -10}}>Tuesday · 24° clear</div>
    <div style={{display: 'flex', gap: 30, marginTop: 70}}>
      {['#e5484d', '#3e63dd', '#30a46c', '#f76b15', '#8e4ec6'].map((c) => (
        <div key={c} style={{width: 200, height: 112, borderRadius: 16, background: `linear-gradient(135deg, ${c}, ${C.card})`}} />
      ))}
    </div>
  </AbsoluteFill>
);

const Presets: React.FC<{t: number}> = ({t}) => {
  const cur = currentIndex(t, presetFocus);
  const layer = (i: number) => (i === 0 ? 1 : Math.min(1, focusAmount(t, presetFocus, i) + (cur > i ? 1 : 0)));
  return (
    <ShamashScreen>
      <div style={{position: 'absolute', top: 80, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 30}}>
        {PRESETS.map((p, i) => {
          const f = focusAmount(t, presetFocus, i);
          return (
            <FocusCard key={p} focus={f} w={250} h={80} radius={40} style={{background: f > 0.5 ? C.gold : C.card, display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
              <div style={{fontSize: 32, fontWeight: 700, color: f > 0.5 ? C.night : C.ink}}>{p}</div>
            </FocusCard>
          );
        })}
      </div>
      <div style={{position: 'absolute', left: 160, top: 220, width: 1600, height: 800, borderRadius: 28, overflow: 'hidden', border: `2px solid ${C.line}`}}>
        <CinemaLayout />
        <AbsoluteFill style={{opacity: layer(1)}}>
          <SportsLayout t={t} />
        </AbsoluteFill>
        <AbsoluteFill style={{opacity: layer(2)}}>
          <KidsLayout t={t} />
        </AbsoluteFill>
        <AbsoluteFill style={{opacity: layer(3)}}>
          <MinimalLayout />
        </AbsoluteFill>
      </div>
    </ShamashScreen>
  );
};

export const Home: React.FC = () => {
  const frame = useCurrentFrame();
  const part = Math.min(3, Math.floor(frame / PART));
  const t = frame - part * PART;
  const cut = interpolate(t, [0, 10], [0, 1], clamp);
  const views = [HomeScreen, Profiles, RowEditor, Presets];
  const View = views[part];
  return (
    <Scene dur={HOME_DUR}>
      <Backdrop />
      <TVFrame>
        <AbsoluteFill style={{background: C.night}}>
          <AbsoluteFill style={{opacity: part === 0 ? 1 : cut}}>
            <View t={t} />
          </AbsoluteFill>
        </AbsoluteFill>
      </TVFrame>
      <Captions items={CAPTIONS.home} />
    </Scene>
  );
};
