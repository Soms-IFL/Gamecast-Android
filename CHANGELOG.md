# Changelog

All notable changes to the IFL Gamecast Android app are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- **New result screen:** when a game ends, both teams' logos and final scores (counting up) are shown, the winner enlarged with a trophy and
  confetti, over a band in the winner's colours that names them ("… WIN"), with "Watch again" for replays. Phones in portrait get a compact
  one-row version.

### Fixed
- In fullscreen, banners such as "FLAG" or "TURNOVER ON DOWNS" crept up into the bottom of the score bug; they now sit clear of it.

## [0.2.3-alpha] - 2026-11-22

### Changed
- **More refined, consistent UI:** every selector is now the same underlined-tab control (Standings views, team and game pages, the box-score team
  switch, plays filter, Teams divisions, Leaders categories) instead of a mix of pill chips and segmented buttons. Buttons and bottom sheets have
  squarer corners to match the cards. Section titles share one style (accent bar and capitals) and Settings groups use small capitalised captions.
  Standings and playoff-odds tables have a grey header band and dividers between rows. The live-game card on Home is flat with a red top edge
  instead of a tinted gradient. The team page's rating tiles and tags use the same corner size.
- **Cleaner fullscreen:** the field now fills the whole screen with a small score bug (teams, scores, quarter, clock, down & distance) at the
  top and the latest play as a caption at the bottom. The controls (play / pause, next play, speed, skip, exit) float over the picture,
  fade away a few seconds after you stop touching the screen and come back with a tap. Overlays keep clear of a display notch.
- **Score celebrations:** touchdowns, field goals, safeties and two-point conversions now play a broadcast-style graphic: the picture dims,
  a band in the scoring team's colours wipes across the field, the team's logo spins in with a ring pulse, the call and the new score slide
  out beside it and confetti bursts from the logo. Turnovers, penalties and quarter breaks get a small banner that drops in and fades out.
  The final-score panel fades in with the winner's logo. The animations follow the playback clock, so they work at any speed, pause with the
  game and restart when you skip to the next play.
- A paused or finished replay no longer redraws every frame.

## [0.2.2-alpha] - 2026-11-22

### Fixed
- **Stuck in landscape:** leaving the viewer's fullscreen mode left the screen locked to landscape and the system bars hidden; both are now restored.
- **Players and ball behind the logo:** the home logo in the middle of the field was a view drawn on top of the field; it is now painted on the
  turf, so the players, routes and ball pass over it.
- A game that moved to a later start time lost its alert after a reboot (the old time was restored); the new time is now stored.
- The check mark on a light team colour in Settings was invisible (white on yellow); it now picks a readable colour.
- The Home scoreboard strip could crash if a game that had just gone live was briefly in two of its lists.

### Performance
- **Instant screens:** Games, Standings, Teams, a team page and Leaders now open with what was stored last time and refresh in the background,
  instead of showing a spinner; they also open offline.
- **Earlier start:** the first screen's requests and the team-logo downloads begin as the app process starts, and identical requests made at
  the same moment share one network call.
- **One connection:** the API and the logo downloads share a single HTTP/2 connection (OkHttp), and extra pages of a long game list load in parallel.
- **Faster logos:** logos are kept on disk regardless of the server's cache headers (refreshed weekly), decoded once per size and reused by
  every badge, and no longer fade in; a logo that is already in memory is drawn on the first frame.
- **Baseline profile:** the app's start-up code is pre-compiled when installed, so the first screens draw sooner.

### Changed
- Loading screens show pulsing placeholder blocks instead of a spinner.
- Tidier lists: the Games tab no longer repeats the week and day on every row (they are already in the tabs and date bands), Home shows
  three upcoming and three recent games under the scoreboard strip, and the status divider is drawn rather than measured.
- Player stats are ordered by type (passing, rushing, receiving, defence, kicking) with dividers, instead of the server's order.

## [0.2.1-alpha] - 2026-11-18

### Changed
- **Games screen as a scoreboard:** underlined week tabs (with a "My team" toggle), grey date bands, and rows with the winner's score bold
  and marked with a pointer, the loser dimmed, and a divided status / kick-off column. The same rows are used on Home and Team pages.

### Fixed
- Player stats showed the raw codes "sk" and "sky"; they now read "Sacks taken" and "Sack yards lost".

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

[Unreleased]: https://github.com/Soms-IFL/Gamecast-Android/compare/v0.2.3-alpha...HEAD
[0.2.3-alpha]: https://github.com/Soms-IFL/Gamecast-Android/compare/v0.2.2-alpha...v0.2.3-alpha
[0.2.2-alpha]: https://github.com/Soms-IFL/Gamecast-Android/compare/v0.2.1-alpha...v0.2.2-alpha
[0.2.1-alpha]: https://github.com/Soms-IFL/Gamecast-Android/compare/v0.2.0-alpha...v0.2.1-alpha
[0.2.0-alpha]: https://github.com/Soms-IFL/Gamecast-Android/compare/v0.1.0-alpha...v0.2.0-alpha
[0.1.0-alpha]: https://github.com/Soms-IFL/Gamecast-Android/releases/tag/v0.1.0-alpha
