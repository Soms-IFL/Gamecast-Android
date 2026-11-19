# IFL Gamecast — Android app

A native Android client (Kotlin, Jetpack Compose, Material 3) for the [IFL Gamecast](https://github.com/Soms-IFL/Gamecast) public API.
It talks to a Gamecast server (default `https://iflgamecast.colebolebole.xyz`, changeable in Settings) and needs no API key.

## Features

- **Screens:** Home, Games, Game (summary / plays / box score), Standings (divisions, league, seeds, playoff odds), Teams, Team,
  Leaders, player sheet, season picker (browse archived seasons), settings.
- **Live scores:** refreshed every few seconds by polling `/api/v1`; the live game shows on Home with win probability and latest plays.
- **Game viewer:** watch the live broadcast in sync, or replay any finished game (including past seasons). The Gamecast game engine
  (`android/app/src/main/assets/sim/engine.js`, MIT, © Soms-IFL) runs inside an embedded QuickJS interpreter and the field, ball, routes,
  scoreboard, win probability and play-by-play are drawn natively on a Compose Canvas. Playback controls, speeds up to 10×,
  fullscreen (landscape + immersive).
- **Playoff odds:** each team's chance to make the playoffs, reach the IFL Bowl and win it, simulated from the engine's pre-game win
  model over the rest of the season (`assets/sim/glue.js`); the last result is cached on the phone.
- **Game-start notifications:** one alarm per upcoming game (all games or just your team), restored after a reboot.
- **Look:** a sports-network style: dark header, scoreboard strip on Home, white cards, bold condensed type, one accent colour.
- **My team:** pick a team and its colour becomes the app's accent; its games are highlighted and its title chances show on Home.
- **Tablets and foldables:** navigation rail, two-pane Home / Game / Team / Viewer, grid layouts for Games, Standings and Leaders.
- **Speed:** requests run in parallel, responses are cached briefly, and other tabs and team logos are pre-loaded after launch.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

## Requirements

- Android 8.0 (API 26) or newer.
- To build: JDK 17 and the Android SDK (platform 34, build-tools 34). The Gradle wrapper is included.

## Build

```powershell
cd android
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
```

Set `ANDROID_HOME` (or put `sdk.dir=...` in `android/local.properties`). The release build is shrunk and signed with the debug key,
so it installs directly for sideloading; use your own keystore for anything you distribute.

## Tests

`testDebugUnitTest` parses real server responses (fixtures in `android/app/src/test/resources`) with the app's own parsers, including a
full replay of the Season 2 IFL Bowl produced by the bundled engine.

## Project layout

```
android/
  app/src/main/java/com/iflgamecast/app/
    MainActivity.kt        app shell: navigation, rail / bottom bar, sheets
    Screen*.kt, Sheets.kt  Compose screens
    Watch.kt               game viewer (live + replay, Canvas field, fullscreen)
    Sim.kt, Data.kt        QuickJS bridge, replay / odds loaders, notification sync
    Api.kt, Models.kt      API client (with a short response cache) and parsers
    Theme.kt, Adaptive.kt  Material 3 theme from a seed colour, window-size classes
    GameAlarms.java, GameAlarmReceiver.java, BootReceiver.java   game-start alerts
  app/src/main/assets/sim/ engine.js (game engine), glue.js (simulate / odds entry points), ENGINE-LICENSE.txt
  app/src/test/            unit tests and captured API fixtures
```

## Notes

- The viewer needs each game's seed, which the public API deliberately doesn't expose. The app reads it from the server's own data
  endpoints (`/api/state`, `/api/history/N`), which are not part of the stable `/api/v1` contract.
- The bundled engine is a snapshot of the server's engine. If the server's engine changes, replace `engine.js`; the viewer flags a replay
  whose final score differs from the recorded one.
- Server addresses must be `https`.
