# Shamash: notes for Claude

Shamash is an open-source home screen for Android TV and Google TV (Android 9+), in Kotlin and Jetpack Compose for
TV, built with Gradle. Package `io.shamash`. Everything the person chooses (name, city, apps, teams, keys) is typed
on the TV in its first-run setup or Settings, or from the phone page: nothing goes in the source or a config file.

The person you're helping may not be technical. Keep it short and friendly, say what you're about to do on their TV
before you do it, and let them do the taps on the TV.

## Installing it for someone

1. **Ask** for the TV's IP address if they didn't say (on the TV: Settings › Network & Internet › their network).
   Nothing else is needed up front: Shamash's own setup on the TV asks for their name, city, apps and teams.
2. **On the TV** (they do it): Settings › System › About › *Android TV OS build*, press OK 7 times; then
   Settings › System › Developer options › **USB debugging** on (and **Network debugging** if the TV shows it).
3. **Tools:** a JDK 17+, the Android SDK (platform `android-36`, the `compileSdk` in `app/build.gradle.kts`) and
   `adb`. Check what's there first (`java -version`, `$ANDROID_HOME` or `sdk.dir` in `local.properties`,
   `adb version`) and install only what's missing; say what you'll install before you do.
   - macOS: `brew install --cask temurin android-commandlinetools android-platform-tools`, then
     `sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"`; the SDK is then in
     `$(brew --prefix)/share/android-commandlinetools`.
   - Linux: OpenJDK 17+ and adb from the package manager; Android's command-line tools (developer.android.com/studio,
     "Command line tools only") unzipped to `~/Android/Sdk/cmdline-tools/latest`; then the same `sdkmanager` line.
   - Windows: WSL, then as on Linux.
   - Accept the SDK licenses (`yes | sdkmanager --licenses`); Gradle then fetches any exact build-tools it wants.
   - `local.properties` (git-ignored) needs `sdk.dir=<the SDK path>` unless `ANDROID_HOME` is set.
4. **Signing key.** Updates must be signed with the same key as the installed copy, or Android refuses them (and
   uninstalling to get past that erases their Shamash settings). Without `signing.*` in `local.properties` the
   release build uses this computer's debug key, which is fine for one computer. For a key that lasts, offer to make
   one *outside* the repo: `keytool -genkeypair -v -keystore ~/shamash-release.jks -alias shamash -keyalg RSA
   -keysize 2048 -validity 10000`, run by **them in their own terminal** (it asks for passwords). Then add
   `signing.storeFile`, `signing.keyAlias` to `local.properties` and have them type `signing.storePassword` and
   `signing.keyPassword` there themselves. Passwords never go in the chat. Tell them to back up the keystore.
5. **Build:** `./gradlew assembleRelease` must end with `BUILD SUCCESSFUL`; the APK is
   `app/build/outputs/apk/release/app-release.apk`.
6. **Install:** `adb connect <IP>:5555`. The TV asks *Allow USB debugging?*: tell them to accept and tick
   *Always allow*. Then `adb install -r app/build/outputs/apk/release/app-release.apk`, and ask them to open
   **Shamash** from the TV's apps.
   On TCL, also run `adb shell "appops set io.shamash AUTO_START allow || appops set io.shamash APP_AUTO_START allow"`
   (firmware names the operation either way): TCL's boot manager otherwise blocks background jobs, so the switch
   and the watchdog never run, and `adb logcat` shows `forbid bind service`. Seen with Free My TV on the same TCL;
   not yet confirmed for Shamash, so check the log if the switch doesn't start.
7. **Tell them what the switch does** before they make it: Shamash becomes the home screen and screensaver, the TV's
   own home screen and the TV maker's promo apps are turned off (never uninstalled), Match Centre may show scores
   over other apps, and **Settings › Home screen › Original** brings it all back. If Shamash crashes 3 times in
   5 minutes, the TV goes back to Original by itself. On TCL TVs the remote's Netflix / YouTube / Prime buttons keep
   working through a small accessibility service that only reads those buttons (**Settings › Remote buttons**).
8. **They switch on the TV:** the last setup step, *Make Shamash your home screen* (or later **Settings › Home
   screen › Use Shamash**). Shamash uses the TV's own ADB on 127.0.0.1:5555 with its own key, so the TV asks once
   more to allow debugging. **Never switch modes from the computer.**
   If the TV's first setup saved the Wi-Fi, the switch stops before changing anything and asks them to forget the
   network and join it again in the TV's settings; then the switch goes through. Saved networks out of range don't
   show in Settings: find the ones whose `cname=` is `com.google.android.tungsten.setupwraith` in
   `adb shell dumpsys wifi` (each block starts with its `ID:`), and only with their OK remove them with
   `adb shell cmd wifi forget-network <id>`.
