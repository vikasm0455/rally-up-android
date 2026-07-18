// Group settings — pushed page for the ACTIVE group. Roster with quiet admin
// actions (promote/remove), invite by email, pending invites, auto-poll, leave.
// 1:1 port of the iOS GroupSettingsView.

package com.badmintonrallyup.app.features.groups

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.AutoPollConfig
import com.badmintonrallyup.app.api.EmptyData
import com.badmintonrallyup.app.api.GroupDetail
import com.badmintonrallyup.app.api.GroupInviteRow
import com.badmintonrallyup.app.api.GroupMemberRow
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GlassPopup
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class InviteReq(val email: String)

@Serializable
private data class RoleReq(val role: String)

@Serializable
private data class AutoPollReq(val enabled: Boolean)

@Composable
fun GroupSettingsScreen(onBack: () -> Unit) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<GroupDetail?>(null) }
    var pending by remember { mutableStateOf<List<GroupInviteRow>>(emptyList()) }
    var autoPoll by remember { mutableStateOf<AutoPollConfig?>(null) }
    var inviteEmail by remember { mutableStateOf("") }
    var confirmLeave by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showVenue by rememberSaveable { mutableStateOf(false) }

    val isAdmin = detail?.myRole == "admin"

    suspend fun load() {
        detail = try {
            ApiClient.get<GroupDetail>("/api/groups/current")
        } catch (e: Exception) { null }
        autoPoll = try {
            ApiClient.get<AutoPollConfig>("/api/config/auto-poll")
        } catch (e: Exception) { null }
        if (detail?.myRole == "admin") {
            pending = try {
                ApiClient.get<List<GroupInviteRow>>("/api/groups/invites")
            } catch (e: Exception) { emptyList() }
        }
    }

    suspend fun invite() {
        busy = true
        try {
            ApiClient.post<GroupInviteRow, InviteReq>(
                "/api/groups/invites", InviteReq(inviteEmail.trim())
            )
            inviteEmail = ""
            AppHaptics.success()
            load()
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    suspend fun revoke(inv: GroupInviteRow) {
        try { ApiClient.delete<EmptyData>("/api/groups/invites/${inv.id}") } catch (_: Exception) {}
        load()
    }

    suspend fun setRole(m: GroupMemberRow, role: String) {
        try { ApiClient.put<EmptyData, RoleReq>("/api/groups/members/${m.id}", RoleReq(role)) } catch (_: Exception) {}
        AppHaptics.voteCast()
        load()
    }

    suspend fun remove(m: GroupMemberRow) {
        try { ApiClient.delete<EmptyData>("/api/groups/members/${m.id}") } catch (_: Exception) {}
        AppHaptics.destructive()
        load()
    }

    suspend fun setAutoPoll(on: Boolean) {
        try {
            autoPoll = ApiClient.put<AutoPollConfig, AutoPollReq>(
                "/api/config/auto-poll", AutoPollReq(enabled = on)
            )
            AppHaptics.voteCast()
        } catch (e: Exception) {
            error = e.message
        }
    }

    suspend fun leave() {
        try {
            ApiClient.post<EmptyData, EmptyData>("/api/groups/leave", null)
            AppHaptics.destructive()
            session.refreshMe()
            onBack()
        } catch (e: Exception) {
            error = e.message
        }
    }

    if (showVenue) {
        BackHandler { showVenue = false }
        VenueSetupScreen(onBack = { showVenue = false })
        return
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(
            title = detail?.name ?: "Group",
            navIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            onNav = onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val d = detail
            if (d != null) {
                SectionLabel("Members · ${d.members.size}")
                Column(Modifier.card(4.dp)) {
                    d.members.forEach { m ->
                        MemberRow(
                            m = m,
                            isAdmin = isAdmin,
                            myId = session.me?.id,
                            onMakeAdmin = { scope.launch { setRole(m, "admin") } },
                            onRemove = { scope.launch { remove(m) } }
                        )
                    }
                }

                if (isAdmin) {
                    Box(Modifier.padding(top = 4.dp)) { SectionLabel("Invite by email") }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RallyTextField(
                            value = inviteEmail,
                            onValueChange = { inviteEmail = it },
                            placeholder = "friend@example.com",
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        GlassButton(
                            "Send",
                            modifier = Modifier.width(84.dp),
                            enabled = !busy && inviteEmail.contains("@")
                        ) { scope.launch { invite() } }
                    }
                    pending.forEach { inv ->
                        Row(
                            Modifier.card(11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(inv.email, style = Theme.body(13.5f), color = Theme.ink)
                                Text(
                                    "invited by ${inv.invitedByName} · pending",
                                    style = Theme.caption(11f), color = Theme.inkMuted
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "Revoke",
                                style = Theme.emphasis(12f), color = Theme.cork,
                                modifier = Modifier.clickable { scope.launch { revoke(inv) } }
                            )
                        }
                    }
                }

                if (isAdmin) {
                    Row(
                        Modifier
                            .clickable { showVenue = true }
                            .card(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Your venue", style = Theme.emphasis(14f), color = Theme.ink)
                            Text(
                                if (d.venueName.isEmpty())
                                    "Courts ${d.courtMin}–${d.courtMax}"
                                else
                                    "${d.venueName} · courts ${d.courtMin}–${d.courtMax}",
                                style = Theme.caption(11.5f), color = Theme.inkMuted
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                            Modifier.size(14.dp), tint = Theme.inkMuted
                        )
                    }
                }

                val ap = autoPoll
                if (ap != null) {
                    Box(Modifier.padding(top = 4.dp)) { SectionLabel("Automation") }
                    Row(
                        Modifier.card(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Auto-poll", style = Theme.emphasis(14f), color = Theme.ink)
                            Text(
                                "Creates the day's poll at ${ap.time} · reminder ${ap.finalReminderTime}",
                                style = Theme.caption(11.5f), color = Theme.inkMuted
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = ap.enabled,
                            onCheckedChange = { on -> scope.launch { setAutoPoll(on) } },
                            enabled = isAdmin,
                            colors = SwitchDefaults.colors(checkedTrackColor = Theme.court)
                        )
                    }
                }

                val err = error
                if (err != null) {
                    Text(err, style = Theme.caption(12f), color = Theme.cork)
                }

                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(Theme.cork.copy(alpha = 0.1f), RoundedCornerShape(Theme.buttonRadius))
                        .clickable { confirmLeave = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Leave this group", style = Theme.emphasis(14f), color = Theme.cork)
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Theme.court)
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    if (confirmLeave) {
        GlassPopup(onDismiss = { confirmLeave = false }) {
            Text(
                "Leave ${detail?.name ?: "this group"}?",
                style = Theme.emphasis(15f), color = Theme.ink
            )
            GlassButton("Leave group", tint = Theme.cork) {
                confirmLeave = false
                scope.launch { leave() }
            }
            Text(
                "Cancel",
                style = Theme.emphasis(13f), color = Theme.inkMuted,
                modifier = Modifier
                    .clickable { confirmLeave = false }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MemberRow(
    m: GroupMemberRow,
    isAdmin: Boolean,
    myId: UUID?,
    onMakeAdmin: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).background(Theme.court, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(m.displayName.take(1), style = Theme.emphasis(12f), color = Color.White)
        }
        Text(m.displayName, style = Theme.body(14f), color = Theme.ink)
        if (m.id == myId) {
            Text("(you)", style = Theme.caption(11f), color = Theme.inkMuted)
        }
        Spacer(Modifier.weight(1f))
        if (m.role == "admin") {
            StatusBadge(text = "admin", kind = BadgeKind.Admin)
        } else if (isAdmin) {
            Box {
                Icon(
                    Icons.Outlined.MoreHoriz, null,
                    Modifier
                        .clickable { menuOpen = true }
                        .padding(6.dp)
                        .size(18.dp),
                    tint = Theme.inkMuted
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Make admin", style = Theme.body(14f), color = Theme.ink) },
                        onClick = { menuOpen = false; onMakeAdmin() }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from group", style = Theme.body(14f), color = Theme.cork) },
                        onClick = { menuOpen = false; onRemove() }
                    )
                }
            }
        }
    }
}
