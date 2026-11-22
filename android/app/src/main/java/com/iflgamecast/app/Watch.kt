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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
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
import androidx.compose.runtime.key
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

    /** How long (broadcast ms) a play's banner or score celebration stays up; shorter when replaying fast. */
    fun holdMs(ev: SimEvent): Float {
        val base = if (ev.scoring != null && ev.banner != null) 3400f else 1900f
        return if (live) base else base / max(1f, speed / 2f)
    }

    val banner: State<SimEvent?> = derivedStateOf {
        val i = idx.value
        if (i == 0) null else {
            val ev = sim.events[i - 1]
            if (ev.banner != null && ev.banner != "KICKOFF" && vt.floatValue - ev.endAct < holdMs(ev)) ev else null
        }
    }

    /** The final-score panel: once the game is over and the last play's banner has finished (or the clock has run out). */
    val finalShown: State<Boolean> = derivedStateOf { idx.value >= n && (banner.value == null || vt.floatValue >= sim.totalMs - 1f) }

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
    // (a paused replay doesn't tick at all: the loop below is only running while there is something to animate)
    LaunchedEffect(v, v.paused) {
        if (!v.live && v.paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (v.live) v.vt.floatValue = (System.currentTimeMillis() + rd.offset - rd.airStart).toFloat().coerceIn(0f, v.sim.totalMs)
                else if (last != 0L) {
                    v.vt.floatValue = min(v.sim.totalMs, v.vt.floatValue + min((now - last) / 1_000_000f, 100f) * v.speed)
                    if (v.vt.floatValue >= v.sim.totalMs) v.paused = true       // reached the end: stop ticking
                }
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
        FullscreenViewer(v)
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
            item { ScoreBar(v) }
            item { FieldBox(v, Modifier.fillMaxWidth().aspectRatio(FW / FH)) }
            item { PlayText(v) }
            item { Controls(v) }
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

private fun ddTextOf(v: VState, snap: Snap): String = when {
    snap.over -> if (snap.qtr >= 5) "Overtime" else ""
    snap.phase == "play" -> v.dd()
    snap.phase == "kickoff" || snap.phase == "freekick" -> "Kickoff"
    snap.phase == "pat" -> "Extra point"
    else -> ""
}

@Composable
private fun ScoreBar(v: VState) {
    val c = MaterialTheme.colorScheme
    val snap = v.snapshot()
    val sim = v.sim
    val ddText = ddTextOf(v, snap)
    @Composable
    fun side(t: SimTeam, team: Team, score: Int, hasBall: Boolean, timeouts: Int, away: Boolean) {
        val lost = snap.over && ((away && snap.scoreAway < snap.scoreHome) || (!away && snap.scoreHome < snap.scoreAway))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TeamBadge(team, 64.dp)
            Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("$score", style = MaterialTheme.typography.displaySmall, color = if (lost) c.onSurfaceVariant else c.onSurface)
            Box(Modifier.height(18.dp)) { if (hasBall) Icon(Icons.Filled.SportsFootball, null, Modifier.size(16.dp), tint = c.primary) }
            Text("●".repeat(timeouts.coerceIn(0, 3)) + "○".repeat((3 - timeouts).coerceIn(0, 3)), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
        }
    }
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (v.live) LiveBadge("LIVE · SYNCED") else Tag("REPLAY")
                Text(v.g.weekLabel + if (v.g.stage == "bowl") " · IFL Bowl" else "", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                side(sim.away, v.g.away, snap.scoreAway, snap.poss == 1 && snap.phase != "coin" && !snap.over, snap.toAway, true)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (snap.over) "Final" else qLabel(snap.qtr), style = MaterialTheme.typography.titleLarge)
                    if (!snap.over) Text(v.clockText.value, style = MaterialTheme.typography.headlineSmall)
                    Text(ddText, style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                side(sim.home, v.g.home, snap.scoreHome, snap.poss == 0 && snap.phase != "coin" && !snap.over, snap.toHome, false)
            }
        }
    }
}

