package com.iflgamecast.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/* ---------------------------------------------------------------- formatting */

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val DAY_LONG_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")

fun fmtTime(ms: Long): String = TIME_FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun dayLabel(ms: Long, long: Boolean = false): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> (if (long) DAY_LONG_FMT else DAY_FMT).format(d)
    }
}

fun countdownText(ms: Long): String {
    val s = ((ms - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
    if (s <= 0) return "Starting…"
    val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
    return when {
        h >= 48 -> "${h / 24} days"
        h > 0 -> "${h}h %02dm".format(m)
        else -> "$m:%02d".format(sec)
    }
}

fun pctText(x: Double): String = when {
    x >= 1.0 -> "100%"
    x <= 0.0 -> "0%"
    x >= 0.995 -> ">99%"
    x < 0.005 -> "<1%"
    else -> "${Math.round(x * 100)}%"
}

fun mmss(sec: Int): String = "${sec / 60}:%02d".format(sec % 60)
fun fmtClock(sec: Float): String { val s = Math.max(0, Math.round(sec)); return "${s / 60}:%02d".format(s % 60) }

fun readableOn(c: Color): Color = if (c.red * 0.2126f + c.green * 0.7152f + c.blue * 0.0722f > 0.55f) Color(0xFF111111) else Color.White

/** A ticking value, refreshed every second (countdowns). */
@Composable
fun tickingNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) { value = System.currentTimeMillis(); delay(1000) }
}

/* ---------------------------------------------------------------- loading helpers */

sealed class Load<out T> {
    object Loading : Load<Nothing>()
    class Ok<T>(val v: T) : Load<T>()
    class Err(val e: Throwable) : Load<Nothing>()
}

class Loader<T>(val state: State<Load<T>>, val reload: () -> Unit)

/** Runs [block] when [keys] change (and every [pollMs] if > 0), exposing Loading / Ok / Err. A failed refresh keeps the last good value. */
@Composable
fun <T> rememberLoad(vararg keys: Any?, pollMs: Long = 0L, block: suspend () -> T): Loader<T> {
    val state = remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var tick by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(*keys, tick) {
        while (true) {
            try {
                state.value = Load.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (state.value !is Load.Ok) state.value = Load.Err(e)
            }
            if (pollMs <= 0) break
            delay(pollMs)
        }
    }
    return Loader(state) { state.value = Load.Loading; tick++ }
}

