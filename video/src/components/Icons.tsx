import React from 'react';

type P = {size?: number; color?: string; stroke?: number};

const Svg: React.FC<P & {children: React.ReactNode}> = ({size = 32, color = 'currentColor', stroke = 2, children}) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
);

export const Lock: React.FC<P> = (p) => (
  <Svg {...p}>
    <rect x="5" y="11" width="14" height="10" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </Svg>
);
export const Pin: React.FC<P & {filled?: boolean}> = ({filled, ...p}) => (
  <Svg {...p}>
    <path d="M9 3h6l-1 6 4 4H6l4-4-1-6z" fill={filled ? p.color : 'none'} />
    <path d="M12 13v8" />
  </Svg>
);
export const Eye: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
    <circle cx="12" cy="12" r="3" />
  </Svg>
);
export const EyeOff: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
    <path d="M3 3l18 18" />
  </Svg>
);
export const Handle: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M5 8h14M5 12h14M5 16h14" />
  </Svg>
);
export const Search: React.FC<P> = (p) => (
  <Svg {...p}>
    <circle cx="11" cy="11" r="7" />
    <path d="M20 20l-4-4" />
  </Svg>
);
export const Mic: React.FC<P> = (p) => (
  <Svg {...p}>
    <rect x="9" y="3" width="6" height="11" rx="3" />
    <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
  </Svg>
);
export const Server: React.FC<P> = (p) => (
  <Svg {...p}>
    <rect x="3" y="4" width="18" height="7" rx="2" />
    <rect x="3" y="13" width="18" height="7" rx="2" />
    <path d="M7 7.5h.01M7 16.5h.01" />
  </Svg>
);
export const Antenna: React.FC<P> = (p) => (
  <Svg {...p}>
    <rect x="3" y="9" width="18" height="12" rx="2" />
    <path d="M8 3l4 5 4-5" />
  </Svg>
);
export const Feed: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M5 11a8 8 0 0 1 8 8M5 5a14 14 0 0 1 14 14" />
    <circle cx="6" cy="18" r="1.2" />
  </Svg>
);
export const Folder: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
  </Svg>
);
export const Check: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M5 12l5 5 9-10" />
  </Svg>
);
export const Play: React.FC<P> = ({size = 32, color = 'currentColor'}) => (
  <svg width={size} height={size} viewBox="0 0 24 24">
    <path d="M7 4l13 8-13 8z" fill={color} />
  </svg>
);
export const Plus: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
);
export const Send: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M4 12l16-8-6 16-2-7z" />
  </Svg>
);
export const Moon: React.FC<P> = (p) => (
  <Svg {...p}>
    <path d="M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z" />
  </Svg>
);
