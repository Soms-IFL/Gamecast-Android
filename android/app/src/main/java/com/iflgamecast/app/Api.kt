package com.iflgamecast.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(val code: Int, message: String) : Exception(message)

/** Client for the public, read-only IFL Gamecast API (/api/v1) plus the site's own data endpoints used by the game viewer. */
object Api {
    const val DEFAULT_BASE = "https://iflgamecast.colebolebole.xyz"

    // Short-lived response cache: moving between tabs (or re-opening a screen) doesn't hit the network again.
    // Live data (status, the live game, single games, plays) is never cached.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, JSONObject>>()

    @Volatile
    var base: String = DEFAULT_BASE
        set(v) { field = v; cache.clear() }

    private fun ttlFor(path: String): Long = when {
        path == "status" || path.startsWith("games/live") -> 0L
        path == "games" -> 15_000L
        path.startsWith("games/") -> 0L
        path.startsWith("players") -> 120_000L
        path.startsWith("seasons") -> 60_000L
        else -> 30_000L                       // teams, standings, leaders
    }

    private fun parse(text: String): JSONObject = try { JSONObject(text) } catch (e: Exception) { throw ApiException(0, "Unexpected response from the server.") }

    fun normalize(raw: String): String {
        var v = raw.trim().trimEnd('/')
        if (v.endsWith("/api/v1")) v = v.removeSuffix("/api/v1").trimEnd('/')
        if (v.isNotEmpty() && !v.startsWith("http://") && !v.startsWith("https://")) v = "https://$v"
        return v
    }

    private fun query(params: Array<out Pair<String, Any?>>): String {
        val parts = params.filter { it.second != null && it.second.toString().isNotEmpty() }
            .map { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second.toString(), "UTF-8") }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }

    private fun http(url: String): String {
        val con = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { throw ApiException(0, "Bad server address") }
        try {
            con.connectTimeout = 10_000
            con.readTimeout = 25_000
            con.setRequestProperty("Accept", "application/json")
            val code = try { con.responseCode } catch (e: Exception) { throw ApiException(0, "Could not reach the server.") }
            val stream = if (code in 200..299) con.inputStream else (con.errorStream ?: throw ApiException(code, "Request failed"))
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                val msg = try { JSONObject(text).optString("error", "") } catch (e: Exception) { "" }
                throw ApiException(code, if (code == 429) "Too many requests — slow down for a moment." else msg.ifEmpty { "Request failed ($code)" })
            }
            return text
        } finally {
            con.disconnect()
        }
    }

    /** GET /api/v1/{path}. Pass [season] for an archived season (null = current). */
    suspend fun v1(path: String, season: Int? = null, vararg params: Pair<String, Any?>): JSONObject {
        val all = if (season != null) arrayOf<Pair<String, Any?>>("season" to season, *params) else arrayOf(*params)
        val url = "$base/api/v1/$path${query(all)}"
        val ttl = ttlFor(path)
        if (ttl > 0) cache[url]?.let { if (System.currentTimeMillis() - it.first < ttl) return it.second }
        val obj = withContext(Dispatchers.IO) { parse(http(url)) }
        if (ttl > 0) cache[url] = System.currentTimeMillis() to obj
        return obj
    }

    /** One of the site's own data endpoints (/api/state, /api/history/N). They carry the game seeds the viewer needs. */
    suspend fun internal(path: String, ttlMs: Long = 0L): JSONObject {
        require(path == "/api/state" || path.matches(Regex("/api/history/[0-9]+")))
        val url = "$base$path"
        if (ttlMs > 0) cache[url]?.let { if (System.currentTimeMillis() - it.first < ttlMs) return it.second }
        val obj = withContext(Dispatchers.IO) { parse(http(url)) }
        if (ttlMs > 0) cache[url] = System.currentTimeMillis() to obj
        return obj
    }

    /** Every game of a season (pages through the 200-per-request limit). */
    suspend fun allGames(season: Int?, vararg params: Pair<String, Any?>): List<Game> {
        val out = ArrayList<Game>()
        var offset = 0
        while (true) {
            val r = v1("games", season, "limit" to 200, "offset" to offset, *params)
            out.addAll(r.a("games").mapObjs { gameOf(it) })
            val total = r.i("total")
            offset += 200
            if (offset >= total) break
        }
        return out
    }
}
