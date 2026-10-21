import {Config} from '@remotion/cli/config';

// The system Chrome renders the frames; Remotion brings its own ffmpeg for encoding.
Config.setBrowserExecutable('/usr/bin/google-chrome');
Config.setVideoImageFormat('jpeg');
Config.setCodec('h264');
// CRF is passed per render (npm run render): the GIF codec refuses it.
Config.setOverwriteOutput(true);
