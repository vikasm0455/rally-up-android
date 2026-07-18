// Log a court — FULL-SCREEN page (frozen pattern: long flows never sheet).
// NO group UI here (user ruling): it lists ALL tonight's logins across my
// groups; the court's group derives from the ticked logins (resolve-group).
// Only when they share MORE than one group do we ask which group to post to.
// 1:1 port of the iOS LogCourtView.swift.

package com.badmintonrallyup.app.features.courts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.ApiUUID
import com.badmintonrallyup.app.api.CredentialView
import com.badmintonrallyup.app.api.GroupCandidate
import com.badmintonrallyup.app.api.GroupDetail
import com.badmintonrallyup.app.api.OcrResult
import com.badmintonrallyup.app.api.ReservationView
import com.badmintonrallyup.app.api.inUseLabel
import com.badmintonrallyup.app.core.Capture
import com.badmintonrallyup.app.core.rememberCameraCapture
import com.badmintonrallyup.app.core.rememberPhotoPicker
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GlassPopup
import com.badmintonrallyup.app.designsystem.GroupDotChip
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import com.badmintonrallyup.app.designsystem.groupChipColor
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class PostCredentialReq(
    val bintangName: String,
    val bintangPassword: String,
    val screenshotPath: String?,
)

@Serializable
private data class EditCourtReq(
    val courtNumber: Int,
    val courtType: String,
    val playerCount: Int,
    val durationMinutes: Int,
    val queueNumber: Int,
    val credentialIds: List<ApiUUID>,
)

@Serializable
private data class ResolveGroupReq(
    val credentialIds: List<ApiUUID>,
)

@Serializable
private data class CreateCourtReq(
    val groupId: ApiUUID?,
    val courtNumber: Int,
    val courtType: String,
    val credentialIds: List<ApiUUID>,
    val playerCount: Int,
    val durationMinutes: Int,
    val startType: String,
    val queueNumber: Int,
    val notes: String?,
)

