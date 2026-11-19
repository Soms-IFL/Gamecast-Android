# Changelog

All notable changes to the IFL Gamecast Android app are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.0-alpha] - 2026-11-18

### Changed
- **Sports-network look:** near-black header with a coloured accent line, white cards on a light grey page (near-black in dark mode),
  bold condensed headings and scores, squarer corners, red "LIVE" tags, and a bottom bar / rail that highlights the current tab in the accent colour.
- **Scoreboard strip** across the top of Home: recent results, the live game and upcoming games, starting at the live or next game.
- **My team** now sets the accent colour (header line, tabs, links, highlights) instead of re-colouring every surface.

## [0.1.0-alpha] - 2026-11-18

First alpha of the native Android app (Kotlin, Jetpack Compose, Material 3).

### Added
- **Screens:** Home, Games (by week), Game (summary, plays, box score), Standings (divisions, league, seeds, odds), Teams, Team
  (roster, schedule), Leaders, a player sheet, a season picker for archived seasons, and settings.
- **Live scores:** the live game appears on Home with win probability and the latest plays; scores refresh every few seconds.
- **Game viewer:** watch the live broadcast in sync, or replay any finished game, including past seasons. The Gamecast game engine runs in
  an embedded QuickJS interpreter and the field, ball, routes, scoreboard, win probability and play-by-play are drawn on a Compose Canvas.
  Includes play/pause, next play, 1x/2x/4x/10x speed, skip to end, and a fullscreen mode (landscape, immersive).
- **Playoff odds:** each team's chance to make the playoffs, reach the IFL Bowl and win it, simulated over the rest of the season; shown on
  Home for your team, on team pages and in the Standings "Odds" tab. The last result is cached on the device.
- **Game-start notifications:** one alarm per upcoming game (all games or just your team), restored after a reboot, with a shortcut to allow
  exact alarms. Tapping a notification opens the game.
- **My team:** choose a team to colour the whole app with its palette (a Material 3 scheme generated from one seed colour), highlight its
  games and show its title chances. Light, dark and system themes.
- **Tablets and foldables:** navigation rail, two-pane Home, Game, Team and Viewer layouts, and grid layouts for Games, Standings and Leaders.
- **Performance:** requests run in parallel, responses are cached briefly, and other tabs and team logos are pre-loaded after launch.
- **IFL logo** as the launcher icon and in the top bar.
- Unit tests that parse real server responses, including a full replay of the Season 2 IFL Bowl.

### Known limitations
- Alpha: compiled, linted and unit-tested, but not yet run on an emulator or many devices.
- The APK is signed with a debug key (it installs directly, but is not suitable for store distribution).
- The viewer reads game seeds from the server's `/api/state` and `/api/history` endpoints, which are not part of the stable `/api/v1` API.
- The server address must be `https`.

[Unreleased]: https://github.com/Soms-IFL/Gamecast-Android/compare/v0.2.0-alpha...HEAD
[0.2.0-alpha]: https://github.com/Soms-IFL/Gamecast-Android/compare/v0.1.0-alpha...v0.2.0-alpha
[0.1.0-alpha]: https://github.com/Soms-IFL/Gamecast-Android/releases/tag/v0.1.0-alpha
