// History — past polls with turnout, newest first. Pushed from More.
// 1:1 port of iOS HistoryView.swift.

package com.badmintonrallyup.app.features.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.PollSummary
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    var polls by remember { mutableStateOf(listOf<PollSummary>()) }
    var loaded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(
            title = "History",
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
            if (polls.isEmpty() && loaded) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = "No sessions yet",
                    message = "Past polls and turnouts collect here."
                )
            } else {
                polls.forEach { p ->
                    Row(
                        Modifier.card(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                formatted(p.gameDate),
                                style = Theme.emphasis(14f), color = Theme.ink
                            )
                            val note = p.note
                            if (!note.isNullOrEmpty()) {
                                Text(
                                    note,
                                    style = Theme.caption(11.5f), color = Theme.inkMuted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "${p.yesCount} in",
                                style = Theme.caption(12f), color = Theme.success
                            )
                            Text(
                                "${p.attendeeCount} played",
                                style = Theme.caption(12f), color = Theme.inkMuted
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        polls = try {
            ApiClient.get<List<PollSummary>>("/api/polls/history")
        } catch (_: Exception) {
            listOf()
        }
        loaded = true
    }
}

private fun formatted(ymd: String): String = try {
    LocalDate.parse(ymd) // yyyy-MM-dd
        .format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
} catch (_: Exception) {
    ymd
}
