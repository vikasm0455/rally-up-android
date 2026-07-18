// Court & Cork — the frozen Skin A tokens (design/DESIGN-SYSTEM.md), 1:1 with
// the iOS Theme.swift. Light/dark resolved automatically; NEVER hardcode
// colors in screens.

package com.badmintonrallyup.app.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badmintonrallyup.app.core.Haptics

object Theme {
    // MARK: Colors (light / dark) — sp/dp scaling handles Dynamic Type parity.
    val court: Color @Composable get() = dynamic(0xFF1A54C9, 0xFF5B8DEF)
    val courtBright: Color @Composable get() = dynamic(0xFF2E6FF2, 0xFF6E9BFF)
    val cork: Color @Composable get() = dynamic(0xFFF4633A, 0xFFFF7A50)
    val chalk: Color @Composable get() = dynamic(0xFFF4F6F9, 0xFF0A1428)   // app background
    val surface: Color @Composable get() = dynamic(0xFFFFFFFF, 0xFF122040) // cards
    val ink: Color @Composable get() = dynamic(0xFF0E1B33, 0xFFEAF0FA)     // primary text
    val inkMuted: Color @Composable get() = dynamic(0xFF5A6A85, 0xFF93A3BF)
    val line: Color @Composable get() = dynamic(0xFFE3E9F2, 0xFF233457)    // hairlines
    val success: Color @Composable get() = dynamic(0xFF1FA55C, 0xFF34C878)
    val amber: Color @Composable get() = dynamic(0xFFF5A623, 0xFFFFBE45)

    // MARK: Type — the 400/500/600 ladder. Nothing heavier, ever.
    fun display(size: Float = 28f) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium)
    fun body(size: Float = 16f) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Normal)
    fun emphasis(size: Float = 16f) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium)
    fun caption(size: Float = 13f) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Normal)
    fun label(size: Float = 11f) = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Medium)

    /** Timer numerals ONLY — the single semibold in the app. */
    fun timer(size: Float = 44f) = TextStyle(
        fontSize = size.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Default
    )

    // MARK: Shape
    val cardRadius: Dp = 16.dp
    val buttonRadius: Dp = 14.dp
    val screenGutter: Dp = 20.dp

    @Composable
    private fun dynamic(light: Long, dark: Long): Color =
        Color(if (isSystemInDarkTheme()) dark else light)
}

// MARK: Liquid glass (DESIGN-SYSTEM §3b) — translucent tint + hairline sheen.
// (Compose has no cross-version backdrop blur; the translucent fill over the
// chalk background reads the same as iOS .ultraThinMaterial in practice.)
@Composable
fun GlassButton(
    title: String,
    modifier: Modifier = Modifier,
    tint: Color = Theme.court,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f), label = "press"
    )
    val shape = RoundedCornerShape(Theme.buttonRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .background(tint.copy(alpha = 0.68f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.38f), shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                enabled = enabled
            ) { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(title, style = Theme.emphasis(15f), color = Color.White)
    }
}

// MARK: Haptics map (DESIGN-SYSTEM §4) — same call sites as iOS.
object AppHaptics {
    fun voteCast() = Haptics.light()
    fun courtLogged() = Haptics.medium()
    fun timerWarning() = Haptics.warning()
    fun success() = Haptics.success()
    fun destructive() = Haptics.heavy()
}
