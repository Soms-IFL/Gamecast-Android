package com.iflgamecast.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONObject

/* ---------------------------------------------------------------- shared hero pieces */

fun situationText(situation: JSONObject?): String {
    if (situation == null) return ""
    val down = situation.i("down")
    return if (down in 1..4) "${ORD[down]} & ${situation.i("toGo")}" else situation.s("phase").replaceFirstChar { it.uppercase() }
}

@Composable
private fun HeroSide(t: Team, score: Int?, hasBall: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TeamBadge(t, 72.dp)
        Text(t.nickname, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        if (score != null) Text("$score", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
        Box(Modifier.height(20.dp)) { if (hasBall) Icon(Icons.Filled.SportsFootball, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
fun LiveHero(det: JSONObject, onOpen: () -> Unit, onWatch: () -> Unit) {
    val g = gameOf(det.obj("game"))
    val sit = det.o("situation")
    val wp = det.o("winProbability")?.d("home", 0.5)?.toFloat()
    val c = MaterialTheme.colorScheme
    Card(
        onClick = onOpen, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
    ) {
        Column(Modifier.background(Brush.verticalGradient(listOf(c.errorContainer.copy(alpha = 0.7f), c.surfaceContainer))).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LiveBadge()
                Text(g.weekLabel + if (g.stage == "bowl") " · IFL Bowl" else "", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeroSide(g.away, g.away.score ?: 0, sit?.sn("possession") == g.away.id, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val q = sit?.i("quarter") ?: 0
                    if (q > 0) Text(qLabel(q), style = MaterialTheme.typography.titleLarge)
                    Text(sit?.s("clock") ?: "", style = MaterialTheme.typography.headlineSmall)
                    Text(situationText(sit), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                }
                HeroSide(g.home, g.home.score ?: 0, sit?.sn("possession") == g.home.id, Modifier.weight(1f))
            }
            if (wp != null) { Spacer(Modifier.height(8.dp)); WinProbBar(g.away, g.home, wp) }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onWatch, Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Filled.LiveTv, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Watch live")
            }
        }
    }
}

@Composable
fun NextHero(g: Game, paused: Boolean, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val now by tickingNow()
    Card(onClick = onOpen, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = c.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Tag("UP NEXT")
                Text(g.weekLabel + if (g.stage == "bowl") " · IFL Bowl" else "", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeroSide(g.away, null, false, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ms = g.startsAtMs
                    if (ms != null) {
                        Text(dayLabel(ms), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                        Text(fmtTime(ms), style = MaterialTheme.typography.headlineSmall)
                        if (!paused) Text(countdownText(ms).also { now.hashCode() }, style = MaterialTheme.typography.labelLarge, color = c.primary)
                    }
                }
                HeroSide(g.home, null, false, Modifier.weight(1f))
            }
            val v = g.venue
            if (v?.name != null) Text(
                v.name + (v.town?.let { " · $it" } ?: ""), Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ChampionCard(team: Team, season: Int, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Card(onClick = onClick, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(team, 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("SEASON $season CHAMPIONS", style = MaterialTheme.typography.labelMedium)
                }
                Text(team.name, style = MaterialTheme.typography.titleLarge)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/* ---------------------------------------------------------------- playoff odds cards */

@Composable
fun OddsRows(t: TeamOdds) {
    @Composable
    fun row(label: String, icon: ImageVector, v: Double) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(pctText(v), style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(progress = { v.toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(4.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        row("Make the playoffs", Icons.Filled.Flag, t.playoffs)
        row("Reach the IFL Bowl", Icons.Filled.Stadium, t.bowl)
        row("Win the IFL Bowl", Icons.Filled.EmojiEvents, t.champ)
    }
}

@Composable
fun TeamOddsCard(o: OddsResult, id: String, showHeader: Boolean, onTeam: (() -> Unit)? = null) {
    val t = o.teams[id] ?: return
    val ref = o.refs[id]
    FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showHeader && ref != null) Row(Modifier.fillMaxWidth().then(if (onTeam != null) Modifier.clickable(onClick = onTeam) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(ref, 40.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(ref.name, style = MaterialTheme.typography.titleMedium); Text("Chance to…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (onTeam != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            } else Text("Chance to…", style = MaterialTheme.typography.titleMedium)
            OddsRows(t)
            val note = o.note(id)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Loads the odds (once per change in games played) and shows the favourite team's card, a prompt, or a spinner. */
@Composable
fun FavoriteOddsCard(gamesPlayed: Int) {
    val s = LocalSettings.current
    val nav = LocalNav.current
    val ctx = LocalContext.current.applicationContext
    val fav = s.favId
    if (fav == null) {
        FilledCard(onClick = nav.settings) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Follow your team", style = MaterialTheme.typography.titleMedium)
                    Text("Pick a team to see its chances of reaching and winning the IFL Bowl.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        return
    }
    val odds = rememberLoad(fav, gamesPlayed, s.base) { OddsLoader.load(ctx) }
    when (val st = odds.state.value) {
        is Load.Loading -> FilledCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp); Spacer(Modifier.width(12.dp))
                Text("Working out your team's title chances…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is Load.Ok -> st.v?.let { TeamOddsCard(it, fav, true) { nav.team(fav) } }
        is Load.Err -> Unit
    }
}

/* ---------------------------------------------------------------- home */

/** The last [n] finished games, newest first (the API lists soonest first, so ask for the total and then the tail). */
private suspend fun recentFinals(n: Int): List<Game> {
    val total = Api.v1("games", null, "status" to "final", "limit" to 1).i("total")
    if (total == 0) return emptyList()
    return Api.v1("games", null, "status" to "final", "limit" to n, "offset" to maxOf(0, total - n)).a("games").mapObjs { gameOf(it) }.reversed()
}

private class HomeData(val status: JSONObject, val live: JSONObject?, val upcoming: List<Game>, val finals: List<Game>)

@Composable
fun HomeScreen() {
    val s = LocalSettings.current
    val season = s.season
    if (season != null) { CenteredContent { ArchiveHome(season) }; return }
    val nav = LocalNav.current
    var state by remember(s.base) { mutableStateOf<Load<HomeData>>(Load.Loading) }
    var tick by remember(s.base) { mutableIntStateOf(0) }

    LaunchedEffect(s.base, tick) {
        var lists: Pair<List<Game>, List<Game>>? = null
        var listsAt = 0L
        var lastLive = false
        while (true) {
            var live = false
            try {
                // everything the screen needs is requested at once instead of one call after another
                val data = coroutineScope {
                    val statusD = async { Api.v1("status") }
                    val liveD = async { Api.v1("games/live", null, "plays" to 8) }
                    val needLists = lists == null || System.currentTimeMillis() - listsAt > 30_000
                    val upD = if (needLists) async { Api.v1("games", null, "status" to "scheduled", "limit" to 5).a("games").mapObjs { gameOf(it) } } else null
                    val finD = if (needLists) async { recentFinals(4) } else null
                    val status = statusD.await()
                    live = status.o("live") != null
                    val det = liveD.await().takeIf { live && it.b("live") }
                    if (upD != null && finD != null) { lists = upD.await() to finD.await(); listsAt = System.currentTimeMillis() }
                    if (live != lastLive) { listsAt = 0L; lastLive = live }   // a game started or ended: refresh the lists next round
                    HomeData(status, det, lists!!.first, lists!!.second)
                }
                state = Load.Ok(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (state !is Load.Ok) state = Load.Err(e)
            }
            delay(if (live) 4000 else 15000)
        }
    }

    when (val st = state) {
        is Load.Loading -> LoadingBox()
        is Load.Err -> ErrorState(st.e) { state = Load.Loading; tick++ }
        is Load.Ok -> {
            val d = st.v
            val status = d.status
            val seasonObj = status.o("season")
            val done = seasonObj?.b("done") == true
            val next = status.o("next")?.let { gameOf(it) }
            val size = LocalWinSize.current
            val left: LazyListScope.() -> Unit = {
                if (status.b("paused")) item {
                    FilledCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PauseCircle, null, tint = MaterialTheme.colorScheme.onTertiaryContainer); Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Schedule paused", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("Games will resume when the league starts the clock again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                if (d.live != null) {
                    val gid = d.live.o("game")?.s("id") ?: ""
                    item { LiveHero(d.live, { nav.game(gid) }, { nav.watch(gid) }) }
                    val plays = d.live.a("recentPlays").mapObjs { playOf(it) }.takeLast(5).reversed()
                    val lg = gameOf(d.live.obj("game"))
                    if (plays.isNotEmpty()) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader("Latest plays", "Full play-by-play") { nav.game(gid) }
                            FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.padding(4.dp)) { plays.forEach { PlayItem(it, lg) } } }
                        }
                    }
                } else if (done) {
                    seasonObj?.o("champion")?.let { ch -> item { ChampionCard(teamOf(ch), seasonObj.i("number")) { nav.team(ch.s("id")) } } }
                } else if (next != null) {
                    item { NextHero(next, status.b("paused")) { nav.game(next.id) } }
                } else item {
                    FilledCard { EmptyState(Icons.Filled.EventBusy, "No game on air", status.sn("nextSeasonStartsAt")?.let { "The next season starts soon." } ?: "Nothing is scheduled right now.") }
                }
                if (!done) item { FavoriteOddsCard(seasonObj?.i("gamesPlayed") ?: 0) }
                if (seasonObj != null) item { SeasonCard(status, seasonObj) }
            }
            val right: LazyListScope.() -> Unit = {
                val up = d.upcoming.filter { !(next != null && it.id == next.id && d.live == null) }.take(4)
                if (up.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SectionHeader("Coming up", "All games", nav.toGames); GameList(up) { nav.game(it.id) } }
                }
                if (d.finals.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SectionHeader("Latest results", "All games", nav.toGames); GameList(d.finals) { nav.game(it.id) } }
                }
            }
            if (size == WinSize.Expanded) Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1.1f).fillMaxHeight(), contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { left() }
                LazyColumn(Modifier.weight(0.9f).fillMaxHeight(), contentPadding = PaddingValues(start = 12.dp, top = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { right() }
            } else CenteredContent(if (size == WinSize.Medium) 720.dp else 900.dp) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) { left(); right() }
            }
        }
    }
}

@Composable
private fun SeasonCard(status: JSONObject, season: JSONObject) {
    val played = season.i("gamesPlayed"); val total = season.i("gamesTotal")
    val bits = ArrayList<String>()
    if (season.b("done") && season.hasValue("champion")) bits.add("Season complete")
    else status.sn("estimatedSeasonEnd")?.let { iso -> runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()?.let { ms -> bits.add("Est. finish ${dayLabel(ms)} ${fmtTime(ms)}") } }
    status.sn("nextSeasonStartsAt")?.let { iso -> runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()?.let { ms -> bits.add("Next season ${dayLabel(ms)} ${fmtTime(ms)}") } }
    FilledCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Season ${season.i("number")}", style = MaterialTheme.typography.titleMedium)
                Text("$played / $total games", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { if (total > 0) played.toFloat() / total else 0f }, Modifier.fillMaxWidth().height(4.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (season.b("playoffsStarted")) "Playoffs under way" else "Regular season", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ArchiveHome(season: Int) {
    val s = LocalSettings.current
    val nav = LocalNav.current
    val loader = rememberLoad(season, s.base) { Api.v1("seasons/$season") }
    LoadView(loader) { d ->
        val champ = d.o("champion")?.let { teamOf(it) }
        val bowl = d.o("iflBowl")?.let { gameOf(it) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                FilledCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Viewing Season $season", style = MaterialTheme.typography.titleSmall)
                            Text("You're looking at an archived season.", style = MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.TextButton(onClick = { s.season = null }) { Text("Current") }
                    }
                }
            }
            if (champ != null) item { ChampionCard(champ, season) { nav.team(champ.id) } }
            if (bowl != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("IFL Bowl", "Watch replay") { nav.watch(bowl.id) }
                    GameList(listOf(bowl)) { nav.game(it.id) }
                }
            }
            item { SectionHeader("Final standings", "Full table", nav.toStandings) }
            for (dv in d.a("standings").mapObjs { it }) item {
                FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(12.dp)) {
                        Text(dv.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                        for (r in dv.a("teams").mapObjs { it }) {
                            val t = teamOf(r.obj("team"))
                            Row(Modifier.fillMaxWidth().clickable { nav.team(t.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${r.i("rank")}", Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TeamBadge(t, 28.dp); Spacer(Modifier.width(10.dp))
                                Text(t.name, Modifier.weight(1f), maxLines = 1)
                                Text("${r.i("wins")}-${r.i("losses")}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
