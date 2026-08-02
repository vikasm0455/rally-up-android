// Logins — Flow 4, to fidelity: kiosk convention (cobalt name / cork mono
// password, tap to copy), in-use badges, share chips. Camera OCR page next.
// 1:1 port of the iOS LoginsView.swift.

package com.badmintonrallyup.app.features.logins

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.CredentialView
import com.badmintonrallyup.app.api.EmptyData
import com.badmintonrallyup.app.api.inUseLabel
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.CreateOrJoinBanner
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.FullScreenCover
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GlassPopup
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import com.badmintonrallyup.app.features.more.ModerationAction
import com.badmintonrallyup.app.features.more.ModerationConfirmPopup
import com.badmintonrallyup.app.features.more.ModerationMenu
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LoginsScreen() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    var credentials by remember { mutableStateOf(listOf<CredentialView>()) }
    var copied by remember { mutableStateOf<UUID?>(null) }
    var showPost by rememberSaveable { mutableStateOf(false) }
    var moderating by remember { mutableStateOf<ModerationAction?>(null) }
    var editing by remember { mutableStateOf<CredentialView?>(null) }
    var deleting by remember { mutableStateOf<CredentialView?>(null) }

    val load: suspend () -> Unit = {
        if (!session.hasActiveGroup) {
            credentials = emptyList()
        } else {
            credentials = try {
                ApiClient.get<List<CredentialView>>("/api/credentials/today")
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    moderating?.let { action ->
        ModerationConfirmPopup(
            action = action,
            onDone = { moderating = null; scope.launch { load() } },
            onCancel = { moderating = null },
        )
    }
    editing?.let { cred ->
        EditLoginPopup(
            credential = cred,
            onDone = { editing = null; scope.launch { load() } },
            onCancel = { editing = null },
        )
    }
    deleting?.let { cred ->
        DeleteLoginPopup(
            credential = cred,
            onDone = { deleting = null; scope.launch { load() } },
            onCancel = { deleting = null },
        )
    }
    if (showPost) {
        FullScreenCover(onDismiss = { showPost = false }) {
            PostLoginScreen(
                onDone = { showPost = false; scope.launch { load() } },
                onClose = { showPost = false }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Large title + trailing toolbar Post button (iOS navigationTitle/toolbar).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Logins", style = Theme.display(24f), color = Theme.ink)
                Spacer(Modifier.weight(1f))
                if (session.hasActiveGroup) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { showPost = true }
                    ) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(16.dp), tint = Theme.court)
                        Text("Post", style = Theme.emphasis(15f), color = Theme.court)
                    }
                }
            }

            if (!session.hasActiveGroup) {
                CreateOrJoinBanner { session.leaveExploring() }
                EmptyState(
                    icon = Icons.Outlined.Key,
                    title = "Shared court logins live here",
                    message = "Group members post court logins so anyone can grab a free one."
                )
            } else if (credentials.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Key,
                    title = "No logins tonight yet",
                    message = "Post one from the camera and it clears at midnight."
                )
            } else {
                SectionLabel("Tonight · ${credentials.size}")
                credentials.forEach { c ->
                    LoginCard(c, copied, onCopied = { copiedId -> copied = copiedId },
                              onModerate = { moderating = it },
                              onEdit = { editing = it },
                              onDelete = { deleting = it })
                }
                Text(
                    "Clears tonight · visible only to the groups each owner picked",
                    style = Theme.caption(11f), color = Theme.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LoginCard(
    c: CredentialView,
    copied: UUID?,
    onCopied: (UUID) -> Unit,
    onModerate: (ModerationAction) -> Unit,
    onEdit: (CredentialView) -> Unit,
    onDelete: (CredentialView) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clickable {
                clipboard.setText(AnnotatedString(c.bintangPassword))
                onCopied(c.id)
                AppHaptics.voteCast()
            }
            .card()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.bintangName, style = Theme.emphasis(16f), color = Theme.court)
            Spacer(Modifier.weight(1f))
            StatusBadge(
                text = if (c.inUse) c.inUseLabel else "free",
                kind = if (c.inUse) BadgeKind.Used else BadgeKind.Free
            )
            if (c.isMine) {
                // Your own post: self-management, not moderation.
                OwnLoginMenu(onEdit = { onEdit(c) }, onDelete = { onDelete(c) })
            } else {
                ModerationMenu(
                    reportLabel = "login", reportType = "credential", reportId = c.id,
                    blockUserId = c.postedBy,
                    blockName = c.postedByName,
                ) { onModerate(it) }
            }
        }
        Text(
            c.bintangPassword,
            style = Theme.emphasis(16f).copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.sp),
            color = Theme.cork
        )
        if (copied == c.id) {
            Text("Copied", style = Theme.caption(11f), color = Theme.success)
        }
    }
}

