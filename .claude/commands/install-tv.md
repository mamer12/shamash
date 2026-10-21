---
description: Build Shamash and install it on an Android TV / Google TV, following CLAUDE.md
argument-hint: <TV IP address, e.g. 192.168.1.50>
---

Install Shamash on the TV at: $ARGUMENTS

Follow "Installing it for someone" in CLAUDE.md, step by step, and its "Always" rules:

1. If no IP address was given above, ask for it (on the TV: Settings › Network & Internet › the network). Nothing
   else is needed up front; Shamash's own setup on the TV asks for the rest.
2. Make sure Developer options and USB debugging are on (the person does it on the TV; tell them how).
3. Check the tools (JDK 17+, Android SDK with platform android-36, adb) and install only what's missing, saying what
   you'll install first.
4. Signing: check `local.properties` for `signing.*`. If there's none, explain that updates need the same key and
   offer to make a keystore outside the repo; the person types its passwords in their own terminal, never in the chat.
5. Build with `./gradlew assembleRelease`.
6. `adb connect <IP>:5555` (they accept *Allow USB debugging?* and tick *Always allow*), then
   `adb install -r app/build/outputs/apk/release/app-release.apk`.
7. Explain what *Make Shamash your home screen* changes and how *Settings › Home screen › Original* undoes it, then
   let them go through setup and make the switch **on the TV**. Never switch modes from the computer.
8. Keys (TMDB, YouTube OAuth) are optional and theirs: they type them on the TV or from the phone page. Never ask for
   them in the chat, print them or write them to a file.
9. Check with `adb logcat -s Shamash` and tell them, in one or two plain sentences, what happened.

Keep every message short and friendly: the person may not be technical.
