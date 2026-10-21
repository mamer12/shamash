import React from 'react';
import {Composition} from 'remotion';
import {Showcase, TOTAL_FRAMES} from './Showcase';
import {FPS} from './theme';

export const RemotionRoot: React.FC = () => (
  <>
    <Composition
      id="Showcase"
      component={Showcase}
      durationInFrames={TOTAL_FRAMES}
      fps={FPS}
      width={1920}
      height={1080}
      defaultProps={{arabic: true}}
    />
    <Composition
      id="ShowcaseEN"
      component={Showcase}
      durationInFrames={TOTAL_FRAMES}
      fps={FPS}
      width={1920}
      height={1080}
      defaultProps={{arabic: false}}
    />
  </>
);
