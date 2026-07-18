// Home — Flow 2 daily loop. Group-AGNOSTIC (user ruling): no group selection
// here. The Tonight card shows the poll count across all my groups with a
// status chip per group → Tonight's polls page. Court rail, kcal, quick actions.
// 1:1 port of the iOS HomeView.swift.

package com.badmintonrallyup.app.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.KcalToday
import com.badmintonrallyup.app.api.ReservationView
import com.badmintonrallyup.app.api.TonightPoll
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.CourtClock
import com.badmintonrallyup.app.designsystem.CreateOrJoinBanner
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.FullScreenCover
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GlassPopup
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.TimerRing
import com.badmintonrallyup.app.designsystem.card
import com.badmintonrallyup.app.designsystem.groupChipColor
import com.badmintonrallyup.app.features.courts.BoardScanScreen
import com.badmintonrallyup.app.features.courts.LogCourtScreen
import com.badmintonrallyup.app.features.logins.PostLoginScreen
import com.badmintonrallyup.app.features.more.KcalScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

@Composable
fun HomeScreen() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    var tonight by remember { mutableStateOf(listOf<TonightPoll>()) }
    var reservations by remember { mutableStateOf(listOf<ReservationView>()) }
    var showCreatePoll by rememberSaveable { mutableStateOf(false) }
    var showLogCourt by rememberSaveable { mutableStateOf(false) }
    var showPostLogin by rememberSaveable { mutableStateOf(false) }
    var showBoardScan by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var kcal by remember { mutableStateOf<KcalToday?>(null) }
    var showKcalPopup by rememberSaveable { mutableStateOf(false) }
    var showTonight by rememberSaveable { mutableStateOf(false) }
    var showKcal by rememberSaveable { mutableStateOf(false) }

    suspend fun load() {
        try {
            kcal = try {
                ApiClient.get<KcalToday>("/api/kcal/today")
            } catch (e: Exception) {
                null
            }
            // Tonight spans ALL my groups — no active group needed.
            tonight = try {
                ApiClient.get<List<TonightPoll>>("/api/polls/tonight")
            } catch (e: Exception) {
                emptyList()
            }
            if (!session.hasActiveGroup) {
                reservations = emptyList()
                return
            }
            reservations = try {
                ApiClient.get<List<ReservationView>>("/api/reservations/today")
            } catch (e: Exception) {
                emptyList()
            }
        } finally {
            loaded = true
        }
    }

    // MARK: Pushed sub-pages (NavigationLink analogs)

    if (showTonight) {
        BackHandler { showTonight = false }
        TonightPollsScreen(
            onChanged = { scope.launch { load() } },
            onBack = { showTonight = false }
        )
        return
    }
    if (showKcal) {
        BackHandler { showKcal = false }
        KcalScreen(onBack = { showKcal = false })
        return
    }

    LaunchedEffect(Unit) { load() }

    val firstName = session.me?.displayName?.split(" ")?.first() ?: "there"
    val activeCourts = reservations.filter { it.status == "active" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Theme.screenGutter)
            .alpha(if (loaded || !session.hasActiveGroup) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Good evening, $firstName", style = Theme.display(24f), color = Theme.ink)

        if (!session.hasActiveGroup) {
            CreateOrJoinBanner { session.leaveExploring() }
        }

        // MARK: Tonight (poll count + per-group status → Tonight's polls page)

        if (!session.hasActiveGroup && tonight.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.ThumbUp,
                title = "Tonight's polls live here",
                message = "Create or join a group to vote on when to play."
            )
        } else if (tonight.isEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.card()
            ) {
                SectionLabel("Tonight", color = Theme.court)
                Text("No polls yet today", style = Theme.emphasis(16f), color = Theme.ink)
                Text(
                    "Be the one who gets tonight going.",
                    style = Theme.caption(), color = Theme.inkMuted
                )
                GlassButton("Start tonight's poll") { showCreatePoll = true }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clickable { showTonight = true }
                    .card()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SectionLabel("Tonight", color = Theme.court)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                        Modifier.size(14.dp), tint = Theme.inkMuted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "${tonight.size}",
                        style = Theme.display(26f), color = Theme.ink,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        "poll${if (tonight.size == 1) "" else "s"} tonight",
                        style = Theme.emphasis(14.5f), color = Theme.ink,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    tonight.forEachIndexed { index, poll ->
                        GroupStatusChip(poll, tint = groupChipColor(index))
                    }
                }
            }
        }

        // MARK: Courts rail

        if (session.hasActiveGroup && activeCourts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("On court now")
                var now by remember { mutableStateOf(Instant.now()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1000)
                        now = Instant.now()
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    activeCourts.forEach { r -> CourtCard(r, now = now) }
                }
            }
        }

        // MARK: Calories (private, web-Home parity)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clickable { showKcalPopup = true }
                .card(12.dp)
        ) {
            Icon(
                Icons.Outlined.LocalFireDepartment, null,
                Modifier.size(19.dp), tint = Theme.cork
            )
            val log = kcal?.myLog
            if (log != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${log.kcal}",
                        style = Theme.display(26f), color = Theme.ink,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        "kcal tonight",
                        style = Theme.caption(12f), color = Theme.inkMuted,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            } else {
                Text("Log tonight's burn", style = Theme.emphasis(14f), color = Theme.ink)
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(Theme.success.copy(alpha = 0.12f), CircleShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Filled.Lock, null, Modifier.size(9.dp), tint = Theme.success)
                Text("Only you", style = Theme.emphasis(10.5f), color = Theme.success)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                Modifier
                    .padding(start = 2.dp)
                    .size(14.dp)
                    .clickable { showKcal = true },
                tint = Theme.inkMuted
            )
        }

        // MARK: Quick actions

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (session.hasActiveGroup) 1f else 0.4f)
        ) {
            QuickAction("Log court", Icons.Outlined.AddBox, enabled = session.hasActiveGroup) {
                showLogCourt = true
            }
            QuickAction("Post login", Icons.Outlined.Key, enabled = session.hasActiveGroup) {
                showPostLogin = true
            }
            QuickAction("Scan board", Icons.Outlined.PhotoCamera, enabled = session.hasActiveGroup) {
                showBoardScan = true
            }
        }
    }

    // MARK: Overlays + full-screen covers

    if (showCreatePoll) {
        CreatePollPopup(
            onDismiss = { showCreatePoll = false },
            onCreated = { showCreatePoll = false; scope.launch { load() } }
        )
    }
    if (showKcalPopup) {
        KcalQuickLogPopup(
            initial = kcal?.myLog?.kcal?.toInt() ?: 300,
            onDismiss = { showKcalPopup = false },
            onSaved = { scope.launch { load() } }
        )
    }
    if (showLogCourt) {
        FullScreenCover(onDismiss = { showLogCourt = false }) {
            LogCourtScreen(
                onDone = { showLogCourt = false; scope.launch { load() } },
                onClose = { showLogCourt = false }
            )
        }
    }
    if (showPostLogin) {
        FullScreenCover(onDismiss = { showPostLogin = false }) {
            PostLoginScreen(
                onDone = { showPostLogin = false; scope.launch { load() } },
                onClose = { showPostLogin = false }
            )
        }
    }
    if (showBoardScan) {
        FullScreenCover(onDismiss = { showBoardScan = false }) {
            BoardScanScreen(
                onDone = { showBoardScan = false; scope.launch { load() } },
                onClose = { showBoardScan = false }
            )
        }
    }
}

