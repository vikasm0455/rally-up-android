// "Play happens in groups" — first-run: accept a waiting invite or create one.
// 1:1 port of iOS GroupOnboardingView.swift.

package com.badmintonrallyup.app.features.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.GroupBrief
import com.badmintonrallyup.app.api.MyInvite
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.Theme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class CreateGroupReq(val name: String)

@Composable
fun GroupOnboardingScreen() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var invites by remember { mutableStateOf(listOf<MyInvite>()) }
    var newGroupName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        invites = try {
            ApiClient.get("/api/invites")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun create() {
        busy = true
        try {
            ApiClient.post<GroupBrief, CreateGroupReq>(
                "/api/groups", CreateGroupReq(newGroupName.trim())
            )
            AppHaptics.success()
            session.refreshMe()
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    suspend fun accept(invite: MyInvite) {
        try {
            ApiClient.post<List<GroupBrief>, Unit>("/api/invites/${invite.id}/accept", null)
            AppHaptics.voteCast()
            session.refreshMe()
        } catch (e: Exception) {
            error = e.message
        }
    }

    suspend fun decline(invite: MyInvite) {
        try {
            ApiClient.post<List<MyInvite>, Unit>("/api/invites/${invite.id}/decline", null)
        } catch (_: Exception) {
        }
        load()
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.chalk)
            .safeDrawingPadding()
            .padding(Theme.screenGutter),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Play happens\nin groups",
            style = Theme.display(),
            color = Theme.ink,
            modifier = Modifier.padding(top = 40.dp)
        )
        Text(
            "Join your crew, or start one — polls, courts and logins live inside a group.",
            style = Theme.caption(),
            color = Theme.inkMuted
        )

        invites.forEach { invite ->
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Theme.surface, RoundedCornerShape(Theme.cardRadius))
                    .padding(14.dp)
            ) {
                Text(invite.groupName, style = Theme.emphasis(), color = Theme.ink)
                Text(
                    "${invite.invitedByName} invited you · ${invite.memberCount} members",
                    style = Theme.caption(),
                    color = Theme.inkMuted
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassButton("Accept invite", modifier = Modifier.weight(1f)) {
                        scope.launch { accept(invite) }
                    }
                    Text(
                        "Decline",
                        style = Theme.emphasis(14f),
                        color = Theme.inkMuted,
                        modifier = Modifier.clickable { scope.launch { decline(invite) } }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RallyTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                placeholder = "Group name",
                modifier = Modifier.fillMaxWidth()
            )
            GlassButton(
                if (busy) "Creating…" else "Create group",
                enabled = !busy && newGroupName.trim().length >= 2
            ) {
                scope.launch { create() }
            }
        }

        error?.let {
            Text(it, style = Theme.caption(), color = Theme.cork)
        }
        Spacer(Modifier.weight(1f))

        Text(
            "Look around first",
            style = Theme.emphasis(14f),
            color = Theme.court,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { session.lookAround() }
                .padding(bottom = 4.dp)
        )
    }
}
