# TV control (becoming the home screen)

Shamash changes system settings the way a person with ADB would, through the TV's own ADB server on 127.0.0.1:5555
(Network/USB debugging must be on; the TV asks once to allow Shamash's key).

- TVC-01 An on-device ADB client: TCP to 127.0.0.1:5555, ADB protocol v1 (CNXN, AUTH with an RSA-2048 key generated
  and kept in the app's files, signature of the token, then the public key on first use), `shell:` streams, timeouts
  10 s. Unit-tested against recorded packets.
- TVC-02 Switch to Shamash: record the current home activity and screensaver; set Shamash as home
  (`cmd package set-home-activity`, and the HOME role where `cmd role` exists); set the screensaver; grant
  `GET_USAGE_STATS` and `SYSTEM_ALERT_WINDOW` app-ops; turn off the brand's promo apps listed for the TV's brand
  (`pm disable-user --user 0`, never uninstall); enable the HOME intent filter (an activity-alias, disabled until now
  so installing never brings up the home chooser).
- TVC-03 Never turn off an app that owns a saved Wi-Fi network (`dumpsys wifi` creator/last-updater); stop and explain
  instead (the user must forget and re-join the network in Settings). An empty, failed or implausible Wi-Fi dump
  aborts before changing anything; missing evidence is never treated as an empty owner list.
- TVC-04 A protected list (picture, sound, inputs, tuner, CEC, remote/Bluetooth, setup, updates, system UI, Play
  Store) is never turned off, whatever the brand list says. Command builders enforce this at execution too; only
  Shamash itself may relinquish a protected HOME role. Component arguments use single quotes to preserve `$`.
- TVC-05 Original mode: turn back on everything Shamash turned off, restore the recorded home and screensaver, remove
  app-ops it granted, disable the HOME alias. One button in Settings; also offered if Shamash crashes 3 times within
  5 minutes (watchdog), automatically. A crash-safe pending marker and a persisted job survive process death and
  reboot; failed or partial restores retry with exponential backoff without requiring external network access.
  Boot verification never overrides a pending restore.
- TVC-06 Brand lists live in one Kotlin file (`BrandProfiles`): per brand, promo/telemetry packages to turn off
  (TCL, Google TV) with a one-line reason each.
- TVC-07 Remote app buttons (TCL keycodes for Netflix/YouTube/Prime buttons) can be mapped to any installed app via an
  accessibility service that only filters those keys; enabled only in Shamash mode.
- TVC-08 Every step is logged; the switch shows progress and, at the end, what changed. Unexpected errors become
  a recoverable failure; coroutine cancellation propagates. Invalid optional remote-service settings leave them alone.
