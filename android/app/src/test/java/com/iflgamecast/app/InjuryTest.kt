package com.iflgamecast.app

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Injury objects as the live server sent them (Argiroupolis Silvers roster, season 3). */
class InjuryTest {
    private fun injury(json: String) = JSONObject(json)

    @Test fun placeholderLengthWithFlagFalseMeansOutForSeason() {
        // the server reported "989 games left" with seasonEnding = false for a season-ending hamstring injury
        assertTrue(injury("""{"part":"hamstring","gamesLeft":989,"seasonEnding":false}""").outForSeason())
    }

    @Test fun flagTrueMeansOutForSeason() {
        assertTrue(injury("""{"part":"knee","gamesLeft":2,"seasonEnding":true}""").outForSeason())
    }

    @Test fun shortInjuryIsNotSeasonEnding() {
        assertFalse(injury("""{"part":"foot","gamesLeft":4,"seasonEnding":false}""").outForSeason())
        assertFalse(injury("""{"part":"ankle","gamesLeft":8,"seasonEnding":false}""").outForSeason())
    }

    @Test fun missingFieldsAreNotSeasonEnding() {
        assertFalse(injury("""{"part":"back"}""").outForSeason())
    }
}
