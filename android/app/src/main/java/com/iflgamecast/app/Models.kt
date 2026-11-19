package com.iflgamecast.app

import androidx.compose.ui.graphics.Color
import org.json.JSONObject

fun parseColor(hex: String?, fallback: Color): Color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { fallback }

data class Team(
    val id: String,
    val abbr: String,
    val name: String,
    val city: String,
    val nickname: String,
    val c1: Color,
    val c2: Color,
    val logo: String?,
    val score: Int?,
) {
    val logoUrl: String? get() = logo?.let { Api.base + it }
}

fun teamOf(o: JSONObject): Team {
    val cols = o.a("colors").strings()
    return Team(
        id = o.s("id"), abbr = o.s("abbr").ifEmpty { o.s("id") }, name = o.s("name"), city = o.s("city"),
        nickname = o.s("nickname").ifEmpty { o.s("name") },
        c1 = parseColor(cols.getOrNull(0), Color(0xFF6B6B78)), c2 = parseColor(cols.getOrNull(1), Color(0xFFFFFFFF)),
        logo = o.sn("logo"), score = if (o.isNull("score")) null else o.optInt("score"),
    )
}

data class Venue(val name: String?, val town: String?, val host: String?)

data class Game(
    val id: String,
    val week: String,
    val label: String?,
    val stage: String,
    val playoff: Boolean,
    val status: String,           // scheduled | live | final
    val startsAtMs: Long?,
    val home: Team,
    val away: Team,
    val overtime: Boolean,
    val winner: String?,
    val venue: Venue?,
) {
    val isLive get() = status == "live"
    val isFinal get() = status == "final"
    val isScheduled get() = status == "scheduled"
    val weekLabel: String get() = if (playoff || week == "P") (label ?: "Playoffs") else "Week $week"
}

fun gameOf(o: JSONObject): Game {
    val v = o.o("venue")
    return Game(
        id = o.s("id"), week = o.s("week"), label = o.sn("label"), stage = o.s("stage"), playoff = o.b("playoff"), status = o.s("status"),
        startsAtMs = if (o.isNull("startsAtMs")) null else o.optLong("startsAtMs"),
        home = teamOf(o.o("home") ?: JSONObject()), away = teamOf(o.o("away") ?: JSONObject()),
        overtime = o.b("overtime"), winner = o.sn("winner"),
        venue = v?.let { Venue(it.sn("name"), it.sn("town"), it.sn("host")) },
    )
}

data class Play(
    val n: Int,
    val kind: String,
    val quarter: Int,
    val clock: String,
    val situation: String?,
    val text: String,
    val tags: List<String>,
    val banner: String?,
    val scoringTeam: String?,
    val homeScore: Int,
    val awayScore: Int,
)

fun playOf(o: JSONObject): Play {
    val sc = o.o("score")
    return Play(
        n = o.i("n"), kind = o.s("kind"), quarter = o.i("quarter", 1), clock = o.s("clock"), situation = o.sn("situation"), text = o.s("text"),
        tags = o.a("tags").strings(), banner = o.sn("banner"), scoringTeam = o.sn("scoringTeam"),
        homeScore = sc?.i("home") ?: 0, awayScore = sc?.i("away") ?: 0,
    )
}

data class Rating(val ovr: Int, val off: Int, val def: Int)

fun ratingOf(o: JSONObject?): Rating? = o?.let { Rating(it.i("ovr"), it.i("off"), it.i("def")) }

data class TeamListing(val team: Team, val division: String, val record: String, val rating: Rating?)

fun teamListingOf(o: JSONObject): TeamListing =
    TeamListing(teamOf(o), o.o("division")?.s("name") ?: "League", o.s("record"), ratingOf(o.o("rating")))

val ORD = listOf("", "1st", "2nd", "3rd", "4th")
fun qLabel(q: Int): String = if (q >= 5) (if (q == 5) "OT" else "${q - 4}OT") else "Q$q"
fun qName(q: Int): String = if (q >= 5) (if (q == 5) "Overtime" else "${q - 4}OT") else "Quarter $q"
