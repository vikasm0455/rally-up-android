// Venue setup — every facility numbers its courts differently. Group admins
// set the range here; court entry validates against it everywhere.
// 1:1 port of iOS VenueSetupView.swift.

package com.badmintonrallyup.app.features.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.CenterFocusWeak
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.GroupDetail
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class VenueSaveReq(
    val venueName: String?,
    val courtMin: Int,
    val courtMax: Int,
)

@Composable
fun VenueSetupScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var venueName by remember { mutableStateOf("") }
    var courtMin by remember { mutableStateOf("1") }
    var courtMax by remember { mutableStateOf("24") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val valid = run {
        val lo = courtMin.toIntOrNull()
        val hi = courtMax.toIntOrNull()
        lo != null && hi != null && lo >= 1 && hi >= lo && hi <= 500
    }

    // iOS receives `detail` from the parent (onAppear prefill); the pinned
    // Android signature doesn't, so prefill from the same endpoint the
    // parent settings page loads.
    LaunchedEffect(Unit) {
        try {
            val detail: GroupDetail = ApiClient.get("/api/groups/current")
            venueName = detail.venueName
            courtMin = "${detail.courtMin}"
            courtMax = "${detail.courtMax}"
        } catch (_: Exception) {
            // Keep the defaults — same behavior as iOS when pushed without data.
        }
    }

    suspend fun save() {
        busy = true
        try {
            val trimmedEmpty = venueName.trim().isEmpty()
            @Suppress("UNUSED_VARIABLE")
            val result: GroupDetail = ApiClient.put(
                "/api/groups/venue",
                VenueSaveReq(
                    venueName = if (trimmedEmpty) null else venueName,
                    courtMin = courtMin.toIntOrNull() ?: 1,
                    courtMax = courtMax.toIntOrNull() ?: 24,
                )
            )
            AppHaptics.success()
            onBack()
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(
            title = "Your venue",
            navIcon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            onNav = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Set your venue's court numbers so court entry validates correctly for the whole group.",
                style = Theme.caption(), color = Theme.inkMuted
            )

            SectionLabel("Courts numbered")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberField(courtMin, { courtMin = it }, Modifier.weight(1f))
                Text("to", style = Theme.caption(), color = Theme.inkMuted)
                NumberField(courtMax, { courtMax = it }, Modifier.weight(1f))
            }

            SectionLabel("Venue name · optional")
            RallyTextField(
                value = venueName,
                onValueChange = { venueName = it },
                placeholder = "Riverside Sports Hall",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.card(padding = 12.dp)
            ) {
                Icon(
                    Icons.Outlined.CenterFocusWeak, null,
                    Modifier.size(18.dp), tint = Theme.court
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Court map — coming soon", style = Theme.emphasis(13f), color = Theme.ink)
                    Text(
                        "Snap your venue's layout and the app learns it. Board scan already adapts to any venue's status board.",
                        style = Theme.caption(11.5f), color = Theme.inkMuted
                    )
                }
            }

            error?.let {
                Text(it, style = Theme.caption(12f), color = Theme.cork)
            }
            GlassButton(
                if (busy) "Saving…" else "Save venue",
                enabled = !busy && valid,
            ) { scope.launch { save() } }
        }
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    RallyTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "",
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = Theme.emphasis(26f),
        centered = true,
        borderColor = Theme.court.copy(alpha = 0.35f),
    )
}
