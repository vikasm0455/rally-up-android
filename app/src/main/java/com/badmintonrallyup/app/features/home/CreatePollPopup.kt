// Create poll — centered liquid-glass pop-up (frozen pattern: short forms pop
// up, never sheets). Three fields, one CTA. 1:1 with the iOS CreatePollPopup.

package com.badmintonrallyup.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.PollView
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GlassPopup
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.Theme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class CreatePollRequest(val proposedTime: String, val note: String?)

/** Wheel row height — 3 visible rows = the iOS 96pt wheel frame. */
private val WheelItemHeight = 32.dp

@Composable
fun CreatePollPopup(onDismiss: () -> Unit, onCreated: () -> Unit) {
    // iOS default: today 18:30 (hour/minute wheel).
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = 18)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = 30)
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun create() {
        val hour = centeredIndex(hourState, 24)
        val minute = centeredIndex(minuteState, 60)
        busy = true
        try {
            ApiClient.post<PollView, CreatePollRequest>(
                "/api/polls",
                CreatePollRequest(
                    proposedTime = "%02d:%02d".format(hour, minute),
                    note = if (note.trim().isEmpty()) null else note,
                )
            )
            AppHaptics.success()
            onDismiss()
            onCreated()
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    GlassPopup(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Close,
                contentDescription = null,
                tint = Theme.inkMuted,
                modifier = Modifier.size(13.dp).clickable { onDismiss() }
            )
        }
        Text("Start tonight's poll", style = Theme.emphasis(17f), color = Theme.ink)
        SectionLabel("When are you playing?")
        Box(
            Modifier.fillMaxWidth().height(WheelItemHeight * 3),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(120.dp)
                    .height(WheelItemHeight)
                    .background(Theme.line.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeWheel(state = hourState, count = 24, pad = false)
                TimeWheel(state = minuteState, count = 60, pad = true)
            }
        }
        RallyTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = "Note · optional",
            modifier = Modifier.fillMaxWidth(),
        )
        val err = error
        if (err != null) {
            Text(err, style = Theme.caption(12f), color = Theme.cork)
        }
        GlassButton(if (busy) "Posting…" else "Post the poll", enabled = !busy) {
            scope.launch { create() }
        }
        Text(
            "Everyone in the group gets a nudge",
            style = Theme.caption(11f), color = Theme.inkMuted
        )
    }
}

// MARK: Hour/minute wheel column — the SwiftUI .wheel DatePicker analog
// (snap-to-center LazyColumn, 3 visible rows).

@Composable
private fun TimeWheel(state: LazyListState, count: Int, pad: Boolean) {
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val selected by remember(state) {
        derivedStateOf { centeredIndex(state, count) }
    }
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = WheelItemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp).height(WheelItemHeight * 3),
    ) {
        items(count) { value ->
            Box(
                Modifier.height(WheelItemHeight).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (pad) "%02d".format(value) else "$value",
                    style = Theme.body(20f),
                    color = if (value == selected) Theme.ink else Theme.inkMuted,
                )
            }
        }
    }
}

/**
 * Index of the row sitting in the wheel's center slot. Uses layoutInfo to pick
 * the visible item whose center is nearest the viewport center — robust to
 * contentPadding, so the highlighted row and the submitted value always agree.
 */
private fun centeredIndex(state: LazyListState, count: Int): Int {
    val layout = state.layoutInfo
    val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    val nearest = layout.visibleItemsInfo.minByOrNull {
        kotlin.math.abs((it.offset + it.size / 2f) - viewportCenter)
    }
    return (nearest?.index ?: 0).coerceIn(0, count - 1)
}
