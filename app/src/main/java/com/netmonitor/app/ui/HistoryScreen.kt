package com.netmonitor.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.netmonitor.app.ui.theme.plainCard
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.netmonitor.app.ui.theme.BlurBehind
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netmonitor.app.data.SpeedState
import com.netmonitor.app.data.UsageBucket
import com.netmonitor.app.data.UsageStore
import com.netmonitor.app.ui.theme.MobileOrange
import com.netmonitor.app.util.Format
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private enum class Period(val label: String) { DAY("Day"), MONTH("Month"), YEAR("Year") }
private enum class Net(val label: String) { TOTAL("Total"), WIFI("WiFi"), MOBILE("Mobile") }

private data class HistoryData(val chart: List<UsageBucket>, val avgUnit: String)

private fun value(b: UsageBucket, net: Net): Long = when (net) {
    Net.TOTAL -> b.total
    Net.WIFI -> b.wifi
    Net.MOBILE -> b.mobile
}

private fun pct(part: Long, whole: Long): Int =
    if (whole <= 0L) 0 else (part * 100.0 / whole).roundToInt()

private fun utcToLocalDay(ms: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

private fun fmtDate(pattern: String, ms: Long): String =
    SimpleDateFormat(pattern, Locale.US).format(Date(ms))

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val store = remember { UsageStore(context) }
    val snap by SpeedState.state.collectAsStateWithLifecycle()
    val version = snap.todayWifi + snap.todayMobile

    val now = remember(version) { store.today() }
    val curYear = now.get(Calendar.YEAR)
    val minYear = remember(version) { minOf(store.firstYear(), curYear - 1) }
    val maxYear = curYear + 1

    var period by remember { mutableStateOf(Period.DAY) }
    var net by remember { mutableStateOf(Net.TOTAL) }
    var year by remember { mutableIntStateOf(curYear) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var custom by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var selected by remember(period, year, month, custom) { mutableIntStateOf(-1) }

    val data = remember(period, year, month, custom, version, minYear) {
        val c = custom
        when {
            c != null -> HistoryData(store.range(c.first, c.second), "day")
            period == Period.DAY -> HistoryData(store.daysOfMonth(year, month), "day")
            period == Period.MONTH -> HistoryData(store.monthsOfYear(year), "month")
            else -> HistoryData(store.years(minYear, maxYear), "year")
        }
    }

    val c = custom
    val navLabel: String
    var canPrev = false
    var canNext = false
    when {
        c != null -> navLabel = fmtDate("d MMM", c.first) + " – " + fmtDate("d MMM", c.second)
        period == Period.DAY -> {
            navLabel = fmtDate("MMM yyyy", Calendar.getInstance().apply { clear(); set(year, month, 1, 12, 0, 0) }.timeInMillis)
            canPrev = year * 12 + month > minYear * 12
            canNext = year * 12 + month < maxYear * 12 + 11
        }
        period == Period.MONTH -> {
            navLabel = year.toString()
            canPrev = year > minYear
            canNext = year < maxYear
        }
        else -> navLabel = "$minYear – $maxYear"
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("History", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Box(
                Modifier
                    .size(36.dp)
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                CalendarIcon(if (c != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Tabs(
            options = Period.values().map { it.label },
            selected = if (c != null) -1 else period.ordinal,
            onSelect = { custom = null; period = Period.values()[it] },
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Net.values().forEach { n ->
                    val on = n == net
                    Text(
                        n.label,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { net = n }
                            .padding(vertical = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c == null && period != Period.YEAR) {
                    Arrow("‹", canPrev) {
                        if (period == Period.DAY) {
                            if (month == 0) { month = 11; year -= 1 } else { month -= 1 }
                        } else { year -= 1 }
                    }
                }
                Text(navLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (c == null && period != Period.YEAR) {
                    Arrow("›", canNext) {
                        if (period == Period.DAY) {
                            if (month == 11) { month = 0; year += 1 } else { month += 1 }
                        } else { year += 1 }
                    }
                }
                if (c != null) {
                    Text(
                        "Clear",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { custom = null }
                            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }

        SummaryCard(data, net)
        ChartCard(data, net, selected) { selected = it }
        ListCard(data.chart.filter { !it.future }.reversed(), net)
        Spacer(Modifier.height(6.dp))
    }

    if (showPicker) {
        RangeDialog(
            onDismiss = { showPicker = false },
            onPick = { s, e ->
                val a = utcToLocalDay(s)
                val b = utcToLocalDay(e)
                custom = if (a <= b) Pair(a, b) else Pair(b, a)
                showPicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(onDismiss: () -> Unit, onPick: (Long, Long) -> Unit) {
    val state = rememberDateRangePickerState()
    val cs = MaterialTheme.colorScheme
    val colors = DatePickerDefaults.colors(
        containerColor = Color.Transparent,
        titleContentColor = cs.onSurfaceVariant,
        headlineContentColor = cs.onSurface,
        weekdayContentColor = cs.onSurfaceVariant,
        subheadContentColor = cs.onSurfaceVariant,
        navigationContentColor = cs.onSurface,
        dayContentColor = cs.onSurface,
        selectedDayContainerColor = cs.primary,
        selectedDayContentColor = cs.onPrimary,
        todayContentColor = cs.primary,
        todayDateBorderColor = cs.primary,
        dayInSelectionRangeContainerColor = cs.primary.copy(alpha = 0.12f),
        dayInSelectionRangeContentColor = cs.onSurface,
    )
    val fmt = remember {
        SimpleDateFormat("d MMM", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis
    val canApply = start != null

    val shape = RoundedCornerShape(16.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BlurBehind()
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .background(cs.surface, shape)
                .plainCard(shape)
        ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.height(410.dp),
            colors = colors,
            showModeToggle = false,
            title = {
                Text(
                    "Select range",
                    Modifier.padding(start = 24.dp, top = 18.dp),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
            },
            headline = {
                val text = when {
                    start == null -> "Start date \u2013 End date"
                    end == null -> fmt.format(Date(start)) + " \u2013 End date"
                    else -> fmt.format(Date(start)) + " \u2013 " + fmt.format(Date(end))
                }
                Text(
                    text,
                    Modifier.padding(start = 24.dp, top = 2.dp, bottom = 10.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (start == null) cs.onSurfaceVariant else cs.onSurface,
                )
            },
        )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = cs.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 14.sp, color = cs.onSurfaceVariant)
                }
                TextButton(
                    enabled = canApply,
                    onClick = {
                        val e = end ?: start
                        if (start != null && e != null) onPick(start, e)
                    },
                ) { Text("Apply", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun CalendarIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.width / 20f
        val sw = 1.6f * s
        drawRoundRect(color, Offset(1.5f * s, 3.5f * s), Size(17f * s, 15f * s), CornerRadius(3f * s), style = Stroke(sw))
        drawLine(color, Offset(1.5f * s, 8.5f * s), Offset(18.5f * s, 8.5f * s), sw)
        drawLine(color, Offset(6f * s, 1f * s), Offset(6f * s, 5.5f * s), sw, StrokeCap.Round)
        drawLine(color, Offset(14f * s, 1f * s), Offset(14f * s, 5.5f * s), sw, StrokeCap.Round)
    }
}

@Composable
private fun Arrow(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 18.sp,
        color = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun Tabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun SummaryCard(data: HistoryData, net: Net) {
    val wifi = data.chart.sumOf { it.wifi }
    val mobile = data.chart.sumOf { it.mobile }
    val total = value(UsageBucket("", "", wifi, mobile), net)
    val elapsed = data.chart.count { !it.future }.coerceAtLeast(1)
    val title = when (net) {
        Net.TOTAL -> "Total usage"
        Net.WIFI -> "WiFi usage"
        Net.MOBILE -> "Mobile usage"
    }
    val wifiColor = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Panel(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 12.sp, color = muted)
                Text("Avg ${Format.size(total / elapsed)} / ${data.avgUnit}", fontSize = 12.sp, color = muted)
            }
            Text(Format.size(total), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            if (net == Net.TOTAL) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (wifi > 0) Box(Modifier.weight(wifi.toFloat()).height(6.dp).background(wifiColor))
                    if (mobile > 0) Box(Modifier.weight(mobile.toFloat()).height(6.dp).background(MobileOrange))
                }
                Spacer(Modifier.height(8.dp))
                val wp = pct(wifi, wifi + mobile)
                val mp = if (wifi + mobile > 0) 100 - wp else 0
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Legend(wifiColor, "WiFi ${Format.size(wifi)} · $wp%")
                    Legend(MobileOrange, "Mobile ${Format.size(mobile)} · $mp%")
                }
            }
        }
    }
}

@Composable
private fun ChartCard(data: HistoryData, net: Net, selected: Int, onSelect: (Int) -> Unit) {
    val sel = if (selected in data.chart.indices) selected else data.chart.indexOfFirst { it.isNow }
    Panel(Modifier.fillMaxWidth()) {
        UsageChart(data.chart, net, sel, onSelect)
    }
}

private fun niceCeil(v: Double): Double {
    if (v <= 0.0) return 100.0
    val base = 10.0.pow(floor(log10(v)))
    val f = v / base
    val steps = doubleArrayOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
    return (steps.firstOrNull { f <= it + 1e-9 } ?: 10.0) * base
}

private fun numText(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

@Composable
private fun UsageChart(buckets: List<UsageBucket>, net: Net, selected: Int, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val wifiColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val baseColor = axisColor.copy(alpha = 0.35f)
    val surface = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface

    val n = buckets.size
    val maxBytes = buckets.maxOfOrNull { value(it, net) } ?: 0L
    val useGb = maxBytes >= 1_073_741_824L
    val div = if (useGb) 1_073_741_824.0 else 1_048_576.0
    val niceMax = (if (maxBytes <= 0L) niceCeil(0.0) else niceCeil(maxBytes / div)).let {
        if (!useGb && it < 1.0) 1.0 else it
    }
    val unit = if (useGb) "GB" else "MB"
    val leftPx = with(density) { 28.dp.toPx() }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(n, leftPx) {
                detectTapGestures { o ->
                    val slot = (size.width - leftPx) / n
                    val idx = ((o.x - leftPx) / slot).toInt()
                    if (o.x >= leftPx && idx in 0 until n) onSelect(idx)
                }
            }
    ) {
        val top = 28.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val plotH = bottom - top
        val slot = (size.width - leftPx) / n
        val axisStyle = TextStyle(fontSize = 10.sp, color = axisColor)
        fun yOf(f: Float) = bottom - plotH * f

        drawLine(grid, Offset(leftPx, yOf(1f)), Offset(size.width, yOf(1f)), 1.dp.toPx())
        drawLine(
            grid, Offset(leftPx, yOf(0.5f)), Offset(size.width, yOf(0.5f)), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        drawLine(baseColor, Offset(leftPx, bottom), Offset(size.width, bottom), 1.dp.toPx())

        listOf(1f to niceMax, 0.5f to niceMax / 2, 0f to 0.0).forEach { (f, v) ->
            val m = measurer.measure(numText(v), axisStyle)
            drawText(m, topLeft = Offset(leftPx - 6.dp.toPx() - m.size.width, yOf(f) - m.size.height / 2f))
        }
        drawText(measurer.measure(unit, axisStyle), topLeft = Offset(0f, 4.dp.toPx()))

        val scaleMax = (niceMax * div).toFloat()
        val barW = minOf(slot * 0.62f, 18.dp.toPx())
        val radius = minOf(barW / 2f, 3.dp.toPx())
        val minH = 3.dp.toPx()

        if (selected in 0 until n) {
            drawRoundRect(
                wifiColor.copy(alpha = 0.10f),
                Offset(leftPx + selected * slot + 1.dp.toPx(), top - 2.dp.toPx()),
                Size(slot - 2.dp.toPx(), plotH + 2.dp.toPx()),
                CornerRadius(4.dp.toPx()),
            )
        }

        buckets.forEachIndexed { i, b ->
            val cx = leftPx + slot * (i + 0.5f)
            val w = if (net == Net.MOBILE) 0L else b.wifi
            val m = if (net == Net.WIFI) 0L else b.mobile
            var wH = w.toFloat() / scaleMax * plotH
            var mH = m.toFloat() / scaleMax * plotH
            val tot = wH + mH
            if (w + m <= 0L) {
                drawCircle(
                    baseColor.copy(alpha = if (b.future) 0.5f else 0.9f),
                    1.6.dp.toPx(),
                    Offset(cx, bottom - 2.dp.toPx()),
                )
            } else {
                if (tot < minH) {
                    wH *= minH / tot
                    mH *= minH / tot
                }
                val x = cx - barW / 2f
                val topY = bottom - wH - mH
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            x, topY, x + barW, bottom,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                            bottomRightCornerRadius = CornerRadius.Zero,
                            bottomLeftCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                clipPath(path) {
                    drawRect(wifiColor, Offset(x, bottom - wH), Size(barW, wH))
                    drawRect(MobileOrange, Offset(x, topY), Size(barW, mH))
                }
            }

            val showLabel = when {
                n <= 12 -> true
                else -> {
                    val step = if (n <= 31) 5 else (n / 6) + 1
                    i == 0 || (i + 1) % step == 0
                }
            }
            if (showLabel) {
                val style = if (i == selected) axisStyle.copy(color = wifiColor, fontWeight = FontWeight.Medium) else axisStyle
                val lm = measurer.measure(b.short, style)
                drawText(lm, topLeft = Offset(cx - lm.size.width / 2f, bottom + 4.dp.toPx()))
            }
        }

        if (selected in 0 until n) {
            val b = buckets[selected]
            val txt = "${b.tip.ifEmpty { b.label }} · ${Format.size(value(b, net))}"
            val tm = measurer.measure(txt, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink))
            val tw = tm.size.width + 20.dp.toPx()
            val th = 22.dp.toPx()
            val cx = leftPx + slot * (selected + 0.5f)
            val tx = (cx - tw / 2f).coerceIn(leftPx, maxOf(leftPx, size.width - tw))
            drawRoundRect(surface, Offset(tx, 0f), Size(tw, th), CornerRadius(6.dp.toPx()))
            drawRoundRect(baseColor, Offset(tx, 0f), Size(tw, th), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawText(tm, topLeft = Offset(tx + 10.dp.toPx(), (th - tm.size.height) / 2f))
            val px = cx.coerceIn(tx + 10.dp.toPx(), tx + tw - 10.dp.toPx())
            val tri = Path().apply {
                moveTo(px - 4.dp.toPx(), th - 0.5f)
                lineTo(px, th + 4.dp.toPx())
                lineTo(px + 4.dp.toPx(), th - 0.5f)
                close()
            }
            drawPath(tri, surface)
        }
    }
}

@Composable
private fun ListCard(items: List<UsageBucket>, net: Net) {
    if (items.isEmpty()) return
    val divider = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val whole = items.sumOf { value(it, net) }
    Panel(Modifier.fillMaxWidth()) {
        Column {
            items.forEachIndexed { i, b ->
                val v = value(b, net)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (b.isNow) {
                            Text(
                                "Today",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Text(Format.size(v), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${pct(v, whole)}%",
                        fontSize = 12.sp,
                        color = muted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                }
                if (i < items.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(divider)
                    )
                }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .plainCard(shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
