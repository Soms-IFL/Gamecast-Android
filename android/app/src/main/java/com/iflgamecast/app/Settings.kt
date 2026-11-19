package com.iflgamecast.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** User preferences (persisted) plus the season being browsed (not persisted). Backed by Compose state so the UI follows changes. */
class AppSettings(private val prefs: SharedPreferences) {
    var base by mutableStateOf(prefs.getString("base", null) ?: Api.DEFAULT_BASE); private set
    var theme by mutableStateOf(prefs.getString("theme", "auto") ?: "auto"); private set          // auto | light | dark
    var favId by mutableStateOf(prefs.getString("favId", null)); private set
    var favColor by mutableStateOf(prefs.getInt("favColor", 0)); private set                        // ARGB of the team's main colour, 0 = none
    var notifyOn by mutableStateOf(prefs.getBoolean("notifyOn", false)); private set
    var notifyTeamOnly by mutableStateOf(prefs.getBoolean("notifyTeamOnly", false)); private set

    /** null = the current season, otherwise an archived season number. */
    var season by mutableStateOf<Int?>(null)

    init { Api.base = base }

    fun changeBase(v: String) { base = v; Api.base = v; prefs.edit().putString("base", v).apply() }
    fun resetBase() { base = Api.DEFAULT_BASE; Api.base = base; prefs.edit().remove("base").apply() }
    fun changeTheme(v: String) { theme = v; prefs.edit().putString("theme", v).apply() }
    fun changeFav(id: String?, color: Int) {
        favId = id; favColor = if (id == null) 0 else color
        prefs.edit().apply { if (id == null) remove("favId") else putString("favId", id); putInt("favColor", favColor) }.apply()
        if (id == null && notifyTeamOnly) changeNotifyTeamOnly(false)
    }
    fun changeNotifyOn(v: Boolean) { notifyOn = v; prefs.edit().putBoolean("notifyOn", v).apply() }
    fun changeNotifyTeamOnly(v: Boolean) { notifyTeamOnly = v; prefs.edit().putBoolean("notifyTeamOnly", v).apply() }

    companion object {
        fun from(ctx: Context) = AppSettings(ctx.getSharedPreferences("ifl_settings", Context.MODE_PRIVATE))
    }
}

val LocalSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }
