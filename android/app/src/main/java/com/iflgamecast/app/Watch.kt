package com.iflgamecast.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Paint
import android.graphics.Typeface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/* ---------------------------------------------------------------- entry */

private sealed interface WatchState
private class NotYet(val game: Game) : WatchState
private class Ready(val game: Game, val data: ReplayData) : WatchState

@Composable
fun WatchScreen(id: String) {
    val s = LocalSettings.current
    val ctx = LocalContext.current.applicationContext
    val loader = rememberLoad(id, s.season, s.base) {
        val g = gameOf(Api.v1("games/$id", s.season).getJSONObject("game"))
        // The public API is the spoiler gate: only games that have started can be watched.
        if (g.isScheduled) NotYet(g) as WatchState else Ready(g, loadReplay(ctx, g, s.season))
    }
    val st = loader.state.value
    SetTitle(if (st is Load.Ok) st.v.let { w -> (if (w is Ready) w.game else (w as NotYet).game).let { "${it.away.abbr} @ ${it.home.abbr}" } } else "Watch")
    when (st) {
        is Load.Loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(); Spacer(Modifier.height(12.dp))
            Text("Setting up the game…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is Load.Err -> ErrorState(st.e, loader.reload)
        is Load.Ok -> when (val w = st.v) {
            is NotYet -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Text("Not on air yet", style = MaterialTheme.typography.titleMedium)
                val ms = w.game.startsAtMs
                Text(
                    "${w.game.away.name} at ${w.game.home.name}" + if (ms != null) " starts ${dayLabel(ms)} at ${fmtTime(ms)}. Come back at kickoff to watch it live." else ".",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
            is Ready -> Viewer(w.game, w.data)
        }
    }
}

/* ---------------------------------------------------------------- playback state */

private class VState(val sim: SimData, val g: Game, val live: Boolean, val rd: ReplayData) {
    val vt: MutableFloatState = mutableFloatStateOf(0f)
    var paused by mutableStateOf(!live)
    var speed by mutableIntStateOf(1)
    var fs by mutableStateOf(false)

    val n = sim.events.size
    /** Plays whose action has finished at the current broadcast time. */
    val idx: State<Int> = derivedStateOf { sim.completed(vt.floatValue) }
    /** The play currently being animated (null while waiting between plays or before kickoff). */
    val begun: State<Int> = derivedStateOf { val i = idx.value; if (i < n && vt.floatValue >= sim.events[i].start) i else -1 }
    val done: Boolean get() = idx.value >= n

    fun snapshot(): Snap = idx.value.let { if (it == 0) sim.snap0 else sim.events[it - 1].post }
    fun dd(): String = idx.value.let { if (it == 0) sim.dd0 else sim.events[it - 1].dd }
    fun wp(): Float = idx.value.let { if (it == 0) sim.wp0 else sim.events[it - 1].wp }

    val clockText: State<String> = derivedStateOf {
        val i = idx.value
        val snap = if (i == 0) sim.snap0 else sim.events[i - 1].post
        val t = vt.floatValue
        var c = snap.clock
        if (i < n && t >= sim.events[i].start) {
            val ev = sim.events[i]
            if (ev.pre.qtr == ev.post.qtr) {
                val lt = t - ev.start
                c = if (lt < ev.preMs) lerp(ev.pre.clock, ev.snapClock, if (ev.preMs > 0f) lt / ev.preMs else 1f)
                else lerp(ev.snapClock, ev.post.clock, if (ev.actMs > 0f) ((lt - ev.preMs) / ev.actMs).coerceIn(0f, 1f) else 1f)
            }
        }
        fmtClock(c)
    }

    val banner: State<SimEvent?> = derivedStateOf {
        val i = idx.value
        if (i == 0) null else {
            val ev = sim.events[i - 1]
            val hold = if (live) 1900f else 1900f / max(1f, speed / 2f)
            if (ev.banner != null && ev.banner != "KICKOFF" && vt.floatValue - ev.endAct < hold) ev else null
        }
    }

    val started: State<Boolean> = derivedStateOf { vt.floatValue > 0f }
    val etaText: State<String> = derivedStateOf {
        if (idx.value >= n) "" else (if (paused) "Paused · " else "") + fmtClock(max(0f, sim.totalMs - vt.floatValue) / speed / 1000f) + " left"
    }

    fun playPause() { if (done) restart() else paused = !paused }
    fun restart() { vt.floatValue = 0f; paused = true }
    fun skipEnd() { vt.floatValue = sim.totalMs }
    fun next() {
        val i = idx.value
        if (i >= n) return
        var t = max(vt.floatValue, sim.events[i].endAct)
        if (paused && i + 1 < n) t = max(t, sim.events[i + 1].start)     // paused: finish this play and park at the next one
        vt.floatValue = t
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/* ---------------------------------------------------------------- viewer */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Viewer(g: Game, rd: ReplayData) {
    val v = remember(rd) { VState(rd.sim, g, g.isLive, rd) }
    val chrome = LocalChrome.current
    val activity = LocalContext.current.findActivity()

    // clock: live games follow the broadcast; replays advance with the chosen speed
    LaunchedEffect(v) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (v.live) v.vt.floatValue = (System.currentTimeMillis() + rd.offset - rd.airStart).toFloat().coerceIn(0f, v.sim.totalMs)
                else if (last != 0L && !v.paused) v.vt.floatValue = min(v.sim.totalMs, v.vt.floatValue + min((now - last) / 1_000_000f, 100f) * v.speed)
                last = now
            }
        }
    }

    // keep the screen awake while watching
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // fullscreen: landscape, hide the system bars and the app's own bars
    DisposableEffect(v.fs) {
        val window = activity?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val on = v.fs          // captured: when this effect is disposed v.fs has already changed, so it can't be re-read there
        chrome.fullscreen = on
        if (on) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (on) { ctrl?.show(WindowInsetsCompat.Type.systemBars()); activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            chrome.fullscreen = false
        }
    }
    BackHandler(enabled = v.fs) { v.fs = false }

    if (v.fs) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0E)).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ScoreBar(v, compact = true)
            FieldBox(v, Modifier.weight(1f).fillMaxWidth())
            PlayText(v, compact = true)
            Controls(v, compact = true)
        }
    } else {
        val mismatch = g.isFinal && (g.home.score != rd.sim.finalHome || g.away.score != rd.sim.finalAway)
        val size = LocalWinSize.current
        val mismatchCard: LazyListScope.() -> Unit = {
            if (mismatch) item {
                FilledCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("This replay may differ slightly from the original broadcast (the game engine has changed since it aired).", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        val gameItems: LazyListScope.() -> Unit = {
            item { ScoreBar(v, compact = false) }
            item { FieldBox(v, Modifier.fillMaxWidth().aspectRatio(FW / FH)) }
            item { PlayText(v, compact = false) }
            item { Controls(v, compact = false) }
        }
        val logItems: LazyListScope.() -> Unit = {
            item { WinProbCard(v) }
            item { SectionHeader("Play-by-play") }
            val count = v.idx.value
            val from = max(0, count - 200)
            if (count == 0) item { Text("Plays appear here as they happen.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(count - from, key = { count - 1 - it }) { k ->
                val ev = v.sim.events[count - 1 - k]
                PlayItem(playFromEvent(ev, count - k), g)
            }
        }
        if (size == WinSize.Expanded) {
            // tablet: the field gets the space; win probability and play-by-play sit beside it
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.weight(1.7f).fillMaxHeight(), contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { mismatchCard(); gameItems() }
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { logItems() }
            }
        } else CenteredContent(if (size == WinSize.Medium) 760.dp else 720.dp) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding()), verticalArrangement = Arrangement.spacedBy(12.dp)) { mismatchCard(); gameItems(); logItems() }
        }
    }
}

