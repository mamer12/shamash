import React from 'react';
import {AbsoluteFill, Series} from 'remotion';
import {Home, HOME_DUR} from './scenes/Home';
import {MEET_DUR, MeetShamash} from './scenes/MeetShamash';
import {OLD_WAY_DUR, OldWay} from './scenes/OldWay';
import {ONE_PLACE_DUR, OnePlace} from './scenes/OnePlace';
import {Outro, OUTRO_DUR} from './scenes/Outro';
import {Setup, SETUP_DUR} from './scenes/Setup';
import {WHY_DUR, WhySwitch} from './scenes/WhySwitch';
import {C} from './theme';

/** The story, in order. Change a scene's length in its file (…_DUR, in frames at 30 fps). */
const SCENES: [React.FC, number][] = [
  [OldWay, OLD_WAY_DUR],
  [MeetShamash, MEET_DUR],
  [Setup, SETUP_DUR],
  [Home, HOME_DUR],
  [OnePlace, ONE_PLACE_DUR],
  [WhySwitch, WHY_DUR],
  [Outro, OUTRO_DUR],
];

export const TOTAL_FRAMES = SCENES.reduce((sum, [, d]) => sum + d, 0);

export const Showcase: React.FC = () => (
  <AbsoluteFill style={{background: C.night}}>
    <style>{'*, *::before, *::after { box-sizing: border-box; }'}</style>
    <Series>
      {SCENES.map(([S, d], i) => (
        <Series.Sequence key={i} durationInFrames={d}>
          <S />
        </Series.Sequence>
      ))}
    </Series>
  </AbsoluteFill>
);
