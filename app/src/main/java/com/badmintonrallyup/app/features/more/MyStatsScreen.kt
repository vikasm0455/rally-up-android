// My Stats — personal analytics from play history. Private to the caller;
// 1:1 port of the iOS MyStatsView. Data: GET /api/stats/me.

package com.badmintonrallyup.app.features.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.badmintonrallyup.app.api.ApiClient
import com.badmintonrallyup.app.api.MyStats
import com.badmintonrallyup.app.designsystem.EmptyState
import com.badmintonrallyup.app.designsystem.ScreenTopBar
import com.badmintonrallyup.app.designsystem.Theme
import com.badmintonrallyup.app.designsystem.card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.BarChart

@Composable
fun MyStatsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("rallyup", android.content.Context.MODE_PRIVATE) }
    var stats by remember { mutableStateOf<MyStats?>(null) }
    var failed by remember { mutableStateOf(false) }
    // months: 1/3/6 presets; 0 = custom weeks.
    var months by remember { mutableStateOf(prefs.getInt("statsMonths", 1)) }
    var weeks by remember { mutableStateOf(prefs.getInt("statsWeeks", 8)) }
    val rangeLabel = when (months) {
        0 -> "last $weeks week" + if (weeks == 1) "" else "s"
        1 -> "last 5 weeks"
        else -> "last $months months"
    }

    LaunchedEffect(months, weeks) {
        prefs.edit().putInt("statsMonths", months).putInt("statsWeeks", weeks).apply()
        val query = if (months == 0) "weeks=$weeks" else "months=$months"
        try {
            stats = ApiClient.get<MyStats>("/api/stats/me?$query")
            failed = false
        } catch (e: Exception) {
            failed = stats == null
        }
    }

    Column(Modifier.fillMaxSize().background(Theme.chalk)) {
        ScreenTopBar(
            title = "My stats",
            navIcon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            onNav = onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.screenGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val s = stats
            when {
                failed -> EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "Couldn't load your stats",
                    message = "Go back and try again."
                )
                s == null -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Theme.court)
                }
                else -> {
                    Text(
                        "${s.sessionsTotal} sessions all-time · private to you",
                        style = Theme.caption(11f), color = Theme.inkMuted
                    )
                    RangePicker(months) { months = it }
                    if (months == 0) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .card(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "−", style = Theme.display(20f),
                                color = if (weeks > 1) Theme.court else Theme.inkMuted,
                                modifier = Modifier.clickable { if (weeks > 1) weeks -= 1 }.padding(horizontal = 10.dp)
                            )
                            Text(
                                "$weeks week" + (if (weeks == 1) "" else "s"),
                                style = Theme.emphasis(14f), color = Theme.ink,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                            )
                            Text(
                                "+", style = Theme.display(20f),
                                color = if (weeks < 26) Theme.court else Theme.inkMuted,
                                modifier = Modifier.clickable { if (weeks < 26) weeks += 1 }.padding(horizontal = 10.dp)
                            )
                        }
                    }
                    StatTiles(s)
                    WeeklyChart(s, rangeLabel)
                    KcalChart(s, rangeLabel)
                    Text(
                        "Sessions count days you were in the confirmed attendance list (or voted yes when attendance wasn't confirmed). Court time counts courts you logged. All stats are private to you.",
                        style = Theme.caption(10.5f), color = Theme.inkMuted
                    )
                }
            }
        }
    }
}

private fun delta(now: Long, before: Long): String {
    val d = now - before
    if (d == 0L) return "same as last month"
    return "${if (d > 0) "▲" else "▼"} ${kotlin.math.abs(d)} vs last month"
}

/** Sub-hour totals show minutes so the tile never reads "0 h" with a trend. */
private fun minutesLabel(minutes: Long): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h"

/** Delta from RAW minutes — rounding per platform must never disagree. */
private fun minutesDelta(now: Long, before: Long): String {
    val d = now - before
    if (d == 0L) return "same as last month"
    val arrow = if (d > 0) "▲" else "▼"
    val magnitude = kotlin.math.abs(d)
    return if (magnitude < 60) "$arrow $magnitude min vs last month"
    else "$arrow ${magnitude / 60}h vs last month"
}

