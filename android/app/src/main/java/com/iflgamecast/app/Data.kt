package com.iflgamecast.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/* ---------------------------------------------------------------- playoff odds */

class TeamOdds(val playoffs: Double, val bowl: Double, val champ: Double)

class OddsResult(
    val teams: Map<String, TeamOdds>, val refs: Map<String, Team>, val playoffsKnown: Boolean, val seeds: List<String>,
    val champion: String?, val played: Int, val total: Int, val sims: Int,
) {
    fun note(id: String?): String = when {
        champion != null -> if (champion == id) "Champions!" else ""
        playoffsKnown && id != null && id !in seeds -> "Missed the playoffs this season."
        playoffsKnown && id != null -> "Playoff seed ${seeds.indexOf(id) + 1}."
        else -> "Simulated %,d times from team ratings · %d of %d games played".format(sims, played, total)
    }
}

/**
 * Chance for every team to make the playoffs, reach the IFL Bowl and win it: the rest of the season is simulated with the engine's own
 * pre-game win model (see assets/sim/glue.js). Built only from the public API; cached until a game finishes or ten minutes pass.
 */
object OddsLoader {
    private var key: String? = null
    private var at = 0L
    private var cached: OddsResult? = null

    suspend fun load(ctx: Context): OddsResult? {
        val (games, tm, st) = coroutineScope {
            val g = async { Api.allGames(null) }
            val t = async { Api.v1("teams") }
            val s = async { Api.v1("standings") }
            Triple(g.await(), t.await(), s.await())
        }
        val teamsArr = tm.a("teams")
        val refs = LinkedHashMap<String, Team>()
        val ratings = JSONObject()
        for (n in 0 until teamsArr.length()) {
            val t = teamsArr.getJSONObject(n)
            val r = t.o("rating") ?: return null
            refs[t.s("id")] = teamOf(t)
            ratings.put(t.s("id"), JSONObject().put("off", r.i("off")).put("def", r.i("def")))
        }
        val reg = games.filter { !it.playoff }
        val played = JSONArray(); val remaining = JSONArray()
        for (g in reg) {
            if (g.isFinal) played.put(JSONObject().put("home", g.home.id).put("away", g.away.id).put("hs", g.home.score ?: 0).put("as", g.away.score ?: 0))
            else remaining.put(JSONObject().put("home", g.home.id).put("away", g.away.id).apply { g.venue?.host?.let { put("host", it) } })
        }
        val seedsArr = st.o("playoffs")?.a("seeds")
        var playoffs: JSONObject? = null
        var seedIds: List<String> = emptyList()
        if (seedsArr != null && seedsArr.length() > 0) {
            val sorted = seedsArr.mapObjs { it }.sortedBy { it.i("seed") }
            seedIds = sorted.map { it.s("id") }
            playoffs = JSONObject().put("seeds", JSONArray(seedIds))
        }
        fun resultOf(g: Game) = if (g.isFinal) JSONObject().put("hs", g.home.score ?: 0).put("as", g.away.score ?: 0) else null
        val sf = games.filter { it.id == "P-SF1" || it.id == "P-SF2" }.sortedBy { it.id }
        val fin = games.firstOrNull { it.id == "P-F" }
        val champion = st.o("champion")?.sn("id")
        val input = JSONObject().put("teams", JSONArray(refs.keys.toList())).put("ratings", ratings).put("played", played).put("remaining", remaining)
            .put("sims", 800).put("champion", champion ?: JSONObject.NULL)
        input.put("playoffs", playoffs ?: JSONObject.NULL)
        input.put("semis", if (playoffs != null && sf.size == 2) JSONArray(sf.map { g ->
            JSONObject().put("home", g.home.id).put("away", g.away.id).apply { g.venue?.host?.let { put("host", it) }; resultOf(g)?.let { put("result", it) } }
        }) else JSONObject.NULL)
        input.put("final", if (fin != null) JSONObject().put("home", fin.home.id).put("away", fin.away.id).put("host", fin.venue?.host ?: JSONObject.NULL)
            .apply { resultOf(fin)?.let { put("result", it) } } else JSONObject.NULL)

        val k = listOf(played.length(), if (playoffs != null) 1 else 0, sf.count { it.isFinal }, if (fin != null) 1 else 0, champion ?: "").joinToString("|")
        if (k == key && System.currentTimeMillis() - at < 600_000 && cached != null) return cached
        // the simulation is the slow part: reuse the last result (saved on disk) while nothing about the season has changed
        val disk = ctx.getSharedPreferences("ifl_cache", Context.MODE_PRIVATE)
        val saved = if (disk.getString("odds_key", null) == k && System.currentTimeMillis() - disk.getLong("odds_at", 0L) < 600_000) disk.getString("odds_out", null) else null
        val out = withContext(Dispatchers.Default) {
            JSONObject(saved ?: SimEngine.odds(ctx, input).also { disk.edit().putString("odds_key", k).putString("odds_out", it).putLong("odds_at", System.currentTimeMillis()).apply() })
        }
        val tObj = out.o("teams") ?: return null
        val res = OddsResult(
            teams = tObj.keyList().associateWith { id -> tObj.getJSONObject(id).let { TeamOdds(it.d("playoffs"), it.d("bowl"), it.d("champ")) } },
            refs = refs, playoffsKnown = playoffs != null, seeds = seedIds, champion = champion, played = played.length(), total = reg.size, sims = out.i("sims"),
        )
        key = k; at = System.currentTimeMillis(); cached = res
        return res
    }
}

