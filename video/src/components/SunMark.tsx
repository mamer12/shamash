import React from 'react';
import {interpolate} from 'remotion';
import {C, clamp, pop} from '../theme';

/**
 * Shamash's sun: a disc with four straight points and four wavy rays between them, after the Mesopotamian
 * sun symbol. `progress` frame-drives the drawing; pass a large number for the finished mark.
 */
export const SunMark: React.FC<{size: number; frame: number; spin?: number; glow?: boolean}> = ({
  size,
  frame,
  spin = 0,
  glow = true,
}) => {
  const draw = interpolate(frame, [0, 36], [0, 1], clamp);
  const straight = [0, 90, 180, 270];
  const wavy = [45, 135, 225, 315];
  return (
    <svg width={size} height={size} viewBox="-100 -100 200 200" style={{overflow: 'visible'}}>
      <defs>
        <radialGradient id="sunCore" cx="50%" cy="45%" r="60%">
          <stop offset="0%" stopColor={C.dawn} />
          <stop offset="100%" stopColor={C.gold} />
        </radialGradient>
        <radialGradient id="sunHalo">
          <stop offset="0%" stopColor="rgba(244,196,80,0.45)" />
          <stop offset="100%" stopColor="rgba(244,196,80,0)" />
        </radialGradient>
      </defs>
      {glow && <circle r={98} fill="url(#sunHalo)" opacity={draw} />}
      <g transform={`rotate(${spin})`}>
        {straight.map((deg, i) => {
          const s = pop(frame, 22 + i * 4);
          return (
            <polygon
              key={deg}
              points="-10,-38 0,-94 10,-38"
              fill={C.gold}
              transform={`rotate(${deg}) scale(${s})`}
            />
          );
        })}
        {wavy.map((deg, i) => {
          const d = interpolate(frame, [30 + i * 4, 52 + i * 4], [1, 0], clamp);
          return (
            <path
              key={deg}
              d="M0,-40 Q9,-48 0,-56 Q-9,-64 0,-72 Q9,-80 0,-86"
              stroke={C.gold}
              strokeWidth={6}
              strokeLinecap="round"
              fill="none"
              pathLength={1}
              strokeDasharray={1}
              strokeDashoffset={d}
              transform={`rotate(${deg})`}
            />
          );
        })}
      </g>
      <circle r={32} fill="url(#sunCore)" opacity={interpolate(frame, [18, 34], [0, 1], clamp)} />
      <circle
        r={32}
        fill="none"
        stroke={C.gold}
        strokeWidth={5}
        pathLength={1}
        strokeDasharray={1}
        strokeDashoffset={1 - draw}
        transform="rotate(-90)"
      />
    </svg>
  );
};
