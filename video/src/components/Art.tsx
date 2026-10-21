import React from 'react';

// Made-up artwork: gradients and shapes standing in for posters, so no real titles or images appear.
const PALETTES: [string, string, string][] = [
  ['#1d2b64', '#f8cdda', '#ffd27a'],
  ['#0f2027', '#2c5364', '#9be7ff'],
  ['#3a1c71', '#d76d77', '#ffaf7b'],
  ['#134e5e', '#71b280', '#e9ffb0'],
  ['#2b1055', '#7597de', '#f6e7ff'],
  ['#42275a', '#734b6d', '#ffcf99'],
  ['#0b486b', '#f56217', '#ffe08a'],
  ['#232526', '#414345', '#f4c450'],
  ['#4b134f', '#c94b4b', '#ffd1a1'],
  ['#1f4037', '#99f2c8', '#ffffff'],
];

export const TITLES = [
  'The Long Night',
  'Salt & Stone',
  'Orbit 9',
  'Desert Bloom',
  'Kings of Ur',
  'Blue Hour',
  'The Archive',
  'River Run',
  'Night Market',
  'Paper Moons',
];

export const Art: React.FC<{seed: number; style?: React.CSSProperties; drift?: number}> = ({seed, style, drift = 0}) => {
  const [a, b, c] = PALETTES[seed % PALETTES.length];
  const sx = 25 + ((seed * 37) % 50) + drift;
  const sy = 22 + ((seed * 17) % 25);
  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        background: [
          `radial-gradient(ellipse 80% 40% at ${30 + ((seed * 13) % 40)}% 105%, ${a} 0 60%, transparent 61%)`,
          `radial-gradient(ellipse 70% 35% at ${70 - ((seed * 7) % 30)}% 112%, rgba(0,0,0,0.55) 0 60%, transparent 61%)`,
          `radial-gradient(circle at ${sx}% ${sy}%, ${c} 0 9%, rgba(255,255,255,0.0) 22%)`,
          `linear-gradient(${150 + seed * 20}deg, ${a}, ${b})`,
        ].join(', '),
        ...style,
      }}
    />
  );
};