/* ---------------------------------------------------------------- viewer data */

class ReplayData(val sim: SimData, val airStart: Long, val offset: Long)

/**
 * Everything the viewer needs for one game. The animation re-simulates the game from its seed; seeds live in the site's own data
 * endpoints (/api/state, /api/history/N), which the native app can read directly. Only games that have already started are allowed.
 */
suspend fun loadReplay(ctx: Context, g: Game, season: Int?): ReplayData {
    val data: JSONObject; val games: JSONArray; val rosters: JSONObject?; var offset = 0L
    if (season == null) {
        val st = Api.internal("/api/state", if (g.isLive) 0L else 20_000L)
        val s = st.o("season") ?: throw ApiException(404, "No season data")
        offset = st.l("now") - System.currentTimeMillis()
        data = s; games = s.a("games"); rosters = s.o("rosters")
    } else {
        val h = Api.internal("/api/history/$season", 600_000L)
        data = h; games = h.a("games"); rosters = h.o("rosters")
    }
    var gj: JSONObject? = null
    for (n in 0 until games.length()) { val x = games.optJSONObject(n); if (x != null && x.s("gid").equals(g.id, true)) { gj = x; break } }
    gj ?: throw ApiException(404, "Game data not found")
    val args = JSONObject().put("home", gj.s("home")).put("away", gj.s("away")).put("seed", gj.s("seed")).put("gid", gj.s("gid"))
        .put("playoff", gj.b("playoff")).put("out", gj.a("out")).put("venue", gj.o("venue") ?: JSONObject.NULL).put("rosters", rosters ?: JSONObject.NULL)
    val sim = withContext(Dispatchers.Default) { parseSim(SimEngine.simulate(ctx, args)) }
    if (data.length() < 0) throw ApiException(500, "unreachable")
    return ReplayData(sim, gj.l("airStart"), offset)
}

/* ---------------------------------------------------------------- game-start notifications */

object Notify {
    fun hasPermission(ctx: Context): Boolean {
        val runtime = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtime && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Hands the upcoming schedule to the alarm scheduler (one alarm per game, restored after reboot). */
    suspend fun sync(ctx: Context, s: AppSettings) {
        try {
            if (!s.notifyOn || !hasPermission(ctx)) { GameAlarms.cancelAll(ctx); return }
            val now = System.currentTimeMillis()
            val teamOnly = s.notifyTeamOnly && s.favId != null
            val games = Api.allGames(null, "status" to "scheduled")
                .filter { (it.startsAtMs ?: 0L) > now - 60_000 && (!teamOnly || it.home.id == s.favId || it.away.id == s.favId) }
                .sortedBy { it.startsAtMs }.take(40)
            val arr = JSONArray()
            for (g in games) arr.put(JSONObject().put("id", g.id).put("ms", g.startsAtMs).put("label", "${g.away.abbr} @ ${g.home.abbr}"))
            GameAlarms.apply(ctx, s.base, arr)
        } catch (e: Exception) { /* offline: keep the alarms we already have */ }
    }
}

/* ---------------------------------------------------------------- startup warm-up */

object Prefetch {
    /** Loads what the other tabs will ask for (and the team logos) in the background, so switching tabs is instant. */
    suspend fun warm(ctx: Context) = coroutineScope {
        val teams = async { runCatching { Api.v1("teams") }.getOrNull() }
        launch { runCatching { Api.v1("standings") } }
        launch { runCatching { Api.allGames(null) } }
        launch { runCatching { Api.v1("leaders", null, "limit" to 5) } }
        val t = teams.await() ?: return@coroutineScope
        val loader = coil.Coil.imageLoader(ctx)
        for (team in t.a("teams").mapObjs { teamOf(it) }) {
            team.logoUrl?.let { loader.enqueue(coil.request.ImageRequest.Builder(ctx).data(it).build()) }
        }
    }
}
