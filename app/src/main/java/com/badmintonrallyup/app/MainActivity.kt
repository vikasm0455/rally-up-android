// Single-activity Compose host — 1:1 with RallyUpApp.swift: lock screen gate,
// RootView phase switch, 5-tab shell, offline pill, push deep links, and the
// DEBUG dev hooks (intent extras) that drive the emulator/UI-test loop.

package com.badmintonrallyup.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.badmintonrallyup.app.auth.AppLock
import com.badmintonrallyup.app.auth.SessionManager
import com.badmintonrallyup.app.core.NetworkMonitor
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.OfflinePill
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.features.auth.AuthFlowScreen
import com.badmintonrallyup.app.features.courts.CourtsScreen
import com.badmintonrallyup.app.features.groups.GroupOnboardingScreen
import com.badmintonrallyup.app.features.groups.GroupsScreen
import com.badmintonrallyup.app.features.home.HomeScreen
import com.badmintonrallyup.app.features.logins.LoginsScreen
import com.badmintonrallyup.app.features.more.MoreScreen
import com.badmintonrallyup.app.push.PushManager
import kotlinx.coroutines.flow.collectLatest

val LocalSession = compositionLocalOf<SessionManager> { error("SessionManager not provided") }
val LocalAppLock = compositionLocalOf<AppLock> { error("AppLock not provided") }
val LocalNetwork = compositionLocalOf<NetworkMonitor> { error("NetworkMonitor not provided") }
val LocalActivity = compositionLocalOf<FragmentActivity> { error("Activity not provided") }

class MainActivity : FragmentActivity() {
    private val session = SessionManager()
    private val appLock = AppLock()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)

        val dev = if (BuildConfig.DEBUG) SessionManager.DevHooks(
            api = intent.getStringExtra("RALLYUP_API"),
            access = intent.getStringExtra("RALLYUP_DEV_ACCESS"),
            refresh = intent.getStringExtra("RALLYUP_DEV_REFRESH"),
            reset = intent.getStringExtra("RALLYUP_DEV_RESET") == "1",
            explore = intent.getStringExtra("RALLYUP_DEV_EXPLORE") == "1",
        ) else null

        PushManager.requestOnLaunch(this)

        val network = NetworkMonitor(this)
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalSession provides session,
                LocalAppLock provides appLock,
                LocalNetwork provides network,
                LocalActivity provides this,
            ) {
                LaunchedEffect(Unit) { session.bootstrap(dev) }
                if (appLock.locked) LockScreen() else RootView()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        intent?.getStringExtra("url")?.let { PushManager.deepLink.value = it }
    }
}

@Composable
fun RootView() {
    val session = LocalSession.current
    if (session.showsMainApp) {
        MainTabView()
        return
    }
    when (session.phase) {
        SessionManager.Phase.Loading ->
            Box(Modifier.fillMaxSize().background(Theme.chalk), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Theme.court)
            }
        SessionManager.Phase.SignedOut -> AuthFlowScreen()
        SessionManager.Phase.Onboarding -> GroupOnboardingScreen()
        SessionManager.Phase.Ready -> MainTabView()
    }
}

@Composable
fun MainTabView() {
    val network = LocalNetwork.current
    var selection by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        PushManager.deepLink.collectLatest { path ->
            if (path == null) return@collectLatest
            selection = when {
                path.contains("court") -> 1
                path.contains("cred") || path.contains("login") -> 2
                path.contains("group") || path.contains("invite") -> 3
                else -> 0
            }
            PushManager.deepLink.value = null
        }
    }

    val tabs = listOf(
        "Home" to Icons.Outlined.Home,
        "Courts" to Icons.Outlined.GridView,
        "Logins" to Icons.Outlined.Key,
        "Groups" to Icons.Outlined.People,
        "More" to Icons.Outlined.MoreHoriz,
    )

    Scaffold(
        containerColor = Theme.chalk,
        bottomBar = {
            NavigationBar(containerColor = Theme.surface) {
                tabs.forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(
                        selected = selection == index,
                        onClick = { selection = index },
                        icon = { Icon(icon, title, Modifier.size(22.dp)) },
                        label = { Text(title, style = Theme.emphasis(11f)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Theme.court,
                            selectedTextColor = Theme.court,
                            unselectedIconColor = Theme.inkMuted,
                            unselectedTextColor = Theme.inkMuted,
                            indicatorColor = Theme.court.copy(alpha = 0.12f),
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Theme.chalk)) {
            when (selection) {
                0 -> HomeScreen()
                1 -> CourtsScreen()
                2 -> LoginsScreen()
                3 -> GroupsScreen()
                else -> MoreScreen()
            }
            if (!network.isOnline) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { OfflinePill() }
            }
        }
    }
}

@Composable
fun LockScreen() {
    val lock = LocalAppLock.current
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { lock.unlock(activity) }
    Column(
        modifier = Modifier.fillMaxSize().background(Theme.chalk),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(32.dp), tint = Theme.court)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Theme.ink)) { append("Rally") }
                withStyle(SpanStyle(color = Theme.cork)) { append("Up") }
            },
            fontSize = 26.sp, style = Theme.display(26f),
            modifier = Modifier.padding(top = 14.dp, bottom = 14.dp)
        )
        GlassButton(
            "Unlock with ${lock.biometryLabel}",
            modifier = Modifier.padding(horizontal = 60.dp)
        ) { lock.unlock(activity) }
    }
}
