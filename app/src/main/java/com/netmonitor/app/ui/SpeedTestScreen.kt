package com.netmonitor.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.netmonitor.app.ui.theme.BlurBehind
import com.netmonitor.app.ui.theme.plainCard
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netmonitor.app.data.Phase
import com.netmonitor.app.data.SpeedServer
import com.netmonitor.app.data.SpeedState
import com.netmonitor.app.data.SpeedTestState
import com.netmonitor.app.data.SpeedTester
import com.netmonitor.app.ui.theme.Blue
import com.netmonitor.app.ui.theme.DownBlue
import com.netmonitor.app.ui.theme.MobileOrange
import com.netmonitor.app.ui.theme.UpGreen
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 150f
private const val SWEEP_ANGLE = 240f

private val MARKS = doubleArrayOf(0.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0)

private fun fracOf(v: Double): Float {
    val x = v.coerceIn(0.0, MARKS.last())
    for (i in 1 until MARKS.size) {
        if (x <= MARKS[i]) {
            val a = MARKS[i - 1]
            val b = MARKS[i]
            return ((i - 1) + (x - a) / (b - a)).toFloat() / (MARKS.size - 1)
        }
    }
    return 1f
}

@Composable
fun SpeedTestScreen() {
    val st by SpeedTester.state.collectAsStateWithLifecycle()
    val client by SpeedTester.client.collectAsStateWithLifecycle()
    val servers by SpeedTester.servers.collectAsStateWithLifecycle()
    val selectedId by SpeedTester.selectedId.collectAsStateWithLifecycle()
    val searchResults by SpeedTester.searchResults.collectAsStateWithLifecycle()
    val searching by SpeedTester.searching.collectAsStateWithLifecycle()
    val refreshing by SpeedTester.refreshing.collectAsStateWithLifecycle()
    val snap by SpeedState.state.collectAsStateWithLifecycle()
    var showServers by remember { mutableStateOf(false) }

    LaunchedEffect(snap.networkLabel) { SpeedTester.refreshInfo() }

    LaunchedEffect(st.phase) {
        if (st.phase == Phase.DONE || st.phase == Phase.ERROR) {
            delay(30_000)
            SpeedTester.clearResult()
        }
    }

    val server = servers.firstOrNull { it.id == selectedId } ?: servers.first()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Speed test", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            val net = when {
                snap.onWifi -> "WiFi"
                snap.networkLabel == "Mobile data" -> "Mobile"
                else -> snap.networkLabel
            }
            Text(net, fontSize = 12.sp, color = muted, maxLines = 1)
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Gauge(value = st.liveMbps, color = if (st.phase == Phase.UPLOAD) UpGreen else Blue)
        }

        StatusText(st)

        ResultsCard(st)
        TestButton(st)
        InfoCard(server = server, clientTitle = clientTitle(client), clientDetail = clientDetail(client),
            clientFailed = client.failed, canChange = !st.running,
            onChange = { showServers = true })
        Spacer(Modifier.height(6.dp))
    }

    if (showServers) {
        ServerSheet(
            servers = servers,
            selectedId = server.id,
            searchResults = searchResults,
            searching = searching,
            refreshing = refreshing,
            onPick = { SpeedTester.select(it); showServers = false },
            onDismiss = { showServers = false },
        )
    }
}

private fun clientTitle(c: com.netmonitor.app.data.ClientInfo): String = when {
    c.loaded -> c.isp.ifBlank { "Unknown provider" }
    c.loading -> "Loading…"
    c.failed -> "Couldn't load"
    else -> "Loading…"
}

private fun clientDetail(c: com.netmonitor.app.data.ClientInfo): String = when {
    c.loaded -> listOf(c.ip, c.place).filter { it.isNotBlank() }.joinToString(" · ")
    c.failed -> "Tap to retry"
    else -> ""
}

