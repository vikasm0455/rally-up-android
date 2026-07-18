// Shared UI vocabulary for the frozen Court & Cork language — 1:1 with the
// iOS Components.swift. Screens compose these; never re-implement cards,
// rings, badges, or labels per-screen.

package com.badmintonrallyup.app.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant

// MARK: Court timing math (shared by Home rail + Courts tab)

object CourtClock {
    /** Fraction of the booking still remaining (1 at start → 0 at expiry). */
    fun fraction(start: Instant, expiry: Instant, now: Instant): Double {
        val total = (expiry.toEpochMilli() - start.toEpochMilli()).toDouble()
        if (total <= 0) return 0.0
        return ((expiry.toEpochMilli() - now.toEpochMilli()) / total).coerceIn(0.0, 1.0)
    }

    fun mmss(until: Instant, now: Instant): String {
        val s = ((until.toEpochMilli() - now.toEpochMilli()) / 1000.0).let {
            kotlin.math.max(0L, Math.round(it))
        }
        return "%d:%02d".format(s / 60, s % 60)
    }
}

// MARK: Card container (opaque surface + hairline — cards stay solid)

@Composable
fun Modifier.card(padding: Dp = 14.dp): Modifier {
    val shape = RoundedCornerShape(Theme.cardRadius)
    return this
        .fillMaxWidth()
        .background(Theme.surface, shape)
        .border(1.dp, Theme.line, shape)
        .padding(padding)
}

// MARK: Uppercase section/eyebrow label

@Composable
fun SectionLabel(text: String, color: Color = Theme.inkMuted) {
    Text(
        text.uppercase(),
        style = Theme.label().copy(letterSpacing = 0.9.sp),
        color = color
    )
}

// MARK: Timer ring — the app's signature court-timer element

@Composable
fun TimerRing(
    progress: Double,
    time: String,
    caption: String? = null,
    color: Color = Theme.court,
    diameter: Dp = 74.dp,
) {
    val track = Theme.line
    val ink = Theme.ink
    val muted = Theme.inkMuted
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val inset = 2.5.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = stroke)
            drawArc(
                color, -90f, (360.0 * progress.coerceIn(0.001, 1.0)).toFloat(),
                false, topLeft, arcSize, style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(time, style = Theme.timer(15f), color = ink)
            if (caption != null) {
                Text(caption, fontSize = 8.5.sp, fontWeight = FontWeight.Medium, color = muted)
            }
        }
    }
}

// MARK: Status pill (FULL / QUEUE / LIVE / FREE / IN USE …)

enum class BadgeKind { Full, Queue, Live, Free, Used, Admin }

@Composable
fun StatusBadge(text: String, kind: BadgeKind) {
    val fg = when (kind) {
        BadgeKind.Full, BadgeKind.Free -> Theme.success
        BadgeKind.Queue -> Theme.amber
        BadgeKind.Live -> Theme.cork
        BadgeKind.Used -> Theme.inkMuted
        BadgeKind.Admin -> Theme.court
    }
    Text(
        text.uppercase(),
        fontSize = 9.5.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
        color = fg,
        modifier = Modifier
            .background(fg.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// MARK: Glass chip (small pill actions)

@Composable
fun GlassChip(text: String, icon: ImageVector? = null) {
    val court = Theme.court
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(court.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, court.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = court)
        Text(text, style = Theme.emphasis(12f), color = court)
    }
}

// MARK: "● Group name" chip — tint identifies the group across Tonight surfaces.

val groupChipColors = listOf(
    Color(0xFF1A54C9), Color(0xFF7A54C9), Color(0xFF1FA55C), Color(0xFFF4633A)
)

fun groupChipColor(index: Int): Color = groupChipColors[index % groupChipColors.size]

@Composable
fun GroupDotChip(name: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .background(tint.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Text(name, style = Theme.emphasis(11f), color = tint)
    }
}

// MARK: Prominent secondary-action tile (capture screens: library / type-in)

@Composable
fun ActionTile(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val court = Theme.court
    val shape = RoundedCornerShape(Theme.buttonRadius)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.7f), shape)
            .border(1.dp, court.copy(alpha = 0.25f), shape)
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = court)
        Text(title, style = Theme.emphasis(12f), color = court)
    }
}

