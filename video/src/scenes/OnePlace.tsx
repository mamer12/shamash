import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {CAPTIONS} from '../captions';
import {Art, TITLES} from '../components/Art';
import {Captions} from '../components/Captions';
import {FocusCard, focusAmount, Schedule} from '../components/Focus';
import {Antenna, Check, Feed, Folder, Mic, Search, Send, Server} from '../components/Icons';
import {Backdrop, Scene} from '../components/Scene';
import {Label, ShamashScreen} from '../components/ShamashScreen';
import {FRAME, TVFrame} from '../components/TVFrame';
import {C, clamp, ease, fonts, pop} from '../theme';
import {TopBar} from './Home';

export const ONE_PLACE_DUR = 450;
const PARTS = [0, 115, 230, 340];

/* ---------- a) One search ---------- */

const RESULTS: [number, string][] = [
  [0, 'Nova'],
  [8, 'Orbit'],
  [5, 'Home server'],
  [2, 'Reelbox'],
  [3, 'IPTV'],
  [4, 'Cinemix'],
  [6, 'Nova'],
  [9, 'My feed'],
  [7, 'Orbit'],
  [1, 'Home server'],
];
const searchFocus: Schedule = [
  [70, 0],
  [86, 1],
  [100, 2],
];

const SearchView: React.FC<{t: number}> = ({t}) => {
  const query = 'night'.slice(0, Math.max(0, Math.floor((t - 8) / 5)));
  return (
    <ShamashScreen>
      <div style={{position: 'absolute', left: 140, top: 90, width: 1640, height: 120, borderRadius: 60, background: C.card, boxShadow: `0 0 0 4px ${C.gold}, 0 0 40px 6px ${C.goldGlow}`, display: 'flex', alignItems: 'center', padding: '0 46px', gap: 30}}>
        <Search size={52} color={C.gold} />
        <div style={{fontSize: 56, fontWeight: 600, flex: 1}}>
          {query}
          <span style={{display: 'inline-block', width: 4, height: 56, background: C.gold, marginLeft: 6, opacity: Math.floor(t / 8) % 2}} />
        </div>
        <Mic size={48} color={C.dim} />
      </div>
      <div style={{position: 'absolute', left: 140, top: 260, fontSize: 30, color: C.dim, opacity: ease(t, 34, 10)}}>
        Results from <span style={{color: C.gold, fontWeight: 700}}>7 sources</span>, in one list
      </div>
      <div style={{position: 'absolute', left: 140, top: 330, width: 1660, display: 'flex', flexWrap: 'wrap', gap: 34}}>
        {RESULTS.map(([seed, source], i) => {
          const p = pop(t, 36 + i * 3);
          return (
            <div key={i} style={{transform: `scale(${p})`, opacity: Math.min(1, p)}}>
              <FocusCard focus={focusAmount(t, searchFocus, i)} w={300} h={300} radius={16}>
                <Art seed={seed} />
                <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(0deg, rgba(0,0,0,0.75), transparent 60%)'}} />
                <div style={{position: 'absolute', top: 14, left: 14, padding: '6px 14px', borderRadius: 8, background: 'rgba(10,12,30,0.8)', fontSize: 22, fontWeight: 700, color: C.gold}}>{source}</div>
                <div style={{position: 'absolute', left: 18, bottom: 16, fontSize: 28, fontWeight: 700}}>{TITLES[seed]}</div>
              </FocusCard>
            </div>
          );
        })}
      </div>
    </ShamashScreen>
  );
};

/* ---------- b) Live score over a playing video ---------- */

const PlayingVideo: React.FC<{t: number}> = ({t}) => (
  <AbsoluteFill style={{fontFamily: fonts.sans, color: C.ink}}>
    <Art seed={3} drift={t * 0.2} style={{transform: `scale(${1.1 + t * 0.0008})`}} />
    <div style={{position: 'absolute', left: `${(t * 4) % 2400 - 400}px`, top: 220, width: 380, height: 140, borderRadius: 70, background: 'rgba(255,255,255,0.18)', filter: 'blur(20px)'}} />
    <div style={{position: 'absolute', left: 0, right: 0, bottom: 0, height: 200, background: 'linear-gradient(0deg, rgba(0,0,0,0.7), transparent)'}} />
    <div style={{position: 'absolute', left: 96, right: 96, bottom: 70, height: 8, borderRadius: 4, background: 'rgba(255,255,255,0.3)'}}>
      <div style={{width: `${38 + t * 0.05}%`, height: 8, borderRadius: 4, background: C.ink}} />
    </div>
    <div style={{position: 'absolute', left: 96, bottom: 100, fontSize: 34, fontWeight: 600}}>Desert Bloom · Episode 4</div>
  </AbsoluteFill>
);

