package com.iflgamecast.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.SportsFootball
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay

/** App-wide chrome state the screens can drive: the title of detail screens and viewer fullscreen. */
class Chrome {
    var detailTitle by mutableStateOf("")
    var fullscreen by mutableStateOf(false)
}

val LocalChrome = staticCompositionLocalOf<Chrome> { error("Chrome not provided") }

@Composable
fun SetTitle(title: String) {
    val chrome = LocalChrome.current
    LaunchedEffect(title) { chrome.detailTitle = title }
}

/** Navigation actions the screens can invoke. */
class Nav(
    val game: (String) -> Unit,
    val team: (String) -> Unit,
    val watch: (String) -> Unit,
    val player: (String) -> Unit,
    val settings: () -> Unit,
    val toGames: () -> Unit,
    val toStandings: () -> Unit,
)

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }

class MainActivity : ComponentActivity() {
    private val pendingGame = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingGame.value = gameFromIntent(intent)
        val settings = AppSettings.from(this)
        setContent { IflRoot(settings, pendingGame.value) { pendingGame.value = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingGame.value = gameFromIntent(intent)
    }

    /** A tap on a game-start notification carries "#/game/<id>". */
    private fun gameFromIntent(i: Intent?): String? =
        i?.getStringExtra("route")?.let { Regex("#/game/([A-Za-z0-9_-]+)").matchEntire(it)?.groupValues?.get(1) }
}

private class Dest(val route: String, val label: String, val icon: ImageVector, val iconOff: ImageVector)

private val DESTS = listOf(
    Dest("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Dest("games", "Games", Icons.Filled.SportsFootball, Icons.Outlined.SportsFootball),
    Dest("standings", "Standings", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    Dest("teams", "Teams", Icons.Filled.Groups, Icons.Outlined.Groups),
    Dest("leaders", "Leaders", Icons.Filled.MilitaryTech, Icons.Outlined.MilitaryTech),
)

/** One-line nav label that ignores big system font sizes (a long label like "Standings" used to wrap onto two lines). */
@Composable
private fun NavLabel(text: String) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, 1f))) {
        Text(text.uppercase(), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
    }
}