@Composable
fun LogCourtScreen(editing: ReservationView? = null, onDone: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var courtNumber by remember { mutableStateOf("") }
    var courtType by remember { mutableStateOf("full") }
    var credentials by remember { mutableStateOf<List<CredentialView>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<UUID>()) }
    var playerCount by remember { mutableStateOf(4) }
    var duration by remember { mutableStateOf(45) }
    var startsNow by remember { mutableStateOf(true) }
    var queue by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var ocrBusy by remember { mutableStateOf(false) }
    var groupChoices by remember { mutableStateOf<List<GroupCandidate>>(emptyList()) }

    val inRange = run {
        val n = courtNumber.toIntOrNull() ?: return@run false
        val r = range ?: return@run true
        n >= r.first && n <= r.second
    }

    // Camera/library photo → OCR → post the login(s) → they land in the
    // checklist below, pre-ticked for this court.
    suspend fun captureLogin(jpeg: ByteArray) {
        ocrBusy = true
        try {
            val result: OcrResult = ApiClient.upload("/api/credentials/ocr", jpeg)
            if (result.logins.isEmpty()) {
                error = "No login found in that photo — you can post one from the Logins tab."
                return
            }
            val newIds = mutableListOf<UUID>()
            result.logins.forEachIndexed { index, login ->
                try {
                    val created: CredentialView = ApiClient.post(
                        "/api/credentials", PostCredentialReq(
                            bintangName = login.bintangName,
                            bintangPassword = login.bintangPassword,
                            screenshotPath = if (index == 0) result.screenshotPath else null,
                        )
                    )
                    newIds.add(created.id)
                } catch (_: Exception) {
                }
            }
            credentials = try {
                ApiClient.get("/api/credentials/today")
            } catch (_: Exception) {
                credentials
            }
            picked = picked + newIds
            error = null
            AppHaptics.success()
        } catch (e: Exception) {
            error = e.message
        } finally {
            ocrBusy = false
        }
    }

    suspend fun create(groupId: UUID?) {
        val number = courtNumber.toIntOrNull() ?: return
        busy = true
        try {
            ApiClient.post<ReservationView, CreateCourtReq>(
                "/api/reservations", CreateCourtReq(
                    groupId = groupId,
                    courtNumber = number,
                    courtType = courtType,
                    credentialIds = picked.toList(),
                    playerCount = playerCount,
                    durationMinutes = duration,
                    startType = "now",
                    queueNumber = if (startsNow) 1 else queue,
                    notes = null,
                )
            )
            AppHaptics.courtLogged()
            onDone()
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    suspend fun submit() {
        val number = courtNumber.toIntOrNull() ?: return
        if (editing != null) {
            busy = true
            try {
                ApiClient.put<ReservationView, EditCourtReq>(
                    "/api/reservations/${editing.id}", EditCourtReq(
                        courtNumber = number,
                        courtType = courtType,
                        playerCount = playerCount,
                        durationMinutes = duration,
                        queueNumber = if (startsNow) 1 else queue,
                        credentialIds = picked.toList(),
                    )
                )
                AppHaptics.courtLogged()
                onDone()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
            return
        }
        // New court: the group derives from the ticked logins. One shared
        // group → post straight there; several → ask which one.
        busy = true
        val candidates: List<GroupCandidate> = try {
            ApiClient.post("/api/reservations/resolve-group", ResolveGroupReq(picked.toList()))
        } catch (_: Exception) {
            emptyList()
        }
        busy = false
        if (candidates.size > 1) {
            groupChoices = candidates
        } else {
            create(candidates.firstOrNull()?.id)
        }
    }

    val snap = rememberCameraCapture { data -> scope.launch { captureLogin(data) } }
    val pick = rememberPhotoPicker { data -> scope.launch { captureLogin(data) } }
    val cameraAvailable = remember { Capture.cameraAvailable(context) }

    LaunchedEffect(Unit) {
        try {
            val detail: GroupDetail = ApiClient.get("/api/groups/current")
            range = detail.courtMin.toInt() to detail.courtMax.toInt()
        } catch (_: Exception) {
        }
        // Every group's logins — the court maps to a group via who's ticked.
        credentials = try {
            ApiClient.get("/api/credentials/today?scope=all")
        } catch (_: Exception) {
            emptyList()
        }
        if (editing != null) {
            courtNumber = "${editing.courtNumber}"
            courtType = editing.courtType
            picked = editing.attachedCredentialIds.toSet()
            playerCount = editing.playerCount?.toInt() ?: 4
            duration = editing.durationMinutes.toInt()
            val q = editing.queueNumber
            if (q != null && q > 1) {
                startsNow = false
                queue = q.toInt()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(
            title = if (editing == null) "Log a court" else "Edit court ${editing.courtNumber}",
            navIcon = Icons.Outlined.Close,
            onNav = onClose,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Capture-first (user direction): snap the login before any form.
            SectionLabel("Who's playing · snap their login")
            SnapTile(ocrBusy = ocrBusy) { if (cameraAvailable) snap() else pick() }
            Column(
                Modifier.card(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                credentials.forEach { c ->
                    CredentialRow(c = c, isPicked = picked.contains(c.id)) {
                        picked = if (picked.contains(c.id)) picked - c.id else picked + c.id
                        AppHaptics.voteCast()
                    }
                }
                if (credentials.isEmpty()) {
                    Text(
                        "No logins posted tonight — you can still log the court.",
                        style = Theme.caption(12f), color = Theme.inkMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            SectionLabel("Court number")
            RallyTextField(
                value = courtNumber,
                onValueChange = { courtNumber = it },
                placeholder = "14",
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = Theme.emphasis(29f),
                centered = true,
                borderColor = Theme.court.copy(alpha = 0.35f),
            )
            val r = range
            if (r != null) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "Numeric keypad opens instantly",
                        style = Theme.caption(11f), color = Theme.inkMuted
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Your venue: ${r.first}–${r.second}",
                        style = Theme.caption(11f),
                        color = if (inRange || courtNumber.isEmpty()) Theme.inkMuted else Theme.cork
                    )
                }
            }

            Segmented(
                options = listOf("full" to "Full court", "half" to "Half court"),
                selection = courtType,
            ) { courtType = it }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.card(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel("Players")
                    StepperRow(value = playerCount, range = 1..12) { playerCount = it }
                }
            }

            SectionLabel("Duration")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(15, 30, 45).forEach { m ->
                    val sel = duration == m
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (sel) Theme.court.copy(alpha = 0.72f) else Theme.surface,
                                shape
                            )
                            .border(1.dp, if (sel) Color.Transparent else Theme.line, shape)
                            .clip(shape)
                            .clickable { duration = m }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$m min", style = Theme.emphasis(13f),
                            color = if (sel) Color.White else Theme.inkMuted
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.card(12.dp)
            ) {
                Text("Starts now", style = Theme.emphasis(14f), color = Theme.ink)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = startsNow,
                    onCheckedChange = { startsNow = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Theme.court)
                )
            }
            if (!startsNow) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.card(12.dp)
                ) {
                    SectionLabel("Queue position")
                    Spacer(Modifier.weight(1f))
                    StepperRow(value = queue, range = 1..9) { queue = it }
                }
            }

            val err = error
            if (err != null) {
                Text(err, style = Theme.caption(12f), color = Theme.cork)
            }
            GlassButton(
                title = if (busy) "Saving…" else if (editing == null) "Start the clock" else "Save changes",
                enabled = !busy && courtNumber.toIntOrNull() != null && inRange,
            ) { scope.launch { submit() } }
        }
    }

    if (groupChoices.isNotEmpty()) {
        GroupPickPopup(
            candidates = groupChoices,
            onPick = { g ->
                groupChoices = emptyList()
                scope.launch { create(g.id) }
            },
            onCancel = { groupChoices = emptyList() }
        )
    }
}