@Composable
private fun Gauge(value: Double, color: Color) {
    val shown by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = spring(dampingRatio = 1f, stiffness = 110f),
        label = "speed",
    )
    val v = shown.toDouble().coerceAtLeast(0.0)
    val frac = fracOf(v)

    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    Box(Modifier.width(250.dp).height(206.dp)) {
        Canvas(Modifier.width(250.dp).height(206.dp)) {
            val stroke = 10.dp.toPx()
            val r = size.width / 2 - stroke / 2
            val c = Offset(size.width / 2, size.width / 2)
            val topLeft = Offset(c.x - r, c.y - r)
            val arcSize = Size(2 * r, 2 * r)

            drawArc(
                color = track, startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (frac > 0.003f) {
                drawArc(
                    color = color, startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE * frac,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            val tickOuter = r - stroke / 2 - 5.dp.toPx()
            for (i in 0..16) {
                val a = Math.toRadians((START_ANGLE + SWEEP_ANGLE * i / 16).toDouble())
                val len = if (i % 2 == 0) 8.dp.toPx() else 4.dp.toPx()
                drawLine(
                    color = tick,
                    start = Offset(c.x + (cos(a) * tickOuter).toFloat(), c.y + (sin(a) * tickOuter).toFloat()),
                    end = Offset(c.x + (cos(a) * (tickOuter - len)).toFloat(), c.y + (sin(a) * (tickOuter - len)).toFloat()),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val labelR = r - stroke / 2 - 24.dp.toPx()
            for (i in MARKS.indices) {
                val a = Math.toRadians((START_ANGLE + SWEEP_ANGLE * i / (MARKS.size - 1)).toDouble())
                val layout = measurer.measure(
                    String.format(Locale.US, "%.0f", MARKS[i]),
                    TextStyle(color = labelColor, fontSize = 10.sp),
                )
                drawText(
                    layout,
                    topLeft = Offset(
                        c.x + (cos(a) * labelR).toFloat() - layout.size.width / 2f,
                        c.y + (sin(a) * labelR).toFloat() - layout.size.height / 2f,
                    ),
                )
            }

            val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * frac).toDouble())
            val len = labelR - 14.dp.toPx()
            val half = 2.5.dp.toPx()
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            val needle = Path().apply {
                moveTo(c.x + dx * len, c.y + dy * len)
                lineTo(c.x - dy * half, c.y + dx * half)
                lineTo(c.x + dy * half, c.y - dx * half)
                close()
            }
            drawPath(needle, MobileOrange)
            drawCircle(MobileOrange, radius = 9.dp.toPx(), center = c, style = Stroke(2.5.dp.toPx()))
            drawCircle(MobileOrange, radius = 3.dp.toPx(), center = c)
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 138.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                String.format(Locale.US, "%.0f", v),
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Mbps", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusText(st: SpeedTestState) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, color) = when (st.phase) {
        Phase.IDLE -> "Ready to test" to muted
        Phase.PING -> "Checking latency…" to muted
        Phase.DOWNLOAD -> (if (st.downMbps != null) "Download complete" else "Testing download…") to muted
        Phase.UPLOAD -> "Testing upload…" to muted
        Phase.DONE -> qualityLabel(st.downMbps ?: 0.0) to UpGreen
        Phase.ERROR -> (st.error ?: "Test failed") to MobileOrange
    }
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .plainCard(shape)
    ) { content() }
}

@Composable
private fun HDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ResultsCard(st: SpeedTestState) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Stat(
                    label = "Download", arrow = 1, arrowColor = DownBlue,
                    value = st.downMbps?.let { mbps(it) } ?: "—", unit = if (st.downMbps != null) "Mbps" else "",
                    sub = st.downMbps?.let { megabytes(it) } ?: "",
                    modifier = Modifier.weight(1f),
                )
                VDivider()
                Stat(
                    label = "Upload", arrow = 2, arrowColor = UpGreen,
                    value = st.upMbps?.let { mbps(it) } ?: "—", unit = if (st.upMbps != null) "Mbps" else "",
                    sub = st.upMbps?.let { megabytes(it) } ?: "",
                    modifier = Modifier.weight(1f),
                )
            }
            HDivider()
            Row(Modifier.height(IntrinsicSize.Min)) {
                Stat(
                    label = "Ping", arrow = 0, arrowColor = Color.Unspecified,
                    value = st.pingMs?.toString() ?: "—", unit = if (st.pingMs != null) "ms" else "",
                    sub = if (st.pingMs != null) "Latency" else "",
                    modifier = Modifier.weight(1f),
                )
                VDivider()
                Stat(
                    label = "Jitter", arrow = 0, arrowColor = Color.Unspecified,
                    value = st.jitterMs?.toString() ?: "—", unit = if (st.jitterMs != null) "ms" else "",
                    sub = st.jitterMs?.let { jitterLabel(it) } ?: "",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun Stat(
    label: String,
    value: String,
    unit: String,
    sub: String,
    arrow: Int,
    arrowColor: Color,
    modifier: Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (arrow != 0) {
                ArrowIcon(up = arrow == 2, color = arrowColor)
                Spacer(Modifier.width(5.dp))
            }
            Text(label, fontSize = 11.sp, color = muted)
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold)) { append(value) }
                if (unit.isNotEmpty()) withStyle(SpanStyle(fontSize = 11.sp, color = muted)) { append(" $unit") }
            },
        )
        Text(sub.ifEmpty { " " }, fontSize = 11.sp, color = muted)
    }
}