private fun NavHostController.goTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IflRoot(settings: AppSettings, pendingGame: String?, onConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val chrome = remember { Chrome() }
    val seed = if (settings.favColor != 0) settings.favColor else DEFAULT_SEED

    CompositionLocalProvider(LocalSettings provides settings, LocalChrome provides chrome) {
        IflTheme(seed, settings.theme) {
            val dark = isDark(settings.theme)
            val view = LocalView.current
            SideEffect {
                ctx.findActivity()?.window?.let {
                    val c = WindowCompat.getInsetsController(it, view)
                    c.isAppearanceLightStatusBars = false      // the header is dark in both themes
                    c.isAppearanceLightNavigationBars = !dark
                }
            }

            val navController = rememberNavController()
            var showSettings by remember { mutableStateOf(false) }
            var showSeasons by remember { mutableStateOf(false) }
            var playerId by remember { mutableStateOf<String?>(null) }

            val nav = remember(navController) {
                Nav(
                    game = { navController.navigate("game/$it") },
                    team = { navController.navigate("team/$it") },
                    watch = { navController.navigate("watch/$it") },
                    player = { playerId = it },
                    settings = { showSettings = true },
                    toGames = { navController.goTop("games") },
                    toStandings = { navController.goTop("standings") },
                )
            }

            LaunchedEffect(pendingGame) {
                if (pendingGame != null) { navController.navigate("game/$pendingGame"); onConsumed() }
            }
            LaunchedEffect(settings.base) { delay(600); Prefetch.warm() }
            // keep the game-start alarms in step with the schedule (cheap: one request, only when alerts are on)
            LaunchedEffect(settings.notifyOn, settings.notifyTeamOnly, settings.favId, settings.base) {
                while (true) { Notify.sync(ctx.applicationContext, settings); delay(15 * 60_000L) }
            }

            val entry by navController.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            val top = DESTS.any { it.route == route }

            CompositionLocalProvider(LocalNav provides nav) {
                BoxWithConstraints(Modifier.background(MaterialTheme.colorScheme.background)) {
                    val wide = maxWidth >= 600.dp
                    Row {
                        if (wide && !chrome.fullscreen) Row {
                          NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Image(painterResource(R.drawable.ifl_logo), "IFL", Modifier.padding(top = 8.dp, bottom = 8.dp).height(52.dp))
                            val navRoute = when (route) { "game/{id}", "watch/{id}" -> "games"; "team/{id}" -> "teams"; else -> route }
                            val itemColors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            for (d in DESTS) NavigationRailItem(
                                selected = navRoute == d.route, onClick = { navController.goTop(d.route) }, colors = itemColors,
                                icon = { Icon(if (navRoute == d.route) d.icon else d.iconOff, d.label) }, label = { NavLabel(d.label) },
                            )
                          }
                          VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Scaffold(
                            topBar = {
                                if (!chrome.fullscreen) Column {
                                TopAppBar(
                                    title = {
                                        Text(
                                            when (route) { "home" -> "IFL GAMECAST"; "games" -> "GAMES"; "standings" -> "STANDINGS"; "teams" -> "TEAMS"; "leaders" -> "LEADERS"; else -> chrome.detailTitle },
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge,
                                        )
                                    },
                                    navigationIcon = {
                                        if (top) Image(painterResource(R.drawable.ifl_logo), "IFL", Modifier.padding(start = 12.dp, end = 4.dp).height(42.dp))
                                        else IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { showSeasons = true },
                                            colors = if (settings.season != null) IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else IconButtonDefaults.iconButtonColors(),
                                        ) { Icon(Icons.Filled.History, if (settings.season != null) "Season ${settings.season} (archived). Choose season" else "Choose season") }
                                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Settings") }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = TopBarColor, titleContentColor = Color.White,
                                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                                    ),
                                )
                                Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary))
                                }
                            },
                            bottomBar = {
                                if (!wide && top && !chrome.fullscreen) Column {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                                        val itemColors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = Color.Transparent,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        for (d in DESTS) NavigationBarItem(
                                            selected = route == d.route, onClick = { navController.goTop(d.route) }, colors = itemColors,
                                            icon = { Icon(if (route == d.route) d.icon else d.iconOff, d.label) }, label = { NavLabel(d.label) },
                                        )
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.background,
                        ) { padding ->
                            BoxWithConstraints(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                              CompositionLocalProvider(LocalWinSize provides winSizeFor(maxWidth)) {
                                Box(Modifier.widthIn(max = 1400.dp).fillMaxSize()) {
                            NavHost(navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                                composable("home") { HomeScreen() }
                                composable("games") { GamesScreen() }
                                composable("standings") { StandingsScreen() }
                                composable("teams") { TeamsScreen() }
                                composable("leaders") { LeadersScreen() }
                                composable("game/{id}", listOf(navArgument("id") { type = NavType.StringType })) { GameScreen(it.arguments?.getString("id") ?: "") }
                                composable("team/{id}", listOf(navArgument("id") { type = NavType.StringType })) { TeamScreen(it.arguments?.getString("id") ?: "") }
                                composable("watch/{id}", listOf(navArgument("id") { type = NavType.StringType })) { WatchScreen(it.arguments?.getString("id") ?: "") }
                            }
                                }
                              }
                            }
                        }
                    }
                }

                if (showSettings) SettingsSheet { showSettings = false }
                if (showSeasons) SeasonSheet(onDismiss = { showSeasons = false }) {
                    showSeasons = false
                    navController.goTop("home")
                }
                playerId?.let { pid ->
                    PlayerSheet(pid, onDismiss = { playerId = null }) { teamId -> playerId = null; navController.navigate("team/$teamId") }
                }
            }
        }
    }
}