@Composable
private fun PlayText(v: VState) {
    val i = v.idx.value
    val b = v.begun.value
    val prev = if (i > 0) v.sim.events[i - 1] else null
    val sitEv = if (b >= 0) v.sim.events[b] else prev
    val sit = sitEv?.let { qLabel(it.qtr) + " " + fmtClock(it.clockStart) + (if (it.preText.isNotEmpty()) " · ${it.preText}" else "") } ?: " "
    val text = prev?.text ?: if (v.live) "Waiting for the next play…" else "Press play to kick off."
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(CardShape).background(c.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Text(sit, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, maxLines = 1)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.onSurface, maxLines = 4)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Controls(v: VState) {
    if (v.live) return
    val done = v.done
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppButton(onClick = { v.playPause() }, contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(if (done) Icons.Filled.Replay else if (v.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(if (done) "Replay" else if (v.paused) (if (v.idx.value == 0 && !v.started.value) "Play" else "Resume") else "Pause")
        }
        AppTonalButton(onClick = { v.next() }, enabled = !done, contentPadding = PaddingValues(horizontal = 16.dp)) { Icon(Icons.Filled.SkipNext, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Next play") }
        SingleChoiceSegmentedButtonRow {
            val speeds = listOf(1, 2, 4, 10)
            speeds.forEachIndexed { i, sp -> SegmentedButton(selected = v.speed == sp, onClick = { v.speed = sp }, shape = SegmentedButtonDefaults.itemShape(i, speeds.size)) { Text("$sp×", maxLines = 1) } }
        }
        TextButton(onClick = { v.skipEnd() }, enabled = !done) { Icon(Icons.Filled.FastForward, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Skip to end") }
        Text(v.etaText.value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ---------------------------------------------------------------- fullscreen */

private val HudBg = Color(0xB3000000)

/** Broadcast-style score bug: both teams, scores, quarter / clock and down & distance in one small pill. */
@Composable
private fun ScoreBug(v: VState, modifier: Modifier = Modifier) {
    val snap = v.snapshot()
    val sim = v.sim
    val dd = ddTextOf(v, snap)
    @Composable
    fun team(t: SimTeam, team: Team, score: Int, hasBall: Boolean, away: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (away) TeamBadge(team, 24.dp)
            Column(horizontalAlignment = if (away) Alignment.Start else Alignment.End) {
                Text(t.abbr, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasBall && away) Icon(Icons.Filled.SportsFootball, null, Modifier.size(12.dp).padding(end = 2.dp), tint = Color.White)
                    Text("$score", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    if (hasBall && !away) Icon(Icons.Filled.SportsFootball, null, Modifier.size(12.dp).padding(start = 2.dp), tint = Color.White)
                }
            }
            if (!away) TeamBadge(team, 24.dp)
        }
    }
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(HudBg).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        team(sim.away, v.g.away, snap.scoreAway, snap.poss == 1 && snap.phase != "coin" && !snap.over, true)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (v.live) Box(Modifier.size(6.dp).clip(CircleShape).background(LiveRed))
                Text(if (snap.over) "FINAL" else qLabel(snap.qtr) + "  " + v.clockText.value, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            }
            if (dd.isNotEmpty()) Text(dd, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        }
        team(sim.home, v.g.home, snap.scoreHome, snap.poss == 0 && snap.phase != "coin" && !snap.over, false)
    }
}

/** The latest play as a caption at the bottom of the field. */
@Composable
private fun PlayCaption(v: VState, modifier: Modifier = Modifier) {
    val i = v.idx.value
    val b = v.begun.value
    val prev = if (i > 0) v.sim.events[i - 1] else null
    val sitEv = if (b >= 0) v.sim.events[b] else prev
    val sit = sitEv?.let { qLabel(it.qtr) + " " + fmtClock(it.clockStart) + (if (it.preText.isNotEmpty()) " · ${it.preText}" else "") } ?: ""
    val text = prev?.text ?: if (v.live) "Waiting for the next play…" else "Press play to kick off."
    Column(modifier.clip(RoundedCornerShape(6.dp)).background(HudBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (sit.isNotEmpty()) Text(sit, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HudButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

/**
 * Fullscreen: the field fills the screen and everything else floats over it. The score bug and the latest play are always
 * there; the controls fade away a few seconds after you stop touching the screen and come back with a tap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullscreenViewer(v: VState) {
    var controls by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }       // bumped on every interaction so the hide timer starts over
    LaunchedEffect(controls, touch, v.paused, v.live) {
        if (controls && (v.live || !v.paused)) { delay(3500); controls = false }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FieldBox(
            v, Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls; touch++ },
            fullscreen = true,
        )
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp, vertical = 10.dp)) {
            ScoreBug(v, Modifier.align(Alignment.TopCenter))
            AnimatedVisibility(controls, Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.clip(CircleShape).background(HudBg)) { HudButton(Icons.Filled.FullscreenExit, "Exit fullscreen") { v.fs = false } }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCaption(v, Modifier.weight(1f))
                if (!v.live) AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
                    Row(Modifier.clip(RoundedCornerShape(24.dp)).background(HudBg).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        HudButton(if (v.done) Icons.Filled.Replay else if (v.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (v.paused) "Play" else "Pause") { v.playPause(); touch++ }
                        HudButton(Icons.Filled.SkipNext, "Next play", enabled = !v.done) { v.next(); touch++ }
                        TextButton(onClick = { v.speed = when (v.speed) { 1 -> 2; 2 -> 4; 4 -> 10; else -> 1 }; touch++ }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("${v.speed}×", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        }
                        HudButton(Icons.Filled.FastForward, "Skip to end", enabled = !v.done) { v.skipEnd(); touch++ }
                    }
                }
            }
        }
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
private fun FieldBox(v: VState, modifier: Modifier, fullscreen: Boolean = false) {
    val dark = isDark(LocalSettings.current.theme)
    val ga = if (dark) Color(0xFF2C6C31) else Color(0xFF3A8A3F)
    val gb = if (dark) Color(0xFF28632C) else Color(0xFF347D39)
    val paints = remember { ScenePaints() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val logoUrl = v.g.home.logoUrl
    // the logo is loaded as a painter and drawn inside the canvas (under the players and ball), not as a view on top of it
    val painter = if (logoUrl != null) rememberAsyncImagePainter(remember(logoUrl) { Logos.request(ctx, logoUrl, true) }) else null
    val logo = painter?.takeIf { it.state is AsyncImagePainter.State.Success }
    Box(modifier.then(if (fullscreen) Modifier else Modifier.clip(RoundedCornerShape(6.dp))).background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val t = v.vt.floatValue                       // read while drawing, so only the canvas redraws each frame
            val sc = min(size.width / FW, size.height / FH)
            translate((size.width - FW * sc) / 2f, (size.height - FH * sc) / 2f) { scale(sc, sc, Offset.Zero) { scene(v, t, ga, gb, paints, logo) } }
        }
        // big plays: scores get a full celebration with the scoring team's logo, other calls a small banner
        val ban = v.banner.value
        if (ban != null) key(ban) {
            if (ban.scoring != null && ban.banner != null) ScoreCelebration(v, ban, Modifier.fillMaxSize())
            else BannerPill(v, ban, Modifier.align(Alignment.TopCenter).padding(top = if (fullscreen) 60.dp else 12.dp))
        }
        // final whistle: the winner's logo and the score
        AnimatedVisibility(v.finalShown.value, enter = fadeIn(tween(350)) + scaleIn(tween(350), 0.92f), exit = fadeOut()) {
            val sim = v.sim
            val homeWon = sim.finalHome > sim.finalAway; val awayWon = sim.finalAway > sim.finalHome
            val w = if (homeWon) sim.home else if (awayWon) sim.away else null
            Column(Modifier.fillMaxSize().background(Color(0xCC000000)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (homeWon || awayWon) TeamBadge(if (homeWon) v.g.home else v.g.away, if (fullscreen) 56.dp else 64.dp)
                Text(if (w != null) "${w.full} win" else "Final — tie", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("${sim.away.abbr} ${sim.finalAway} – ${sim.finalHome} ${sim.home.abbr}", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (!v.live) AppButton(onClick = { v.restart() }, Modifier.padding(top = 8.dp)) { Icon(Icons.Filled.Replay, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Watch again") }
            }
        }
        if (!fullscreen) IconButton(
            onClick = { v.fs = true }, Modifier.align(Alignment.TopEnd).padding(4.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x8C000000), contentColor = Color.White),
        ) { Icon(Icons.Filled.Fullscreen, "Fullscreen") }
    }
}

/* ---------------------------------------------------------------- score celebrations */

// Everything below is a pure function of the playhead (v.vt), so it plays correctly at any speed, pauses with the game and
// can be scrubbed: progress runs 0..1 over the play's hold time, starting when the play's action ends.

internal fun easeOutCubic(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1f - u * u * u }
internal fun easeOutBack(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val u = t.coerceIn(0f, 1f) - 1f; return 1f + c3 * u * u * u + c1 * u * u }
internal fun segment(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)
internal fun fadeOut(p: Float): Float = 1f - segment(p, 0.84f, 1f)

private class Bit(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float)

private fun confettiFor(seed: Int, colors: List<Color>): List<Bit> {
    val r = kotlin.random.Random(seed)
    return List(48) { Bit(r.nextFloat() * 2f * PI.toFloat(), 0.35f + r.nextFloat() * 0.8f, 0.02f + r.nextFloat() * 0.025f, colors[r.nextInt(colors.size)], (r.nextFloat() - 0.5f) * 720f) }
}

/** Keeps a team colour readable against the band behind it. */
private fun accentOn(c: Color, band: Color): Color {
    val lc = c.red * 0.2126f + c.green * 0.7152f + c.blue * 0.0722f
    val lb = band.red * 0.2126f + band.green * 0.7152f + band.blue * 0.0722f
    return if (kotlin.math.abs(lc - lb) < 0.25f) (if (lb > 0.5f) Color(0xFF111111) else Color.White) else c
}

/**
 * Touchdown / field goal / safety / two-point graphic: the picture dims, a band in the scoring team's colours wipes across the field,
 * the team's logo pops in with a spin and a ring pulse, the call and the new score slide out beside it and confetti bursts from the logo.
 */
@Composable
private fun ScoreCelebration(v: VState, ev: SimEvent, modifier: Modifier = Modifier) {
    val homeScored = ev.scoring == 0
    val tm = if (homeScored) v.sim.home else v.sim.away
    val team = if (homeScored) v.g.home else v.g.away
    val hold = v.holdMs(ev)
    val prog = { ((v.vt.floatValue - ev.endAct) / hold).coerceIn(0f, 1f) }
    val band = tm.c1
    val onBand = readableOn(band)
    val accent = accentOn(tm.c2, band)
    val confetti = remember(ev) { confettiFor(ev.start.toInt(), listOf(tm.c1, tm.c2, Color.White, Color(0xFFFFD54A))) }
    val call = (ev.banner ?: "SCORE").uppercase()
    val line = "${v.sim.away.abbr} ${ev.post.scoreAway} – ${ev.post.scoreHome} ${v.sim.home.abbr}"
    BoxWithConstraints(modifier) {
        val h = maxHeight
        val bandH = (h * 0.5f).coerceIn(56.dp, 150.dp)
        val logo = bandH * 0.9f
        val fontPx = with(LocalDensity.current) { (bandH * 0.36f).toSp() }
        // each half of the band must hold its text on one line: shrink long calls ("KICK RETURN TD") and long score lines to fit
        val half = (maxWidth - logo).value / 2f - 14f
        val fit = (half / (call.length * bandH.value * 0.36f * 0.56f)).coerceIn(0.4f, 1f)
        val fitLine = (half / (line.length * bandH.value * 0.36f * 0.62f * 0.56f)).coerceIn(0.4f, 1f)

        // dim the field
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f * fadeOut(prog()) }.background(Color.Black))
        // team-colour band, wiping out from the middle
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(bandH)
                .graphicsLayer { val p = prog(); scaleY = easeOutCubic(segment(p, 0f, 0.12f)); alpha = fadeOut(p) }
                .background(band)
                .drawBehind { drawRect(accent, Offset.Zero, Size(size.width, 3.dp.toPx())); drawRect(accent, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) },
        )
        // ring pulses and confetti
        Canvas(Modifier.fillMaxSize()) {
            val p = prog()
            val c = Offset(size.width / 2f, size.height / 2f)
            val a = fadeOut(p)
            for (k in 0..1) {
                val s = segment(p, 0.03f + k * 0.12f, 0.40f + k * 0.12f)
                if (s > 0f && s < 1f) drawCircle(accent.copy(alpha = 0.8f * (1f - s) * a), size.height * (0.12f + 0.8f * easeOutCubic(s)), c, style = Stroke(3.dp.toPx()))
            }
            val fly = easeOutCubic(segment(p, 0.02f, 0.6f))
            val fall = segment(p, 0.15f, 1f)
            for (b in confetti) {
                val r = b.speed * size.height * 0.95f * fly
                val x = c.x + kotlin.math.cos(b.angle) * r * 1.7f
                val y = c.y + kotlin.math.sin(b.angle) * r + size.height * 0.55f * fall * fall
                val alpha = (1f - segment(p, 0.55f, 0.92f)) * (if (p < 0.02f) 0f else 1f)
                if (alpha <= 0f) continue
                val s = b.size * size.height
                rotate(b.spin * p, Offset(x, y)) { drawRect(b.color.copy(alpha = alpha), Offset(x - s / 2f, y - s * 0.3f), Size(s, s * 0.6f)) }
            }
        }
        // call on the left, logo in the middle, score on the right
        Row(Modifier.align(Alignment.Center).fillMaxWidth().height(bandH), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(end = 10.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    call, maxLines = 1, softWrap = false, color = onBand, style = MaterialTheme.typography.headlineMedium, fontSize = fontPx * fit,
                    modifier = Modifier.graphicsLayer { val p = prog(); val s = easeOutCubic(segment(p, 0.08f, 0.30f)); translationX = -(1f - s) * 70.dp.toPx(); alpha = s * fadeOut(p) },
                )
            }
            Box(
                Modifier.size(logo).graphicsLayer {
                    val p = prog()
                    val pop = easeOutBack(segment(p, 0.02f, 0.24f)) * (1f + 0.03f * kotlin.math.sin(p * 18.85f))
                    scaleX = pop; scaleY = pop
                    rotationZ = -(1f - easeOutCubic(segment(p, 0.02f, 0.32f))) * 200f
                    alpha = fadeOut(p)
                },
                contentAlignment = Alignment.Center,
            ) { TeamBadge(team, logo) }
            Column(
                Modifier.weight(1f).padding(start = 10.dp)
                    .graphicsLayer { val p = prog(); val s = easeOutCubic(segment(p, 0.12f, 0.34f)); translationX = (1f - s) * 70.dp.toPx(); alpha = s * fadeOut(p) },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(line, maxLines = 1, softWrap = false, color = onBand, style = MaterialTheme.typography.titleLarge, fontSize = fontPx * 0.62f * fitLine)
                Text(tm.full, maxLines = 1, color = onBand.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A turnover, penalty, quarter break and the like: a small banner that drops in from the top and fades out. */
@Composable
private fun BannerPill(v: VState, ev: SimEvent, modifier: Modifier = Modifier) {
    val hold = v.holdMs(ev)
    val bad = ev.tags.any { it == "TO" || it == "FLAG" }
    Surface(
        modifier.graphicsLayer {
            val p = ((v.vt.floatValue - ev.endAct) / hold).coerceIn(0f, 1f)
            val s = easeOutCubic(segment(p, 0f, 0.14f))
            translationY = -(1f - s) * 40.dp.toPx(); alpha = s * fadeOut(p)
        },
        shape = RoundedCornerShape(6.dp), color = if (bad) Color(0xFFB3261E) else Color(0xE6000000), contentColor = Color.White,
    ) { Text((ev.banner ?: "").uppercase(), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium) }
}
