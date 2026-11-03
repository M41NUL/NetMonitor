package com.netmonitor.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import com.netmonitor.app.ui.theme.DialogBlur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netmonitor.app.MainActivity

private data class NavTab(val key: String, val label: String)

private val tabs = listOf(
    NavTab(MainActivity.TAB_HOME, "Home"),
    NavTab(MainActivity.TAB_HISTORY, "History"),
    NavTab(MainActivity.TAB_SPEED, "Speed"),
    NavTab(MainActivity.TAB_SETTINGS, "Settings"),
)

@Composable
fun NetMonitorApp(tab: String, onTabChange: (String) -> Unit) {
    val blurRadius by animateDpAsState(
        if (DialogBlur.count > 0) 14.dp else 0.dp, tween(220), label = "dialogBlur",
    )

    val ctx = androidx.compose.ui.platform.LocalContext.current
    var autoUpdate by remember { mutableStateOf<com.netmonitor.app.data.UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        if (com.netmonitor.app.data.UpdateChecker.promptDismissed) return@LaunchedEffect
        val r = com.netmonitor.app.data.UpdateChecker.check(ctx)
        if (r is com.netmonitor.app.data.UpdateResult.Available) autoUpdate = r.info
    }
    autoUpdate?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = com.netmonitor.app.data.UpdateChecker.installedVersionName(ctx),
            onUpdate = {
                com.netmonitor.app.data.UpdateChecker.promptDismissed = true
                openUrl(ctx, info.url); autoUpdate = null
            },
            onDismiss = {
                com.netmonitor.app.data.UpdateChecker.promptDismissed = true
                autoUpdate = null
            },
        )
    }
    BackHandler(enabled = tab != MainActivity.TAB_HOME) { onTabChange(MainActivity.TAB_HOME) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(if (blurRadius > 0.dp) Modifier.fillMaxSize().blur(blurRadius) else Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { FloatingNavBar(selected = tab, onSelect = onTabChange) },
        ) { padding ->

            val keys = tabs.map { it.key }
            val pagerState = rememberPagerState(initialPage = keys.indexOf(tab).coerceAtLeast(0)) { keys.size }
            val currentTab by rememberUpdatedState(tab)
            var programmatic by remember { mutableStateOf(false) }

            LaunchedEffect(tab) {
                val idx = keys.indexOf(tab)
                if (idx >= 0 && pagerState.currentPage != idx) {
                    programmatic = true
                    try {
                        if (kotlin.math.abs(idx - pagerState.currentPage) == 1) {
                            pagerState.animateScrollToPage(
                                idx,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f),
                            )
                        } else {
                            pagerState.scrollToPage(idx)
                        }
                    } finally {
                        programmatic = false
                    }
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    if (!programmatic && keys[page] != currentTab) onTabChange(keys[page])
                }
            }

            val fling = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1200f),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(padding),
                beyondViewportPageCount = 1,
                flingBehavior = fling,
                key = { keys[it] },
            ) { page ->

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    when (keys[page]) {
                        MainActivity.TAB_HOME -> HomeScreen(onRunSpeedTest = { onTabChange(MainActivity.TAB_SPEED) })
                        MainActivity.TAB_HISTORY -> HistoryScreen()
                        MainActivity.TAB_SPEED -> SpeedTestScreen()
                        MainActivity.TAB_SETTINGS -> SettingsScreen(active = currentTab == MainActivity.TAB_SETTINGS)
                        else -> ComingSoon(tabs[page].label)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun FloatingNavBar(selected: String, onSelect: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 4.dp, top = 4.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier
                    .fillMaxSize()

                    .pointerInput(Unit) {
                        fun pick(x: Float) {
                            val i = (x / size.width * tabs.size).toInt().coerceIn(0, tabs.lastIndex)
                            onSelect(tabs[i].key)
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { pick(it.x) },
                        ) { change, _ -> pick(change.position.x) }
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { t ->
                    NavItem(
                        tab = t,
                        selected = selected == t.key,
                        onClick = { onSelect(t.key) },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
        label = "navProgress",
    )
    val p = progress.coerceIn(0f, 1f)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "navPress",
    )

    val cs = MaterialTheme.colorScheme
    val fg = lerp(cs.onSurfaceVariant, cs.primary, p)

    Box(
        modifier
            .weight(1f + 0.7f * progress)
            .height(40.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(CircleShape)
            .background(cs.primary.copy(alpha = 0.14f * p))
            .semantics { contentDescription = tab.label }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        val sc = 1f + 0.1f * progress
                        scaleX = sc
                        scaleY = sc
                    },
            ) { drawNavIcon(tab.key, fg, selected) }

            Text(
                " " + tab.label,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val pl = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                        val w = (pl.width * p).roundToInt()
                        layout(w, pl.height) { pl.placeRelative(0, 0) }
                    }
                    .graphicsLayer { alpha = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f) }
                    .padding(start = 0.dp),
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun DrawScope.drawNavIcon(key: String, color: Color, selected: Boolean) {
    val k = size.width / 24f
    val stroke = Stroke(
        width = (if (selected) 2.2f else 2f),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    fun path(d: String) = PathParser().parsePathString(d).toPath()
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        when (key) {
            MainActivity.TAB_HOME -> {
                drawPath(path("M3 11L12 3L21 11"), color, style = stroke)
                drawPath(path("M5 10V20H10V14H14V20H19V10"), color, style = stroke)
            }
            MainActivity.TAB_HISTORY -> {
                drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke)
                drawPath(path("M12 7V12L15 14"), color, style = stroke)
            }
            MainActivity.TAB_SPEED -> {
                drawPath(path("M3.5 17A9 9 0 1 1 20.5 17"), color, style = stroke)
                drawPath(path("M12 15L16 10"), color, style = stroke)
            }
            else -> {
                drawPath(path("M4 7H13M19 7H20M4 17H5M11 17H20"), color, style = stroke)
                drawCircle(color, radius = 2.2f, center = Offset(16f, 7f), style = stroke)
                drawCircle(color, radius = 2.2f, center = Offset(8f, 17f), style = stroke)
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
