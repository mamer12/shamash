import {Config} from '@remotion/cli/config';

// The system Chrome renders the frames; Remotion brings its own ffmpeg for encoding.
Config.setBrowserExecutable('/usr/bin/google-chrome');
Config.setVideoImageFormat('jpeg');
Config.setCodec('h264');
Config.setCrf(23);
Config.setOverwriteOutput(true);
