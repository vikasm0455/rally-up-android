// Groups — Flow 5, to fidelity: your groups as radio rows (one-tap switch),
// admin badges, incoming invites, create/join. 1:1 port of GroupsView.swift.

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.ApiUUID
import com.badmintonrallyup.app.api.EmptyData
import com.badmintonrallyup.app.api.GroupBrief
import com.badmintonrallyup.app.api.MyInvite
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class ActiveGroupReq(val groupId: ApiUUID)

@Serializable
private data class GroupsCreateReq(val name: String)

@Composable
fun GroupsScreen() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var groups by remember { mutableStateOf(listOf<GroupBrief>()) }
    var invites by remember { mutableStateOf(listOf<MyInvite>()) }
    var newGroupName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        groups = try {
            ApiClient.get<List<GroupBrief>>("/api/groups")
        } catch (e: Exception) {
            emptyList()
        }
        invites = try {
            ApiClient.get<List<MyInvite>>("/api/invites")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun activate(g: GroupBrief) {
        if (g.isActive) return
        try {
            val updated: List<GroupBrief> =
                ApiClient.put("/api/groups/active", ActiveGroupReq(g.id))
            groups = updated
            AppHaptics.voteCast()
            session.refreshMe()
        } catch (_: Exception) {
        }
    }

    suspend fun create() {
        busy = true
        try {
            ApiClient.post<GroupBrief, GroupsCreateReq>(
                "/api/groups", GroupsCreateReq(newGroupName.trim())
            )
            newGroupName = ""
            AppHaptics.success()
            session.refreshMe()
            load()
        } catch (_: Exception) {
        } finally {
            busy = false
        }
    }

    suspend fun accept(inv: MyInvite) {
        try {
            ApiClient.post<List<GroupBrief>, EmptyData>("/api/invites/${inv.id}/accept", null)
        } catch (_: Exception) {
        }
        AppHaptics.voteCast()
        session.refreshMe()
        load()
    }

    suspend fun decline(inv: MyInvite) {
        try {
            ApiClient.post<List<MyInvite>, EmptyData>("/api/invites/${inv.id}/decline", null)
        } catch (_: Exception) {
        }
        load()
    }

    LaunchedEffect(Unit) { load() }

    if (showSettings) {
        BackHandler { showSettings = false }
        GroupSettingsScreen(onBack = { showSettings = false })
        return
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Large title (iOS navigationTitle).
            Text("Groups", style = Theme.display(24f), color = Theme.ink)

            if (groups.isNotEmpty()) {
                SectionLabel("Your groups · tap to switch")
                Column(Modifier.card(4.dp)) {
                    groups.forEach { g ->
                        GroupRow(
                            g = g,
                            onActivate = { scope.launch { activate(g) } },
                            onOpenSettings = { showSettings = true },
                        )
                    }
                }
            }

            if (invites.isNotEmpty()) {
                Box(Modifier.padding(top = 4.dp)) { SectionLabel("Invites for you") }
                invites.forEach { inv ->
                    InviteCard(
                        inv = inv,
                        onAccept = { scope.launch { accept(inv) } },
                        onDecline = { scope.launch { decline(inv) } },
                    )
                }
            }

            Box(Modifier.padding(top = 4.dp)) {
                SectionLabel(if (groups.isEmpty()) "Start a group" else "New group")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RallyTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = "Group name",
                    modifier = Modifier.fillMaxWidth(),
                )
                GlassButton(
                    if (busy) "Creating…" else "Create group",
                    enabled = !(busy || newGroupName.trim().length < 2),
                ) { scope.launch { create() } }
            }

            Text(
                "Switching changes what Home, Courts and Logins show — your kCal stays yours everywhere.",
                style = Theme.caption(11f),
                color = Theme.inkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun GroupRow(g: GroupBrief, onActivate: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Icon(
            if (g.isActive) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
            null,
            Modifier.size(18.dp),
            tint = if (g.isActive) Theme.court else Theme.line
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(g.name, style = Theme.emphasis(15f), color = Theme.ink)
            Text("${g.memberCount} members", style = Theme.caption(11.5f), color = Theme.inkMuted)
        }
        Spacer(Modifier.weight(1f))
        if (g.role == "admin") StatusBadge(text = "admin", kind = BadgeKind.Admin)
        if (g.isActive) {
            StatusBadge(text = "active", kind = BadgeKind.Free)
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                Modifier
                    .padding(start = 2.dp)
                    .size(16.dp)
                    .clickable { onOpenSettings() },
                tint = Theme.inkMuted
            )
        }
    }
}

@Composable
private fun InviteCard(inv: MyInvite, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.card()
    ) {
        Text(inv.groupName, style = Theme.emphasis(15f), color = Theme.ink)
        Text(
            "${inv.invitedByName} invited you · ${inv.memberCount} members",
            style = Theme.caption(12f),
            color = Theme.inkMuted
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassButton("Accept", modifier = Modifier.weight(1f)) { onAccept() }
            Text(
                "Decline",
                style = Theme.emphasis(13f),
                color = Theme.cork,
                modifier = Modifier.clickable { onDecline() }
            )
        }
    }
}