const LiveScore: React.FC<{t: number}> = ({t}) => {
  const slide = ease(t, 14, 22);
  const goal = t >= 60;
  const flash = goal ? pop(t, 60) : 0;
  const flashOut = interpolate(t, [60, 66, 88, 96], [0, 1, 1, 0], clamp);
  const score = t >= 92 ? '2 – 0' : '1 – 0';
  const minute = t >= 92 ? "68'" : "67'";
  return (
    <AbsoluteFill>
      <PlayingVideo t={t} />
      <div
        style={{
          position: 'absolute',
          top: 60,
          right: 60,
          transform: `translateX(${(1 - slide) * 700}px) scale(${1 + 0.12 * flashOut})`,
          transformOrigin: 'right top',
          display: 'flex',
          alignItems: 'center',
          gap: 22,
          padding: '20px 32px',
          borderRadius: 50,
          background: flashOut > 0.5 ? C.gold : 'rgba(10,12,30,0.88)',
          color: flashOut > 0.5 ? C.night : C.ink,
          boxShadow: `0 10px 50px rgba(0,0,0,0.5), 0 0 ${60 * flashOut}px ${20 * flashOut}px ${C.goldGlow}`,
          fontFamily: fonts.sans,
        }}
      >
        <span style={{width: 18, height: 18, borderRadius: 9, background: C.live, opacity: 0.6 + 0.4 * Math.sin(t / 3)}} />
        <span style={{width: 36, height: 36, borderRadius: 18, background: '#d13b3b', border: '3px solid #fff'}} />
        <span style={{fontSize: 38, fontWeight: 800}}>RED</span>
        <span style={{fontSize: 44, fontWeight: 800, minWidth: 120, textAlign: 'center'}}>{flashOut > 0.5 ? 'GOAL!' : score}</span>
        <span style={{fontSize: 38, fontWeight: 800}}>BLU</span>
        <span style={{width: 36, height: 36, borderRadius: 18, background: '#2f5fd0', border: '3px solid #fff'}} />
        <span style={{fontSize: 30, opacity: 0.8}}>{minute}</span>
      </div>
      {goal && flash > 0 && (
        <AbsoluteFill style={{background: `radial-gradient(circle at 85% 8%, rgba(244,196,80,${0.35 * flashOut}), transparent 45%)`}} />
      )}
    </AbsoluteFill>
  );
};

/* ---------- c) Your own servers ---------- */

const ADDONS: {name: string; sub: string; Icon: React.FC<{size?: number; color?: string}>}[] = [
  {name: 'Plex', sub: 'Your media server', Icon: Server},
  {name: 'Jellyfin / Emby', sub: 'Your media server', Icon: Folder},
  {name: 'IPTV', sub: 'M3U + TV guide', Icon: Antenna},
  {name: 'My feed', sub: 'RSS, Atom, JSON', Icon: Feed},
];

