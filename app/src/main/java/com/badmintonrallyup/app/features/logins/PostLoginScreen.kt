// Post a login — FULL-SCREEN camera-OCR flow (frozen pattern), 1:1 with the
// iOS PostLoginView. Photo of the kiosk screen or handwritten note → OCR reads
// every login → editable rows → "Post to groups" multi-select (last selection
// remembered, first time none ticked) → post.

package com.badmintonrallyup.app.features.logins

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.RallyUpApp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.ApiUUID
import com.badmintonrallyup.app.api.CredentialView
import com.badmintonrallyup.app.api.EmptyData
import com.badmintonrallyup.app.api.GroupBrief
import com.badmintonrallyup.app.api.OcrResult
import com.badmintonrallyup.app.core.Capture
import com.badmintonrallyup.app.core.rememberCameraCapture
import com.badmintonrallyup.app.core.rememberPhotoPicker
import com.badmintonrallyup.app.designsystem.ActionTile
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GroupDotChip
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import com.badmintonrallyup.app.designsystem.groupChipColor
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

private const val LAST_GROUPS_KEY = "postLogin.lastGroupIds"

private data class EntryRow(
    val id: UUID = UUID.randomUUID(),
    val name: String = "",
    val password: String = "",
)

@Serializable
private data class PostCredentialReq(
    val bintangName: String,
    val bintangPassword: String,
    val screenshotPath: String? = null,
)

@Serializable
private data class ShareReq(val groupIds: List<ApiUUID>)

