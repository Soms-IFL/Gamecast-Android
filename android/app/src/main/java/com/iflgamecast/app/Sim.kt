package com.iflgamecast.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import app.cash.quickjs.QuickJs
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max

/**
 * Runs the IFL game engine (the same JavaScript the website uses, bundled in assets/sim) inside an embedded QuickJS interpreter.
 * Only JSON crosses the boundary. A fresh interpreter is made per call (the viewer and the odds use separate locks, so they can run side by side).
 */
object SimEngine {
    private val simLock = Any()
    private val oddsLock = Any()

    private fun asset(ctx: Context, name: String): String = ctx.assets.open("sim/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun run(lock: Any, ctx: Context, call: String): String = synchronized(lock) {
        val js = QuickJs.create()
        try {
            js.evaluate(asset(ctx, "engine.js"), "engine.js")
            js.evaluate(asset(ctx, "glue.js"), "glue.js")
            js.evaluate(call, "call.js") as String
        } finally {
            js.close()
        }
    }

    fun simulate(ctx: Context, args: JSONObject): String = run(simLock, ctx, "simulate(" + JSONObject.quote(args.toString()) + ")")
    fun odds(ctx: Context, input: JSONObject): String = run(oddsLock, ctx, "odds(" + JSONObject.quote(input.toString()) + ")")
}

/** A snapshot of the game state (before or after a play). Field units: yards along the field (-10..110 including end zones). */
class Snap(
    val qtr: Int, val clock: Float, val poss: Int, val ball: Float, val ballAbs: Float, val ballY: Float,
    val down: Int, val togo: Int, val fdAbs: Float, val toHome: Int, val toAway: Int, val scoreHome: Int, val scoreAway: Int,
    val phase: String, val over: Boolean, val attackRight: Boolean, val kickAbs: Float,
)

class PathPt(val x: Float, val y: Float, val m: String, val h: Float)
class Seg(val ax: Float, val ay: Float, val bx: Float, val by: Float, val m: String, val h: Float, val t0: Float, val dur: Float)

class SimTeam(val id: String, val abbr: String, val name: String, val city: String, val full: String, val c1: Color, val c2: Color)

class SimEvent(
    val kind: String, val text: String, val banner: String?, val tags: List<String>, val preText: String,
    val qtr: Int, val clockStart: Float, val snapClock: Float, val scoring: Int?,
    val flagAbs: Float?, val flagY: Float,
    val path: List<PathPt>, val pre: Snap, val post: Snap, val dd: String, val wp: Float,
    // broadcast timeline (ms)
    val start: Float, val preMs: Float, val actMs: Float, val endAct: Float,
) {
    var segs: List<Seg> = emptyList()
    var segTotal: Float = 0f
    var nameBefore: String? = null
    var nameAfter: String? = null
    var airIdx: Int = -1
    var trailFrom: Int = 0       // first event whose route is still on the field when this event begins
}

class SimData(
    val home: SimTeam, val away: SimTeam, val events: List<SimEvent>, val snap0: Snap, val wp0: Float, val dd0: String,
    val finalHome: Int, val finalAway: Int, val totalMs: Float,
) {
    /** Number of plays whose action has finished at broadcast time [t] (ms). */
    fun completed(t: Float): Int {
        var lo = 0
        var hi = events.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (events[mid].endAct <= t) lo = mid + 1 else hi = mid }
        return lo
    }
}

private fun snapOf(o: JSONObject): Snap {
    val to = o.a("to"); val sc = o.a("score")
    return Snap(
        qtr = o.i("qtr", 1), clock = o.d("clock").toFloat(), poss = o.i("poss"), ball = o.d("ball").toFloat(), ballAbs = o.d("ballAbs", 50.0).toFloat(),
        ballY = o.d("ballY", 26.65).toFloat(), down = o.i("down"), togo = o.i("togo"), fdAbs = o.d("fdAbs").toFloat(),
        toHome = to.optInt(0, 3), toAway = to.optInt(1, 3), scoreHome = sc.optInt(0, 0), scoreAway = sc.optInt(1, 0),
        phase = o.s("phase"), over = o.b("over"), attackRight = o.b("attackRight"), kickAbs = o.d("kickAbs", 35.0).toFloat(),
    )
}