const Servers: React.FC<{t: number}> = ({t}) => (
  <ShamashScreen>
    <div style={{position: 'absolute', left: 140, top: 90}}>
      <Label>Settings · Add-ons</Label>
      <div style={{fontSize: 60, fontWeight: 700, marginTop: 8}}>Bring your own sources</div>
    </div>
    <div style={{position: 'absolute', left: 140, top: 270, display: 'flex', gap: 40}}>
      {ADDONS.map(({name, sub, Icon}, i) => {
        const on = ease(t, 12 + i * 12, 10);
        return (
          <FocusCard key={name} focus={focusAmount(t, ADDONS.map((_, k) => [8 + k * 12, k] as [number, number]), i)} w={380} h={250} radius={20} style={{background: C.card, padding: 30}}>
            <Icon size={64} color={C.gold} />
            <div style={{fontSize: 36, fontWeight: 700, marginTop: 18}}>{name}</div>
            <div style={{fontSize: 24, color: C.dim, marginTop: 4}}>{sub}</div>
            <div style={{position: 'absolute', right: 26, top: 30, width: 84, height: 46, borderRadius: 23, background: on > 0.5 ? C.gold : 'rgba(245,240,230,0.2)'}}>
              <div style={{position: 'absolute', top: 5, left: 5 + on * 38, width: 36, height: 36, borderRadius: 18, background: on > 0.5 ? C.night : C.ink}} />
            </div>
          </FocusCard>
        );
      })}
    </div>
    {['From your Plex', 'Jellyfin · Recently added', 'IPTV · On now', 'My feed'].map((row, r) => {
      const p = ease(t, 60 + r * 8, 14);
      return (
        <div key={row} style={{position: 'absolute', left: 140, top: 580 + r * 120, opacity: p, transform: `translateX(${(1 - p) * 60}px)`, display: 'flex', alignItems: 'center', gap: 30}}>
          <div style={{width: 380, fontSize: 30, fontWeight: 600}}>{row}</div>
          {[0, 1, 2, 3, 4, 5].map((k) => (
            <div key={k} style={{position: 'relative', width: 180, height: 96, borderRadius: 10, overflow: 'hidden'}}>
              <Art seed={r * 2 + k} />
            </div>
          ))}
        </div>
      );
    })}
  </ShamashScreen>
);

/* ---------- d) The phone as remote and queue ---------- */

const PHONE = {x: 1560, y: 170, w: 330, h: 660};

const PhoneView: React.FC<{t: number}> = ({t}) => {
  const typedTitle = 'The Long Night'.slice(0, Math.max(0, Math.floor((t - 10) / 2)));
  const press = pop(t, 44);
  return (
    <div style={{position: 'absolute', left: PHONE.x, top: PHONE.y, width: PHONE.w, height: PHONE.h, borderRadius: 48, background: '#05060d', boxShadow: '0 0 0 3px #2b2f4d, 0 40px 80px rgba(0,0,0,0.6)', padding: 14, transform: `translateY(${(1 - ease(t, 0, 20)) * 400}px)`, fontFamily: fonts.sans, color: C.ink, zIndex: 20}}>
      <div style={{width: '100%', height: '100%', borderRadius: 36, background: `linear-gradient(180deg, ${C.dusk}, ${C.night})`, padding: '44px 22px', position: 'relative', overflow: 'hidden'}}>
        <div style={{display: 'flex', alignItems: 'center', gap: 10, fontSize: 22, fontWeight: 700}}>
          <span style={{width: 14, height: 14, borderRadius: 7, background: '#30a46c'}} /> Living room TV
        </div>
        <div style={{marginTop: 26, fontSize: 18, color: C.dim}}>Send a title or a link</div>
        <div style={{marginTop: 8, height: 56, borderRadius: 12, background: C.card, border: `2px solid ${C.gold}`, display: 'flex', alignItems: 'center', padding: '0 14px', fontSize: 22}}>{typedTitle}</div>
        <div style={{marginTop: 14, height: 56, borderRadius: 12, background: C.gold, color: C.night, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, fontSize: 22, fontWeight: 700, transform: `scale(${t >= 44 ? 1 - 0.06 * Math.sin(Math.PI * Math.min(1, press)) : 1})`}}>
          <Send size={22} color={C.night} /> Send to TV
        </div>
        {/* D-pad */}
        <div style={{position: 'absolute', left: '50%', bottom: 60, width: 190, height: 190, marginLeft: -95, borderRadius: 95, background: C.card, border: `1px solid ${C.line}`}}>
          {[0, 90, 180, 270].map((d) => (
            <div key={d} style={{position: 'absolute', left: 85, top: 14, width: 0, height: 0, borderLeft: '10px solid transparent', borderRight: '10px solid transparent', borderBottom: `14px solid ${C.dim}`, transformOrigin: '10px 81px', transform: `rotate(${d}deg)`}} />
          ))}
          <div style={{position: 'absolute', left: 60, top: 60, width: 70, height: 70, borderRadius: 35, background: C.mine, border: `2px solid ${C.gold}`}} />
        </div>
      </div>
    </div>
  );
};

