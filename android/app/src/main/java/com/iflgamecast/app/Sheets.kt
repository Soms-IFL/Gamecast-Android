package com.iflgamecast.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/* ---------------------------------------------------------------- player */

private val STAT_LABEL = mapOf(
    "gp" to "Games", "pa" to "Pass attempts", "pc" to "Completions", "py" to "Passing yards", "ptd" to "Passing TD", "pint" to "Interceptions thrown",
    "ra" to "Rushes", "ry" to "Rushing yards", "rlng" to "Longest rush", "rtd" to "Rushing TD", "tgt" to "Targets", "rec" to "Receptions",
    "recy" to "Receiving yards", "reclng" to "Longest reception", "rectd" to "Receiving TD", "fum" to "Fumbles", "tk" to "Tackles", "sacks" to "Sacks",
    "ints" to "Interceptions", "intyds" to "INT return yards", "deftd" to "Defensive TD", "pd" to "Passes defended", "ff" to "Forced fumbles",
    "fr" to "Fumble recoveries", "kr" to "Kick returns", "kry" to "KR yards", "pr" to "Punt returns", "pry" to "PR yards", "krtd" to "KR TD", "prtd" to "PR TD",
    "fga" to "FG attempts", "fgm" to "FG made", "fglng" to "Longest FG", "xpa" to "XP attempts", "xpm" to "XP made", "pn" to "Punts", "pny" to "Punt yards",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(id: String, onDismiss: () -> Unit, onTeam: (String) -> Unit) {
    val s = LocalSettings.current
    val loader = rememberLoad(id, s.season, s.base) { Api.v1("players/$id", s.season) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            when (val st = loader.state.value) {
                is Load.Loading -> LoadingBox(Modifier.fillMaxWidth().height(200.dp))
                is Load.Err -> Text(st.e.message ?: "Couldn't load this player", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                is Load.Ok -> {
                    val p = st.v
                    val team = teamOf(p.obj("team"))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(team, 56.dp); Spacer(Modifier.width(16.dp))
                        Column {
                            Text(p.s("name"), style = MaterialTheme.typography.headlineSmall)
                            Text("#${p.i("number")} · ${p.s("position")} · ${team.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, v) in listOf("OVERALL" to p.i("rating"), "AGE" to p.i("age"), "YEARS PRO" to p.i("yearsPro"))) {
                            Column(Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$v", style = MaterialTheme.typography.headlineMedium); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    p.o("injury")?.let { inj ->
                        Spacer(Modifier.height(12.dp))
                        FilledCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Injured — ${inj.s("part")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(if (inj.b("seasonEnding")) "Out for the rest of the season" else "Out ${inj.i("gamesLeft")} more game${if (inj.i("gamesLeft") == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Season ${p.i("season")} stats", style = MaterialTheme.typography.titleMedium)
                    val stats = p.o("seasonStats")
                    val keys = stats?.keyList()?.filter { (stats.optDouble(it, 0.0)) != 0.0 }?.sortedBy { if (it == "gp") 0 else 1 }.orEmpty()
                    Spacer(Modifier.height(8.dp))
                    if (keys.isEmpty()) Text("No stats recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else FilledCard {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            for (k in keys) Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(STAT_LABEL[k] ?: k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%,d".format(stats!!.optInt(k)), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onTeam(team.id) }, Modifier.fillMaxWidth()) { Text("Team page") }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- season picker */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonSheet(onDismiss: () -> Unit, onPicked: () -> Unit) {
    val s = LocalSettings.current
    val loader = rememberLoad(s.base) { Api.v1("seasons").a("seasons").mapObjs { it } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Choose season", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LoadViewInline(loader) { seasons ->
                for (se in seasons) {
                    val cur = se.b("current"); val n = se.i("number")
                    val selected = (s.season == null && cur) || s.season == n
                    val champ = se.o("champion")?.let { teamOf(it) }
                    Row(
                        Modifier.fillMaxWidth().clickable { s.season = if (cur) null else n; onPicked() }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (champ != null) TeamBadge(champ, 40.dp)
                        else Icon(if (cur) Icons.Filled.PlayCircle else Icons.Filled.Help, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Season $n" + if (cur) " · current" else "", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (if (champ != null) "Champion: ${champ.name}" else if (se.b("done")) "Complete" else "In progress") + (se.o("runnerUp")?.let { " · Runner-up ${it.s("abbr")}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- settings */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val teams = rememberLoad(s.base) { Api.v1("teams").a("teams").mapObjs { teamOf(it) } }
    var baseText by remember { mutableStateOf(s.base) }
    var notifOk by remember { mutableStateOf(Notify.hasPermission(ctx)) }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifOk = Notify.hasPermission(ctx)
        if (granted && notifOk) { s.changeNotifyOn(true); scope.launch { Notify.sync(ctx.applicationContext, s) }; toast("Game-start alerts are on") }
        else toast("Notifications are blocked — allow them in Android settings")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                val modes = listOf("auto" to "System", "light" to "Light", "dark" to "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (k, label) -> SegmentedButton(selected = s.theme == k, onClick = { s.changeTheme(k) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(label) } }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("My team & colours", style = MaterialTheme.typography.titleSmall)
                Text("Pick a team to colour the whole app with its palette, highlight its games and see its title chances on Home.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    @Composable
                    fun swatch(color: androidx.compose.ui.graphics.Color, selected: Boolean, label: String, onClick: () -> Unit) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(color).border(BorderStroke(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface), CircleShape).clickable(onClick = onClick),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Filled.Check, label, tint = androidx.compose.ui.graphics.Color.White) }
                    }
                    swatch(androidx.compose.ui.graphics.Color(DEFAULT_SEED), s.favId == null, "Default colours") { s.changeFav(null, 0) }
                    val list = (teams.state.value as? Load.Ok)?.v.orEmpty()
                    for (t in list) swatch(t.c1, s.favId == t.id, t.name) { s.changeFav(t.id, t.c1.toArgb()) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Game-start alerts", style = MaterialTheme.typography.bodyLarge)
                        Text("A notification when a game kicks off — even if the app is closed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s.notifyOn && notifOk, onCheckedChange = { on ->
                        if (!on) { s.changeNotifyOn(false); scope.launch { Notify.sync(ctx.applicationContext, s) } }
                        else if (Notify.hasPermission(ctx)) { s.changeNotifyOn(true); scope.launch { Notify.sync(ctx.applicationContext, s) }; toast("Game-start alerts are on") }
                        else if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else toast("Notifications are turned off for this app in Android settings")
                    })
                }
                if (s.notifyOn && notifOk) {
                    val modes = listOf(false to "All games", true to "My team only")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, (teamOnly, label) ->
                            SegmentedButton(
                                selected = s.notifyTeamOnly == teamOnly, onClick = { s.changeNotifyTeamOnly(teamOnly); scope.launch { Notify.sync(ctx.applicationContext, s) } },
                                enabled = !teamOnly || s.favId != null, shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }
                    if (s.favId == null) Text("Pick a team above to get alerts for just its games.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!GameAlarms.canExact(ctx)) FilledCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Alerts may arrive a few minutes late. Allow exact alarms for on-time kickoff alerts.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                            }) { Text("Allow") }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Server", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(baseText, { baseText = it }, Modifier.fillMaxWidth(), label = { Text("Gamecast server address") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val v = Api.normalize(baseText)
                        val old = Api.base
                        scope.launch {
                            try {
                                Api.base = v; Api.v1("status")
                                s.changeBase(v); baseText = v; toast("Connected to $v")
                            } catch (e: Exception) {
                                Api.base = old
                                toast(if (e is ApiException && e.code == 404) "That server doesn't serve /api/v1 yet" else "Couldn't connect to $v")
                            }
                        }
                    }) { Text("Save & test") }
                    TextButton(onClick = { s.resetBase(); baseText = s.base; toast("Server address reset") }) { Text("Reset") }
                }
                Text("The app reads /api/v1 on this server (read-only, no key needed).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
