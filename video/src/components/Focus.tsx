import React from 'react';
import {spring} from 'remotion';
import {C, FPS} from '../theme';

/** A focus schedule: at each frame, the remote moves focus to that index. */
export type Schedule = [number, number][];

/** How focused item `i` is at `frame` (0..1, with a little spring). */
export const focusAmount = (frame: number, schedule: Schedule, i: number): number => {
  let cur = -1;
  let prev = -1;
  let start = 0;
  for (const [at, idx] of schedule) {
    if (frame >= at) {
      prev = cur;
      cur = idx;
      start = at;
    }
  }
  const s = spring({frame: frame - start, fps: FPS, config: {damping: 16, stiffness: 190, mass: 0.6}});
  if (i === cur) return s;
  if (i === prev) return Math.max(0, 1 - s);
  return 0;
};

/** A card that grows and glows gold when the remote's focus is on it. */
export const FocusCard: React.FC<{
  focus: number;
  w: number;
  h: number;
  radius?: number;
  color?: string;
  style?: React.CSSProperties;
  children?: React.ReactNode;
}> = ({focus, w, h, radius = 16, color = C.gold, style, children}) => {
  const f = Math.max(0, focus);
  return (
    <div
      style={{
        position: 'relative',
        width: w,
        height: h,
        borderRadius: radius,
        transform: `scale(${1 + 0.08 * f})`,
        boxShadow: `0 0 0 ${4 * Math.min(f, 1)}px ${color}, 0 0 ${50 * f}px ${12 * f}px ${color === C.gold ? C.goldGlow : 'rgba(255,255,255,0.35)'}`,
        overflow: 'hidden',
        flexShrink: 0,
        zIndex: f > 0.05 ? 2 : 1,
        ...style,
      }}
    >
      {children}
    </div>
  );
};
