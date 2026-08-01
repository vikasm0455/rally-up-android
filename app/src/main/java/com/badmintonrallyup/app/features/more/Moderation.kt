// Guideline 1.2 (UGC): report objectionable content + block members.
// 1:1 with the iOS Moderation.swift — ⋯ menus on posted cards, glass confirm,
// Blocked members management under More.

package com.badmintonrallyup.app.features.more

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PanTool
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.ApiUUID
import com.badmintonrallyup.app.api.BlockedUser
import com.badmintonrallyup.app.api.EmptyData
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.GlassPopup
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

object Moderation {
    @Serializable
    private data class ReportReq(val contentType: String, val contentId: ApiUUID, val note: String? = null)
    @Serializable
    private data class BlockReq(val userId: ApiUUID)

    suspend fun report(type: String, id: UUID) {
        ApiClient.post<EmptyData, ReportReq>("/api/moderation/report", ReportReq(type, id))
    }

    suspend fun block(userId: UUID) {
        ApiClient.post<EmptyData, BlockReq>("/api/moderation/block", BlockReq(userId))
    }

    suspend fun unblock(userId: UUID) {
        ApiClient.delete<EmptyData>("/api/moderation/block/$userId")
    }

    suspend fun blocked(): List<BlockedUser> = ApiClient.get("/api/moderation/blocked")
}

/** What the ⋯ menu is about to do — drives the shared confirm pop-up. */
sealed class ModerationAction {
    data class Report(val type: String, val id: UUID, val label: String) : ModerationAction()
    data class Block(val userId: UUID, val name: String) : ModerationAction()
}

/** The ⋯ trigger used on every member-posted card. */
@Composable
fun ModerationMenu(
    reportLabel: String,
    reportType: String,
    reportId: UUID,
    blockUserId: UUID? = null,
    blockName: String? = null,
    onSelect: (ModerationAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Outlined.MoreHoriz, "more",
            Modifier.size(22.dp).clickable { open = true },
            tint = Theme.inkMuted
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Report this $reportLabel", color = Color(0xFFC62828)) },
                leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = Color(0xFFC62828)) },
                onClick = {
                    open = false
                    onSelect(ModerationAction.Report(reportType, reportId, reportLabel))
                }
            )
            if (blockUserId != null && blockName != null) {
                DropdownMenuItem(
                    text = { Text("Block $blockName", color = Color(0xFFC62828)) },
                    leadingIcon = { Icon(Icons.Outlined.PanTool, null, tint = Color(0xFFC62828)) },
                    onClick = {
                        open = false
                        onSelect(ModerationAction.Block(blockUserId, blockName))
                    }
                )
            }
        }
    }
}

/** Centered glass confirm (frozen pattern) for report/block. */
@Composable
fun ModerationConfirmPopup(
    action: ModerationAction,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val title = when (action) {
        is ModerationAction.Report -> "Report this ${action.label}?"
        is ModerationAction.Block -> "Block ${action.name}?"
    }
    val message = when (action) {
        is ModerationAction.Report ->
            "It's hidden for you right away and sent to RallyUp for review."
        is ModerationAction.Block ->
            "Everything ${action.name} posted disappears from your app immediately, and RallyUp is notified for review. ${action.name} won't know. You can unblock anytime in More → Blocked members."
    }
    val confirmLabel = when (action) {
        is ModerationAction.Report -> "Report"
        is ModerationAction.Block -> "Block ${action.name}"
    }

    GlassPopup(onDismiss = onCancel) {
        Text(title, style = Theme.emphasis(16f), color = Theme.ink)
        Text(
            message, style = Theme.caption(12f), color = Theme.inkMuted,
            textAlign = TextAlign.Center
        )
        error?.let { Text(it, style = Theme.caption(12f), color = Theme.cork) }
        GlassButton(
            if (busy) "Working…" else confirmLabel,
            tint = Color(0xFFC62828),
            enabled = !busy,
        ) {
            scope.launch {
                busy = true
                try {
                    when (action) {
                        is ModerationAction.Report -> Moderation.report(action.type, action.id)
                        is ModerationAction.Block -> Moderation.block(action.userId)
                    }
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

/** More → Blocked members: list + unblock. */
@Composable
fun BlockedMembersScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf(listOf<BlockedUser>()) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun load() {
        blocked = try { Moderation.blocked() } catch (e: Exception) { blocked }
        loaded = true
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(
            title = "Blocked members",
            navIcon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            onNav = onBack,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loaded && blocked.isEmpty()) {
                EmptyState(
                    Icons.Outlined.PanTool,
                    "No blocked members",
                    "If someone posts something they shouldn't, use the ⋯ menu on their post to report it or block them."
                )
            }
            blocked.forEach { member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.card(12.dp)
                ) {
                    Box(
                        Modifier.size(30.dp).background(Theme.court.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.displayName.take(1), style = Theme.emphasis(12f), color = Theme.court)
                    }
                    Text(member.displayName, style = Theme.emphasis(14f), color = Theme.ink)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Unblock", style = Theme.emphasis(12.5f), color = Theme.court,
                        modifier = Modifier.clickable {
                            scope.launch {
                                try { Moderation.unblock(member.id) } catch (_: Exception) {}
                                load()
                            }
                        }
                    )
                }
            }
            Text(
                "Blocked members can't see that you blocked them. Their posts, polls and courts stay hidden from you everywhere until you unblock.",
                style = Theme.caption(11f), color = Theme.inkMuted
            )
        }
    }
}
