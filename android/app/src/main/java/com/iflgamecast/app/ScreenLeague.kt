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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

private fun divOrder(name: String): Int {
    val n = name.lowercase()
    return when { "east" in n -> 0; "central" in n -> 1; "west" in n -> 2; else -> 9 }
}

/* ---------------------------------------------------------------- standings */

private class Col(val label: String, val width: Dp, val value: (JSONObject) -> String, val color: ((JSONObject) -> Color?)? = null)

@Composable
private fun StandingsTable(title: String?, rows: List<JSONObject>, full: Boolean, onTeam: (String) -> Unit) {
    val s = LocalSettings.current
    val good = successColor(); val bad = MaterialTheme.colorScheme.error
    val cols = buildList {
        add(Col("W-L", 56.dp, { r -> "${r.i("wins")}-${r.i("losses")}" + if (r.i("ties") > 0) "-${r.i("ties")}" else "" }))
        if (full) add(Col("PCT", 52.dp, { r -> "%.3f".format(r.d("pct")).removePrefix("0") }))
        add(Col("PF", 44.dp, { r -> "${r.i("pointsFor")}" }))
        add(Col("PA", 44.dp, { r -> "${r.i("pointsAgainst")}" }))
        add(Col("DIFF", 52.dp, { r -> (if (r.i("differential") > 0) "+" else "") + r.i("differential") }, { r -> if (r.i("differential") > 0) good else if (r.i("differential") < 0) bad else null }))
        if (full) {
            add(Col("HOME", 56.dp, { r -> r.s("home") })); add(Col("AWAY", 56.dp, { r -> r.s("away") }))
            add(Col("DIV", 56.dp, { r -> r.s("divisionRecord") })); add(Col("STRK", 48.dp, { r -> r.s("streak").ifEmpty { "–" } }))
        }
    }
    val tableW = (24 + 176).dp + cols.fold(0.dp) { a, c -> a + c.width }     // the header band and dividers span the whole scrollable table
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) SectionHeader(title)
        FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.width(tableW).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Team", Modifier.width(176.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (c in cols) Text(c.label, Modifier.width(c.width), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rows.forEachIndexed { i, r ->
                    val t = teamOf(r.obj("team"))
                    if (i > 0) HorizontalDivider(Modifier.width(tableW), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.width(tableW).clickable { onTeam(t.id) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(176.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${r.i("rank", i + 1)}", Modifier.width(20.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TeamBadge(t, 28.dp); Spacer(Modifier.width(8.dp))
                            Text(t.name + if (s.favId == t.id) " ★" else "", maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        for (c in cols) Text(c.value(r), Modifier.width(c.width), textAlign = TextAlign.End, color = c.color?.invoke(r) ?: Color.Unspecified)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen() {
    val s = LocalSettings.current
    val nav = LocalNav.current
    val ctx = LocalContext.current.applicationContext
    val loader = rememberLoad(s.season, s.base, staleFirst = true) { Api.v1("standings", s.season) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    LoadView(loader) { d ->
        val size = LocalWinSize.current
        val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
        LazyVerticalGrid(
            if (size == WinSize.Compact) GridCells.Fixed(1) else GridCells.Adaptive(400.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding()),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            d.o("champion")?.let { ch -> item(span = full) { ChampionCard(teamOf(ch), d.i("season")) { nav.team(ch.s("id")) } } }
            item(span = full) { Text("Season ${d.i("season")} · ${d.i("regularSeasonGamesPlayed")} of ${d.i("regularSeasonGames")} regular-season games played", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item(span = full) {
                EqualTabs(listOf("Divisions", "League", "Seeds", "Odds"), mode, Modifier.clip(CardShape)) { mode = it }

            }
            when (mode) {
                0 -> for (dv in d.a("divisions").mapObjs { it }.sortedBy { divOrder(it.s("name")) }) item(key = "div${dv.s("id")}") {
                    StandingsTable(dv.s("name") + " Division", dv.a("teams").mapObjs { it }, false, nav.team)
                }
                1 -> item(span = full) { StandingsTable(null, d.a("overall").mapObjs { it }, true, nav.team) }
                2 -> {
                    val seeds = d.o("playoffs")?.a("seeds")?.mapObjs { it }.orEmpty()
                    if (seeds.isEmpty()) item(span = full) { EmptyState(Icons.Filled.EmojiEvents, "Playoff seeds aren't set yet", "They're locked in when the regular season ends.") }
                    else item(span = full) {
                        FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column {
                                seeds.forEachIndexed { i, x ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    val t = teamOf(x)
                                    Row(Modifier.fillMaxWidth().clickable { nav.team(t.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${x.i("seed")}", Modifier.width(36.dp), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                        TeamBadge(t, 40.dp); Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) { Text(t.name, style = MaterialTheme.typography.bodyLarge); x.sn("qualified")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> item(span = full) { OddsTable(ctx, nav) }
            }
        }
    }
}

@Composable
private fun OddsTable(ctx: android.content.Context, nav: Nav) {
    val s = LocalSettings.current
    if (s.season != null) { EmptyState(Icons.Filled.Insights, "Odds are for the current season", "Switch back to the current season to see playoff and IFL Bowl chances."); return }
    val odds = rememberLoad(s.base) { OddsLoader.load(ctx) }
    LoadViewInline(odds) { o ->
        if (o == null) { EmptyState(Icons.Filled.Insights, "Odds aren't available yet"); return@LoadViewInline }
        val ids = o.teams.keys.sortedWith(compareByDescending<String> { o.teams[it]?.champ ?: 0.0 }.thenByDescending { o.teams[it]?.bowl ?: 0.0 }.thenByDescending { o.teams[it]?.playoffs ?: 0.0 })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("Team", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (h in listOf("PLAYOFFS", "BOWL", "TITLE")) Text(h, Modifier.width(if (h == "PLAYOFFS") 72.dp else 52.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    for ((i, id) in ids.withIndex()) {
                        val t = o.teams.getValue(id); val ref = o.refs[id] ?: continue
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().clickable { nav.team(id) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                TeamBadge(ref, 28.dp); Spacer(Modifier.width(8.dp))
                                Text(ref.name + if (s.favId == id) " ★" else "", maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(pctText(t.playoffs), Modifier.width(72.dp), textAlign = TextAlign.End)
                            Text(pctText(t.bowl), Modifier.width(52.dp), textAlign = TextAlign.End)
                            Text(pctText(t.champ), Modifier.width(52.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            val note = o.note(null)
            Text("$note. Bowl = reaches the IFL Bowl; title = wins it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** Like LoadView but sized to its content (for use inside a list item). */
@Composable
fun <T> LoadViewInline(loader: Loader<T>, content: @Composable (T) -> Unit) {
    when (val st = loader.state.value) {
        is Load.Loading -> LoadingBox(Modifier.fillMaxWidth().height(120.dp))
        is Load.Err -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(st.e.message ?: "Something went wrong", color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.TextButton(onClick = loader.reload) { Text("Try again") }
        }
        is Load.Ok -> content(st.v)
    }
}

/* ---------------------------------------------------------------- teams */

@Composable
fun TeamsScreen() {
    val s = LocalSettings.current
    val nav = LocalNav.current
    val loader = rememberLoad(s.season, s.base, staleFirst = true) { Api.v1("teams", s.season).a("teams").mapObjs { teamListingOf(it) } }
    var sel by rememberSaveable { mutableStateOf("all") }
    LoadView(loader) { teams ->
        val divs = teams.groupBy { it.division }.toSortedMap(compareBy({ divOrder(it) }, { it }))
        val shown = divs.filterKeys { sel == "all" || it == sel }
        Column(Modifier.fillMaxSize()) {
            val divNames = divs.keys.toList()
            ScrollTabs(listOf("All") + divNames, if (sel == "all") 0 else divNames.indexOf(sel) + 1) { sel = if (it == 0) "all" else divNames[it - 1] }

            LazyVerticalGrid(
                GridCells.Adaptive(170.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((name, list) in shown) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(name) }
                    items(list, key = { it.team.id }) { tl ->
                        FilledCard(onClick = { nav.team(tl.team.id) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TeamBadge(tl.team, 48.dp); Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(tl.team.name + if (s.favId == tl.team.id) " ★" else "", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                    Text(tl.record + (tl.rating?.let { " · OVR ${it.ovr}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val POS_ORDER = listOf("QB", "RB", "WR", "TE", "OL", "DL", "LB", "CB", "S", "K", "P")
private val POS_NAME = mapOf("QB" to "Quarterbacks", "RB" to "Running backs", "WR" to "Wide receivers", "TE" to "Tight ends", "OL" to "Offensive line", "DL" to "Defensive line", "LB" to "Linebackers", "CB" to "Cornerbacks", "S" to "Safeties", "K" to "Kickers", "P" to "Punters")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(id: String) {
    val s = LocalSettings.current
    val nav = LocalNav.current
    val ctx = LocalContext.current.applicationContext
    val loader = rememberLoad(id, s.season, s.base, staleFirst = true) { Api.v1("teams/$id", s.season) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val st = loader.state.value
    SetTitle((st as? Load.Ok)?.v?.s("name") ?: "Team")
    LoadView(loader) { t ->
        val team = teamOf(t)
        val rec = t.o("record"); val rating = ratingOf(t.o("rating"))
        val roster = t.a("roster").mapObjs { it }
        val schedule = t.a("schedule").mapObjs { gameOf(it) }
        val isFav = s.favId == team.id
        val grad = Brush.linearGradient(listOf(team.c1, team.c1.copy(red = team.c1.red * 0.55f, green = team.c1.green * 0.55f, blue = team.c1.blue * 0.55f)))
        val size = LocalWinSize.current
        val topItems: LazyListScope.() -> Unit = {
            item {
                Box(Modifier.padding(16.dp).fillMaxWidth().clip28().background(grad).padding(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamBadge(team, 88.dp); Spacer(Modifier.width(16.dp))
                            Column {
                                Text((t.o("division")?.s("name") ?: "") + " Division", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                                Text(team.city, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(team.nickname, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rec?.let { Pill("${it.i("wins")}-${it.i("losses")}" + if (it.i("ties") > 0) "-${it.i("ties")}" else "") }
                                    rec?.sn("streak")?.let { Pill(it) }
                                }
                            }
                        }
                        if (rating != null) Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((label, v) in listOf("OVERALL" to rating.ovr, "OFFENSE" to rating.off, "DEFENSE" to rating.def)) {
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.16f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$v", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isFav) AppTonalButton(onClick = { s.changeFav(null, 0) }) { Icon(Icons.Filled.Star, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("My team") }
                    else AppOutlinedButton(onClick = { s.changeFav(team.id, team.c1.toArgb()) }) { Icon(Icons.Filled.StarBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Make my team") }
                    rec?.let { Text("PF ${it.i("pointsFor")} · PA ${it.i("pointsAgainst")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (s.season == null) item { Box(Modifier.padding(16.dp)) { TeamOddsInline(ctx, team.id) } }
        }
        val tabRow: @Composable () -> Unit = {
            EqualTabs(listOf("Roster", "Schedule"), tab) { tab = it }

        }
        val bodyItems: LazyListScope.() -> Unit = {
            if (tab == 0) {
                for (pos in POS_ORDER) {
                    val list = roster.filter { it.s("position") == pos }.sortedByDescending { it.i("rating") }
                    if (list.isEmpty()) continue
                    item(key = "pos$pos") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(bottom = 4.dp)) {
                                    Text((POS_NAME[pos] ?: pos).uppercase(), Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    for (p in list) Row(Modifier.fillMaxWidth().clickable { nav.player(p.s("id")) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${p.i("number")}", Modifier.width(32.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Column(Modifier.weight(1f)) {
                                            Text(p.s("name"), maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                                            Text("Age ${p.i("age")} · ${p.i("yearsPro")} yr${if (p.i("yearsPro") == 1) "" else "s"} pro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        p.o("injury")?.let { inj ->
                                            Tag(if (inj.b("seasonEnding")) "OUT – SEASON" else "OUT ${inj.i("gamesLeft")}", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text("${p.i("rating")}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }
                    }
                }
            } else item {
                Box(Modifier.padding(16.dp)) { if (schedule.isEmpty()) EmptyState(Icons.Filled.Event, "No games scheduled") else GameList(schedule) { nav.game(it.id) } }
            }
        }
        if (size == WinSize.Expanded) {
            // tablet: team card, odds and buttons on the left; roster / schedule on the right
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(0.38f).fillMaxHeight(), contentPadding = PaddingValues(start = 8.dp, bottom = 24.dp)) { topItems() }
                Column(Modifier.weight(0.62f).fillMaxHeight()) {
                    tabRow()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) { bodyItems() }
                }
            }
        } else CenteredContent(if (size == WinSize.Medium) 720.dp else 760.dp) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) { topItems(); item { tabRow() }; bodyItems() }
        }
    }
}

private fun Modifier.clip28() = this.then(Modifier.clip(CardShape))

@Composable
private fun Pill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun TeamOddsInline(ctx: android.content.Context, id: String) {
    val s = LocalSettings.current
    val odds = rememberLoad(id, s.base) { OddsLoader.load(ctx) }
    val st = odds.state.value
    if (st is Load.Ok && st.v != null) TeamOddsCard(st.v, id, showHeader = false)
}

/* ---------------------------------------------------------------- leaders */

@Composable
fun LeadersScreen() {
    val s = LocalSettings.current
    val nav = LocalNav.current
    val size = LocalWinSize.current
    var cat by rememberSaveable { mutableStateOf("overview") }
    val overview = rememberLoad(s.season, s.base, staleFirst = true) { Api.v1("leaders", s.season, "limit" to 5) }
    LoadView(overview) { ov ->
        val cats = ov.o("categories") ?: JSONObject()
        val keys = cats.keyList()
        val active = if (cat == "overview" || cat in keys) cat else "overview"
        Column(Modifier.fillMaxSize()) {
            ScrollTabs(listOf("Overview") + keys.map { cats.o(it)?.s("label") ?: it }, if (active == "overview") 0 else keys.indexOf(active) + 1) { cat = if (it == 0) "overview" else keys[it - 1] }

            if (active == "overview") {
                val nonEmpty = keys.filter { (cats.o(it)?.a("leaders")?.length() ?: 0) > 0 }
                if (nonEmpty.isEmpty()) EmptyState(Icons.Filled.MilitaryTech, "No stats yet", "Leaders appear once games have been played.")
                else LazyVerticalGrid(
                    if (size == WinSize.Compact) GridCells.Fixed(1) else GridCells.Adaptive(340.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    for (k in nonEmpty) item(key = k) {
                        val c = cats.obj(k)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(c.s("label"), "More") { cat = k }
                            LeaderList(c.a("leaders").mapObjs { it }.take(3), nav)
                        }
                    }
                }
            } else {
                val full = rememberLoad(active, s.season, s.base) { Api.v1("leaders", s.season, "category" to active, "limit" to 25) }
                LoadView(full) { r ->
                    val list = r.o("categories")?.o(active)?.a("leaders")?.mapObjs { it }.orEmpty()
                    if (list.isEmpty()) EmptyState(Icons.Filled.MilitaryTech, "No leaders yet")
                    else CenteredContent { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding())) { item { LeaderList(list, nav) } } }
                }
            }
        }
    }
}

@Composable
private fun LeaderList(list: List<JSONObject>, nav: Nav) {
    val c = MaterialTheme.colorScheme
    FilledCard(color = c.surfaceContainerLow) {
        Column {
            list.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = c.outlineVariant)
                val first = l.i("rank") == 1
                val team = teamOf(l.obj("teamInfo"))
                Row(
                    Modifier.fillMaxWidth().background(if (first) c.primaryContainer else Color.Transparent).clickable { nav.player(l.s("id")) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${l.i("rank")}", Modifier.width(24.dp), style = MaterialTheme.typography.labelLarge, color = if (first) c.onPrimaryContainer else c.onSurfaceVariant)
                    TeamBadge(team, 32.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.s("name"), maxLines = 1, style = MaterialTheme.typography.bodyLarge, color = if (first) c.onPrimaryContainer else c.onSurface)
                        Text("${l.s("position")} · ${team.abbr} · ${l.i("gamesPlayed")} GP", style = MaterialTheme.typography.bodySmall, color = if (first) c.onPrimaryContainer.copy(alpha = 0.75f) else c.onSurfaceVariant)
                    }
                    Text("%,d".format(l.i("value")), style = MaterialTheme.typography.titleLarge, color = if (first) c.onPrimaryContainer else c.onSurface)
                }
            }
        }
    }
}