// MARK: Tonight per-group status chip

@Composable
private fun GroupStatusChip(poll: TonightPoll, tint: Color) {
    val (status, statusColor) = when (poll.myVote) {
        "yes" -> "IN ✓" to Theme.success
        "maybe" -> "MAYBE" to Theme.amber
        "no" -> "OUT" to Theme.inkMuted
        else -> "VOTE" to Theme.cork
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .background(tint.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Text(poll.groupName, style = Theme.emphasis(11f), color = tint)
        Text(
            status,
            style = Theme.emphasis(9.5f),
            color = statusColor,
            modifier = Modifier
                .background(statusColor.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.5.dp)
        )
    }
}

// MARK: Court rail card

@Composable
private fun CourtCard(r: ReservationView, now: Instant) {
    val queued = now.isBefore(r.startAt)
    val frac = if (queued) 0.15 else CourtClock.fraction(start = r.startAt, expiry = r.expiryAt, now = now)
    val target = if (queued) r.startAt else r.expiryAt
    val color = if (queued) Theme.amber
        else if (r.expiryAt.toEpochMilli() - now.toEpochMilli() < 300_000L) Theme.cork
        else Theme.court
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(150.dp)
            .card(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Court ${r.courtNumber}", style = Theme.emphasis(13f), color = Theme.ink)
            Spacer(Modifier.weight(1f))
            StatusBadge(
                text = if (queued) "queue ${r.queueNumber ?: 1}" else "full",
                kind = if (queued) BadgeKind.Queue else BadgeKind.Full
            )
        }
        TimerRing(
            progress = frac,
            time = CourtClock.mmss(until = target, now = now),
            caption = if (queued) "TO START" else "LEFT",
            color = color, diameter = 74.dp
        )
        Text(
            r.attachedLogins ?: r.reservedByName,
            style = Theme.caption(10.5f), color = Theme.inkMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// MARK: Quick action tile

@Composable
private fun RowScope.QuickAction(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled) { onClick() }
            .card(6.dp)
            .padding(vertical = 11.dp)
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = Theme.court)
        Text(title, style = Theme.emphasis(11f), color = Theme.ink)
    }
}

// MARK: Quick calorie log — centered glass pop-up (frozen pattern: short forms pop up).

@Serializable
private data class KcalQuickLogReq(val kcal: Int, val note: String? = null)

@Composable
private fun KcalQuickLogPopup(initial: Int, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun save() {
        busy = true
        try {
            val saved: KcalToday? = try {
                ApiClient.post<KcalToday, KcalQuickLogReq>(
                    "/api/kcal", KcalQuickLogReq(kcal = draft, note = null)
                )
            } catch (e: Exception) {
                null
            }
            if (saved != null) {
                AppHaptics.success()
                onDismiss()
                onSaved()
            }
        } finally {
            busy = false
        }
    }

    GlassPopup(onDismiss = onDismiss) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Outlined.LocalFireDepartment, null,
                Modifier.size(18.dp), tint = Theme.cork
            )
            Text("Tonight's burn", style = Theme.emphasis(16f), color = Theme.ink)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Lock, null, Modifier.size(9.dp), tint = Theme.success)
                Text("Only you", style = Theme.emphasis(10.5f), color = Theme.success)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("$draft", style = Theme.display(34f), color = Theme.ink)
            Text("kcal", style = Theme.caption(), color = Theme.inkMuted)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { draft = (draft - 10).coerceIn(0, 2000) }) {
                    Icon(Icons.Outlined.Remove, null, Modifier.size(18.dp), tint = Theme.court)
                }
                IconButton(onClick = { draft = (draft + 10).coerceIn(0, 2000) }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = Theme.court)
                }
            }
        }
        GlassButton(if (busy) "Saving…" else "Save", enabled = !busy) {
            scope.launch { save() }
        }
        Text(
            "Cancel",
            style = Theme.caption(13f), color = Theme.inkMuted,
            modifier = Modifier.clickable { onDismiss() }
        )
    }
}
