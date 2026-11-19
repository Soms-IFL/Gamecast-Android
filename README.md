# IFL Gamecast app

A Material 3 app for the [IFL Gamecast public API](https://github.com/Soms-IFL/Gamecast/blob/main/API.md),
pointed at https://iflgamecast.colebolebole.xyz. It's an installable web app (PWA): no build step, no dependencies.

## Run it

Any static file server works. The app talks to the live server by default (CORS is open).

```powershell
py -m http.server 8000 --directory app
```

Open http://127.0.0.1:8000 (use `127.0.0.1`, not `localhost`, on Windows to avoid IPv6 stalls).
On a phone, host the `app/` folder anywhere (GitHub Pages, Netlify, the Gamecast server's `public/` folder, …) and use
"Add to Home screen" / "Install app".

Server address: Settings (gear icon) → Server, or `?api=https://host` in the URL.

## What's in it

| Screen | API |
|---|---|
| Home — live scoreboard + win probability + latest plays (pushed over SSE, polling fallback), next-game countdown, season progress, results | `status`, `games/live`, `games/live/stream`, `games` |
| Games — schedule by week, grouped by day | `games` |
| Game — summary/line score/scoring, full play-by-play, box score, inactives, live updates | `games/{id}`, `games/{id}/plays`, `games/{id}/stream` |
| Standings — divisions, league, playoff seeds, champion | `standings` |
| Teams / team page — ratings, roster with injuries, schedule | `teams`, `teams/{id}` |
| Player sheet | `players/{id}` |
| Leaders — all categories | `leaders` |
| Season picker — archived seasons | `seasons`, `seasons/{n}`, `?season=N` |

Material 3 details: the full colour scheme derives from one hue, so picking **My team** in Settings re-themes the app
with that team's colour; light/dark/system; navigation bar on phones and rail on wide screens; tonal cards, chips,
segmented buttons, tabs, bottom sheets, snackbars.

## Watching games (live + replays)

Game page → **Watch live** / **Watch replay** (also on Home's live card, and for any game in an archived season you pick
with the history icon). The viewer re-simulates the game with the site's own engine (`app/vendor/engine.js`, MIT,
© Soms-IFL) from its seed and animates it on a field with the ball, route trails, line of scrimmage / first-down line,
scoreboard, win probability and play-by-play. Live mode is locked to the broadcast clock; replays have play/pause,
next play, 1×/2×/4×/10× and skip to end.

Seeds come from the site's `/api/state` and `/api/history/N`, not `/api/v1`, and those don't send CORS headers. So the
viewer works in the **Android app** (a native bridge fetches them) or when this app is served from the Gamecast server
itself; in a plain browser on another origin it shows an explanation. If the server's engine changes, update
`app/vendor/engine.js` (the viewer warns when a replay's score differs from the recorded one).

**Fullscreen:** the button on the field's corner (or `F`; `Esc` / Android Back to leave). In the Android app it also rotates to
landscape and hides the system bars.

## Playoff odds

Home (once you've picked **My team**), Standings → **Odds**, and every team page show each team's chance to make the
playoffs, reach the IFL Bowl and win it. `app/odds.js` simulates the rest of the season 1,500 times: unplayed games use the
engine's own pre-game win model (checked identical to `IFL.pregameWinProb` for all 84 games), the league's real tiebreakers
(`IFL.playoffSeeds`) pick the four playoff teams, then semifinals (1v4, 2v3) and the Bowl are sampled. Uses only `/api/v1`
data, so it works in any browser. The Bowl's venue isn't known until it's scheduled, so it's treated as neutral.

## Android app (native)

`android/` is a real native app: Kotlin + Jetpack Compose + Material 3, no WebView. `dist/IFL-Gamecast.apk` is the release build
(shrunk, signed with the debug key so it installs directly and updates the earlier version in place); `dist/IFL-Gamecast-debug.apk`
is a plain debug build if you ever need to compare.

- **Screens:** Home, Games, Game (summary / plays / box score), Standings (divisions, league, seeds, odds), Teams, Team, Leaders,
  player sheet, season picker, settings. Live scores refresh every few seconds (polling `/api/v1`).
- **Theme:** Material 3 colour scheme generated from a seed colour; "My team" re-colours the whole app.
- **Game viewer:** the website's own game engine (`android/app/src/main/assets/sim/engine.js`, MIT, (c) Soms-IFL) runs inside an
  embedded QuickJS interpreter; the field, ball, routes, scoreboard, win probability and play-by-play are drawn natively on a Compose
  Canvas. Seeds come from `/api/state` and `/api/history/N` (no CORS limits in a native app). Fullscreen = landscape + immersive.
- **Playoff odds:** the same engine's pre-game win model, simulated over the rest of the season (`assets/sim/glue.js`).
- **Game-start notifications:** alarms per upcoming game (`GameAlarms.java`), restored after reboot.

Build (the toolchain lives in `.android-tools/`, safe to delete if you don't plan to rebuild):

```powershell
$t = "C:\Claude Projects\IFL App\.android-tools"
$env:JAVA_HOME="$t\jdk"; $env:ANDROID_HOME="$t\sdk"; $env:GRADLE_USER_HOME="$t\gradle-home"
cd "C:\Claude Projects\IFL App\android"
& "$t\gradle\bin\gradle.bat" assembleRelease testDebugUnitTest
```

On another machine: install JDK 17 and the Android SDK (platform 34, build-tools 34), then `cd android && ./gradlew assembleRelease testDebugUnitTest`
(the Gradle wrapper is included; set `ANDROID_HOME` or add `android/local.properties` with `sdk.dir=...`).

`testDebugUnitTest` parses real server responses (fixtures in `app/src/test/resources`) with the app's own parsers, including a full
replay of the Season 2 IFL Bowl.

(The web version in `app/` still works as a PWA and shares the same engine.)

## Developing without the server

`dev/mock_server.py` serves `app/` plus a fake `/api/v1` with a game that is always "on air" (a new play every few seconds):

```powershell
py dev/mock_server.py 8099
```

Open http://127.0.0.1:8099/?api=self — `?api=self` makes the app use the page's own server as the API.
(Note: a saved server address in Settings overrides this; use Settings → Reset.)
