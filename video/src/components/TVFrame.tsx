import React from 'react';
import {useCurrentFrame} from 'remotion';
import {ease} from '../theme';

export const SCREEN_W = 1920;
export const SCREEN_H = 1080;
const SCALE = 0.75;
export const FRAME = {x: (1920 - SCREEN_W * SCALE) / 2, y: 40, w: SCREEN_W * SCALE, h: SCREEN_H * SCALE};

/**
 * A TV set. Its children are laid out on a full 1920x1080 screen, shown at 75% so the captions fit under it.
 */
export const TVFrame: React.FC<{children: React.ReactNode; enter?: boolean; style?: React.CSSProperties}> = ({
  children,
  enter = true,
  style,
}) => {
  const f = useCurrentFrame();
  const p = enter ? ease(f, 0, 24) : 1;
  return (
    <div
      style={{
        position: 'absolute',
        left: FRAME.x,
        top: FRAME.y,
        width: FRAME.w,
        height: FRAME.h,
        borderRadius: 14,
        overflow: 'hidden',
        background: '#000',
        boxShadow: '0 0 0 10px #05060d, 0 0 0 12px #2b2f4d, 0 40px 120px rgba(0,0,0,0.65)',
        transform: `scale(${0.95 + 0.05 * p})`,
        ...style,
      }}
    >
      <div
        style={{
          position: 'absolute',
          width: SCREEN_W,
          height: SCREEN_H,
          transform: `scale(${SCALE})`,
          transformOrigin: 'top left',
          overflow: 'hidden',
        }}
      >
        {children}
      </div>
    </div>
  );
};
