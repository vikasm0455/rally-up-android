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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Key
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
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.CredentialView
import com.badmintonrallyup.app.api.inUseLabel
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.BadgeKind
import com.badmintonrallyup.app.designsystem.CreateOrJoinBanner
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.FullScreenCover
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.StatusBadge
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import com.badmintonrallyup.app.features.more.ModerationAction
import com.badmintonrallyup.app.features.more.ModerationConfirmPopup
import com.badmintonrallyup.app.features.more.ModerationMenu
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
                              onModerate = { moderating = it })
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
private fun LoginCard(c: CredentialView, copied: UUID?, onCopied: (UUID) -> Unit, onModerate: (ModerationAction) -> Unit) {
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
            ModerationMenu(
                reportLabel = "login", reportType = "credential", reportId = c.id,
                blockUserId = if (c.isMine) null else c.postedBy,
                blockName = if (c.isMine) null else c.postedByName,
            ) { onModerate(it) }
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