private fun playFromEvent(ev: SimEvent, n: Int) = Play(
    n = n, kind = ev.kind, quarter = ev.pre.qtr, clock = fmtClock(ev.pre.clock), situation = ev.preText.ifEmpty { null }, text = ev.text, tags = ev.tags,
    banner = ev.banner, scoringTeam = when (ev.scoring) { 0 -> "home"; 1 -> "away"; else -> null }, homeScore = ev.post.scoreHome, awayScore = ev.post.scoreAway,
)

/* ---------------------------------------------------------------- scoreboard, text, controls */

@Composable
private fun ScoreBar(v: VState, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    val snap = v.snapshot()
    val sim = v.sim
    val ddText = when {
        snap.over -> if (snap.qtr >= 5) "Overtime" else ""
        snap.phase == "play" -> v.dd()
        snap.phase == "kickoff" || snap.phase == "freekick" -> "Kickoff"
        snap.phase == "pat" -> "Extra point"
        else -> ""
    }
    @Composable
    fun side(t: SimTeam, team: Team, score: Int, hasBall: Boolean, timeouts: Int, away: Boolean) {
        val won = snap.over && ((away && snap.scoreAway > snap.scoreHome) || (!away && snap.scoreHome > snap.scoreAway))
        val lost = snap.over && ((away && snap.scoreAway < snap.scoreHome) || (!away && snap.scoreHome < snap.scoreAway))
        val scoreColor = if (compact) Color.White else if (lost) c.onSurfaceVariant else c.onSurface
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (away) { TeamBadge(team, 36.dp); Text("$score", style = MaterialTheme.typography.headlineMedium, color = scoreColor, fontWeight = if (won) FontWeight.Bold else FontWeight.Normal) }
                else { Text("$score", style = MaterialTheme.typography.headlineMedium, color = scoreColor, fontWeight = if (won) FontWeight.Bold else FontWeight.Normal); TeamBadge(team, 36.dp) }
                if (hasBall) Icon(Icons.Filled.SportsFootball, null, Modifier.size(16.dp), tint = Color(0xFFB8BBFF))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TeamBadge(team, 64.dp)
                Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("$score", style = MaterialTheme.typography.displaySmall, color = scoreColor, fontWeight = if (won) FontWeight.Bold else FontWeight.Normal)
                Box(Modifier.height(18.dp)) { if (hasBall) Icon(Icons.Filled.SportsFootball, null, Modifier.size(16.dp), tint = c.primary) }
                Text("●".repeat(timeouts.coerceIn(0, 3)) + "○".repeat((3 - timeouts).coerceIn(0, 3)), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
    }
    val mid: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val qText = if (snap.over) "Final" else qLabel(snap.qtr)
            if (compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(qText, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (!snap.over) Text(v.clockText.value, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (ddText.isNotEmpty()) Text(ddText, style = MaterialTheme.typography.labelMedium, color = Color(0xFFCFD0FF))
                }
            } else {
                Text(qText, style = MaterialTheme.typography.titleLarge)
                if (!snap.over) Text(v.clockText.value, style = MaterialTheme.typography.headlineSmall)
                Text(ddText, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
    val away: @Composable () -> Unit = { side(sim.away, v.g.away, snap.scoreAway, snap.poss == 1 && snap.phase != "coin" && !snap.over, snap.toAway, true) }
    val home: @Composable () -> Unit = { side(sim.home, v.g.home, snap.scoreHome, snap.poss == 0 && snap.phase != "coin" && !snap.over, snap.toHome, false) }
    if (compact) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B1B22)).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) { away(); mid(); home() }
    } else {
        Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (v.live) LiveBadge("LIVE · SYNCED") else Tag("REPLAY")
                    Text(v.g.weekLabel + if (v.g.stage == "bowl") " · IFL Bowl" else "", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { away(); mid(); home() }
            }
        }
    }
}