9. **Keys are theirs and secret.** TMDB (themoviedb.org › Settings › API) and the YouTube OAuth client (ID + secret,
   type *TVs and Limited Input devices*) are optional. They type them on the TV in setup or **Settings › Keys**, or
   from their phone: **Settings › Phone remote** shows an address and a 6-digit PIN, and every text field on the TV
   offers "type it on your phone". Don't ask for keys in the chat, never print them, never put them in a file or a
   commit. If they paste one in the chat anyway, tell them to type it on the TV and don't repeat it.
10. **Check:** `adb logcat -s Shamash` (the app's one log tag) shows what the switch did and what failed.

## Updating and undoing

- Update: `git pull`, `./gradlew assembleRelease`, `adb install -r ...` with the **same key**. Shamash re-checks its
  mode by itself after an update.
- Undo: they choose **Settings › Home screen › Original** on the TV. To remove Shamash, do that first, then
  `adb uninstall io.shamash`.
- Uninstalled while still in Shamash mode? The apps it turned off stay off. Find them with
  `adb shell pm list packages -d`, turn the TV's own home screen and the promo apps from
  `feature/tvcontrol/BrandProfiles.kt` back on with `adb shell pm enable <package>` (on Google TV the home screen is
  `com.google.android.apps.tv.launcherx`, and `com.google.android.tungsten.setupwraith` if it is off), then
  `adb shell settings delete secure screensaver_components`. See `docs/INSTALL.md`.

## Adapting it to another TV brand

Read first, change after. Read-only commands:

```bash
adb shell getprop ro.product.manufacturer; adb shell getprop ro.product.brand; adb shell getprop ro.build.version.release
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell settings get secure screensaver_components
adb shell pm list packages -s     # system apps
```

Then add the brand's promo, demo, store-mode and telemetry apps to `feature/tvcontrol/BrandProfiles.kt` (one reason
per line, only those), and anything that controls picture, sound, inputs, tuner, CEC, remote, setup or updates to
`PROTECTED`. When in doubt, protect it. Build, install, have them switch on the TV, read `adb logcat -s Shamash`,
then have them switch back to *Original* once and confirm everything returned.

## Always

- Never commit `local.properties`, a keystore (`*.jks`, `*.keystore`) or any key, token or password.
- Never uninstall or clear the data of system apps; the only change to apps is `pm disable-user --user 0` through
  Shamash's switch, so *Original* can bring them back.
- Never turn off an app that saved a Wi-Fi network: Android deletes its networks and Original can't bring them back.
- Don't change the TV's picture, sound, network or account settings.
- Keep the way back working: the *Original* mode and the watchdog.
- Don't switch modes from the computer; the person does it on the TV.

## Working on the code

- Build and test: `./gradlew testDebugUnitTest assembleRelease`; both must pass. JDK 17, minSdk 28, targetSdk 35,
  compileSdk 36.
- **Spec first.** Behavior is specified in `docs/specs/*.md` as numbered requirements (`HOME-04`, `TVC-03`...).
  Change the spec, then the code; code points back with `// SPEC: <ID>` and JVM tests are named after IDs
  (`` `LAY_04 hidden rows are not fetched` ``) with fixtures under `app/src/test/resources/`.
- **Layers:** `feature → domain ← data` (`docs/specs/architecture.md`). `domain` is pure and imports neither;
  one `ServiceLocator` builds the graph (no DI framework); sources are listed in `data/Sources.kt`.
- **Strings:** every user-facing text in `res/values/` (English) and `res/values-ar/` (Arabic); layouts must work
  right-to-left. Add-ons use their own `strings_<name>.xml`.
- **Fail soft:** a failing source returns an empty result and logs through `Logger.w` (tag `Shamash`); one source
  never empties Home. No network or disk on the main thread (`suspend` on `Dispatchers.IO`).
- **Secrets:** never log a value, a URL carrying one or a response body; requests with keys bypass the disk cache
  (`HttpPrivacy`).
- **Performance budgets** are in `docs/specs/performance.md`: images at their drawn size, one combined home state,
  no work per frame. See `docs/PERFORMANCE.md`.
- Add-ons: `docs/ADDONS.md`. Small functions and files (ARCH-08), comments only for the why.