@Composable
private fun ArrowIcon(up: Boolean, color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        fun y(f: Float) = if (up) h * (1f - f) else h * f
        drawLine(color, Offset(w / 2, y(0f)), Offset(w / 2, y(1f)), sw, StrokeCap.Round)
        drawLine(color, Offset(w / 2, y(1f)), Offset(w * 0.12f, y(0.55f)), sw, StrokeCap.Round)
        drawLine(color, Offset(w / 2, y(1f)), Offset(w * 0.88f, y(0.55f)), sw, StrokeCap.Round)
    }
}

@Composable
private fun TestButton(st: SpeedTestState) {
    val label = when {
        st.running -> "Stop"
        st.phase == Phase.IDLE -> "Start test"
        else -> "Test again"
    }
    val bg = if (st.running) MaterialTheme.colorScheme.surfaceVariant else Blue
    val fg = if (st.running) MaterialTheme.colorScheme.onSurface else Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { if (st.running) SpeedTester.cancel() else SpeedTester.start() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoCard(
    server: SpeedServer,
    clientTitle: String,
    clientDetail: String,
    clientFailed: Boolean,
    canChange: Boolean,
    onChange: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canChange, onClick = onChange)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile { ServerGlyph(MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("SERVER", fontSize = 10.sp, color = muted, letterSpacing = 0.8.sp)
                    Text(server.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (server.detail.isNotBlank()) {
                        Text(server.detail, fontSize = 12.sp, color = muted, maxLines = 1)
                    }
                }
                server.pingMs?.let { PingPill(it) }
                if (canChange) {
                    Spacer(Modifier.width(6.dp))
                    ChevronIcon(muted)
                }
            }
            Box(Modifier.padding(start = 60.dp)) { HDivider() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = clientFailed) { SpeedTester.refreshInfo() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile { ClientGlyph(MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("CLIENT", fontSize = 10.sp, color = muted, letterSpacing = 0.8.sp)
                    Text(clientTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (clientDetail.isNotEmpty()) {
                        Text(clientDetail, fontSize = 12.sp, color = muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun IconTile(content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun pingColor(ms: Int): Color = when {
    ms < 50 -> Color(0xFF2E9E5B)
    ms < 120 -> Color(0xFFD99A00)
    else -> MobileOrange
}

@Composable
private fun PingPill(ms: Int) {
    val c = pingColor(ms)
    Text(
        "$ms ms",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = c,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSheet(
    servers: List<SpeedServer>,
    selectedId: String,
    searchResults: List<SpeedServer>,
    searching: Boolean,
    refreshing: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val muted = cs.onSurfaceVariant
    var query by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            SpeedTester.search("")
        } else {
            delay(500)
            SpeedTester.search(query)
        }
    }

    val q = query.trim()
    val local = if (q.isEmpty()) servers else servers.filter {
        it.name.contains(q, true) || it.detail.contains(q, true)
    }
    val remote = if (q.length < 2) emptyList() else searchResults.filter { r -> local.none { it.id == r.id } }
    val shown = (local + remote).distinctBy { it.id }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        BlurBehind()
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Select server", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("${shown.size} servers", fontSize = 11.sp, color = muted)
                }
                Text(
                    if (refreshing) "Refreshing\u2026" else "Refresh",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (refreshing) muted else cs.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !refreshing) { SpeedTester.refreshInfo() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            SearchField(query, { query = it }, searching, Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(6.dp))

            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.id }) { sv ->
                    ServerRow(sv, sv.id == selectedId) { onPick(sv.id) }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (q.isEmpty()) "No other servers found. Check your connection and tap Refresh."
                            else if (searching) "Searching…" else "No servers match \"$q\"",
                            fontSize = 12.sp,
                            color = muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (searching) {
                    item {
                        Text("Searching more…", fontSize = 12.sp, color = muted, modifier = Modifier.padding(16.dp, 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, busy: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(cs.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search city, country or provider", fontSize = 14.sp, color = cs.onSurfaceVariant.copy(alpha = 0.8f))
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = cs.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).clickable { onChange("") },
                contentAlignment = Alignment.Center,
            ) { CloseGlyph(cs.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ServerRow(s: SpeedServer, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (on) cs.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) cs.primary else cs.onSurface,
                maxLines = 1,
            )
            if (s.detail.isNotBlank()) {
                Text(s.detail, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
        s.pingMs?.let { PingPill(it) }
        if (on) {
            Spacer(Modifier.width(10.dp))
            CheckIcon(cs.primary)
        }
    }
}

@Composable
private fun SearchGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val sw = 1.8.dp.toPx()
        val w = size.width
        drawCircle(color, radius = w * 0.34f, center = Offset(w * 0.42f, w * 0.42f), style = Stroke(sw))
        drawLine(color, Offset(w * 0.67f, w * 0.67f), Offset(w * 0.92f, w * 0.92f), sw, StrokeCap.Round)
    }
}

@Composable
private fun CloseGlyph(color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val sw = 1.8.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), sw, StrokeCap.Round)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
    }
}

@Composable
private fun ChevronIcon(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val sw = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.3f, h * 0.15f), Offset(w * 0.72f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.72f, h * 0.5f), Offset(w * 0.3f, h * 0.85f), sw, StrokeCap.Round)
    }
}

@Composable
private fun ServerGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val sw = 1.6.dp.toPx()
        val w = size.width
        listOf(0.1f, 0.56f).forEach { y ->
            drawRoundRect(
                color, Offset(w * 0.08f, w * y), Size(w * 0.84f, w * 0.34f),
                androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = Stroke(sw),
            )
            drawCircle(color, radius = w * 0.04f, center = Offset(w * 0.26f, w * (y + 0.17f)))
        }
    }
}

@Composable
private fun ClientGlyph(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val sw = 1.6.dp.toPx()
        val w = size.width
        val c = Offset(w / 2, w / 2)
        drawCircle(color, radius = w * 0.42f, center = c, style = Stroke(sw))
        drawOval(color, Offset(w * 0.3f, w * 0.08f), Size(w * 0.4f, w * 0.84f), style = Stroke(sw))
        drawLine(color, Offset(w * 0.08f, w / 2), Offset(w * 0.92f, w / 2), sw)
    }
}

@Composable
private fun CheckIcon(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val sw = 1.8.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.4f, h * 0.82f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.82f), Offset(w * 0.9f, h * 0.2f), sw, StrokeCap.Round)
    }
}

private fun mbps(v: Double): String =
    String.format(Locale.US, if (v >= 100) "%.0f" else "%.1f", v)

private fun megabytes(v: Double): String =
    String.format(Locale.US, if (v / 8.0 >= 10) "%.1f MB/s" else "%.2f MB/s", v / 8.0)

private fun qualityLabel(down: Double): String = when {
    down < 5 -> "Fine for browsing"
    down < 15 -> "Good for video calls"
    down < 50 -> "Good for HD streaming"
    down < 150 -> "Good for 4K streaming"
    else -> "Great for everything"
}

private fun jitterLabel(ms: Int): String = when {
    ms < 10 -> "Stable connection"
    ms < 30 -> "Slight variation"
    else -> "Unstable connection"
}
