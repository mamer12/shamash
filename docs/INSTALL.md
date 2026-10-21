# Installing Shamash

Step by step, from a computer, for Android TV and Google TV 9 and newer. Prefer to have it done for you? See
*Install with Claude* in the [README](../README.md#install-with-claude-easiest).

- [1. Turn on debugging on the TV](#1-turn-on-debugging-on-the-tv)
- [2. Tools on your computer](#2-tools-on-your-computer)
- [3. Get the code](#3-get-the-code)
- [4. Your signing key](#4-your-signing-key)
- [5. Build](#5-build)
- [6. Install](#6-install)
- [7. First run and the switch](#7-first-run-and-the-switch)
- [Updating](#updating)
- [Undo and uninstall](#undo-and-uninstall)

## 1. Turn on debugging on the TV

1. **Settings › System › About › Android TV OS build**: press OK 7 times, until it says you're a developer.
2. **Settings › System › Developer options › USB debugging: On**, and **Network debugging** if your TV lists it.
3. Note the TV's IP address (Settings › Network & Internet › your network), for example `192.168.1.50`.

Shamash itself needs debugging left on: it makes the switch through the TV's own debugging connection.

## 2. Tools on your computer

You need a **JDK 17 or newer**, the **Android SDK** (platform `android-36`) and **adb**. With Android Studio, open
the folder and it installs and points to the SDK for you. Without it:

| System | Install |
|---|---|
| macOS | `brew install --cask temurin android-commandlinetools android-platform-tools` (the SDK goes to `$(brew --prefix)/share/android-commandlinetools`) |
| Linux | OpenJDK 17+ and adb from your package manager; Android's [command-line tools](https://developer.android.com/studio#command-line-tools-only) unzipped to `~/Android/Sdk/cmdline-tools/latest` |
| Windows | Install WSL, then follow Linux |

Then:

```bash
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

Once the licenses are accepted, Gradle downloads anything else the build asks for.

## 3. Get the code

Fork [mamer12/shamash](https://github.com/mamer12/shamash) on GitHub if you want your own copy, then:

```bash
git clone https://github.com/<you>/shamash.git
cd shamash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties    # your SDK's path (not needed if ANDROID_HOME is set)
```

`local.properties` is git-ignored: it holds paths and passwords for this computer only.

## 4. Your signing key

Android installs an update only if it is signed with the same key as the copy on the TV. Without a key of your own,
the release build is signed with this computer's debug key (`~/.android/debug.keystore`): fine if you always build on
this computer, but an update built elsewhere won't install without uninstalling first, which erases your Shamash
settings.

To make a key that lasts (keep it **outside** the repo, and back it up):

```bash
keytool -genkeypair -v -keystore ~/shamash-release.jks -alias shamash -keyalg RSA -keysize 2048 -validity 10000
```

Then add to `local.properties`:

```properties
signing.storeFile=/home/<you>/shamash-release.jks
signing.storePassword=<the keystore password>
signing.keyAlias=shamash
signing.keyPassword=<the key password>
```

Never commit `local.properties` or the keystore (`*.jks` and `*.keystore` are git-ignored too).

## 5. Build

```bash
./gradlew assembleRelease
```

The app is `app/build/outputs/apk/release/app-release.apk` (about 4.6 MB).

## 6. Install

```bash
adb connect 192.168.1.50:5555
```

The TV asks **Allow USB debugging?**: accept and tick **Always allow**. Then:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Installing doesn't change anything else: Shamash isn't a home screen until you switch, so the TV won't ask which
home screen to use.

**TCL TVs:** TCL's boot manager can block background jobs, so the switch and the watchdog wouldn't run (the log
shows `forbid bind service`). If that happens, allow auto-start (firmware names it either way):

```bash
adb shell "appops set io.shamash AUTO_START allow || appops set io.shamash APP_AUTO_START allow"
```

## 7. First run and the switch

Open **Shamash** from the TV's apps. Setup asks, one screen at a time (every step but the welcome can be skipped and
changed later in Settings):

1. Welcome
2. About you: first name and city (greeting, weather, the evening look)
3. Quick start: a ready-made starting point ("I use Netflix and YouTube", "Sports first"...)
4. Your apps: the ones you subscribe to
5. Your teams and competitions
6. What you like
7. How should Home look?
8. Optional keys: TMDB, YouTube (see the [README](../README.md#optional-keys)); typed here, in Settings › Keys, or
   from your phone
9. **Make Shamash your home screen**

The switch (the last step, or later **Settings › Home screen › Use Shamash**) asks the TV once more to allow
debugging, then:

- sets Shamash as the home screen and the screensaver,
- turns off the TV's own home screen and the promo apps listed for your TV's brand
  (`app/src/main/java/io/shamash/feature/tvcontrol/BrandProfiles.kt`); nothing is uninstalled, and picture, sound,
  inputs, the tuner, the remote, updates and the Play Store are never touched,
- lets Match Centre show over other apps and the *Recent apps* row see which apps you used,
- on TCL TVs, lets the remote's Netflix / YouTube / Prime buttons open the apps you pick (Settings › Remote buttons).

If the TV's first setup saved your Wi-Fi network, the switch stops before changing anything and asks you to forget
the network and join it again (Settings › Network & Internet), because turning off the app that saved it would erase
it. At the end, the switch shows what changed. On the computer, `adb logcat -s Shamash` shows each step.

## Updating

```bash
git pull                     # in a fork: git pull https://github.com/mamer12/shamash.git main
./gradlew assembleRelease
adb connect 192.168.1.50:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

Your settings stay. After an update (and after a reboot), Shamash checks that its mode is still whole and quietly
re-applies it, remote buttons included.

If `adb install` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed copy was signed with another key.
Build with the original key; if it's lost, the only way is to uninstall (below, Original first) and install again,
which erases Shamash's settings (a backup made first in Settings › Backup keeps them).

## Undo and uninstall

- **Back to the TV's own home screen:** **Settings › Home screen › Original**. Everything Shamash turned off comes
  back, with the TV's home screen and screensaver. You can switch again any time.
- **Watchdog:** if Shamash crashes 3 times within 5 minutes, the TV goes back to Original by itself; Settings then
  says why.
- **Uninstall:** choose *Original* first, then remove it from the TV's Settings › Apps, or `adb uninstall io.shamash`.

**Uninstalled while still in Shamash mode?** The apps it turned off stay off. Bring them back from the computer:

```bash
adb shell pm list packages -d                                  # what is turned off
adb shell pm enable com.google.android.apps.tv.launcherx       # Google TV's home screen (on Google TV)
adb shell pm enable com.google.android.tungsten.setupwraith    # Google TV's setup, if it is listed
adb shell pm enable <package>                                  # each promo app from BrandProfiles.kt that is listed
adb shell settings delete secure screensaver_components        # the TV's default screensaver
```

Press Home: the TV's own home screen is back.
