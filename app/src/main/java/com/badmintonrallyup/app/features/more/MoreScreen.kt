// More — Flow 6: profile, private kCal, biometric app lock, sign out,
// delete account. 1:1 port of iOS Features/More/MoreView.swift.

package com.badmintonrallyup.app.features.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.LocalAppLock
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GlassPopup
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch

@Composable
fun MoreScreen() {
    val session = LocalSession.current
    val appLock = LocalAppLock.current
    val scope = rememberCoroutineScope()

    var showKcal by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showBlocked by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // AppLock.enabled is prefs-backed (not observable) — mirror it locally so
    // the switch recomposes, same behavior as the iOS binding.
    var lockEnabled by remember { mutableStateOf(appLock.enabled) }

    if (showStats) {
        BackHandler { showStats = false }
        MyStatsScreen(onBack = { showStats = false })
        return
    }
    if (showBlocked) {
        BackHandler { showBlocked = false }
        BlockedMembersScreen(onBack = { showBlocked = false })
        return
    }
    if (showKcal) {
        BackHandler { showKcal = false }
        KcalScreen(onBack = { showKcal = false })
        return
    }
    if (showHistory) {
        BackHandler { showHistory = false }
        HistoryScreen(onBack = { showHistory = false })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Theme.screenGutter),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("More", style = Theme.display(24f), color = Theme.ink)

        // MARK: Profile
        val me = session.me
        if (me != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.card()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Theme.court, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(me.displayName.take(1), style = Theme.emphasis(16f), color = Color.White)
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(me.displayName, style = Theme.emphasis(15f), color = Theme.ink)
                    Text(me.email, style = Theme.caption(12f), color = Theme.inkMuted)
                }
            }
        }

        // MARK: Calories / History
        Column(Modifier.card(padding = 0.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showKcal = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Icon(Icons.Outlined.LocalFireDepartment, null, Modifier.size(18.dp), tint = Theme.cork)
                Text("Calories", style = Theme.body(15f), color = Theme.ink)
                Spacer(Modifier.weight(1f))
                Text("private", style = Theme.caption(11f), color = Theme.success)
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                    Modifier.size(14.dp), tint = Theme.inkMuted
                )
            }
            HorizontalDivider(color = Theme.line, thickness = 1.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showHistory = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = Theme.court)
                Text("History", style = Theme.body(15f), color = Theme.ink)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                    Modifier.size(14.dp), tint = Theme.inkMuted
                )
            }
        }

        // MARK: Security
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Security")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.card(padding = 0.dp).padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Outlined.Fingerprint, null, Modifier.size(18.dp), tint = Theme.court)
                Text(
                    "Unlock with ${appLock.biometryLabel}",
                    style = Theme.body(15f), color = Theme.ink
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = {
                        lockEnabled = it
                        appLock.enabled = it
                        AppHaptics.voteCast()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Theme.court)
                )
            }
        }

        // MARK: About — legal links (matches iOS More → About)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("About")
            Column(Modifier.card(padding = 0.dp)) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Text(
                    "Blocked members",
                    style = Theme.body(15f), color = Theme.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBlocked = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                )
                HorizontalDivider(color = Theme.line, thickness = 1.dp)
                Text(
                    "My stats",
                    style = Theme.body(15f), color = Theme.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStats = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                )
                HorizontalDivider(color = Theme.line, thickness = 1.dp)
                Text(
                    "Terms of Service",
                    style = Theme.body(15f), color = Theme.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri("https://badmintonrallyup.com/terms") }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                )
                HorizontalDivider(color = Theme.line, thickness = 1.dp)
                Text(
                    "Privacy Policy",
                    style = Theme.body(15f), color = Theme.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri("https://badmintonrallyup.com/privacy") }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                )
            }
        }

        // MARK: Sign out / delete account (Apple 5.1.1 parity)
        Column(Modifier.card(padding = 0.dp)) {
            Text(
                "Sign out",
                style = Theme.body(15f), color = Theme.inkMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { session.signOut() } }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            )
            HorizontalDivider(color = Theme.line, thickness = 1.dp)
            Text(
                "Delete account",
                style = Theme.body(15f), color = Theme.cork,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmDelete = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            )
        }
    }

    if (confirmDelete) {
        GlassPopup(onDismiss = { confirmDelete = false }) {
            Text(
                "Delete your account? Your personal data is removed permanently.",
                style = Theme.caption(13f), color = Theme.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            GlassButton("Delete my account", tint = Theme.cork) {
                confirmDelete = false
                scope.launch {
                    try { session.deleteAccount() } catch (_: Exception) {}
                }
            }
            Text(
                "Cancel",
                style = Theme.emphasis(14f), color = Theme.court,
                modifier = Modifier
                    .clickable { confirmDelete = false }
                    .padding(6.dp)
            )
        }
    }
}