private val NAME = Regex("[A-Z]\\. [A-Z][A-Za-z'-]+")
private val PICK = Regex("INTERCEPTED by ([A-Z]\\. [A-Z][A-Za-z'-]+)")
private val PASS_FAIL = Regex("incomplete|thrown away|spiked")
private val COIN = Regex("coin toss")

fun parseSim(json: String): SimData {
    val o = JSONObject(json)
    val teams = o.a("teams").mapObjs {
        val cols = it.a("colors").strings()
        SimTeam(it.s("id"), it.s("abbr"), it.s("name"), it.s("city"), it.s("full"), parseColor(cols.getOrNull(0), Color.Gray), parseColor(cols.getOrNull(1), Color.White))
    }
    val tl = o.a("tl")
    val evs = ArrayList<SimEvent>()
    val raw = o.a("events")
    var lastPoss = -1
    for (n in 0 until raw.length()) {
        val e = raw.getJSONObject(n)
        val t = tl.getJSONObject(n)
        val path = e.a("path").mapObjs { PathPt(it.d("x").toFloat(), it.d("y").toFloat(), it.s("m"), it.d("h").toFloat()) }
        val pre = snapOf(e.getJSONObject("pre"))
        val post = snapOf(e.getJSONObject("post"))
        val start = t.d("start").toFloat(); val preMs = t.d("pre").toFloat(); val actMs = t.d("act").toFloat()
        val fl = e.o("flag")
        val ev = SimEvent(
            kind = e.s("kind"), text = e.s("text"), banner = e.sn("banner"), tags = e.a("tags").strings(), preText = e.s("preText"),
            qtr = e.i("qtr", 1), clockStart = e.d("clockStart").toFloat(), snapClock = e.d("snapClock").toFloat(),
            scoring = if (e.isNull("scoring")) null else e.optInt("scoring"),
            flagAbs = fl?.d("abs")?.toFloat(), flagY = fl?.d("y", 26.65)?.toFloat() ?: 26.65f,
            path = path, pre = pre, post = post, dd = e.s("dd"), wp = e.d("wp", 0.5).toFloat(),
            start = start, preMs = preMs, actMs = actMs, endAct = start + preMs + actMs,
        )
        // route animation segments (same pacing as the website)
        val segs = ArrayList<Seg>(); var total = 0f
        for (k in 1 until path.size) {
            val a = path[k - 1]; val b = path[k]
            val v = if (b.m == "kick") 30f else if (b.m == "air") 22f else 8.5f
            val dur = max(0.18f, hypot(b.x - a.x, b.y - a.y) / v) + (if (b.m == "kick") 0.25f else 0f)
            segs.add(Seg(a.x, a.y, b.x, b.y, b.m, b.h, total, dur)); total += dur
        }
        ev.segs = segs; ev.segTotal = total
        ev.airIdx = segs.indexOfFirst { it.m == "air" || it.m == "kick" }
        // name tag on the ball carrier
        val names = NAME.findAll(ev.text).map { it.value }.toList()
        val pick = PICK.find(ev.text)?.groupValues?.get(1)
        val passFail = PASS_FAIL.containsMatchIn(ev.text)
        ev.nameBefore = if (ev.kind in listOf("kickoff", "punt", "fg", "pat")) null else names.getOrNull(0)
        ev.nameAfter = pick ?: (if (passFail) null else names.getOrNull(1))
        // when the route trail starts over
        val clears = ev.kind == "period" || (ev.kind == "info" && COIN.containsMatchIn(ev.text)) ||
            pre.phase == "kickoff" || pre.phase == "freekick" || (lastPoss >= 0 && pre.phase == "play" && pre.poss != lastPoss)
        ev.trailFrom = if (clears || n == 0) n else evs[n - 1].trailFrom
        if (post.phase == "play") lastPoss = post.poss
        evs.add(ev)
    }
    val last = evs.lastOrNull()
    val lastTl = if (tl.length() > 0) tl.getJSONObject(tl.length() - 1) else null
    val sc = o.a("score")
    return SimData(
        home = teams[0], away = teams[1], events = evs, snap0 = snapOf(o.getJSONObject("snap0")), wp0 = o.d("wp0", 0.5).toFloat(), dd0 = o.s("dd0"),
        finalHome = sc.optInt(0), finalAway = sc.optInt(1),
        totalMs = if (lastTl != null) (lastTl.d("start") + lastTl.d("dur")).toFloat() else (last?.endAct ?: 0f),
    )
}