@Composable
private fun StatTiles(s: MyStats) {
    val tiles = listOf(
        Triple("${s.sessionsThisMonth}", "Sessions this month", delta(s.sessionsThisMonth, s.sessionsLastMonth)),
        Triple("${s.currentStreakWeeks} wk${if (s.currentStreakWeeks == 1L) "" else "s"}", "Current streak", "best: ${s.bestStreakWeeks} wks"),
        Triple(minutesLabel(s.courtMinutesThisMonth), "Court time this month", minutesDelta(s.courtMinutesThisMonth, s.courtMinutesLastMonth)),
        Triple(s.avgKcalPerSession?.toString() ?: "—", "Avg kcal / session", s.yesRatePercent?.let { "yes-rate $it%" } ?: ""),
    )
    tiles.chunked(2).forEach { rowTiles ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowTiles.forEach { (value, label, sub) ->
                Column(
                    Modifier.weight(1f).card(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(value, style = Theme.display(24f), color = Theme.ink)
                    Text(label.uppercase(), style = Theme.emphasis(10f), color = Theme.inkMuted)
                    if (sub.isNotEmpty()) Text(
                        sub, style = Theme.caption(10.5f),
                        // Green celebrates gains only — a down month stays neutral.
                        color = if (sub.startsWith("▲")) Theme.success else Theme.inkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun RangePicker(selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Theme.chalk, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(1, 3, 6, 0).forEach { m ->
            val on = m == selected
            Text(
                if (m == 0) "Custom" else "${m}M",
                style = Theme.emphasis(13f),
                color = if (on) androidx.compose.ui.graphics.Color.White else Theme.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (on) Theme.court.copy(alpha = 0.72f) else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable { onPick(m) }
                    .padding(vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun WeeklyChart(s: MyStats, rangeLabel: String) {
    val maxCount = maxOf(1L, s.weeklySessions.maxOfOrNull { it.sessions } ?: 1L)
    Column(Modifier.card(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Sessions per week ", style = Theme.emphasis(12.5f), color = Theme.ink)
            Text("· $rangeLabel", style = Theme.caption(11.5f), color = Theme.inkMuted)
        }
        val labelEvery = maxOf(1, Math.round(s.weeklySessions.size / 6.0f))
        Row(
            Modifier.fillMaxWidth().height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(if (s.weeklySessions.size > 13) 2.dp else 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            s.weeklySessions.forEachIndexed { index, week ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(maxOf(4f, week.sessions.toFloat() / maxCount * 84f).dp)
                            .background(
                                Theme.court.copy(alpha = if (index == s.weeklySessions.size - 1) 1f else 0.42f),
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                            )
                    )
                    Text(
                        if (index % labelEvery == 0 || index == s.weeklySessions.size - 1)
                            week.weekStart.drop(5).replace("-", "/") else " ",
                        style = Theme.caption(7.5f), color = Theme.inkMuted, maxLines = 1,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun KcalChart(s: MyStats, rangeLabel: String) {
    Column(Modifier.card(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Calories per session ", style = Theme.emphasis(12.5f), color = Theme.ink)
            Text("· $rangeLabel", style = Theme.caption(11.5f), color = Theme.inkMuted)
        }
        if (s.kcalSeries.isEmpty()) {
            Text(
                "No calorie logs yet — log one from Home after a session.",
                style = Theme.caption(12f), color = Theme.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
        } else {
            val maxKcal = maxOf(1, s.kcalSeries.maxOfOrNull { it.kcal } ?: 1)
            val court = Theme.court
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                // 8px top headroom so the peak's stroke isn't clipped; a
                // terminal dot so a single log still renders (a one-point
                // path strokes nothing).
                val points = s.kcalSeries
                val inset = 8f
                fun xAt(index: Int) = if (points.size == 1) size.width / 2
                else inset + index.toFloat() / (points.size - 1) * (size.width - 2 * inset)
                fun yAt(kcal: Int) = size.height - kcal.toFloat() / maxKcal * (size.height - inset)
                if (points.size > 1) {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = xAt(index)
                        val y = yAt(point.kcal)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path, color = court,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                drawCircle(
                    color = court, radius = 3.dp.toPx(),
                    center = Offset(xAt(points.size - 1), yAt(points.last().kcal))
                )
            }
        }
    }
}
