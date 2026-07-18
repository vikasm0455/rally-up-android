// Tonight's polls — one votable card per group that has a poll today.
// Voting works per card with NO group switching (membership-based backend).
// Reached from the Home Tonight card; poll pushes land here too.
// 1:1 port of iOS TonightPollsView.swift.

package com.badmintonrallyup.app.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ThumbUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.PollView
import com.badmintonrallyup.app.api.TonightPoll
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.GroupDotChip
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import com.badmintonrallyup.app.designsystem.groupChipColor
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class TonightVoteReq(val vote: String)

@Composable
fun TonightPollsScreen(onChanged: () -> Unit, onBack: () -> Unit) {
    var polls by remember { mutableStateOf(listOf<TonightPoll>()) }
    var busyPoll by remember { mutableStateOf<UUID?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var openPollId by rememberSaveable { mutableStateOf<UUID?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        polls = try {
            ApiClient.get<List<TonightPoll>>("/api/polls/tonight")
        } catch (_: Exception) {
            polls
        }
        loaded = true
    }

    suspend fun vote(poll: TonightPoll, option: String) {
        busyPoll = poll.id
        try {
            ApiClient.put<PollView, TonightVoteReq>(
                "/api/polls/${poll.id}/vote", TonightVoteReq(vote = option)
            )
            AppHaptics.voteCast()
            load()
            onChanged()
        } catch (_: Exception) {
            // Same as iOS `try?` — a failed vote leaves the card unchanged.
        } finally {
            busyPoll = null
        }
    }

    // navigationDestination(for: UUID.self) → PollDetailView(pollId:)
    val openId = openPollId
    if (openId != null) {
        BackHandler { openPollId = null }
        PollDetailScreen(pollId = openId, onBack = { openPollId = null })
        return
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(
            title = "Tonight's polls",
            navIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            onNav = onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loaded && polls.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.ThumbUp,
                    title = "No polls tonight yet",
                    message = "When any of your groups starts one, it shows up here."
                )
            }
            polls.forEachIndexed { index, poll ->
                PollCard(
                    poll = poll,
                    tint = groupChipColor(index),
                    busy = busyPoll == poll.id,
                    onOpen = { openPollId = poll.id },
                    onVote = { option -> scope.launch { vote(poll, option) } }
                )
            }
        }
    }

    LaunchedEffect(Unit) { load() }
}

@Composable
private fun PollCard(
    poll: TonightPoll,
    tint: Color,
    busy: Boolean,
    onOpen: () -> Unit,
    onVote: (String) -> Unit,
) {
    Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GroupDotChip(name = poll.groupName, tint = tint)
            Spacer(Modifier.weight(1f))
            StatusChip(poll.myVote)
        }
        Row(
            Modifier.fillMaxWidth().clickable { onOpen() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Playing at ${poll.proposedTime}?",
                style = Theme.emphasis(17f), color = Theme.ink
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                Modifier.size(14.dp), tint = Theme.inkMuted
            )
        }
        VotePicker(poll = poll, busy = busy, onVote = onVote)
        Text(countsLine(poll), style = Theme.caption(12f), color = Theme.inkMuted)
    }
}

private fun countsLine(poll: TonightPoll): String {
    if (poll.myVote == null) {
        return if (poll.yesCount == 0L) "No votes yet — be first"
        else "${poll.yesCount} in so far — no vote from you yet"
    }
    return "${poll.yesCount} in · ${poll.maybeCount} maybe"
}

@Composable
private fun StatusChip(vote: String?) {
    val (text, color) = when (vote) {
        "yes" -> "YOU'RE IN ✓" to Theme.success
        "maybe" -> "MAYBE" to Theme.amber
        "no" -> "OUT" to Theme.inkMuted
        else -> "VOTE NOW" to Theme.cork
    }
    Text(
        text,
        style = Theme.emphasis(10.5f),
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun VotePicker(poll: TonightPoll, busy: Boolean, onVote: (String) -> Unit) {
    val shape = RoundedCornerShape(Theme.buttonRadius)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.chalk, shape)
            .border(1.dp, Theme.line, shape)
            .padding(4.dp)
    ) {
        listOf("yes", "maybe", "no").forEach { option ->
            val selected = poll.myVote == option
            Text(
                option.replaceFirstChar { it.uppercase() },
                style = Theme.emphasis(14f),
                color = if (selected) Color.White else Theme.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) Theme.court.copy(alpha = 0.72f) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = !busy && !poll.attendanceLocked) { onVote(option) }
                    .padding(vertical = 9.dp)
            )
        }
    }
}
