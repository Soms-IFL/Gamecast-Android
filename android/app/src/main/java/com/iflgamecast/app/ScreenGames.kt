package com.iflgamecast.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/* ---------------------------------------------------------------- games (schedule by week) */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen() {
    val s = LocalSettings.current
    val nav = LocalNav.current
    val loader = rememberLoad(s.season, s.base, pollMs = 20_000L) { Api.allGames(s.season) }
    var selected by rememberSaveable(s.season) { mutableStateOf<String?>(null) }
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    LoadView(loader) { games ->
        if (games.isEmpty()) { EmptyState(Icons.Filled.EventBusy, "No games yet", "The schedule appears once a season is created."); return@LoadView }
        val byWeek = games.groupBy { it.week }
        val keys = byWeek.keys.sortedWith(compareBy({ it == "P" }, { it.toIntOrNull() ?: 99 }))
        val default = (games.firstOrNull { it.isLive } ?: games.firstOrNull { it.isScheduled })?.week ?: keys.last()
        val week = selected?.takeIf { it in byWeek } ?: default
        var list = byWeek[week].orEmpty()
        if (onlyFav && s.favId != null) list = list.filter { it.home.id == s.favId || it.away.id == s.favId }
        val days = list.groupBy { g -> g.startsAtMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } }
        val chipState = rememberLazyListState()
        LaunchedEffect(week) { chipState.animateScrollToItem(maxOf(0, keys.indexOf(week) - 1)) }
        Column(Modifier.fillMaxSize()) {
            // week selector: underlined tabs like a scoreboard date bar
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                LazyRow(state = chipState, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(keys) { k ->
                        val on = k == week
                        Column(Modifier.clickable { selected = k }.padding(horizontal = 12.dp).height(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    (if (k == "P") "Playoffs" else "Week $k").uppercase(), style = MaterialTheme.typography.labelLarge,
                                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                )
                            }
                            Box(Modifier.width(36.dp).height(3.dp).background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent))
                        }
                    }
                    if (s.favId != null) item {
                        Column(Modifier.clickable { onlyFav = !onlyFav }.padding(horizontal = 12.dp).height(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = if (onlyFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text("MY TEAM", style = MaterialTheme.typography.labelLarge, color = if (onlyFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.width(36.dp).height(3.dp).background(if (onlyFav) MaterialTheme.colorScheme.primary else Color.Transparent))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            val size = LocalWinSize.current
            if (days.isEmpty()) EmptyState(Icons.Filled.EventBusy, "No games match")
            else LazyVerticalGrid(
                if (size == WinSize.Compact) GridCells.Fixed(1) else GridCells.Adaptive(420.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding()),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for ((day, gs) in days) item(key = day?.toString() ?: "tbd") {
                    GameList(gs, header = gs.first().startsAtMs?.let { dayLabel(it, long = true) } ?: "Time to be announced") { nav.game(it.id) }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- game detail */

private class GameData(val game: Game, val detail: JSONObject, val plays: List<Play>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(id: String) {
    val s = LocalSettings.current
    val nav = LocalNav.current
    var state by remember(id, s.season, s.base) { mutableStateOf<Load<GameData>>(Load.Loading) }
    var tick by remember(id, s.season) { mutableIntStateOf(0) }
    var tab by rememberSaveable(id) { mutableIntStateOf(-1) }
    var playFilter by rememberSaveable { mutableIntStateOf(0) }
    var boxSide by rememberSaveable { mutableIntStateOf(0) }
    var pane by rememberSaveable(id) { mutableIntStateOf(0) }
    val size = LocalWinSize.current
    SetTitle(((state as? Load.Ok)?.v?.game)?.let { "${it.away.abbr} @ ${it.home.abbr}" } ?: "Game")

    LaunchedEffect(id, s.season, s.base, tick) {
        val plays = ArrayList<Play>()
        var next = 0
        while (true) {
            var status = ""
            try {
                val d = Api.v1("games/$id", s.season)
                val g = gameOf(d.getJSONObject("game"))
                status = g.status
                if (!g.isScheduled) {
                    val r = Api.v1("games/$id/plays", s.season, "since" to next, "limit" to 1000)
                    for (p in r.a("plays").mapObjs { playOf(it) }) if (plays.none { it.n == p.n }) plays.add(p)
                    next = r.i("next", next)
                }
                state = Load.Ok(GameData(g, d, plays.toList()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (state !is Load.Ok) state = Load.Err(e)
            }
            if (status == "final") break
            delay(if (status == "live") 4000 else 15000)
        }
    }

    when (val st = state) {
        is Load.Loading -> LoadingBox()
        is Load.Err -> ErrorState(st.e) { state = Load.Loading; tick++ }
        is Load.Ok -> {
            val data = st.v; val g = data.game
            val effTab = if (tab >= 0) tab else if (g.isLive) 1 else 0
            if (size == WinSize.Expanded) {
                // tablet: the game on the left, plays / box score on the right
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(0.42f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
                        item { Box(Modifier.padding(start = 24.dp, top = 16.dp, end = 12.dp)) { GameBoard(data.detail, g) { nav.watch(g.id) } } }
                        summaryItems(data, nav)
                    }
                    Column(Modifier.weight(0.58f).fillMaxHeight()) {
                        TabRow(selectedTabIndex = pane) {
                            listOf("Plays", "Box score").forEachIndexed { i, label -> Tab(selected = pane == i, onClick = { pane = i }, enabled = !g.isScheduled, text = { Text(label) }) }
                        }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            if (g.isScheduled) item { EmptyState(Icons.Filled.SportsFootball, "Not on air yet", "Plays and the box score appear as the game airs.") }
                            else if (pane == 0) playItems(data, playFilter) { playFilter = it }
                            else boxItems(data, boxSide, { boxSide = it }, nav)
                        }
                    }
                }
            } else CenteredContent(if (size == WinSize.Medium) 720.dp else 760.dp) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { Box(Modifier.padding(16.dp)) { GameBoard(data.detail, g) { nav.watch(g.id) } } }
                item {
                    TabRow(selectedTabIndex = effTab) {
                        listOf("Summary", "Plays", "Box score").forEachIndexed { i, label ->
                            Tab(selected = effTab == i, onClick = { tab = i }, enabled = i == 0 || !g.isScheduled, text = { Text(label) })
                        }
                    }
                }
                when (effTab) {
                    0 -> summaryItems(data, nav)
                    1 -> playItems(data, playFilter) { playFilter = it }
                    else -> boxItems(data, boxSide, { boxSide = it }, nav)
                }
            } }
        }
    }
}

@Composable
private fun GameBoard(detail: JSONObject, g: Game, onWatch: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val s = LocalSettings.current
    val sit = detail.o("situation")
    val wp = detail.o("winProbability")
    val h = wp?.d("home", 0.5)?.toFloat()
    val nav = LocalNav.current
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                when { g.isLive -> LiveBadge(); g.isFinal -> Tag("FINAL", c.primaryContainer, c.onPrimaryContainer); else -> Tag("SCHEDULED") }
                Text(g.weekLabel + if (g.stage == "bowl") " · IFL Bowl" else "", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                for (side in 0..2) {
                    if (side == 1) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when {
                                g.isLive -> {
                                    val q = sit?.i("quarter") ?: 0
                                    if (q > 0) Text(qLabel(q), style = MaterialTheme.typography.titleLarge)
                                    Text(sit?.s("clock") ?: "", style = MaterialTheme.typography.headlineSmall)
                                    Text(situationText(sit), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
                                }
                                g.isFinal -> {
                                    Text(if (g.overtime) "Final / OT" else "Final", style = MaterialTheme.typography.titleMedium)
                                    g.startsAtMs?.let { Text(dayLabel(it), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) }
                                }
                                else -> {
                                    val now by tickingNow()
                                    g.startsAtMs?.let {
                                        Text(dayLabel(it), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                                        Text(fmtTime(it), style = MaterialTheme.typography.headlineSmall)
                                        Text(countdownText(it).also { now.hashCode() }, style = MaterialTheme.typography.labelLarge, color = c.primary)
                                    }
                                }
                            }
                        }
                    } else {
                        val t = if (side == 0) g.away else g.home
                        val won = g.isFinal && g.winner == t.id; val lost = g.isFinal && g.winner != null && g.winner != t.id
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { nav.team(t.id) }) { TeamBadge(t, 72.dp) }
                            Text(t.nickname + if (s.favId == t.id) " ★" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            if (g.isScheduled) Text("–", style = MaterialTheme.typography.displayMedium, color = c.outline)
                            else Text("${t.score ?: 0}", style = MaterialTheme.typography.displayMedium, fontWeight = if (won) FontWeight.Bold else FontWeight.Normal, color = if (lost) c.onSurfaceVariant else c.onSurface)
                            Box(Modifier.height(20.dp)) { if (g.isLive && sit?.sn("possession") == t.id) Icon(Icons.Filled.SportsFootball, null, Modifier.size(18.dp), tint = c.primary) }
                        }
                    }
                }
            }
            if (h != null) { Spacer(Modifier.height(8.dp)); WinProbBar(g.away, g.home, h, if (wp.s("basis") == "pregame") "Pregame win probability" else "Win probability") }
            val v = detail.o("venue") ?: g.venue?.let { JSONObject().put("name", it.name).put("town", it.town) }
            if (v?.sn("name") != null) Text(
                v.s("name") + (v.sn("town")?.let { " · $it" } ?: ""), Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            if (!g.isScheduled) Button(onClick = onWatch, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)) {
                Icon(if (g.isLive) Icons.Filled.LiveTv else Icons.Filled.PlayCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (g.isLive) "Watch live" else "Watch replay")
            }
        }
    }
}

/* ---- summary tab */

private fun androidx.compose.foundation.lazy.LazyListScope.summaryItems(data: GameData, nav: Nav) {
    val g = data.game; val d = data.detail
    item { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (g.isScheduled) FilledCard(color = MaterialTheme.colorScheme.secondaryContainer) {
            Text("Play-by-play and the box score appear as the game airs — nothing is revealed early.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
        d.o("quarters")?.let { LineScore(g, it) }
        val scoring = data.plays.filter { it.scoringTeam != null }
        if (scoring.isNotEmpty()) {
            Text("Scoring", style = MaterialTheme.typography.titleMedium)
            FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) { Column(Modifier.padding(4.dp)) { scoring.forEach { PlayItem(it, g, highlight = false) } } }
        }
        val inactive = d.o("inactive")
        if (inactive != null && (inactive.a("home").length() > 0 || inactive.a("away").length() > 0)) {
            Text("Inactive players", style = MaterialTheme.typography.titleMedium)
            FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((team, key) in listOf(g.away to "away", g.home to "home")) {
                        Row(verticalAlignment = Alignment.CenterVertically) { TeamBadge(team, 24.dp); Spacer(Modifier.width(8.dp)); Text(team.abbr, style = MaterialTheme.typography.titleSmall) }
                        val list = inactive.a(key).mapObjs { it }
                        if (list.isEmpty()) Text("No one out", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (p in list) Row(Modifier.fillMaxWidth().clickable { nav.player(p.s("id")) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag("OUT", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(8.dp)); Text(p.s("name"), Modifier.weight(1f)); Text(p.s("position"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } }
}

@Composable
private fun LineScore(g: Game, q: JSONObject) {
    val away = q.a("away").ints(); val home = q.a("home").ints()
    val n = maxOf(away.size, home.size)
    val cell = 36.dp
    FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                for (i in 0 until n) Text(if (i < 4) "${i + 1}" else if (i == 4) "OT" else "${i - 3}OT", Modifier.width(cell), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("T", Modifier.width(cell), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            for ((team, arr) in listOf(g.away to away, g.home to home)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { TeamBadge(team, 24.dp); Spacer(Modifier.width(8.dp)); Text(team.abbr, style = MaterialTheme.typography.titleSmall) }
                    for (i in 0 until n) Text("${arr.getOrElse(i) { 0 }}", Modifier.width(cell), textAlign = TextAlign.Center)
                    Text("${team.score ?: arr.sum()}", Modifier.width(cell), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ---- plays tab */

private fun androidx.compose.foundation.lazy.LazyListScope.playItems(data: GameData, filter: Int, setFilter: (Int) -> Unit) {
    val g = data.game
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = filter == 0, onClick = { setFilter(0) }, label = { Text("All plays") }) }
            item { FilterChip(selected = filter == 1, onClick = { setFilter(1) }, label = { Text("Scoring") }) }
        }
    }
    val plays = if (filter == 1) data.plays.filter { it.scoringTeam != null } else data.plays
    if (plays.isEmpty()) { item { EmptyState(Icons.Filled.SportsFootball, "No plays yet", "Plays show up here as they finish airing.") }; return }
    // live games read newest first, finished games in order
    val ordered = if (g.isLive) plays.reversed() else plays
    var lastQ = -1
    for (p in ordered) {
        if (p.quarter != lastQ) { lastQ = p.quarter; val q = p.quarter; item(key = "q$q") { Text(qName(q).uppercase(), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } }
        item(key = "p${p.n}") { Box(Modifier.padding(horizontal = 8.dp)) { PlayItem(p, g) } }
    }
}

/* ---- box score tab */

private val STAT_ROWS: List<Triple<String, (JSONObject) -> String, Pair<Boolean, ((JSONObject) -> Double)?>>> = listOf(
    Triple("Total yards", { t: JSONObject -> "${t.i("yds")}" }, false to { t: JSONObject -> t.d("yds") }),
    Triple("Passing yards", { t: JSONObject -> "${t.i("py")}" }, false to { t: JSONObject -> t.d("py") }),
    Triple("Rushing yards", { t: JSONObject -> "${t.i("ry")}" }, false to { t: JSONObject -> t.d("ry") }),
    Triple("First downs", { t: JSONObject -> "${t.i("fd")}" }, false to { t: JSONObject -> t.d("fd") }),
    Triple("Plays", { t: JSONObject -> "${t.i("plays")}" }, false to { t: JSONObject -> t.d("plays") }),
    Triple("Turnovers", { t: JSONObject -> "${t.i("to")}" }, true to { t: JSONObject -> t.d("to") }),
    Triple("Penalties", { t: JSONObject -> "${t.i("pen")}–${t.i("peny")}" }, false to { t: JSONObject -> t.d("pen") }),
    Triple("Time of possession", { t: JSONObject -> mmss(t.i("top")) }, false to { t: JSONObject -> t.d("top") }),
    Triple("3rd down", { t: JSONObject -> "${t.i("d3c")}/${t.i("d3a")}" }, false to { t: JSONObject -> if (t.i("d3a") > 0) t.d("d3c") / t.d("d3a") else 0.0 }),
    Triple("4th down", { t: JSONObject -> "${t.i("d4c")}/${t.i("d4a")}" }, false to { t: JSONObject -> if (t.i("d4a") > 0) t.d("d4c") / t.d("d4a") else 0.0 }),
    Triple("Sacks taken", { t: JSONObject -> "${t.i("sk")}–${t.i("sky")}" }, true to { t: JSONObject -> t.d("sk") }),
)

private class PGroup(val title: String, val test: (JSONObject) -> Boolean, val cols: List<Pair<String, (JSONObject) -> String>>, val sortKey: String)

private fun num(key: String): (JSONObject) -> String = { it.i(key).toString() }

private val PLAYER_GROUPS = listOf(
    PGroup("Passing", { it.i("pa") > 0 }, listOf("C/ATT" to { s: JSONObject -> "${s.i("pc")}/${s.i("pa")}" }, "YDS" to num("py"), "TD" to num("ptd"), "INT" to num("pint")), "py"),
    PGroup("Rushing", { it.i("ra") > 0 }, listOf("CAR" to num("ra"), "YDS" to num("ry"), "LNG" to num("rlng"), "TD" to num("rtd")), "ry"),
    PGroup("Receiving", { it.i("rec") > 0 || it.i("tgt") > 0 }, listOf("REC" to num("rec"), "TGT" to num("tgt"), "YDS" to num("recy"), "TD" to num("rectd")), "recy"),
    PGroup("Defense", { it.i("tk") + it.i("sacks") + it.i("ints") + it.i("pd") + it.i("ff") + it.i("fr") > 0 }, listOf("TK" to num("tk"), "SCK" to num("sacks"), "INT" to num("ints"), "PD" to num("pd")), "tk"),
    PGroup("Kicking", { it.i("fga") > 0 || it.i("xpa") > 0 }, listOf("FG" to { s: JSONObject -> "${s.i("fgm")}/${s.i("fga")}" }, "LNG" to num("fglng"), "XP" to { s: JSONObject -> "${s.i("xpm")}/${s.i("xpa")}" }), "fgm"),
    PGroup("Punting", { it.i("pn") > 0 }, listOf("PUNTS" to num("pn"), "YDS" to num("pny")), "pny"),
    PGroup("Returns", { it.i("kr") + it.i("pr") > 0 }, listOf("KR" to num("kr"), "KR YDS" to num("kry"), "PR" to num("pr"), "PR YDS" to num("pry")), "kry"),
)

@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.foundation.lazy.LazyListScope.boxItems(data: GameData, side: Int, setSide: (Int) -> Unit, nav: Nav) {
    val g = data.game
    val box = data.detail.o("box")
    if (box == null) { item { EmptyState(Icons.Filled.TableChart, "No box score yet") }; return }
    item {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val aS = box.o("away")?.o("stats"); val hS = box.o("home")?.o("stats")
            if (aS != null && hS != null) StatCompare(g, aS, hS)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(g.away, g.home).forEachIndexed { i, t ->
                    SegmentedButton(selected = side == i, onClick = { setSide(i) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(t.abbr) }
                }
            }
        }
    }
    val players = box.o(if (side == 0) "away" else "home")?.a("players")?.mapObjs { it }.orEmpty()
    var any = false
    for (grp in PLAYER_GROUPS) {
        val rows = players.filter { p -> p.o("stats")?.let { grp.test(it) } == true }.sortedByDescending { it.o("stats")?.i(grp.sortKey) ?: 0 }
        if (rows.isEmpty()) continue
        any = true
        item(key = "grp${grp.title}$side") { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(grp.title, style = MaterialTheme.typography.titleMedium)
            FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Player", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for ((h, _) in grp.cols) Text(h, Modifier.width(52.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for (p in rows) {
                        val st = p.o("stats") ?: continue
                        Row(Modifier.fillMaxWidth().clickable { nav.player(p.s("id")) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.s("short").ifEmpty { p.s("name") }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text("#${p.i("number")} · ${p.s("position")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            for ((_, f) in grp.cols) Text(f(st), Modifier.width(52.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }
        } }
    }
    if (!any) item { EmptyState(Icons.Filled.TableChart, "No player stats yet") }
}

@Composable
private fun StatCompare(g: Game, a: JSONObject, h: JSONObject) {
    FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(g.away, 32.dp); Text("Team stats", style = MaterialTheme.typography.titleMedium); TeamBadge(g.home, 32.dp)
            }
            for ((label, fmt, cmp) in STAT_ROWS) {
                val (lowerBetter, numFn) = cmp
                val an = numFn?.invoke(a) ?: 0.0; val hn = numFn?.invoke(h) ?: 0.0
                val tot = (an + hn).let { if (it <= 0.0) 1.0 else it }
                val aLead = if (lowerBetter) an < hn else an > hn; val hLead = if (lowerBetter) hn < an else hn > an
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(a), Modifier.weight(1f), fontWeight = if (aLead) FontWeight.Bold else FontWeight.Normal)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmt(h), Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = if (hLead) FontWeight.Bold else FontWeight.Normal)
                    }
                    Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val aw = (an / tot).toFloat().coerceIn(0.001f, 1f); val hw = (hn / tot).toFloat().coerceIn(0.001f, 1f)
                        Box(Modifier.weight(aw).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(g.away.c1.copy(alpha = if (aLead) 1f else 0.45f)))
                        Box(Modifier.weight(hw).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(g.home.c1.copy(alpha = if (hLead) 1f else 0.45f)))
                    }
                }
            }
        }
    }
}
