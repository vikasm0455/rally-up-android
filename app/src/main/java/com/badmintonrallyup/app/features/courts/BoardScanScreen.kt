// Board scan — FULL-SCREEN camera flow. Photo of the venue's status board →
// OCR matches YOUR group's posted logins onto courts → review → one tap logs
// everything with timers read off the board. Works with any venue's board.
// 1:1 port of the iOS BoardScanView.

package com.badmintonrallyup.app.features.courts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.ApiUUID
import com.badmintonrallyup.app.api.BoardMatch
import com.badmintonrallyup.app.api.BoardScanResult
import com.badmintonrallyup.app.api.ReservationView
import com.badmintonrallyup.app.core.Capture
import com.badmintonrallyup.app.core.rememberCameraCapture
import com.badmintonrallyup.app.core.rememberPhotoPicker
import com.badmintonrallyup.app.designsystem.ActionTile
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class LogCourtReq(
    val courtNumber: Short,
    val courtType: String,
    val credentialIds: List<ApiUUID>,
    val playerCount: Short,
    val durationMinutes: Short,
    val startType: String,
    val queueNumber: Short,
    val notes: String? = null,
)

@Composable
fun BoardScanScreen(onDone: () -> Unit, onClose: () -> Unit) {
    var result by remember { mutableStateOf<BoardScanResult?>(null) }
    var picked by remember { mutableStateOf(setOf<Short>()) }
    var scanning by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val cameraAvailable = Capture.cameraAvailable(LocalContext.current)

    suspend fun scan(jpeg: ByteArray) {
        // Normalization already happened inside the capture helpers.
        scanning = true
        try {
            val r: BoardScanResult = ApiClient.upload("/api/reservations/scan-board", jpeg)
            result = r
            picked = r.matches
                .filter { m -> !m.credentialIds.all { m.alreadyInUseIds.contains(it) } }
                .map { it.courtNumber }
                .toSet()
            if (r.matches.isNotEmpty()) AppHaptics.success()
        } catch (e: Exception) {
            error = e.message
        } finally {
            scanning = false
        }
    }

    suspend fun logPicked() {
        val r = result ?: return
        busy = true
        try {
            var failures = 0
            for (m in r.matches) {
                if (!picked.contains(m.courtNumber)) continue
                val usable = m.credentialIds.filter { !m.alreadyInUseIds.contains(it) }
                val req = LogCourtReq(
                    courtNumber = m.courtNumber,
                    courtType = "full",
                    credentialIds = usable,
                    playerCount = maxOf(1, m.playerCount.toInt()).toShort(),
                    durationMinutes = ((m.minutesLeft?.toInt() ?: 45).coerceIn(1, 45)).toShort(),
                    startType = "now",
                    queueNumber = m.queuePosition ?: 1.toShort(),
                    notes = null,
                )
                try {
                    ApiClient.post<ReservationView, LogCourtReq>("/api/reservations", req)
                } catch (e: Exception) {
                    failures += 1
                }
            }
            if (failures > 0) {
                error = "$failures court${if (failures == 1) "" else "s"} couldn't be logged — maybe already on the board."
            } else {
                AppHaptics.courtLogged()
                onClose()
                onDone()
            }
        } finally {
            busy = false
        }
    }

    val camera = rememberCameraCapture { data -> scope.launch { scan(data) } }
    val pickPhoto = rememberPhotoPicker { data -> scope.launch { scan(data) } }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Scan board", navIcon = Icons.Outlined.Close, onNav = onClose)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (cameraAvailable) {
                CaptureCard(
                    scanning = scanning,
                    hasResult = result != null,
                    onTap = { if (!scanning) camera() }
                )
                ActionTile(
                    title = "Choose from library",
                    icon = Icons.Outlined.PhotoLibrary,
                    modifier = Modifier.fillMaxWidth()
                ) { if (!scanning) pickPhoto() }
            } else {
                CaptureCard(
                    scanning = scanning,
                    hasResult = result != null,
                    onTap = { if (!scanning) pickPhoto() }
                )
            }

            result?.let { r ->
                if (r.matches.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.GridView,
                        title = "No matches on this board",
                        message = r.message
                    )
                } else {
                    SectionLabel("Matched · review before logging")
                    for (m in r.matches) {
                        MatchCard(
                            m = m,
                            isPicked = picked.contains(m.courtNumber),
                            onToggle = {
                                picked = if (picked.contains(m.courtNumber)) {
                                    picked - m.courtNumber
                                } else {
                                    picked + m.courtNumber
                                }
                                AppHaptics.voteCast()
                            }
                        )
                    }
                    error?.let {
                        Text(it, style = Theme.caption(12f), color = Theme.cork)
                    }
                    GlassButton(
                        if (busy) "Logging…" else "Log ${picked.size} court${if (picked.size == 1) "" else "s"}",
                        enabled = !busy && picked.isNotEmpty()
                    ) { scope.launch { logPicked() } }
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(scanning: Boolean, hasResult: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(Theme.cardRadius)
    val court = Theme.court
    val dashColor = court.copy(alpha = 0.3f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(court.copy(alpha = 0.06f), shape)
            .drawBehind {
                val strokeWidth = 1.2.dp.toPx()
                val inset = strokeWidth / 2
                drawRoundRect(
                    color = dashColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(Theme.cardRadius.toPx() - inset),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
            .clickable { onTap() }
            .padding(vertical = if (!hasResult) 30.dp else 14.dp)
    ) {
        Icon(
            if (scanning) Icons.Outlined.HourglassEmpty else Icons.Outlined.CenterFocusWeak,
            null, Modifier.size(30.dp), tint = court
        )
        Text(
            if (scanning) "Reading the board…" else if (!hasResult) "Snap the status board" else "Retake",
            style = Theme.emphasis(14f), color = court
        )
        if (!hasResult) {
            Text(
                "We match only your group's posted logins — other courts are ignored.",
                style = Theme.caption(11.5f), color = Theme.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MatchCard(m: BoardMatch, isPicked: Boolean, onToggle: () -> Unit) {
    val queued = m.location == "queue"
    val usable = m.credentialIds.filter { !m.alreadyInUseIds.contains(it) }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .alpha(if (usable.isEmpty()) 0.5f else 1f)
            .clickable(enabled = usable.isNotEmpty()) { onToggle() }
            .card(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (isPicked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                null, Modifier.size(20.dp),
                tint = if (isPicked) Theme.court else Theme.line
            )
            Text("Court ${m.courtNumber}", style = Theme.emphasis(15f), color = Theme.ink)
            StatusBadge(
                text = if (queued) "queue ${m.queuePosition ?: 1}" else "playing",
                kind = if (queued) BadgeKind.Queue else BadgeKind.Full
            )
            Spacer(Modifier.weight(1f))
            m.minutesLeft?.let { mins ->
                Text(
                    if (queued) "starts $mins min" else "$mins min left",
                    style = Theme.caption(12f), color = Theme.inkMuted
                )
            }
        }
        Text(
            m.bintangNames.joinToString(", "),
            style = Theme.caption(12f), color = Theme.court,
            modifier = Modifier.padding(start = 28.dp)
        )
        m.inUseCourt?.let { court ->
            Text(
                "a matched login is already on court $court — it'll be skipped",
                style = Theme.caption(10.5f), color = Theme.amber,
                modifier = Modifier.padding(start = 28.dp)
            )
        }
    }
}
