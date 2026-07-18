// Poll detail — pushed from Home: full vote list, attendance confirm + lock-in
// (walk-ins addable), admin unlock. Flow 2 of the frozen design.
// 1:1 port of the iOS PollDetailView.swift.

package com.badmintonrallyup.app.features.home

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.badmintonrallyup.app.api.ApiUUID
import com.badmintonrallyup.app.api.PollView
import com.badmintonrallyup.app.api.PublicUser
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

@Composable
fun PollDetailScreen(pollId: UUID, onBack: () -> Unit) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var poll by remember { mutableStateOf<PollView?>(null) }
    var members by remember { mutableStateOf(listOf<PublicUser>()) }
    var here by remember { mutableStateOf(setOf<UUID>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isGroupAdmin = session.me?.activeGroupRole == "admin"

    suspend fun load() {
        poll = try { ApiClient.get<PollView>("/api/polls/$pollId") } catch (e: Exception) { null }
        members = try { ApiClient.get<List<PublicUser>>("/api/members") } catch (e: Exception) { emptyList() }
        val p = poll
        if (p != null && here.isEmpty()) {
            here = if (p.attendanceLocked) {
                p.attendees.map { it.id }.toSet()
            } else {
                p.votes.filter { it.vote == "yes" }.map { it.userId }.toSet()
            }
        }
    }

    suspend fun confirm(lock: Boolean, ids: List<UUID>) {
        busy = true
        try {
            val updated: PollView =
                ApiClient.post("/api/polls/$pollId/attendance", AttendanceReq(userIds = ids, lock = lock))
            poll = updated
            here = updated.attendees.map { it.id }.toSet()
            AppHaptics.success()
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    fun toggle(id: UUID) {
        here = if (here.contains(id)) here - id else here + id
        AppHaptics.voteCast()
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar("Tonight's poll", Icons.AutoMirrored.Outlined.ArrowBack, onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val p = poll
            if (p != null) {
                Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Proposed ${p.proposedTime}", color = Theme.court)
                    Text("Playing at ${p.proposedTime}?", style = Theme.emphasis(18f), color = Theme.ink)
                    val note = p.note
                    if (!note.isNullOrEmpty()) {
                        Text(note, style = Theme.caption(), color = Theme.inkMuted)
                    }
                    Text(
                        "${p.yesCount} in · ${p.maybeCount} maybe · ${p.noCount} out",
                        style = Theme.caption(12f), color = Theme.inkMuted
                    )
                }

                SectionLabel("Votes")
                Column(Modifier.card(4.dp)) {
                    p.votes.forEach { v ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(v.displayName, style = Theme.body(14f), color = Theme.ink)
                            Spacer(Modifier.weight(1f))
                            StatusBadge(badgeText(v.vote), badgeKind(v.vote))
                        }
                    }
                    if (p.votes.isEmpty()) {
                        Text(
                            "No votes yet", style = Theme.caption(), color = Theme.inkMuted,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 12.dp)
                        )
                    }
                }

                if (p.attendanceLocked) {
                    LockedSection(
                        poll = p,
                        isGroupAdmin = isGroupAdmin,
                        onUnlock = { ids -> scope.launch { confirm(lock = false, ids = ids) } }
                    )
                } else {
                    AttendanceSection(
                        poll = p,
                        members = members,
                        here = here,
                        busy = busy,
                        onToggle = { toggle(it) },
                        onLock = { ids -> scope.launch { confirm(lock = true, ids = ids) } }
                    )
                }

                val err = error
                if (err != null) {
                    Text(err, style = Theme.caption(12f), color = Theme.cork)
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
}

// MARK: Attendance

@Composable
private fun AttendanceSection(
    poll: PollView,
    members: List<PublicUser>,
    here: Set<UUID>,
    busy: Boolean,
    onToggle: (UUID) -> Unit,
    onLock: (List<UUID>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Confirm who's actually here")
        Text(
            "Anyone can tick — walk-ins count even if they didn't vote.",
            style = Theme.caption(11.5f), color = Theme.inkMuted
        )
        Column(Modifier.card(4.dp)) {
            members.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(m.id) }
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (here.contains(m.id)) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        null, Modifier.size(18.dp),
                        tint = if (here.contains(m.id)) Theme.court else Theme.line
                    )
                    Text(m.displayName, style = Theme.body(14f), color = Theme.ink)
                    Spacer(Modifier.weight(1f))
                    if (poll.votes.none { it.userId == m.id }) {
                        Text("didn't vote", style = Theme.caption(10.5f), color = Theme.inkMuted)
                    }
                }
            }
        }
        GlassButton(
            if (busy) "Locking…" else "Lock in ${here.size} attendee${if (here.size == 1) "" else "s"}",
            enabled = !(busy || here.isEmpty())
        ) { onLock(here.toList()) }
        Text(
            "Locking closes voting · a group admin can unlock",
            style = Theme.caption(11f), color = Theme.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LockedSection(
    poll: PollView,
    isGroupAdmin: Boolean,
    onUnlock: (List<UUID>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Locked in · ${poll.attendees.size} playing", color = Theme.success)
        Column(Modifier.card(4.dp)) {
            poll.attendees.forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp), tint = Theme.success)
                    Text(a.displayName, style = Theme.body(14f), color = Theme.ink)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (isGroupAdmin) {
            Text(
                "Unlock voting",
                style = Theme.emphasis(13f), color = Theme.court,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUnlock(poll.attendees.map { it.id }) }
            )
        }
    }
}

// MARK: Data

private fun badgeText(vote: String): String = when (vote) {
    "yes" -> "in"
    "no" -> "out"
    else -> "maybe"
}

private fun badgeKind(vote: String): BadgeKind = when (vote) {
    "yes" -> BadgeKind.Full
    "no" -> BadgeKind.Live
    else -> BadgeKind.Queue
}

@Serializable
private data class AttendanceReq(
    val userIds: List<ApiUUID>,
    val lock: Boolean,
)