@Composable
private fun CredentialRow(c: CredentialView, isPicked: Boolean, onToggle: () -> Unit) {
    val disabled = c.inUse && !isPicked
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled) { onToggle() }
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Icon(
            if (isPicked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            null, Modifier.size(18.dp),
            tint = if (isPicked) Theme.court else Theme.line
        )
        Text(
            c.bintangName, style = Theme.body(14f),
            color = if (disabled) Theme.inkMuted else Theme.ink
        )
        Spacer(Modifier.weight(1f))
        StatusBadge(text = c.inUseLabel, kind = if (c.inUse) BadgeKind.Used else BadgeKind.Free)
    }
}

@Composable
private fun SnapTile(ocrBusy: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(Theme.buttonRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.7f), shape)
            .border(1.dp, Theme.court.copy(alpha = 0.25f), shape)
            .clip(shape)
            .clickable(enabled = !ocrBusy) { onTap() }
            .padding(vertical = 13.dp)
    ) {
        Icon(
            if (ocrBusy) Icons.Outlined.HourglassEmpty else Icons.Outlined.CenterFocusWeak,
            null, Modifier.size(16.dp), tint = Theme.court
        )
        Text(
            if (ocrBusy) "Reading the login…" else "Snap a login",
            style = Theme.emphasis(13f), color = Theme.court
        )
    }
}

// Asked ONLY when the ticked logins share more than one group —
// centered glass pop-up (frozen pattern: short questions pop up).
@Composable
private fun GroupPickPopup(
    candidates: List<GroupCandidate>,
    onPick: (GroupCandidate) -> Unit,
    onCancel: () -> Unit,
) {
    GlassPopup(onDismiss = onCancel) {
        Text("Which group gets this court?", style = Theme.emphasis(16f), color = Theme.ink)
        Text(
            "Everyone you ticked plays in more than one of your groups.",
            style = Theme.caption(12f), color = Theme.inkMuted,
            textAlign = TextAlign.Center
        )
        val shape = RoundedCornerShape(Theme.buttonRadius)
        candidates.forEachIndexed { index, g ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Theme.surface, shape)
                    .border(1.dp, Theme.line, shape)
                    .clip(shape)
                    .clickable { onPick(g) }
                    .padding(vertical = 11.dp, horizontal = 12.dp)
            ) {
                GroupDotChip(name = g.name, tint = groupChipColor(index))
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
                    Modifier.size(12.dp), tint = Theme.inkMuted
                )
            }
        }
        Text(
            "Cancel", style = Theme.caption(13f), color = Theme.inkMuted,
            modifier = Modifier.clickable { onCancel() }
        )
    }
}

@Composable
private fun Segmented(
    options: List<Pair<String, String>>,
    selection: String,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.surface, CircleShape)
            .border(1.dp, Theme.line, CircleShape)
            .padding(3.dp)
    ) {
        options.forEach { (key, label) ->
            val sel = selection == key
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (sel) Theme.court.copy(alpha = 0.72f) else Color.Transparent,
                        CircleShape
                    )
                    .clip(CircleShape)
                    .clickable { onSelect(key) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, style = Theme.emphasis(13f),
                    color = if (sel) Color.White else Theme.inkMuted
                )
            }
        }
    }
}

@Composable
private fun StepperRow(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }) {
            Icon(Icons.Outlined.Remove, "decrease", Modifier.size(18.dp), tint = Theme.court)
        }
        Text("$value", style = Theme.emphasis(15f), color = Theme.ink)
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }) {
            Icon(Icons.Outlined.Add, "increase", Modifier.size(18.dp), tint = Theme.court)
        }
    }
}
