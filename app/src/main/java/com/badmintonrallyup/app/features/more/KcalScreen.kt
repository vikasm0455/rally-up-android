// Calories — strictly private to the account (never visible to any group).
// Tonight's burn + 7-session history chart + stats. Flow 6.
// 1:1 port of the iOS KcalView.swift (Swift Charts BarMark → Canvas bars).

package com.badmintonrallyup.app.features.more

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.KcalHistory
import com.badmintonrallyup.app.api.KcalToday
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.SectionLabel
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Serializable
private data class KcalReq(val kcal: Int, val note: String? = null)

@Composable
fun KcalScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var today by remember { mutableStateOf<KcalToday?>(null) }
    var history by remember { mutableStateOf<KcalHistory?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableIntStateOf(300) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        today = try { ApiClient.get<KcalToday>("/api/kcal/today") } catch (e: Exception) { null }
        history = try { ApiClient.get<KcalHistory>("/api/kcal/history") } catch (e: Exception) { null }
    }

    suspend fun save() {
        busy = true
        try {
            val updated: KcalToday = ApiClient.post("/api/kcal", KcalReq(kcal = draft, note = null))
            today = updated
            editing = false
            AppHaptics.success()
            load()
        } catch (e: Exception) {
            // iOS uses try? — failures are silent here.
        } finally {
            busy = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar("Calories", Icons.AutoMirrored.Outlined.ArrowBack, onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Tonight's burn", color = Theme.court)
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Theme.success.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(10.dp), tint = Theme.success)
                    Text("Only you", style = Theme.emphasis(11f), color = Theme.success)
                }
            }

            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val log = today?.myLog
                if (editing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("$draft", style = Theme.timer(34f), color = Theme.ink)
                        Text("kcal", style = Theme.caption(), color = Theme.inkMuted)
                        Spacer(Modifier.weight(1f))
                        // Stepper(0...2000, step: 10), labels hidden — value shown at left.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StepButton(Icons.Outlined.Remove) {
                                draft = (draft - 10).coerceIn(0, 2000)
                            }
                            StepButton(Icons.Outlined.Add) {
                                draft = (draft + 10).coerceIn(0, 2000)
                            }
                        }
                    }
                    GlassButton(
                        if (busy) "Saving…" else "Save tonight's burn",
                        enabled = !busy
                    ) { scope.launch { save() } }
                } else if (log != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${log.kcal}", style = Theme.timer(34f), color = Theme.ink,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            "kcal", style = Theme.caption(), color = Theme.inkMuted,
                            modifier = Modifier.alignByBaseline()
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Edit", style = Theme.emphasis(13f), color = Theme.court,
                            modifier = Modifier
                                .alignByBaseline()
                                .clickable { draft = log.kcal.toInt(); editing = true }
                        )
                    }
                } else {
                    Text("Nothing logged tonight", style = Theme.emphasis(15f), color = Theme.ink)
                    GlassButton("Log tonight's burn") { editing = true }
                }
            }

            val h = history
            if (h != null && h.points.isNotEmpty()) {
                SectionLabel("Last sessions")
                Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KcalBarChart(h.points.takeLast(10))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${h.totalSessions}", "SESSIONS", Modifier.weight(1f))
                        Stat("${h.avgKcal}", "AVG KCAL", Modifier.weight(1f))
                        Stat("${h.maxKcal}", "BEST", Modifier.weight(1f))
                    }
                }
            }

            Text(
                "Private to your account — never visible to any group, in any group.",
                style = Theme.caption(11f), color = Theme.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// MARK: Stepper button (iOS Stepper analog — minus / plus, kiosk vocabulary)

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(Theme.surface, CircleShape)
            .border(1.dp, Theme.line, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Theme.court)
    }
}

// MARK: Stat tile (SESSIONS / AVG KCAL / BEST)

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .background(Theme.chalk, RoundedCornerShape(12.dp))
            .padding(vertical = 9.dp)
    ) {
        Text(value, style = Theme.emphasis(16f), color = Theme.ink)
        Text(
            label, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
            color = Theme.inkMuted
        )
    }
}

// MARK: History chart — Canvas port of the Swift Charts BarMark block:
// bars = court @ 75% with 5pt corner radius, trailing Y gridlines + labels
// (size 9, muted), X labels = gameDate.suffix(5), frame height 130.

@Composable
private fun KcalBarChart(points: List<KcalHistory.Point>) {
    val barColor = Theme.court.copy(alpha = 0.75f)
    val gridColor = Theme.line
    val labelColor = Theme.inkMuted
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val maxKcal = points.maxOf { it.kcal.toInt() }.coerceAtLeast(1)

        // Nice axis ticks (~4 gridlines) — mirrors the Swift Charts automatic axis.
        val rawStep = maxKcal / 4.0
        val magnitude = 10.0.pow(floor(log10(rawStep)))
        val residual = rawStep / magnitude
        val nice = when {
            residual <= 1.0 -> 1.0
            residual <= 2.0 -> 2.0
            residual <= 5.0 -> 5.0
            else -> 10.0
        }
        val step = (nice * magnitude).toInt().coerceAtLeast(1)
        val axisMax = ceil(maxKcal.toDouble() / step).toInt() * step
        val ticks = (0..axisMax step step).toList()

        val yLabelPad = 6.dp.toPx()
        val maxYLabelWidth = ticks.maxOf {
            textMeasurer.measure(AnnotatedString("$it"), labelStyle).size.width
        }
        val xLabelHeight = textMeasurer.measure(AnnotatedString("00-00"), labelStyle).size.height
        val xLabelGap = 4.dp.toPx()
        val plotWidth = size.width - maxYLabelWidth - yLabelPad
        val plotHeight = size.height - xLabelHeight - xLabelGap

        // Y gridlines + trailing value labels.
        ticks.forEach { tick ->
            val y = plotHeight * (1f - tick.toFloat() / axisMax)
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1f)
            val layout = textMeasurer.measure(AnnotatedString("$tick"), labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    size.width - layout.size.width,
                    (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
                )
            )
        }

        // Bars + X labels (one slot per point, bar centered in its slot).
        val slot = plotWidth / points.size
        val barWidth = slot * 0.62f
        points.forEachIndexed { index, p ->
            val barHeight = plotHeight * (p.kcal.toFloat() / axisMax)
            val left = slot * index + (slot - barWidth) / 2f
            drawRoundRect(
                barColor,
                topLeft = Offset(left, plotHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(5.dp.toPx())
            )
            val layout = textMeasurer.measure(AnnotatedString(p.gameDate.takeLast(5)), labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    (slot * index + (slot - layout.size.width) / 2f)
                        .coerceIn(0f, size.width - layout.size.width),
                    plotHeight + xLabelGap
                )
            )
        }
    }
}