@Composable
fun PostLoginScreen(onDone: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf(listOf<EntryRow>()) }
    var screenshotPath by remember { mutableStateOf<String?>(null) }
    var ocrMessage by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf(listOf<GroupBrief>()) }
    var shareWith by remember { mutableStateOf(setOf<UUID>()) }
    var ocrBusy by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val validRows = rows.filter { it.name.trim().isNotEmpty() && it.password.trim().isNotEmpty() }

    suspend fun runOcr(data: ByteArray) {
        ocrBusy = true
        try {
            val result: OcrResult = ApiClient.upload("/api/credentials/ocr", data)
            screenshotPath = result.screenshotPath
            val found = result.logins.map { EntryRow(name = it.bintangName, password = it.bintangPassword) }
            rows = rows + found
            ocrMessage = if (found.isEmpty())
                "No logins read — add them below and the photo still attaches."
            else
                "${found.size} login${if (found.size == 1) "" else "s"} read — check and post."
            if (found.isNotEmpty()) AppHaptics.success()
        } catch (e: Exception) {
            error = e.message
        } finally {
            ocrBusy = false
        }
    }

    val snapCamera = rememberCameraCapture { data -> scope.launch { runOcr(data) } }
    val pickPhoto = rememberPhotoPicker { data -> scope.launch { runOcr(data) } }

    LaunchedEffect(Unit) {
        groups = try { ApiClient.get("/api/groups") } catch (e: Exception) { emptyList() }
        val remembered = RallyUpApp.instance
            .getSharedPreferences("rallyup.settings", Context.MODE_PRIVATE)
            .getStringSet(LAST_GROUPS_KEY, emptySet()) ?: emptySet()
        shareWith = remembered.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .toSet()
            .intersect(groups.map { it.id }.toSet())
    }

    fun post() {
        scope.launch {
            busy = true
            try {
                for ((index, row) in validRows.withIndex()) {
                    val created: CredentialView = ApiClient.post(
                        "/api/credentials",
                        PostCredentialReq(
                            bintangName = row.name.trim(),
                            bintangPassword = row.password.trim(),
                            screenshotPath = if (index == 0) screenshotPath else null,
                        )
                    )
                    // The login goes to exactly the ticked groups.
                    try {
                        ApiClient.put<EmptyData, ShareReq>(
                            "/api/credentials/${created.id}/shares", ShareReq(shareWith.toList())
                        )
                    } catch (_: Exception) {}
                }
                RallyUpApp.instance
                    .getSharedPreferences("rallyup.settings", Context.MODE_PRIVATE)
                    .edit().putStringSet(LAST_GROUPS_KEY, shareWith.map { it.toString() }.toSet())
                    .apply()
                AppHaptics.success()
                onDone()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(title = "Post a login", navIcon = Icons.Outlined.Close, onNav = onClose)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Capture card — camera on devices, library fallback elsewhere.
            val court = Theme.court
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(court.copy(alpha = 0.06f), RoundedCornerShape(Theme.cardRadius))
                    .drawBehind {
                        drawRoundRect(
                            color = court.copy(alpha = 0.3f),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(
                                width = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                            )
                        )
                    }
                    .clickable(enabled = !ocrBusy) {
                        if (Capture.cameraAvailable(context)) snapCamera() else pickPhoto()
                    }
                    .padding(vertical = 26.dp, horizontal = 16.dp)
            ) {
                Icon(
                    if (ocrBusy) Icons.Outlined.HourglassEmpty else Icons.Outlined.CenterFocusWeak,
                    null, Modifier.size(32.dp), tint = court
                )
                Text(
                    if (ocrBusy) "Reading logins…" else "Snap the kiosk screen or note",
                    style = Theme.emphasis(14f), color = court
                )
                Text(
                    "Kiosk screens and handwriting both work — several logins in one shot.",
                    style = Theme.caption(11.5f), color = Theme.inkMuted,
                    textAlign = TextAlign.Center
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    "Choose from library", Icons.Outlined.PhotoLibrary,
                    Modifier.weight(1f)
                ) { if (!ocrBusy) pickPhoto() }
                ActionTile(
                    "Type them in", Icons.Outlined.Keyboard,
                    Modifier.weight(1f)
                ) { if (!ocrBusy) rows = rows + EntryRow() }
            }

            ocrMessage?.let {
                Text(it, style = Theme.caption(12f), color = Theme.success)
            }

            if (rows.isNotEmpty()) SectionLabel("Check before posting")
            rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Theme.surface, RoundedCornerShape(Theme.buttonRadius))
                        .border(1.dp, Theme.line, RoundedCornerShape(Theme.buttonRadius))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    RallyTextField(
                        value = row.name,
                        onValueChange = { new ->
                            rows = rows.map { if (it.id == row.id) it.copy(name = new) else it }
                        },
                        placeholder = "Name",
                        textStyle = Theme.emphasis(15f).copy(color = Theme.court),
                        borderColor = Color.Transparent,
                        modifier = Modifier.weight(1f)
                    )
                    RallyTextField(
                        value = row.password,
                        onValueChange = { new ->
                            rows = rows.map { if (it.id == row.id) it.copy(password = new) else it }
                        },
                        placeholder = "Password",
                        textStyle = Theme.emphasis(14f).copy(
                            color = Theme.cork, fontFamily = FontFamily.Monospace
                        ),
                        borderColor = Color.Transparent,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.Cancel, "remove",
                        Modifier
                            .size(20.dp)
                            .clickable { rows = rows.filterNot { it.id == row.id } },
                        tint = Theme.line
                    )
                }
            }

            if (groups.isNotEmpty()) {
                SectionLabel("Post to groups · pick any")
                Column(Modifier.card(4.dp)) {
                    groups.forEachIndexed { index, g ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareWith = if (g.id in shareWith) shareWith - g.id else shareWith + g.id
                                }
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                        ) {
                            Icon(
                                if (g.id in shareWith) Icons.Filled.CheckBox
                                else Icons.Outlined.CheckBoxOutlineBlank,
                                null, Modifier.size(20.dp),
                                tint = if (g.id in shareWith) Theme.court else Theme.line
                            )
                            GroupDotChip(g.name, groupChipColor(index))
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            error?.let {
                Text(it, style = Theme.caption(12f), color = Theme.cork)
            }
            GlassButton(
                if (busy) "Posting…"
                else "Post to ${shareWith.size} group${if (shareWith.size == 1) "" else "s"}",
                enabled = !busy && validRows.isNotEmpty() && shareWith.isNotEmpty()
            ) { post() }
        }
    }
}
