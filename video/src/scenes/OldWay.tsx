import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {CAPTIONS} from '../captions';
import {Captions} from '../components/Captions';
import {FocusCard, focusAmount, Schedule} from '../components/Focus';
import {Backdrop, Scene} from '../components/Scene';
import {TVFrame} from '../components/TVFrame';
import {clamp, fonts, pop} from '../theme';

export const OLD_WAY_DUR = 450;

// A made-up stock home screen. Every name here is invented.
const GRAY = '#15171c';
const TILE_COLORS = ['#e94057', '#f27121', '#8a2387', '#00b09b', '#f7971e', '#ee0979', '#4776e6', '#ff512f'];
const ROW1 = ['MEGA DEAL', 'TRY FREE', 'WIN A TV', 'NEW SHOW', 'BUY NOW', '50% OFF', 'UPGRADE', 'HOT PICK'];
const ROW2 = ['ShopNow', 'GameZone', 'FitPro', 'CasinoX', 'NewsFlash', 'BuyMore', 'DealHub', 'PlayHub'];

const Badge: React.FC<{text: string; bg?: string}> = ({text, bg = '#ffcc00'}) => (
  <div
    style={{
      position: 'absolute',
      top: 12,
      left: 12,
      padding: '4px 10px',
      borderRadius: 6,
      background: bg,
      color: '#111',
      fontFamily: fonts.sans,
      fontWeight: 800,
      fontSize: 18,
      letterSpacing: 1,
    }}
  >
    {text}
  </div>
);

const Popup: React.FC<{frame: number; at: number; style: React.CSSProperties; children: React.ReactNode}> = ({
  frame,
  at,
  style,
  children,
}) => {
  const s = pop(frame, at);
  if (frame < at) return null;
  return (
    <div
      style={{
        position: 'absolute',
        transform: `scale(${s})`,
        opacity: Math.min(1, s * 1.5),
        borderRadius: 14,
        boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
        fontFamily: fonts.sans,
        zIndex: 10,
        ...style,
      }}
    >
      {children}
    </div>
  );
};

const focus: Schedule = [
  [40, 0],
  [80, 1],
  [115, 2],
  [150, 3],
  [190, 4],
];