@Composable
fun <T> LoadView(loader: Loader<T>, content: @Composable (T) -> Unit) {
    when (val st = loader.state.value) {
        is Load.Loading -> LoadingBox()
        is Load.Err -> ErrorState(st.e, loader.reload)
        is Load.Ok -> content(st.v)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorState(e: Throwable, onRetry: () -> Unit) {
    val title: String; val body: String
    if (e is ApiException && e.code == 0) {
        title = "Can't reach the server"; body = "The app couldn't connect to ${Api.base}. Check your connection or the server address in settings."
    } else if (e is ApiException && e.code == 404) {
        title = "Not found"; body = e.message ?: "The server doesn't have that."
    } else {
        title = "Something went wrong"; body = e.message ?: ""
    }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.CloudOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Try again") }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/* ---------------------------------------------------------------- small components */

@Composable
fun TeamBadge(t: Team, size: Dp) {
    var loaded by remember(t.logoUrl) { mutableStateOf(false) }
    Box(
        Modifier.size(size).clip(CircleShape).background(t.c1).border(BorderStroke(size.value.coerceAtMost(40f).div(16f).dp.coerceAtLeast(1.5.dp), t.c2), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(t.abbr, color = readableOn(t.c1), fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        val url = t.logoUrl
        if (url != null) AsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize().background(if (loaded) Color.White else Color.Transparent).padding(size * 0.12f),
        )
    }
}

@Composable
fun Tag(text: String, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(3.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Solid red broadcast-style "LIVE" tag. */
@Composable
fun LiveBadge(text: String = "LIVE") {
    Surface(color = LiveRed, contentColor = Color.White, shape = RoundedCornerShape(3.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Section title: accent bar, bold condensed capitals, optional action link. */
@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(18.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.titleMedium)
        }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
fun FilledCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = MaterialTheme.colorScheme.surfaceContainerLow, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = color)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick, modifier.fillMaxWidth(), colors = colors, shape = CardShape, border = border) { content() }
    else Card(modifier.fillMaxWidth(), colors = colors, shape = CardShape, border = border) { content() }
}

/** A compact game row: both teams with scores on the left, status/time on the right. */
@Composable
fun GameRow(g: Game, onClick: () -> Unit) {
    val s = LocalSettings.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in listOf(g.away, g.home)) {
                val won = g.isFinal && g.winner == t.id
                val lost = g.isFinal && g.winner != null && g.winner != t.id
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        t.name + if (s.favId == t.id) "  ★" else "", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
                        color = if (lost) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    if (t.score != null) Text(
                        "${t.score}", style = MaterialTheme.typography.titleMedium, fontWeight = if (won) FontWeight.Bold else FontWeight.Medium,
                        color = if (lost) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(92.dp), horizontalAlignment = Alignment.End) {
            when {
                g.isLive -> { LiveBadge(); Text(g.weekLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                g.isFinal -> {
                    Text(if (g.overtime) "FINAL / OT" else "FINAL", style = MaterialTheme.typography.labelMedium)
                    Text(g.weekLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    val ms = g.startsAtMs
                    if (ms != null) {
                        Text(fmtTime(ms), style = MaterialTheme.typography.labelLarge)
                        Text(dayLabel(ms), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text("TBD", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun GameList(games: List<Game>, onGame: (Game) -> Unit) {
    FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            games.forEachIndexed { i, g ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GameRow(g) { onGame(g) }
            }
        }
    }
}

/** Win-probability bar split between the two teams' colours. */
@Composable
fun WinProbBar(away: Team, home: Team, homeProb: Float, label: String = "Win probability") {
    val p = homeProb.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            if (1f - p > 0.001f) Box(Modifier.weight(1f - p).fillMaxSize().background(away.c1))
            if (p > 0.001f) Box(Modifier.weight(p).fillMaxSize().background(home.c1))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${away.abbr} ${Math.round((1f - p) * 100)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Math.round(p * 100)}% ${home.abbr}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlayItem(p: Play, game: Game?, highlight: Boolean = p.scoringTeam != null) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (highlight) Modifier.background(c.primaryContainer.copy(alpha = 0.55f), RoundedCornerShape(4.dp)) else Modifier).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.width(52.dp)) {
            Text(qLabel(p.quarter), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Text(p.clock, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            if (p.situation != null) Text(p.situation, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Text(p.text, style = MaterialTheme.typography.bodyMedium)
            val labels = ArrayList<Pair<String, Int>>()   // text, kind: 0 plain, 1 scoring, 2 bad
            val tagSet = p.tags
            p.banner?.let { labels.add(it to if (tagSet.any { t -> t == "TD" || t == "FG" }) 1 else if (tagSet.any { t -> t == "TO" || t == "FLAG" }) 2 else 0) }
            for (t in tagSet) when (t) {
                "TO" -> if (labels.none { it.first == "TURNOVER" || it.first == p.banner }) labels.add("TURNOVER" to 2)
                "INJ" -> labels.add("INJURY" to 0)
                "ONSIDE", "2PT", "MISS" -> if (labels.none { it.first == t }) labels.add(t to 0)
            }
            if (p.scoringTeam != null && game != null) labels.add("${game.away.abbr} ${p.awayScore} – ${p.homeScore} ${game.home.abbr}" to 1)
            if (labels.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((text, kind) in labels) when (kind) {
                    1 -> Tag(text, c.primaryContainer, c.onPrimaryContainer)
                    2 -> Tag(text, c.errorContainer, c.onErrorContainer)
                    else -> Tag(text)
                }
            }
        }
    }
}
