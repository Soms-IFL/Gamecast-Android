package com.iflgamecast.app

import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses real responses captured from the live Gamecast server (src/test/resources) with the app's own parsers. */
class ParsingTest {
    private fun res(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()
    private fun json(name: String) = JSONObject(res(name))

    @Test fun simulationOutputParsesAndTimelineIsConsistent() {
        val sim = parseSim(res("sim_s2_final.json"))
        assertEquals(179, sim.events.size)
        assertEquals("MON", sim.home.id); assertEquals("BRV", sim.away.id)
        assertEquals(40, sim.finalHome); assertEquals(27, sim.finalAway)
        assertTrue(sim.totalMs > 600_000f)
        // the timeline never goes backwards
        for (i in 1 until sim.events.size) {
            assertTrue("start must increase at $i", sim.events[i].start >= sim.events[i - 1].start)
            assertTrue("endAct must increase at $i", sim.events[i].endAct >= sim.events[i - 1].endAct)
            assertTrue("trailFrom within range at $i", sim.events[i].trailFrom in 0..i)
        }
        assertEquals(0, sim.completed(0f))
        assertEquals(sim.events.size, sim.completed(sim.totalMs))
        assertEquals(1, sim.completed(sim.events[0].endAct))
        // the scoreboard after the last play matches the final score
        val last = sim.events.last().post
        assertEquals(40, last.scoreHome); assertEquals(27, last.scoreAway); assertTrue(last.over)
        // win probability ends decided
        assertTrue(sim.events.last().wp > 0.99f)
        // route animation data exists for plays that move the ball, and trails start over at kickoffs
        val moving = sim.events.filter { it.segs.isNotEmpty() }
        assertTrue(moving.size > 100)
        assertTrue(moving.all { it.segTotal > 0f })
        assertTrue(sim.events.any { it.nameBefore != null })
        assertTrue(sim.events.any { it.scoring == 0 } && sim.events.any { it.scoring == 1 })
        assertTrue(sim.events.any { it.banner == "TOUCHDOWN" })
    }

    @Test fun gamesListParses() {
        val r = json("api/games_s2.json")
        val games = r.a("games").mapObjs { gameOf(it) }
        assertEquals(87, r.i("total"))
        assertTrue(games.isNotEmpty())
        assertTrue(games.all { it.id.isNotEmpty() && it.home.id.isNotEmpty() && it.away.id.isNotEmpty() })
        val bowl = games.first { it.id == "P-F" }
        assertTrue(bowl.isFinal); assertEquals("bowl", bowl.stage); assertEquals("MON", bowl.winner)
        assertEquals(40, bowl.home.score); assertEquals(27, bowl.away.score)
        assertNotNull(bowl.startsAtMs)
        assertEquals("IFL Bowl", bowl.weekLabel)
        assertEquals("Week 1", games.first { it.week == "1" }.weekLabel)
    }

    @Test fun currentGamesIncludeScheduledAndLive() {
        val games = json("api/games_current.json").a("games").mapObjs { gameOf(it) }
        assertTrue(games.isNotEmpty())
        assertTrue(games.all { it.status in listOf("scheduled", "live", "final") })
        // scheduled games have no score
        assertTrue(games.filter { it.isScheduled }.all { it.home.score == null && it.away.score == null })
    }

    @Test fun liveGameDetailParses() {
        val d = json("api/games_live.json")
        if (!d.b("live")) return                       // nothing on air when the fixture was taken
        val g = gameOf(d.obj("game"))
        assertTrue(g.isLive)
        assertNotNull(g.home.score); assertNotNull(g.away.score)
        val sit = d.o("situation")
        assertNotNull(sit)
        assertTrue(sit!!.i("quarter") in 1..8)
        assertTrue(situationText(sit).isNotEmpty())
        assertTrue(d.o("winProbability")!!.d("home") in 0.0..1.0)
        assertTrue(d.a("recentPlays").length() >= 0)
        val box = d.o("box")
        assertNotNull(box)
        assertNotNull(box!!.o("home")?.o("stats"))
        assertTrue(box.o("home")!!.a("players").length() > 0)
    }

    @Test fun finalGameDetailAndPlaysParse() {
        val d = json("api/game_final.json")
        val g = gameOf(d.obj("game"))
        assertTrue(g.isFinal)
        assertEquals(4, d.o("quarters")!!.a("home").length())
        val away = d.o("quarters")!!.a("away").ints(); val home = d.o("quarters")!!.a("home").ints()
        assertEquals(27, away.sum()); assertEquals(40, home.sum())
        val stats = d.o("box")!!.o("home")!!.o("stats")!!
        assertTrue(stats.i("yds") > 0)
        val plays = json("api/plays_final.json").a("plays").mapObjs { playOf(it) }
        assertEquals(179, plays.size)
        assertTrue(plays.any { it.scoringTeam != null })
        assertEquals(40, plays.last().homeScore); assertEquals(27, plays.last().awayScore)
        assertEquals((1..179).toList(), plays.map { it.n })
    }

    @Test fun scheduledGameDetailHasPregameOdds() {
        val d = json("api/game_scheduled.json")
        val g = gameOf(d.obj("game"))
        assertTrue(g.isScheduled)
        val wp = d.o("winProbability")!!
        assertEquals("pregame", wp.s("basis")); assertTrue(wp.d("home") in 0.0..1.0)
        assertFalse(d.has("box"))
    }

    @Test fun teamsParse() {
        val teams = json("api/teams.json").a("teams").mapObjs { teamListingOf(it) }
        assertEquals(12, teams.size)
        assertTrue(teams.all { it.rating != null && it.team.nickname.isNotEmpty() && it.division.isNotEmpty() })
        assertEquals(setOf("IFL East", "IFL Central", "IFL West"), teams.map { it.division }.toSet())
    }

    @Test fun teamDetailParses() {
        val t = json("api/team_kyl.json")
        assertEquals("KYL", teamOf(t).id)
        val roster = t.a("roster").mapObjs { it }
        assertTrue(roster.size > 30)
        assertTrue(roster.all { it.s("id").isNotEmpty() && it.s("position").isNotEmpty() && it.i("rating") > 0 })
        assertTrue(t.a("schedule").mapObjs { gameOf(it) }.isNotEmpty())
        assertNotNull(ratingOf(t.o("rating")))
        assertNotNull(t.o("record")?.i("wins"))
    }

    @Test fun standingsParse() {
        val s = json("api/standings_s2.json")
        val divs = s.a("divisions").mapObjs { it }
        assertEquals(3, divs.size)
        assertEquals(12, divs.sumOf { it.a("teams").length() })
        assertEquals(12, s.a("overall").length())
        val first = s.a("overall").getJSONObject(0)
        assertTrue(teamOf(first.obj("team")).id.isNotEmpty())
        assertTrue(first.i("wins") >= first.i("losses"))
        val seeds = s.o("playoffs")!!.a("seeds").mapObjs { it }
        assertEquals(4, seeds.size)
        assertEquals("MON", teamOf(s.o("champion")!!).id)
    }

    @Test fun leadersParse() {
        val c = json("api/leaders_s2.json").o("categories")!!
        assertEquals(10, c.keyList().size)
        val pass = c.o("passingYards")!!
        assertEquals("Passing Yards", pass.s("label"))
        val leaders = pass.a("leaders").mapObjs { it }
        assertEquals(3, leaders.size)
        assertEquals(1, leaders[0].i("rank"))
        assertTrue(leaders[0].i("value") >= leaders[1].i("value"))
        assertTrue(teamOf(leaders[0].obj("teamInfo")).id.isNotEmpty())
    }

    @Test fun seasonsAndPlayerAndStatusParse() {
        val seasons = json("api/seasons.json").a("seasons").mapObjs { it }
        assertTrue(seasons.size >= 3)
        assertTrue(seasons.first().b("current"))
        assertNotNull(seasons.first { it.i("number") == 2 }.o("champion"))
        val p = json("api/player.json")
        assertEquals("QB", p.s("position")); assertTrue(p.i("rating") > 0)
        assertEquals("KYL", teamOf(p.obj("team")).id)
        val st = json("api/status.json")
        assertTrue(st.o("season")!!.i("gamesTotal") > 0)
    }

    @Test fun windowSizeClasses() {
        assertEquals(WinSize.Compact, winSizeFor(360.dp)); assertEquals(WinSize.Compact, winSizeFor(599.dp))
        assertEquals(WinSize.Medium, winSizeFor(600.dp)); assertEquals(WinSize.Medium, winSizeFor(839.dp))
        assertEquals(WinSize.Expanded, winSizeFor(840.dp)); assertEquals(WinSize.Expanded, winSizeFor(1280.dp))
    }

    @Test fun formattingHelpers() {
        assertEquals("100%", pctText(1.0)); assertEquals("0%", pctText(0.0)); assertEquals("<1%", pctText(0.002)); assertEquals(">99%", pctText(0.997)); assertEquals("34%", pctText(0.34))
        assertEquals("OT", qLabel(5)); assertEquals("2OT", qLabel(6)); assertEquals("Q3", qLabel(3))
        assertEquals("7:05", fmtClock(425f)); assertEquals("0:00", fmtClock(-3f))
        assertEquals("https://x.example", Api.normalize("x.example/"))
        assertEquals("https://x.example", Api.normalize("https://x.example/api/v1"))
        assertEquals("http://10.0.0.5:3000", Api.normalize("http://10.0.0.5:3000/"))
    }
}