/** ⋯ on your own login: Edit / Delete (Report/Block stay on others' posts). */
@Composable
private fun OwnLoginMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Outlined.MoreHoriz, "more",
            Modifier.size(22.dp).clickable { open = true },
            tint = Theme.inkMuted
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit this login") },
                leadingIcon = { Icon(Icons.Outlined.Edit, null, tint = Theme.inkMuted) },
                onClick = { open = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Delete this login", color = Color(0xFFC62828)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = Color(0xFFC62828)) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}

@Serializable
private data class UpdateLoginReq(val bintangName: String, val bintangPassword: String)

/** Owner fixes a typo in their posted login (PUT /api/credentials/:id). */
@Composable
private fun EditLoginPopup(
    credential: CredentialView,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(credential.bintangName) }
    var password by remember { mutableStateOf(credential.bintangPassword) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = name.trim().isNotEmpty() && name.trim().length <= 50 &&
        password.trim().isNotEmpty() && password.trim().length <= 50

    GlassPopup(onDismiss = onCancel) {
        Text("Edit login", style = Theme.emphasis(16f), color = Theme.ink)
        Text(
            "Everyone you shared it with sees the update on their next refresh.",
            style = Theme.caption(12f), color = Theme.inkMuted,
            textAlign = TextAlign.Center
        )
        SectionLabel("Bintang name")
        RallyTextField(value = name, onValueChange = { name = it }, placeholder = "Name")
        SectionLabel("Password")
        RallyTextField(
            value = password, onValueChange = { password = it }, placeholder = "Password",
            textStyle = Theme.emphasis(15f).copy(
                color = Theme.cork, fontFamily = FontFamily.Monospace
            ),
            keyboardOptions = KeyboardOptions(autoCorrect = false)
        )
        error?.let { Text(it, style = Theme.caption(12f), color = Theme.cork) }
        GlassButton(if (busy) "Saving…" else "Save changes", enabled = !busy && valid) {
            scope.launch {
                if (busy) return@launch
                busy = true
                try {
                    ApiClient.put<EmptyData, UpdateLoginReq>(
                        "/api/credentials/${credential.id}",
                        UpdateLoginReq(name.trim(), password.trim())
                    )
                    AppHaptics.success()
                    onDone()
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
        Text(
            "Cancel", style = Theme.caption(13f), color = Theme.inkMuted,
            modifier = Modifier.clickable { onCancel() }
        )
    }
}

/** Owner deletes their posted login (DELETE /api/credentials/:id). */
@Composable
private fun DeleteLoginPopup(
    credential: CredentialView,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassPopup(onDismiss = onCancel) {
        Text("Delete this login?", style = Theme.emphasis(16f), color = Theme.ink)
        Text(
            "It disappears right away for every group you shared it with. Courts already logged with it keep working.",
            style = Theme.caption(12f), color = Theme.inkMuted,
            textAlign = TextAlign.Center
        )
        error?.let { Text(it, style = Theme.caption(12f), color = Theme.cork) }
        GlassButton(
            if (busy) "Working…" else "Delete login",
            tint = Color(0xFFC62828),
            enabled = !busy,
        ) {
            scope.launch {
                if (busy) return@launch
                busy = true
                try {
                    ApiClient.delete<EmptyData>("/api/credentials/${credential.id}")
                    AppHaptics.destructive()
                    onDone()
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        }
        Text(
            "Cancel", style = Theme.caption(13f), color = Theme.inkMuted,
            modifier = Modifier.clickable { onCancel() }
        )
    }
}
