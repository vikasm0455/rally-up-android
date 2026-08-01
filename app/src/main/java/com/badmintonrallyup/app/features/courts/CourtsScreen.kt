// Courts — Flow 3, to fidelity: full-width court rows with timer rings + Done/
// Edit, live countdown, completed section. 1:1 port of the iOS CourtsView.

package com.badmintonrallyup.app.features.courts

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.EmptyData
import com.badmintonrallyup.app.api.ReservationView
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.CourtClock
import com.badmintonrallyup.app.designsystem.CreateOrJoinBanner
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.FullScreenCover
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.TimerRing
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.delay
import com.badmintonrallyup.app.features.more.ModerationAction
import com.badmintonrallyup.app.features.more.ModerationConfirmPopup
import com.badmintonrallyup.app.features.more.ModerationMenu
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun CourtsScreen() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    var reservations by remember { mutableStateOf<List<ReservationView>>(emptyList()) }
    var showLogCourt by rememberSaveable { mutableStateOf(false) }
    var editingReservation by remember { mutableStateOf<ReservationView?>(null) }
    var moderating by remember { mutableStateOf<ModerationAction?>(null) }


    // TimelineView(.periodic(from: .now, by: 1)) → 1s ticker.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Instant.now()
        }
    }

    val active = reservations.filter { it.status == "active" }
    val completed = reservations.filter { it.status == "completed" }

    suspend fun load() {
        if (!session.hasActiveGroup) {
            reservations = emptyList()
            return
        }
        reservations = try {
            ApiClient.get<List<ReservationView>>("/api/reservations/today")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun complete(r: ReservationView) {
        try {
            ApiClient.put<EmptyData, Int>("/api/reservations/${r.id}/complete", null)
        } catch (_: Exception) {
        }
        AppHaptics.success()
        load()
    }

    LaunchedEffect(Unit) { load() }

    moderating?.let { action ->
        ModerationConfirmPopup(
            action = action,
            onDone = { moderating = null; scope.launch { load() } },
            onCancel = { moderating = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Theme.screenGutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // navigationTitle("Courts") + topBarTrailing plus button.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Courts", style = Theme.display(24f), color = Theme.ink)
            Spacer(Modifier.weight(1f))
            if (session.hasActiveGroup) {
                Icon(
                    Icons.Outlined.Add, "log court",
                    Modifier.size(22.dp).clickable { showLogCourt = true },
                    tint = Theme.court
                )
            }
        }
        if (!session.hasActiveGroup) {
            CreateOrJoinBanner { session.leaveExploring() }
            EmptyState(
                icon = Icons.Outlined.GridView,
                title = "Live court timers show here",
                message = "In a group, every court in play appears with its countdown."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Active · ${active.size}")
                if (active.isEmpty()) {
                    Text(
                        "No courts in play right now.",
                        style = Theme.caption(), color = Theme.inkMuted,
                        modifier = Modifier.card()
                    )
                } else {
                    active.forEach { r ->
                        CourtRow(
                            r = r, now = now,
                            mine = r.reservedBy == session.me?.id,
                            onDone = { scope.launch { complete(r) } },
                            onEdit = { editingReservation = r },
                            onModerate = { moderating = it }
                        )
                    }
                }
            }
            if (completed.isNotEmpty()) {
                Box(Modifier.padding(top = 4.dp)) {
                    SectionLabel("Completed today · ${completed.size}")
                }
                completed.forEach { r ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .alpha(0.6f)
                            .card(11.dp)
                    ) {
                        Text("Court ${r.courtNumber}", style = Theme.emphasis(13f), color = Theme.ink)
                        Text("· ${r.reservedByName}", style = Theme.caption(12f), color = Theme.inkMuted)
                        Spacer(Modifier.weight(1f))
                        Text("${r.durationMinutes} min", style = Theme.caption(12f), color = Theme.inkMuted)
                    }
                }
            }
        }
    }

    if (showLogCourt) {
        FullScreenCover(onDismiss = { showLogCourt = false }) {
            LogCourtScreen(
                onDone = {
                    showLogCourt = false
                    scope.launch { load() }
                },
                onClose = { showLogCourt = false }
            )
        }
    }
    editingReservation?.let { r ->
        FullScreenCover(onDismiss = { editingReservation = null }) {
            LogCourtScreen(
                editing = r,
                onDone = {
                    editingReservation = null
                    scope.launch { load() }
                },
                onClose = { editingReservation = null }
            )
        }
    }
}

@Composable
private fun CourtRow(
    r: ReservationView,
    now: Instant,
    mine: Boolean,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onModerate: (ModerationAction) -> Unit,
) {
    val queued = now.isBefore(r.startAt)
    val frac = if (queued) 0.15 else CourtClock.fraction(start = r.startAt, expiry = r.expiryAt, now = now)
    val target = if (queued) r.startAt else r.expiryAt
    val color = when {
        queued -> Theme.amber
        r.expiryAt.toEpochMilli() - now.toEpochMilli() < 300_000 -> Theme.cork
        else -> Theme.court
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.card(12.dp)
    ) {
        TimerRing(
            progress = frac,
            time = CourtClock.mmss(until = target, now = now),
            color = color,
            diameter = 64.dp
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Court ${r.courtNumber}", style = Theme.emphasis(15f), color = Theme.ink)
                StatusBadge(
                    text = if (queued) "queue ${r.queueNumber ?: 1}" else "full",
                    kind = if (queued) BadgeKind.Queue else BadgeKind.Full
                )
            }
            Text(
                r.attachedLogins ?: "logged by ${r.reservedByName}",
                style = Theme.caption(11.5f), color = Theme.inkMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MiniButton("Done", Theme.success, onDone)
            MiniButton("Edit", Theme.court, onEdit)
        }
        ModerationMenu(
            reportLabel = "court", reportType = "reservation", reportId = r.id,
            blockUserId = if (mine) null else r.reservedBy,
            blockName = if (mine) null else r.reservedByName,
        ) { onModerate(it) }
    }
}

@Composable
private fun MiniButton(title: String, color: Color, onClick: () -> Unit) {
    Text(
        title, style = Theme.emphasis(11f), color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}
