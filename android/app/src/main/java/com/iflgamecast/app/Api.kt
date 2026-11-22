package com.iflgamecast.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val code: Int, message: String) : Exception(message)

/** One HTTP client for the whole app: the API and the team logos share its connection (one TLS handshake, HTTP/2). */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
        .build()
}

/** Coroutine-context marker: Api calls made inside answer from stored data only (of any age) and fail when nothing is stored. */
class CacheOnlyContext : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CacheOnlyContext>
}

val cacheOnly = CacheOnlyContext()

/** Client for the public, read-only IFL Gamecast API (/api/v1) plus the site's own data endpoints used by the game viewer. */
object Api {
    const val DEFAULT_BASE = "https://iflgamecast.colebolebole.xyz"

    private class Entry(val at: Long, val json: JSONObject)

    // Short-lived in-memory cache: moving between tabs (or re-opening a screen) doesn't hit the network again.
    // Live data (single games, plays) is never cached; status and the live game for 1.5 s only.
    private val cache = ConcurrentHashMap<String, Entry>()

    // Identical requests made at the same moment (a screen and the start-up prefetch, say) share one network call.
    private val inflight = ConcurrentHashMap<String, Deferred<JSONObject>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The last good answer for slow-changing data is also kept on disk, so screens can show it instantly on the next launch.
    @Volatile private var diskDir: File? = null

    @Volatile
    var base: String = DEFAULT_BASE
        set(v) { field = v; cache.clear() }

    fun init(ctx: Context) {
        val dir = File(ctx.cacheDir, "api").also { it.mkdirs() }
        diskDir = dir
        scope.launch {
            val cutoff = System.currentTimeMillis() - 7 * 24 * 3600_000L
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }

    private fun ttlFor(path: String): Long = when {
        path == "status" || path.startsWith("games/live") -> 1_500L
        path == "games" -> 15_000L
        path.startsWith("games/") -> 0L
        path.startsWith("players") -> 120_000L
        path.startsWith("seasons") -> 60_000L
        else -> 30_000L                       // teams, standings, leaders
    }

    private fun persistable(path: String): Boolean =
        path == "teams" || path == "standings" || path == "games" || path == "leaders" || path == "seasons" ||
            path.startsWith("teams/") || path.startsWith("seasons/")

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

    /* ---- disk ---- */

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun diskFile(url: String): File? = diskDir?.let { File(it, sha1(url)) }
    private fun writeDisk(url: String, text: String) { runCatching { diskFile(url)?.writeText(text) } }

    /** Whatever we have for [url], however old: memory first, then disk. */
    private fun stale(url: String, persist: Boolean): JSONObject? {
        cache[url]?.let { return it.json }
        if (!persist) return null
        val f = diskFile(url) ?: return null
        return runCatching { if (f.exists()) JSONObject(f.readText()).also { cache[url] = Entry(f.lastModified(), it) } else null }.getOrNull()
    }

    /* ---- network ---- */

    private suspend fun fetch(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: throw ApiException(0, "Bad server address")
        val req = Request.Builder().url(httpUrl).header("Accept", "application/json").build()
        return suspendCancellableCoroutine { cont ->
            val call = Http.client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { cont.resumeWithException(ApiException(0, "Could not reach the server.")) }
                override fun onResponse(call: Call, response: Response) {
                    response.use { r ->
                        try {
                            val code = r.code
                            val text = r.body?.string().orEmpty()
                            if (code !in 200..299) {
                                val msg = try { JSONObject(text).optString("error", "") } catch (e: Exception) { "" }
                                throw ApiException(code, if (code == 429) "Too many requests — slow down for a moment." else msg.ifEmpty { "Request failed ($code)" })
                            }
                            cont.resume(text)
                        } catch (e: Exception) {
                            cont.resumeWithException(if (e is ApiException) e else ApiException(0, "Could not reach the server."))
                        }
                    }
                }
            })
        }
    }

    private suspend fun get(url: String, ttl: Long, persist: Boolean): JSONObject {
        if (coroutineContext[CacheOnlyContext] != null) return stale(url, persist) ?: throw ApiException(-1, "Not stored yet")
        if (ttl > 0) cache[url]?.let { if (System.currentTimeMillis() - it.at < ttl) return it.json }
        val job = inflight.computeIfAbsent(url) {
            scope.async(start = CoroutineStart.LAZY) {
                val text = fetch(url)
                val obj = parse(text)
                if (ttl > 0) {
                    cache[url] = Entry(System.currentTimeMillis(), obj)
                    if (persist) writeDisk(url, text)
                }
                obj
            }.also { j -> j.invokeOnCompletion { inflight.remove(url, j) } }
        }
        job.start()
        return job.await()
    }

    /** GET /api/v1/{path}. Pass [season] for an archived season (null = current). */
    suspend fun v1(path: String, season: Int? = null, vararg params: Pair<String, Any?>): JSONObject {
        val all = if (season != null) arrayOf<Pair<String, Any?>>("season" to season, *params) else arrayOf(*params)
        return get("$base/api/v1/$path${query(all)}", ttlFor(path), persistable(path))
    }

    /** One of the site's own data endpoints (/api/state, /api/history/N). They carry the game seeds the viewer needs. */
    suspend fun internal(path: String, ttlMs: Long = 0L): JSONObject {
        require(path == "/api/state" || path.matches(Regex("/api/history/[0-9]+")))
        return get("$base$path", ttlMs, false)
    }

    /** Every game of a season (the API returns 200 per request; further pages are fetched together). */
    suspend fun allGames(season: Int?, vararg params: Pair<String, Any?>): List<Game> = coroutineScope {
        val first = v1("games", season, "limit" to 200, "offset" to 0, *params)
        val total = first.i("total")
        val rest = (200 until total step 200).map { off -> async { v1("games", season, "limit" to 200, "offset" to off, *params) } }
        (listOf(first) + rest.awaitAll()).flatMap { r -> r.a("games").mapObjs { gameOf(it) } }
    }
}
