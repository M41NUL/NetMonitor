package com.netmonitor.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.netmonitor.app.ui.theme.plainCard
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netmonitor.app.data.AppSettings
import com.netmonitor.app.data.ChartStyle
import com.netmonitor.app.data.Snapshot
import com.netmonitor.app.data.SpeedUnit
import com.netmonitor.app.data.SpeedState
import com.netmonitor.app.ui.theme.Blue
import com.netmonitor.app.ui.theme.MobileOrange
import com.netmonitor.app.service.SpeedService
import com.netmonitor.app.util.Format
import com.netmonitor.app.util.WifiName
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import java.util.Locale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

private val DownColor = Color(0xFF2A78D6)
private val UpColor = Color(0xFF1BAF7A)
private val OffColor = Color(0xFF9AA3AE)

private enum class Filter(val label: String) { TOTAL("Total"), WIFI("WiFi"), MOBILE("Mobile") }

@Composable
fun HomeScreen(onRunSpeedTest: () -> Unit) {
    val snap by SpeedState.state.collectAsStateWithLifecycle()
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    val homeCtx = LocalContext.current
    var filter by remember { mutableStateOf(Filter.TOTAL) }

    val down = when (filter) {
        Filter.TOTAL -> snap.totalDown
        Filter.WIFI -> snap.wifiDown
        Filter.MOBILE -> snap.mobileDown
    }
    val up = when (filter) {
        Filter.TOTAL -> snap.totalUp
        Filter.WIFI -> snap.wifiUp
        Filter.MOBILE -> snap.mobileUp
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("NetMonitor", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("Live network usage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!settings.monitoringOn) {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        AppSettings.update { it.copy(monitoringOn = true) }
                        SpeedService.start(homeCtx)
                    }
            ) {
                Column {
                    Text("Monitoring is off", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap to turn on live speed and usage",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        FilterTabs(filter) { filter = it }
        SpeedCard(snap, filter, down, up)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (filter != Filter.MOBILE) {
                StatCard("WiFi today", Format.size(snap.todayWifi), DownColor, Modifier.weight(1f))
            }
            if (filter != Filter.WIFI) {
                StatCard("Mobile today", Format.size(snap.todayMobile), MobileOrange, Modifier.weight(1f))
            }
        }
        UsageCard(snap, filter)
        RunTestButton(onRunSpeedTest)
    }
}

@Composable
private fun FilterTabs(selected: Filter, onSelect: (Filter) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Filter.values().forEach { f ->
                val on = f == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(f) }
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        f.label,
                        Modifier.padding(bottom = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) DownColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(2.dp)
                            .background(if (on) DownColor else Color.Transparent)
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
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .plainCard(shape)
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun SpeedCard(snap: Snapshot, filter: Filter, down: Long, up: Long) {
    val downHist = when (filter) {
        Filter.TOTAL -> snap.history
        Filter.WIFI -> snap.wifiHistory
        Filter.MOBILE -> snap.mobileHistory
    }
    val upHist = when (filter) {
        Filter.TOTAL -> snap.upHistory
        Filter.WIFI -> snap.wifiUpHistory
        Filter.MOBILE -> snap.mobileUpHistory
    }
    val onMobile = snap.networkLabel == "Mobile data"
    val connected = snap.networkLabel != "No connection"
    val operator = snap.mobileOperator ?: "Mobile"

    val active = when (filter) {
        Filter.TOTAL -> connected
        Filter.WIFI -> snap.onWifi
        Filter.MOBILE -> onMobile
    }
    val footer = when (filter) {
        Filter.TOTAL -> if (onMobile) operator else snap.networkLabel
        Filter.WIFI -> if (snap.onWifi) snap.networkLabel else "WiFi · Not connected"
        Filter.MOBILE -> when {
            onMobile -> operator
            connected && snap.mobileSignalPercent >= 0 -> "$operator · Not in use"
            else -> "$operator · Data off"
        }
    }

    val showWifi = snap.onWifi && filter != Filter.MOBILE
    val signal = when {
        showWifi -> snap.signalPercent
        filter == Filter.WIFI -> -1
        onMobile -> snap.signalPercent
        filter == Filter.MOBILE -> snap.mobileSignalPercent
        else -> -1
    }
    val showMobile = !showWifi && signal >= 0

    val ctx = LocalContext.current
    var permTick by remember { mutableIntStateOf(0) }
    var denied by rememberSaveable { mutableIntStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.any { it }) {
            permTick++

            AppSettings.update { it.copy(showSsid = true) }
        } else denied++
    }
    val wifi by produceState(WifiName.Result(), showWifi, permTick) {
        if (!showWifi) {
            value = WifiName.Result()
            return@produceState
        }
        while (true) {
            value = WifiName.read(ctx)
            delay(2000)
        }
    }
    val footerText = if (showWifi) when {
        wifi.name != null -> "WiFi · ${wifi.name}"
        wifi.needsPermission -> "WiFi · Tap to show name"
        wifi.locationOff -> "WiFi · Turn on Location for name"
        else -> footer
    } else footer
    val footerClick: (() -> Unit)? = when {
        !showWifi -> null
        wifi.needsPermission -> {
            {
                if (denied >= 2) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    permLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            }
        }
        wifi.locationOff -> {
            {
                ctx.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        else -> null
    }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                SpeedBlock(false, "Download", Format.speed(if (active) down else 0L), if (active) DownColor else OffColor, Modifier.weight(1f))
                SpeedBlock(true, "Upload", Format.speed(if (active) up else 0L), if (active) UpColor else OffColor, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            SpeedGraph(downHist, upHist, active)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                val text = footerText + if (signal >= 0) " · Signal $signal%" else ""
                Text(
                    text,
                    Modifier
                        .weight(1f)
                        .then(if (footerClick != null) Modifier.clickable(onClick = footerClick) else Modifier),
                    fontSize = 12.sp,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val level = ((signal.coerceAtLeast(0)) + 24) / 25
                if (showWifi) {
                    WifiIcon(level, tint, 18.dp)
                } else if (showMobile) {
                    SignalBars(level, tint, 16.dp)
                }
            }
        }
    }
}

@Composable
private fun SpeedBlock(up: Boolean, label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow(up, color)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun Arrow(up: Boolean, color: Color, dim: Dp = 14.dp) {
    Canvas(Modifier.size(dim)) {
        val w = size.width
        val h = size.height
        val d = if (up) -1f else 1f
        val cy = h / 2
        val path = Path().apply {
            moveTo(w / 2, cy - d * h * 0.4f)
            lineTo(w / 2, cy + d * h * 0.4f)
            moveTo(w * 0.2f, cy + d * h * 0.1f)
            lineTo(w / 2, cy + d * h * 0.4f)
            lineTo(w * 0.8f, cy + d * h * 0.1f)
        }
        drawPath(path, color, style = Stroke(w * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun Path.smooth(pts: List<Offset>) {
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val mx = (a.x + b.x) / 2
        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
    }
}

private fun niceMax(maxBytes: Long): Double {
    val bits = AppSettings.current.speedUnit == SpeedUnit.BITS
    val scale = if (bits) 8.0 else 1.0
    val unit = if (bits) 1000.0 else 1024.0
    val x = maxBytes.coerceAtLeast(1024L).toDouble() * scale
    val base = if (x < unit * unit) unit else unit * unit
    val u = x / base
    val p = 10.0.pow(floor(log10(u)))
    val n = listOf(1.0, 2.0, 5.0, 10.0).first { it * p >= u - 1e-9 } * p
    return n * base / scale
}

private fun axisLabel(bytes: Double): String {
    if (bytes <= 0.0) return "0"
    val bits = AppSettings.current.speedUnit == SpeedUnit.BITS
    val v = bytes * if (bits) 8.0 else 1.0
    val b = if (bits) 1000.0 else 1024.0
    val (num, unit) = when {
        v < b * b -> v / b to (if (bits) "Kb/s" else "KB/s")
        else -> v / (b * b) to (if (bits) "Mb/s" else "MB/s")
    }
    val text = if (abs(num - num.roundToInt()) < 0.05) "${num.roundToInt()}" else String.format(Locale.US, "%.1f", num)
    return "$text $unit"
}

@Composable
private fun SpeedGraph(down: List<Long>, up: List<Long>, active: Boolean = true) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val measurer = rememberTextMeasurer()
    val style = AppSettings.state.collectAsStateWithLifecycle().value.chartStyle
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
    ) {
        val top = 16.dp.toPx()
        val base = size.height - 6.dp.toPx()
        val peak = maxOf(down.maxOrNull() ?: 0L, up.maxOrNull() ?: 0L)
        val niceTop = niceMax(peak)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))

        fun yOf(v: Double) = (base - (v / niceTop).toFloat() * (base - top)).coerceIn(top, base)

        listOf(niceTop, niceTop / 2, 0.0).forEach { v ->
            val y = yOf(v)
            if (v > 0.0) drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.5f, pathEffect = dash)
            else drawLine(grid, Offset(0f, y), Offset(size.width, y), 2f)
            val layout = measurer.measure(axisLabel(v), TextStyle(color = labelColor, fontSize = 10.sp))
            drawText(layout, topLeft = Offset(size.width - layout.size.width, y - layout.size.height - 1.dp.toPx()))
        }

        if (!active) {
            drawLine(OffColor, Offset(0f, base), Offset(size.width, base), 5f, cap = StrokeCap.Round)
            drawCircle(surface, 11f, Offset(size.width, base))
            drawCircle(OffColor, 7f, Offset(size.width, base))
            return@Canvas
        }

        if (style == ChartStyle.BAR) {
            val n = minOf(down.size, up.size)
            if (n < 1) return@Canvas
            val slot = size.width / n
            val barW = slot * 0.38f
            val minH = 2f
            for (i in 0 until n) {
                val x = i * slot + slot * 0.09f
                val dy = yOf(down[i].toDouble())
                val uy = yOf(up[i].toDouble())
                drawRoundRect(
                    DownColor, Offset(x, minOf(dy, base - minH)),
                    Size(barW, maxOf(base - dy, minH)), CornerRadius(barW / 3),
                )
                drawRoundRect(
                    UpColor, Offset(x + barW + slot * 0.05f, minOf(uy, base - minH)),
                    Size(barW, maxOf(base - uy, minH)), CornerRadius(barW / 3),
                )
            }
            return@Canvas
        }

        if (down.size < 2 || up.size < 2) return@Canvas

        fun points(s: List<Long>) = s.mapIndexed { i, v ->
            Offset(i * size.width / (s.size - 1), yOf(v.toDouble()))
        }
        val downPts = points(down)
        val up2 = points(up)

        fun linePath(pts: List<Offset>) = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            when (style) {
                ChartStyle.STEP -> for (i in 1 until pts.size) {
                    lineTo(pts[i].x, pts[i - 1].y)
                    lineTo(pts[i].x, pts[i].y)
                }
                ChartStyle.MULTI -> for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                else -> smooth(pts)
            }
        }

        if (style == ChartStyle.CLASSIC) {
            val fill = Path().apply {
                moveTo(0f, base)
                lineTo(downPts.first().x, downPts.first().y)
                smooth(downPts)
                lineTo(size.width, base)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(DownColor.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = base))
        }

        listOf(downPts to DownColor, up2 to UpColor).forEach { (pts, color) ->
            drawPath(linePath(pts), color, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (style == ChartStyle.MULTI) {
                pts.forEach { drawCircle(color, 4f, it) }
            }
        }
        listOf(downPts.last() to DownColor, up2.last() to UpColor).forEach { (pt, color) ->
            drawCircle(surface, 11f, pt)
            drawCircle(color, 7f, pt)
        }
    }
}

@Composable
private fun WifiIcon(active: Int, color: Color, dim: Dp = 18.dp) {
    Canvas(Modifier.size(dim)) {
        val cx = size.width / 2
        val cy = size.height * 0.88f
        val stroke = size.width * 0.11f

        val radii = listOf(0.34f, 0.62f, 0.90f)
        radii.forEachIndexed { i, r ->
            val rad = size.width * r * 0.98f
            drawArc(
                color.copy(alpha = if (active >= i + 2) 1f else 0.3f),
                startAngle = -135f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - rad, cy - rad), size = Size(rad * 2, rad * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        drawCircle(color.copy(alpha = if (active >= 1) 1f else 0.3f), stroke * 0.8f, Offset(cx, cy))
    }
}

@Composable
private fun SignalBars(active: Int, color: Color, dim: Dp = 14.dp) {
    Canvas(Modifier.size(dim)) {
        val w = size.width / 7f
        repeat(4) { i ->
            val h = size.height * (0.3f + 0.7f * i / 3f)
            drawRoundRect(
                color.copy(alpha = if (i < active) 1f else 0.3f),
                Offset(i * 2 * w, size.height - h),
                Size(w, h),
                CornerRadius(w / 3),
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UsageCard(snap: Snapshot, filter: Filter) {
    val total = snap.monthWifi + snap.monthMobile
    val wifiFrac = if (total > 0) snap.monthWifi.toFloat() / total else 0f
    val mobileFrac = if (total > 0) 1f - wifiFrac else 0f
    val wifiShare by animateFloatAsState(wifiFrac, tween(600), label = "wifiShare")
    val mobileShare by animateFloatAsState(mobileFrac, tween(600), label = "mobileShare")
    val wifiPct = (wifiFrac * 100).roundToInt()
    val mobilePct = if (total > 0) 100 - wifiPct else 0
    val track = MaterialTheme.colorScheme.surfaceVariant
    val shown = when (filter) {
        Filter.TOTAL -> total
        Filter.WIFI -> snap.monthWifi
        Filter.MOBILE -> snap.monthMobile
    }
    val parts = Format.size(shown).split(" ")
    val title = when (filter) {
        Filter.TOTAL -> "This month"
        Filter.WIFI -> "This month · WiFi"
        Filter.MOBILE -> "This month · Mobile"
    }

    Card(Modifier.fillMaxWidth()) {
        Column {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(120.dp)) {
                        val stroke = 14.dp.toPx()
                        val topLeft = Offset(stroke / 2, stroke / 2)
                        val arc = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                        if (total > 0) {
                            val both = filter == Filter.TOTAL
                            val gap = if (both && wifiShare in 0.001f..0.999f) 2f else 0f
                            val wifiSweep = 360f * wifiShare
                            val mobileSweep = 360f * mobileShare
                            if (filter != Filter.MOBILE && wifiSweep > 0f) drawArc(
                                DownColor, -90f + gap / 2, (wifiSweep - gap).coerceAtLeast(0.1f),
                                false, topLeft, arc, style = Stroke(stroke),
                            )
                            if (filter != Filter.WIFI && mobileSweep > 0f) {
                                val start = if (both) -90f + wifiSweep else -90f
                                drawArc(
                                    MobileOrange, start + gap / 2, (mobileSweep - gap).coerceAtLeast(0.1f),
                                    false, topLeft, arc, style = Stroke(stroke),
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(parts[0], fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(parts.getOrElse(1) { "" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    if (filter != Filter.MOBILE) {
                        UsageRow(DownColor, "WiFi", Format.size(snap.monthWifi), wifiPct)
                    }
                    if (filter == Filter.TOTAL) {
                        Box(
                            Modifier
                                .padding(vertical = 10.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    if (filter != Filter.WIFI) {
                        UsageRow(MobileOrange, "Mobile", Format.size(snap.monthMobile), mobilePct)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRow(color: Color, label: String, size: String, pct: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Text(size, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("$pct%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RunTestButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Blue)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val p = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(size.width * 0.15f, size.height)
                close()
            }
            drawPath(p, Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text("Run speed test", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