@Composable
private fun PlayText(v: VState, compact: Boolean) {
    val i = v.idx.value
    val b = v.begun.value
    val prev = if (i > 0) v.sim.events[i - 1] else null
    val sitEv = if (b >= 0) v.sim.events[b] else prev
    val sit = sitEv?.let { qLabel(it.qtr) + " " + fmtClock(it.clockStart) + (if (it.preText.isNotEmpty()) " · ${it.preText}" else "") } ?: " "
    val text = prev?.text ?: if (v.live) "Waiting for the next play…" else "Press play to kick off."
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (compact) Color(0xFF1B1B22) else c.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 12.dp),
    ) {
        Text(sit, style = MaterialTheme.typography.labelMedium, color = if (compact) Color(0xFFCFD0FF) else c.onSurfaceVariant, maxLines = 1)
        Text(text, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, color = if (compact) Color.White else c.onSurface, maxLines = if (compact) 2 else 4)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Controls(v: VState, compact: Boolean) {
    if (v.live) return
    val done = v.done
    val etaText = v.etaText.value
    val light = if (compact) Color(0xFFE4E5FF) else Color.Unspecified
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { v.playPause() }, contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(if (done) Icons.Filled.Replay else if (v.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(if (done) "Replay" else if (v.paused) (if (v.idx.value == 0 && !v.started.value) "Play" else "Resume") else "Pause")
        }
        FilledTonalButton(onClick = { v.next() }, enabled = !done, contentPadding = PaddingValues(horizontal = 16.dp)) { Icon(Icons.Filled.SkipNext, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Next play") }
        SingleChoiceSegmentedButtonRow {
            val speeds = listOf(1, 2, 4, 10)
            speeds.forEachIndexed { i, sp -> SegmentedButton(selected = v.speed == sp, onClick = { v.speed = sp }, shape = SegmentedButtonDefaults.itemShape(i, speeds.size)) { Text("$sp×", maxLines = 1) } }
        }
        TextButton(onClick = { v.skipEnd() }, enabled = !done) { Icon(Icons.Filled.FastForward, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Skip to end", color = light) }
        Text(etaText, style = MaterialTheme.typography.labelMedium, color = if (compact) Color(0xFFCFD0FF) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WinProbCard(v: VState) {
    val sim = v.sim
    val count = v.idx.value
    FilledCard(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Win probability", style = MaterialTheme.typography.titleSmall)
            WinProbBar(v.g.away, v.g.home, v.wp(), "")
            val line = MaterialTheme.colorScheme.primary; val mid = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                drawLine(mid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                if (count == 0) return@Canvas
                var tmax = 3600f
                val pts = ArrayList<Pair<Float, Float>>()
                pts.add(0f to sim.wp0)
                var lastT = 0f
                for (k in 0 until count) {
                    val ev = sim.events[k]; val post = ev.post
                    val tg = if (post.qtr <= 4) (post.qtr - 1) * 900f + (900f - post.clock) else 3600f + (post.qtr - 5) * 600f + (600f - post.clock)
                    lastT = max(tg, lastT); pts.add(lastT to ev.wp)
                }
                tmax = max(tmax, lastT)
                val path = Path()
                pts.forEachIndexed { i, (t, p) ->
                    val x = t / tmax * size.width; val y = (1f - p) * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/* ---------------------------------------------------------------- the field */

private const val FW = 1200f
private const val FH = 533.3f
private fun fx(a: Float) = (a + 10f) * 10f
private fun fy(y: Float) = y * 10f

private class ScenePaints {
    private fun paint(size: Float, color: Int, bold: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    val num = paint(44f, 0xE6FFFFFF.toInt())
    val ez = paint(56f, 0xFFFFFFFF.toInt()).apply { letterSpacing = 0.08f }
    val tag = paint(13f, 0xFFFFFFFF.toInt())
    val down = paint(16f, 0xFF111111.toInt())
    val mid = paint(44f, 0xFF000000.toInt())
}

private fun visibleColor(c: Color): Color = if (c.red * 0.2126f + c.green * 0.7152f + c.blue * 0.0722f < 0.12f) Color.White else c

private fun DrawScope.text(txt: String, x: Float, cy: Float, p: Paint) {
    val fm = p.fontMetrics
    drawIntoCanvas { it.nativeCanvas.drawText(txt, x, cy - (fm.ascent + fm.descent) / 2f, p) }
}

private class BallPos(val x: Float, val y: Float, val h: Float, val ang: Float, val tag: String?)

private fun pathPos(ev: SimEvent, p: Float): BallPos {
    val tt = p.coerceIn(0f, 1f) * ev.segTotal
    var sg = ev.segs.last(); var lt = 1f
    for (s in ev.segs) { if (tt <= s.t0 + s.dur) { sg = s; lt = (tt - s.t0) / s.dur; break } }
    lt = lt.coerceIn(0f, 1f)
    val x = sg.ax + (sg.bx - sg.ax) * lt; val y = sg.ay + (sg.by - sg.ay) * lt
    val air = sg.m == "air" || sg.m == "kick"
    val h = if (air) (if (sg.h == 0f) 2f else sg.h) * 4f * lt * (1f - lt) else 0f
    val ang = (atan2(sg.by - sg.ay, sg.bx - sg.ax) * 180f / PI.toFloat())
    val k = ev.segs.indexOf(sg)
    val afterThrow = ev.airIdx >= 0 && (k > ev.airIdx || (k == ev.airIdx && lt >= 1f))
    val name = if (air && lt < 1f) null else if (afterThrow) ev.nameAfter else ev.nameBefore
    return BallPos(x, y, h, ang, if (p >= 1f) null else name)
}

private fun DrawScope.ball(x: Float, y: Float, h: Float, ang: Float) {
    val px = fx(x); val py = fy(y)
    drawOval(Color.Black.copy(alpha = 0.35f * (if (h > 0f) max(0.25f, 1f - h / 30f) else 1f)), Offset(px - 9f, py - 5f), Size(18f, 10f))
    withTransform({ translate(px, py - h * 2.2f); rotate(ang, Offset.Zero); scale(1f + h / 12f, 1f + h / 12f, Offset.Zero) }) {
        drawOval(Color(0xFF8B4A22), Offset(-9f, -5.6f), Size(18f, 11.2f))
        drawOval(Color(0xFF3D1D08), Offset(-9f, -5.6f), Size(18f, 11.2f), style = Stroke(1.2f))
        drawLine(Color.White, Offset(-4f, 0f), Offset(4f, 0f), 1.2f)
    }
}

private fun DrawScope.scene(v: VState, t: Float, grassA: Color, grassB: Color, paints: ScenePaints, logo: Painter?) {
    val sim = v.sim; val n = sim.events.size
    val idx = sim.completed(t)
    val prev = if (idx > 0) sim.events[idx - 1] else null
    val cur = if (idx < n && t >= sim.events[idx].start) sim.events[idx] else null
    val lt = if (cur != null) t - cur.start else 0f
    val p = if (cur != null && lt >= cur.preMs) (if (cur.actMs > 0f) ((lt - cur.preMs) / cur.actMs).coerceIn(0f, 1f) else 1f) else 0f
    val lineSnap: Snap? = when {
        cur != null -> if (cur.pre.phase == "play") cur.pre else null
        prev != null -> if (prev.post.phase == "play") prev.post else null
        else -> null
    }

    // turf
    for (i in 0 until 20) drawRect(if (i % 2 == 1) grassB else grassA, Offset(fx(i * 5f), 0f), Size(50f, FH))
    if (lineSnap != null && !lineSnap.over && lineSnap.ball >= 80f) drawRect(Color(0xFFFF2D2D).copy(alpha = 0.09f), Offset(if (lineSnap.attackRight) fx(80f) else fx(0f), 0f), Size(200f, FH))

    // end zones with the team names
    for (side in 0..1) {
        val team = if (side == 0) sim.home else sim.away
        val x0 = if (side == 0) 0f else 1100f
        drawRect(team.c1, Offset(x0, 0f), Size(100f, FH))
        drawRect(team.c2.copy(alpha = 0.9f), Offset(if (side == 0) x0 + 88f else x0, 0f), Size(12f, FH))
        val cx = if (side == 0) x0 + 44f else x0 + 56f
        paints.ez.color = readableOn(team.c1).toArgb()
        val name = team.name.uppercase()
        paints.ez.textSize = 56f
        val w = paints.ez.measureText(name)
        if (w > 480f) paints.ez.textSize = 56f * 480f / w
        rotate(if (side == 0) -90f else 90f, Offset(cx, FH / 2)) { text(name, cx, FH / 2, paints.ez) }
    }

    // markings
    val white = Color.White.copy(alpha = 0.9f)
    var yd = 0
    while (yd <= 100) { drawLine(white, Offset(fx(yd.toFloat()), 0f), Offset(fx(yd.toFloat()), FH), if (yd == 0 || yd == 100) 4f else 2f); yd += 5 }
    drawRect(white, Offset(1f, 1f), Size(1198f, 531.3f), style = Stroke(4f))
    for (y in 1..99) {
        if (y % 5 == 0) continue
        for (hy in floatArrayOf(fy(0.6f), fy(23.58f), fy(29.05f), fy(52f))) drawLine(white, Offset(fx(y.toFloat()), hy), Offset(fx(y.toFloat()), hy + 7f), 1.6f)
    }
    var num = 10
    while (num <= 90) {
        val label = (if (num <= 50) num else 100 - num).toString()
        text(label, fx(num.toFloat()), fy(12f) - 10f, paints.num)
        rotate(180f, Offset(fx(num.toFloat()), fy(41.3f) + 10f)) { text(label, fx(num.toFloat()), fy(41.3f) + 10f, paints.num) }
        num += 10
    }
    drawCircle(white, 74f, Offset(fx(50f), fy(26.65f)))
    drawCircle(sim.home.c1, 74f, Offset(fx(50f), fy(26.65f)), style = Stroke(5f))
    if (logo == null) { paints.mid.color = sim.home.c1.toArgb(); text(sim.home.abbr, fx(50f), fy(26.65f), paints.mid) }
    else {
        // the home logo is part of the turf, so it is drawn here, before the players and the ball
        val box = 116f
        val iw = logo.intrinsicSize
        val k = if (iw.isSpecified && iw.width > 0f && iw.height > 0f) min(box / iw.width, box / iw.height) else 0f
        val w = if (k > 0f) iw.width * k else box
        val h = if (k > 0f) iw.height * k else box
        translate(fx(50f) - w / 2f, fy(26.65f) - h / 2f) { with(logo) { draw(Size(w, h)) } }
    }

    // route trail of the current drive
    val trailFrom = if (cur != null) cur.trailFrom else prev?.trailFrom ?: 0
    for (k in trailFrom until idx) {
        val ev = sim.events[k]
        if (ev.path.size < 2) continue
        val col = visibleColor(if (ev.pre.poss == 0) sim.home.c1 else sim.away.c1).copy(alpha = 0.85f)
        for (s in ev.segs) {
            val air = s.m == "air" || s.m == "kick"
            drawLine(col, Offset(fx(s.ax), fy(s.ay)), Offset(fx(s.bx), fy(s.by)), if (air) 2.5f else 4f, StrokeCap.Round, pathEffect = if (air) PathEffect.dashPathEffect(floatArrayOf(7f, 6f)) else null)
        }
    }

    // line of scrimmage, first-down line and the chain crew
    if (lineSnap != null && !lineSnap.over) {
        val gtg = lineSnap.ball + lineSnap.togo >= 100f
        val xa = fx(lineSnap.ballAbs); val xb = fx(lineSnap.fdAbs)
        drawRect(Color(0xFF2F7BFF).copy(alpha = 0.9f), Offset(xa - 2f, 0f), Size(4f, FH))
        if (!gtg) drawRect(Color(0xFFFFD400).copy(alpha = 0.95f), Offset(xb - 2f, 0f), Size(4f, FH))
        val orange = Color(0xFFFF8A00)
        drawLine(orange, Offset(xa, 9f), Offset(if (gtg) xa else xb, 9f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
        drawRoundRect(orange, Offset(xa - 2.5f, 1f), Size(5f, 18f), CornerRadius(1.5f)); drawRoundRect(Color.Black, Offset(xa - 2.5f, 1f), Size(5f, 18f), CornerRadius(1.5f), style = Stroke(0.8f))
        if (!gtg) { drawRoundRect(orange, Offset(xb - 2.5f, 1f), Size(5f, 18f), CornerRadius(1.5f)); drawRoundRect(Color.Black, Offset(xb - 2.5f, 1f), Size(5f, 18f), CornerRadius(1.5f), style = Stroke(0.8f)) }
        drawRoundRect(orange, Offset(xa - 11f, 1f), Size(22f, 20f), CornerRadius(3f)); drawRoundRect(Color.Black, Offset(xa - 11f, 1f), Size(22f, 20f), CornerRadius(3f), style = Stroke(0.8f))
        text("${lineSnap.down}", xa, 11f, paints.down)
    }

    // penalty flag
    val flagEv = if (cur != null) (if (cur.flagAbs != null && p > 0.35f) cur else null) else prev?.takeIf { it.flagAbs != null }
    val flagAbs = flagEv?.flagAbs
    if (flagEv != null && flagAbs != null) {
        translate(fx(flagAbs), fy(flagEv.flagY.coerceIn(6f, 53f) - 3f).coerceIn(30f, 500f)) {
            val flag = Path().apply { moveTo(0f, 0f); lineTo(18f, -6f); lineTo(0f, -12f); close() }
            drawPath(flag, Color(0xFFF5C400)); drawPath(flag, Color(0xFF8A6D00), style = Stroke(1.2f))
            drawLine(Color(0xFF8A6D00), Offset(0f, 0f), Offset(0f, 10f), 2f)
        }
    }

    // the ball (and the name of whoever has it)
    fun resting(s: Snap) { if (s.phase == "kickoff" || s.phase == "freekick") ball(s.kickAbs, 26.65f, 0f, 90f) else ball(s.ballAbs, s.ballY, 0f, 0f) }
    var tag: BallPos? = null
    if (cur != null) {
        if (cur.segs.isNotEmpty()) {
            if (lt < cur.preMs) ball(cur.path[0].x, cur.path[0].y, 0f, 0f)
            else { val bp = pathPos(cur, p); ball(bp.x, bp.y, bp.h, bp.ang); tag = bp }
        } else resting(cur.pre)
    } else if (prev == null) ball(50f, 26.65f, 0f, 90f)
    else {
        val post = prev.post
        if (post.phase in listOf("play", "pat", "kickoff", "freekick") || prev.segs.isEmpty()) resting(post)
        else { val bp = pathPos(prev, 1f); ball(bp.x, bp.y, 0f, bp.ang) }
    }
    val tg = tag
    val tagName = tg?.tag
    if (tg != null && tagName != null) {
        val w = tagName.length * 8f + 14f
        val ty = max(4f, fy(tg.y) - 42f)
        drawRoundRect(Color.Black.copy(alpha = 0.78f), Offset(fx(tg.x) - w / 2f, ty), Size(w, 22f), CornerRadius(3f))
        text(tagName, fx(tg.x), ty + 11f, paints.tag)
    }
}

@Composable
private fun FieldBox(v: VState, modifier: Modifier) {
    val dark = isDark(LocalSettings.current.theme)
    val ga = if (dark) Color(0xFF2C6C31) else Color(0xFF3A8A3F)
    val gb = if (dark) Color(0xFF28632C) else Color(0xFF347D39)
    val paints = remember { ScenePaints() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val logoUrl = v.g.home.logoUrl
    // the logo is loaded as a painter and drawn inside the canvas (under the players and ball), not as a view on top of it
    val painter = if (logoUrl != null) rememberAsyncImagePainter(remember(logoUrl) { Logos.request(ctx, logoUrl, true) }) else null
    val logo = painter?.takeIf { it.state is AsyncImagePainter.State.Success }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val t = v.vt.floatValue                       // read while drawing, so only the canvas redraws each frame
                val sc = min(size.width / FW, size.height / FH)
                translate((size.width - FW * sc) / 2f, (size.height - FH * sc) / 2f) { scale(sc, sc, Offset.Zero) { scene(v, t, ga, gb, paints, logo) } }
            }
        }
        // big-play banner
        val ban = v.banner.value
        if (ban != null) {
            val good = ban.tags.any { it == "TD" || it == "FG" }; val bad = ban.tags.any { it == "TO" || it == "FLAG" }
            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp), shape = RoundedCornerShape(50),
                color = if (good) Color(0xFF1A7F37) else if (bad) Color(0xFFB3261E) else Color(0xCC000000), contentColor = Color.White,
            ) { Text(ban.banner ?: "", Modifier.padding(horizontal = 18.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold) }
        }
        // final
        if (v.done) {
            val sim = v.sim
            val w = if (sim.finalHome > sim.finalAway) sim.home else if (sim.finalAway > sim.finalHome) sim.away else null
            Column(Modifier.fillMaxSize().background(Color(0xBD000000)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (w != null) "${w.full} win" else "Final — tie", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("${sim.away.abbr} ${sim.finalAway} – ${sim.finalHome} ${sim.home.abbr}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (!v.live) Button(onClick = { v.restart() }, Modifier.padding(top = 8.dp)) { Icon(Icons.Filled.Replay, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Watch again") }
            }
        }
        IconButton(
            onClick = { v.fs = !v.fs }, Modifier.align(Alignment.TopEnd).padding(4.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x8C000000), contentColor = Color.White),
        ) { Icon(if (v.fs) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (v.fs) "Exit fullscreen" else "Fullscreen") }
    }
}