const StockHome: React.FC<{frame: number}> = ({frame}) => {
  const shift = -frame * 1.4;
  const grey = interpolate(frame, [300, 380], [0, 0.85], clamp);
  return (
    <AbsoluteFill style={{background: GRAY, filter: `grayscale(${grey}) brightness(${1 - grey * 0.45})`}}>
      {/* Tabs */}
      <div style={{position: 'absolute', top: 34, left: 60, display: 'flex', gap: 14, fontFamily: fonts.sans}}>
        {['For You', 'Live', 'Shop', 'Apps', 'Offers', 'Partners'].map((t, i) => (
          <div
            key={t}
            style={{
              padding: '10px 26px',
              borderRadius: 30,
              background: i === 0 ? '#fff' : 'rgba(255,255,255,0.08)',
              color: i === 0 ? '#111' : '#bbb',
              fontSize: 26,
              fontWeight: 600,
            }}
          >
            {t}
          </div>
        ))}
      </div>
      {/* Sponsored hero */}
      <div
        style={{
          position: 'absolute',
          top: 110,
          left: 60,
          width: 1800,
          height: 400,
          borderRadius: 18,
          overflow: 'hidden',
          background: 'linear-gradient(120deg, #ff0080, #ff8c00 60%, #ffd200)',
          fontFamily: fonts.sans,
        }}
      >
        <Badge text="SPONSORED" />
        <div style={{position: 'absolute', left: 70, top: 90, color: '#fff', fontWeight: 900, fontSize: 104, letterSpacing: -2}}>
          MEGA SALE
        </div>
        <div style={{position: 'absolute', left: 74, top: 220, color: '#fff', fontWeight: 600, fontSize: 38}}>
          Install StreamMax Ultra today. Offer ends soon!
        </div>
        <div
          style={{
            position: 'absolute',
            left: 74,
            top: 295,
            padding: '14px 34px',
            background: '#111',
            color: '#fff',
            borderRadius: 10,
            fontWeight: 700,
            fontSize: 30,
          }}
        >
          Install now
        </div>
        <div
          style={{
            position: 'absolute',
            right: -80,
            top: -60,
            width: 560,
            height: 560,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.18)',
            transform: `scale(${1 + Math.sin(frame / 6) * 0.04})`,
          }}
        />
        <div style={{position: 'absolute', right: 110, top: 120, color: '#fff', fontWeight: 900, fontSize: 150, transform: `rotate(-8deg)`}}>
          -70%
        </div>
      </div>
      {/* Rows */}
      {[
        {title: 'Sponsored for you', items: ROW1, top: 545, offset: 0},
        {title: 'Featured apps · paid placement', items: ROW2, top: 805, offset: 3},
      ].map((row, r) => (
        <div key={row.title} style={{position: 'absolute', top: row.top, left: 60, right: 0, fontFamily: fonts.sans}}>
          <div style={{color: '#ddd', fontSize: 28, fontWeight: 600, marginBottom: 16}}>
            {row.title} <span style={{color: '#ffcc00', fontSize: 20, marginLeft: 10}}>AD</span>
          </div>
          <div style={{display: 'flex', gap: 24, transform: `translateX(${r === 0 ? shift : shift * 0.6}px)`}}>
            {row.items.map((label, i) => (
              <FocusCard
                key={label}
                focus={r === 0 ? focusAmount(frame, focus, i) : 0}
                w={r === 0 ? 320 : 250}
                h={r === 0 ? 180 : 170}
                radius={12}
                color="#ffffff"
                style={{background: TILE_COLORS[(i + row.offset) % TILE_COLORS.length]}}
              >
                <Badge text={r === 0 ? 'AD' : 'INSTALL'} bg={r === 0 ? '#ffcc00' : '#fff'} />
                <div
                  style={{
                    position: 'absolute',
                    left: 18,
                    bottom: 16,
                    color: '#fff',
                    fontWeight: 800,
                    fontSize: r === 0 ? 34 : 30,
                  }}
                >
                  {label}
                </div>
              </FocusCard>
            ))}
          </div>
        </div>
      ))}
      {/* Things that keep popping up */}
      <Popup frame={frame} at={95} style={{right: 70, top: 140, width: 470, padding: 28, background: '#fff', color: '#111'}}>
        <div style={{fontWeight: 800, fontSize: 30}}>Limited offer!</div>
        <div style={{fontSize: 24, marginTop: 6}}>Subscribe now and save. Ends tonight.</div>
      </Popup>
      <Popup
        frame={frame}
        at={150}
        style={{left: 640, top: 600, width: 560, height: 315, background: '#000', border: '3px solid #444', overflow: 'hidden'}}
      >
        <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(45deg,#0f0c29,#302b63,#24243e)'}} />
        <div style={{position: 'absolute', left: 24, top: 110, color: '#fff', fontSize: 50, fontWeight: 900}}>BUY MORE STUFF</div>
        <div
          style={{
            position: 'absolute',
            right: 16,
            bottom: 16,
            padding: '8px 16px',
            background: 'rgba(0,0,0,0.75)',
            color: '#fff',
            fontSize: 22,
            borderRadius: 6,
          }}
        >
          Ad · skip in {Math.max(1, 5 - Math.floor((frame - 150) / 30))}
        </div>
      </Popup>
      <Popup frame={frame} at={205} style={{left: 70, top: 380, width: 520, padding: 26, background: '#2b2d33', color: '#fff'}}>
        <div style={{fontWeight: 700, fontSize: 28}}>New app installed: CasinoX</div>
        <div style={{fontSize: 22, color: '#aaa', marginTop: 6}}>Pre-installed by your TV maker</div>
      </Popup>
      <Popup frame={frame} at={250} style={{right: 60, bottom: 40, width: 760, padding: '22px 30px', background: '#ffcc00', color: '#111'}}>
        <div style={{fontWeight: 800, fontSize: 30}}>Shop deals on your TV right now →</div>
      </Popup>
    </AbsoluteFill>
  );
};

export const OldWay: React.FC = () => {
  const frame = useCurrentFrame();
  return (
    <Scene dur={OLD_WAY_DUR}>
      <Backdrop />
      <TVFrame>
        <StockHome frame={frame} />
      </TVFrame>
      <Captions items={CAPTIONS.oldWay} />
    </Scene>
  );
};