// MARK: Teaching empty state (used across tabs, esp. explore mode)

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.card(padding = 16.dp).padding(vertical = 12.dp)
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = Theme.court.copy(alpha = 0.7f))
        Text(title, style = Theme.emphasis(15f), color = Theme.ink)
        Text(
            message, style = Theme.caption(), color = Theme.inkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

// MARK: "Create or join a group" banner — shown throughout explore mode

@Composable
fun CreateOrJoinBanner(onTap: () -> Unit) {
    val shape = RoundedCornerShape(Theme.cardRadius)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme.court.copy(alpha = 0.9f), shape)
            .clickable { onTap() }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Icon(Icons.Outlined.GroupAdd, null, Modifier.size(17.dp), tint = Color.White)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text("You're just looking around", style = Theme.emphasis(13f), color = Color.White)
            Text(
                "Create or join a group to start playing",
                style = Theme.caption(11f), color = Color.White.copy(alpha = 0.85f)
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(14.dp), tint = Color.White)
    }
}

// MARK: Offline pill

@Composable
fun OfflinePill() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(top = 4.dp)
            .background(Color(0xFF0E1B33).copy(alpha = 0.88f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text("⚡", fontSize = 10.sp, color = Color.White)
        Text("Offline — showing last known state", style = Theme.emphasis(11f), color = Color.White)
    }
}

// MARK: Centered glass pop-up (frozen pattern: short forms pop up, never
// bottom sheets) — scrim + translucent card, same look as the iOS popups.
// Dialog-backed so it layers above the tab bar and handles system back.

@Composable
fun GlassPopup(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .fillMaxWidth()
                    .shadow(30.dp, shape)
                    .background(Theme.chalk.copy(alpha = 0.97f), shape)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* swallow taps inside the card */ }
                    .padding(18.dp),
                content = content
            )
        }
    }
}

// MARK: Screen chrome — inline nav bar (title + leading icon), shared by all
// pushed pages and full-screen covers so every screen matches the iOS bars.

@Composable
fun ScreenTopBar(
    title: String,
    navIcon: ImageVector,
    onNav: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Box(
            Modifier
                .size(34.dp)
                .background(Theme.surface, CircleShape)
                .border(1.dp, Theme.line, CircleShape)
                .clickable { onNav() },
            contentAlignment = Alignment.Center
        ) {
            Icon(navIcon, "back", Modifier.size(16.dp), tint = Theme.court)
        }
        Text(
            title, style = Theme.emphasis(17f), color = Theme.ink,
            modifier = Modifier.align(Alignment.Center)
        )
        if (trailing != null) {
            Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

// MARK: Text field in the kiosk vocabulary (surface bg + hairline)

@Composable
fun RallyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle? = null,
    centered: Boolean = false,
    borderColor: Color? = null,
) {
    val shape = RoundedCornerShape(Theme.buttonRadius)
    val style = (textStyle ?: Theme.body(15f)).copy(
        color = Theme.ink,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(Theme.court),
        modifier = modifier
            .background(Theme.surface, shape)
            .border(1.dp, borderColor ?: Theme.line, shape),
        decorationBox = { inner ->
            Box(
                Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = style.copy(color = Theme.inkMuted.copy(alpha = 0.6f)))
                }
                inner()
            }
        }
    )
}

// MARK: Full-screen cover — the iOS fullScreenCover analog for long flows
// (log court, post login, board scan). Covers the tab bar; back = dismiss.

@Composable
fun FullScreenCover(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(Modifier.fillMaxSize().background(Theme.chalk)) { content() }
    }
}