const PhoneTV: React.FC<{t: number}> = ({t}) => {
  const toast = ease(t, 66, 14);
  const queued = pop(t, 70);
  return (
    <ShamashScreen>
      <TopBar />
      <div style={{position: 'absolute', left: 96, top: 200}}>
        <Label>Up next</Label>
        <div style={{fontSize: 60, fontWeight: 700, marginTop: 8}}>Your queue</div>
      </div>
      <div style={{position: 'absolute', left: 96, top: 360, display: 'flex', gap: 28}}>
        <div style={{transform: `scale(${t >= 70 ? queued : 0})`, transformOrigin: 'left center'}}>
          <FocusCard focus={t >= 70 ? Math.min(1, queued) : 0} w={392} h={220}>
            <Art seed={0} />
            <div style={{position: 'absolute', left: 20, bottom: 18, fontSize: 28, fontWeight: 700}}>{TITLES[0]}</div>
          </FocusCard>
        </div>
        {[4, 2].map((s) => (
          <div key={s} style={{position: 'relative', width: 392, height: 220, borderRadius: 16, overflow: 'hidden', transform: `translateX(${t >= 70 ? 0 : -420}px)`}}>
            <Art seed={s} />
            <div style={{position: 'absolute', left: 20, bottom: 18, fontSize: 28, fontWeight: 700}}>{TITLES[s]}</div>
          </div>
        ))}
      </div>
      <div style={{position: 'absolute', left: 96, top: 680, display: 'flex', alignItems: 'center', gap: 18, padding: '22px 34px', borderRadius: 18, background: C.mine, border: `2px solid ${C.gold}`, fontSize: 32, opacity: toast, transform: `translateY(${(1 - toast) * 30}px)`}}>
        <Check size={34} color={C.gold} stroke={3} /> Sent from your phone: added to your queue
      </div>
    </ShamashScreen>
  );
};

/** A small card flying from the phone into the TV. */
const Flyer: React.FC<{t: number}> = ({t}) => {
  if (t < 46 || t > 76) return null;
  const p = ease(t, 46, 26);
  const from = {x: PHONE.x + 40, y: PHONE.y + 260};
  const to = {x: FRAME.x + 96 * 0.75, y: FRAME.y + 360 * 0.75};
  const x = interpolate(p, [0, 1], [from.x, to.x]);
  const y = interpolate(p, [0, 1], [from.y, to.y]) - Math.sin(Math.PI * p) * 160;
  return (
    <div style={{position: 'absolute', left: x, top: y, width: 240, height: 135, borderRadius: 12, overflow: 'hidden', boxShadow: `0 0 40px 10px ${C.goldGlow}`, transform: `scale(${interpolate(p, [0, 1], [0.6, 1.2])})`, opacity: interpolate(t, [46, 50, 72, 76], [0, 1, 1, 0], clamp), zIndex: 30}}>
      <Art seed={0} />
    </div>
  );
};

export const OnePlace: React.FC = () => {
  const frame = useCurrentFrame();
  const part = PARTS.filter((p) => frame >= p).length - 1;
  const t = frame - PARTS[part];
  const cut = part === 0 ? 1 : interpolate(t, [0, 10], [0, 1], clamp);
  return (
    <Scene dur={ONE_PLACE_DUR}>
      <Backdrop />
      <TVFrame>
        <AbsoluteFill style={{background: C.night}}>
          <AbsoluteFill style={{opacity: cut}}>
            {part === 0 && <SearchView t={t} />}
            {part === 1 && <LiveScore t={t} />}
            {part === 2 && <Servers t={t} />}
            {part === 3 && <PhoneTV t={t} />}
          </AbsoluteFill>
        </AbsoluteFill>
      </TVFrame>
      {part === 3 && (
        <>
          <PhoneView t={t} />
          <Flyer t={t} />
        </>
      )}
      <Captions items={CAPTIONS.onePlace} />
    </Scene>
  );
};
