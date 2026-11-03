package com.netmonitor.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.netmonitor.app.data.UpdateChecker
import com.netmonitor.app.data.UpdateInfo
import com.netmonitor.app.data.UpdateResult
import com.netmonitor.app.ui.theme.CardDialog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.netmonitor.app.ui.theme.ConfirmDialog
import com.netmonitor.app.data.UsageStore
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import com.netmonitor.app.ui.theme.plainCard
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

import android.net.Uri
import com.netmonitor.app.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netmonitor.app.data.Accent
import com.netmonitor.app.data.AppSettings
import com.netmonitor.app.data.ChartStyle
import com.netmonitor.app.data.SettingsData
import com.netmonitor.app.data.SpeedState
import com.netmonitor.app.data.Snapshot
import com.netmonitor.app.data.SpeedUnit
import com.netmonitor.app.data.TapAction
import com.netmonitor.app.data.ThemeMode
import com.netmonitor.app.service.SpeedService
import com.netmonitor.app.util.WifiName
import com.netmonitor.app.ui.theme.Blue
import com.netmonitor.app.ui.theme.UpGreen
import com.netmonitor.app.util.NotifText
import com.netmonitor.app.util.StatusIcon
import com.netmonitor.app.util.Format
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit

private enum class Page { MAIN, NOTIFICATION }

private enum class Dialog { NONE, UNIT, THEME, CHART, LIMIT, DAILY_LIMIT, START_HOUR }

private val Amber = Color(0xFFBA7517)

@Composable
fun SettingsScreen(active: Boolean = true) {
    var page by remember { mutableStateOf(Page.MAIN) }
    BackHandler(enabled = active && page != Page.MAIN) { page = Page.MAIN }

    when (page) {
        Page.MAIN -> MainSettings(
            onOpenNotification = { page = Page.NOTIFICATION },
        )
        Page.NOTIFICATION -> NotificationSettings(onBack = { page = Page.MAIN })
    }
}

@Composable
private fun MainSettings(onOpenNotification: () -> Unit) {
    val context = LocalContext.current
    val st by AppSettings.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf(Dialog.NONE) }
    val snap by SpeedState.state.collectAsStateWithLifecycle()

    var open by rememberSaveable { mutableStateOf<String?>(null) }

    var confirmReset by remember { mutableStateOf<String?>(null) }

    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifAllowed = remember(refresh) { notificationsAllowed(context) }
    val batteryAllowed = remember(refresh) { batteryUnrestricted(context) }
    val locationAllowed = remember(refresh) { WifiName.hasPermission(context) }
    val locationAlways = remember(refresh) { WifiName.hasBackgroundPermission(context) }
    val gpsOn = remember(refresh) { WifiName.isLocationOn(context) }
    var bgAsked by remember { mutableStateOf(false) }
    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        SettingsGroup {
            ToggleRow(
                title = "NetMonitor",
                subtitle = if (st.monitoringOn) "On · live speed and usage are running"
                else "Off · notification and background service are stopped",
                checked = st.monitoringOn,
                onChange = { v ->
                    AppSettings.update { it.copy(monitoringOn = v) }
                    if (v) SpeedService.start(context) else SpeedService.stop(context)
                },
            )
        }

        val needs = listOf(notifAllowed, batteryAllowed).count { !it }
        fun toggle(id: String) { open = if (open == id) null else id }

        val permissions: @Composable () -> Unit = {
            ExpandableCard(
                title = "Permissions",
                summary = if (needs == 0) "All allowed" else "$needs needs action",
                expanded = open == "permissions",
                onToggle = { toggle("permissions") },
                dot = if (needs == 0) UpGreen else Amber,
            ) {
                SettingRow(
                    title = "Notification permission",
                    value = if (notifAllowed) "Allowed" else "Needs action",
                    dot = if (notifAllowed) UpGreen else Amber,
                    onClick = {
                        if (!notifAllowed && Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    },
                )
                RowDivider()
                SettingRow(
                    title = "Battery optimization",
                    subtitle = "Set to unrestricted so the notification keeps running",
                    value = if (batteryAllowed) "Allowed" else "Needs action",
                    dot = if (batteryAllowed) UpGreen else Amber,
                    onClick = { openBatterySettings(context) },
                )
                RowDivider()
                SettingRow(
                    title = "Location permission",
                    subtitle = "Optional. Needed to show the WiFi name (\"Allow all the time\")",
                    value = when {
                        !locationAllowed -> "Optional"
                        !locationAlways -> "Only while using"
                        else -> "Allowed all the time"
                    },
                    dot = if (locationAllowed && locationAlways) UpGreen else if (locationAllowed) Amber else null,
                    onClick = {
                        when {
                            !locationAllowed -> locationLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                            !locationAlways && !bgAsked && Build.VERSION.SDK_INT >= 29 -> {
                                bgAsked = true
                                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                            else -> context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
                RowDivider()
                SettingRow(
                    title = "GPS location",
                    subtitle = "Optional. Must be on to read the WiFi name",
                    value = if (gpsOn) "Enabled" else "Disabled",
                    dot = if (gpsOn) UpGreen else null,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }
        }

        if (needs > 0) permissions()

        ExpandableCard(
            title = "Notification",
            summary = "What it shows, WiFi name, lockscreen, tap action",
            expanded = open == "notification",
            onToggle = { toggle("notification") },
        ) {
            SettingRow(
                title = "Notification",
                subtitle = "What it shows, WiFi name, lockscreen, tap action",
                onClick = onOpenNotification,
            )
        }

        ExpandableCard(
            title = "Display",
            summary = "${st.speedUnit.label} · ${st.theme.label}",
            expanded = open == "display",
            onToggle = { toggle("display") },
        ) {
            SettingRow("Speed unit", value = st.speedUnit.label, onClick = { dialog = Dialog.UNIT })
            RowDivider()
            SettingRow("Theme", value = st.theme.label, onClick = { dialog = Dialog.THEME })
            RowDivider()
            SettingRow(
                title = "Chart style",
                subtitle = "Graph on the home screen",
                value = st.chartStyle.label.substringBefore(" ("),
                onClick = { dialog = Dialog.CHART },
            )
        }

        ExpandableCard(
            title = "Data",
            summary = when {
                st.dailyLimitOn && st.dailyLimitMb > 0 && st.limitMb > 0 -> "Daily and monthly limits"
                st.dailyLimitOn && st.dailyLimitMb > 0 -> "Daily limit ${limitText(st.dailyLimitMb)}"
                st.limitMb > 0 -> "Limit ${st.limitMb} MB / month"
                else -> "No data limit"
            },
            expanded = open == "data",
            onToggle = { toggle("data") },
        ) {
            SettingRow(
                title = "Daily data limit",
                subtitle = "Alerts you when mobile data used today reaches it",
                value = if (st.dailyLimitOn && st.dailyLimitMb > 0) "${limitText(st.dailyLimitMb)} / day" else "Off",
                onClick = { dialog = Dialog.DAILY_LIMIT },
            )
            RowDivider()
            SettingRow(
                title = "Data usage limit",
                subtitle = "Alerts you when mobile data this month reaches it. Set 0 to turn off",
                value = if (st.limitMb > 0) "${st.limitMb} MB / month" else "Off",
                onClick = { dialog = Dialog.LIMIT },
            )
            RowDivider()
            ToggleRow(
                title = "Limit alerts",
                subtitle = "Sends an alert when a data limit is reached",
                checked = st.limitAlerts,
                onChange = { v -> AppSettings.update { it.copy(limitAlerts = v) } },
            )
            RowDivider()
            SettingRow(
                title = "Starting hour of day",
                subtitle = "Match your ISP reset time or wake-up time",
                value = hourLabel(st.startHour),
                onClick = { dialog = Dialog.START_HOUR },
            )
        }

        if (needs == 0) permissions()

        ExpandableCard(
            title = "Reset",
            summary = "Restore settings or clear usage history",
            expanded = open == "reset",
            onToggle = { toggle("reset") },
        ) {
            SettingRow(
                title = "Reset settings",
                subtitle = "Back to default. Usage history is kept",
                onClick = { confirmReset = "settings" },
            )
            RowDivider()
            SettingRow(
                title = "Clear usage history",
                subtitle = "Deletes all saved WiFi and mobile usage",
                onClick = { confirmReset = "usage" },
            )
            RowDivider()
            SettingRow(
                title = "Reset everything",
                subtitle = "Settings and usage history, like a fresh install",
                onClick = { confirmReset = "all" },
            )
        }

        ExpandableCard(
            title = "About",
            summary = "NetMonitor · v${versionName(context)}",
            expanded = open == "about",
            onToggle = { toggle("about") },
        ) {
            AboutContent(versionName(context))
        }
        Spacer(Modifier.height(8.dp))
    }

    confirmReset?.let { kind ->
        val (title, text) = when (kind) {
            "settings" -> "Reset settings?" to "All settings go back to default. Usage history is kept."
            "usage" -> "Clear usage history?" to "All saved WiFi and mobile usage will be deleted. This cannot be undone."
            else -> "Reset everything?" to "Settings go back to default and all usage history is deleted. This cannot be undone."
        }
        ConfirmDialog(
            title = title,
            text = text,
            confirmLabel = "Reset",
            onConfirm = {
                if (kind == "settings" || kind == "all") {
                    AppSettings.update { SettingsData() }
                    SpeedService.start(context)
                }
                if (kind == "usage" || kind == "all") UsageStore(context).clearAll()
                Toast.makeText(context, "Done", Toast.LENGTH_SHORT).show()
                confirmReset = null
            },
            onDismiss = { confirmReset = null },
        )
    }

    when (dialog) {
        Dialog.UNIT -> ChoiceDialog(
            title = "Speed unit",
            options = SpeedUnit.values().map { it to it.label },
            selected = st.speedUnit,
            onSelect = { u -> AppSettings.update { it.copy(speedUnit = u) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.values().map { it to it.label },
            selected = st.theme,
            onSelect = { t -> AppSettings.update { it.copy(theme = t) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.CHART -> ChoiceDialog(
            title = "Chart style",
            options = ChartStyle.values().map { it to it.label },
            selected = st.chartStyle,
            onSelect = { c -> AppSettings.update { it.copy(chartStyle = c) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.START_HOUR -> ChoiceDialog(
            title = "Starting hour of day",
            options = (0..23).map { it to hourLabel(it) },
            selected = st.startHour,
            onSelect = { h -> AppSettings.update { it.copy(startHour = h) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.DAILY_LIMIT -> DailyLimitDialog(
            usedBytes = snap.todayMobile,
            currentMb = st.dailyLimitMb,
            currentOn = st.dailyLimitOn,
            onSave = { mb, on -> AppSettings.update { it.copy(dailyLimitMb = mb, dailyLimitOn = on) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.LIMIT -> LimitDialog(
            current = st.limitMb,
            onSave = { mb -> AppSettings.update { it.copy(limitMb = mb) } },
            onDismiss = { dialog = Dialog.NONE },
        )
        Dialog.NONE -> Unit
    }
}

private class SsidControl(
    val onToggle: (Boolean) -> Unit,
)

@Composable
private fun rememberSsidControl(): SsidControl {
    val context = LocalContext.current
    val st by AppSettings.state.collectAsStateWithLifecycle()

    var refresh by remember { mutableIntStateOf(0) }

    var grantRequested by remember { mutableStateOf(false) }
    fun enableSsidIfGranted() {
        if (grantRequested && WifiName.hasPermission(context)) {
            grantRequested = false
            AppSettings.update { it.copy(showSsid = true) }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        enableSsidIfGranted()
    }
    val fine = remember(refresh) { WifiName.hasPermission(context) }
    val always = remember(refresh) { WifiName.hasBackgroundPermission(context) }
    var bgAsked by remember { mutableStateOf(false) }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refresh++
        enableSsidIfGranted()
    }
    val locLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refresh++
        enableSsidIfGranted()
    }

    fun openAppSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun grant() {
        grantRequested = true
        when {
            !fine -> locLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            !always -> {
                if (!bgAsked && Build.VERSION.SDK_INT >= 29) {
                    bgAsked = true
                    bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    openAppSettings()
                }
            }
        }
    }

    return SsidControl(
        onToggle = { v ->
            AppSettings.update { it.copy(showSsid = v) }
            if (!v) grantRequested = false
            if (v && (!fine || !always)) grant()
        },
    )
}

@Composable
private fun NotificationSettings(onBack: () -> Unit) {
    val st by AppSettings.state.collectAsStateWithLifecycle()
    val snap by SpeedState.state.collectAsStateWithLifecycle()
    var showTap by remember { mutableStateOf(false) }
    val ssid = rememberSsidControl()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.clickable(onClick = onBack).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Notification", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))

        SectionLabel("Preview")
        NotificationPreview(snap, st)

        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            ToggleRow(
                title = "Hide lockscreen notification",
                subtitle = "Hides the notification on lockscreen",
                checked = st.hideLockscreen,
                onChange = { v -> AppSettings.update { it.copy(hideLockscreen = v) } },
            )
            RowDivider()
            ToggleRow(
                title = "Notification preference",
                subtitle = "Show only when internet is connected",
                checked = st.onlyWhenConnected,
                onChange = { v -> AppSettings.update { it.copy(onlyWhenConnected = v) } },
            )
            RowDivider()
            ToggleRow(
                title = "Show Up/Down speed",
                subtitle = "Shows upload and download speed",
                checked = st.showSpeed,
                onChange = { v -> AppSettings.update { it.copy(showSpeed = v) } },
            )
            RowDivider()
            ToggleRow(
                title = "Speed in status bar",
                subtitle = "Shows live speed on phone status bar",
                checked = st.statusBarSpeed,
                onChange = { v -> AppSettings.update { it.copy(statusBarSpeed = v) } },
            )
            RowDivider()
            ToggleRow(
                title = "Show signal strength",
                subtitle = "Shows signal % in the notification",
                checked = st.showSignal,
                onChange = { v -> AppSettings.update { it.copy(showSignal = v) } },
            )
            RowDivider()
            ToggleRow(
                title = "Show WiFi / Mobile usage",
                subtitle = "Shows data used on each network",
                checked = st.showUsage,
                onChange = { v -> AppSettings.update { it.copy(showUsage = v) } },
            )
            RowDivider()
            ToggleRow(
                title = "Show Today / This month",
                subtitle = "Shows daily and monthly totals",
                checked = st.showTotals,
                onChange = { v -> AppSettings.update { it.copy(showTotals = v) } },
            )
            RowDivider()
            ToggleRow(
                title = "Show WiFi SSID in notification",
                subtitle = "Shows the connected WiFi name in the notification",
                checked = st.showSsid,
                onChange = ssid.onToggle,
            )
            RowDivider()
            ToggleRow(
                title = "Show Stop button",
                subtitle = "Adds a Stop action to the notification",
                checked = st.showStopButton,
                onChange = { v -> AppSettings.update { it.copy(showStopButton = v) } },
            )
            RowDivider()
            SettingRow(
                title = "Notification tap action",
                subtitle = st.tapAction.label,
                onClick = { showTap = true },
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showTap) {
        ChoiceDialog(
            title = "Notification tap action",
            options = TapAction.values().map { it to it.label },
            selected = st.tapAction,
            onSelect = { a -> AppSettings.update { it.copy(tapAction = a) } },
            onDismiss = { showTap = false },
        )
    }
}

@Composable
private fun NotificationPreview(snap: Snapshot, st: SettingsData) {
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val text = MaterialTheme.colorScheme.onSurface
    val idle = NotifText.isIdle(snap, st)
    val title = if (idle) "NetMonitor" else NotifText.title(snap, st)
    val lines = if (idle) listOf("Waiting for internet\u2026") else NotifText.lines(snap, st)
    val stopShown = idle || st.showStopButton
    val hiddenOnLock = idle || st.hideLockscreen
    val clock = remember { java.text.SimpleDateFormat("h:mm", java.util.Locale.US).format(java.util.Date()) }
    val speed = if (st.statusBarSpeed && !idle) StatusIcon.parts(maxOf(snap.totalDown, snap.totalUp), st.speedUnit) else null

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PreviewLabel("Status bar")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(clock, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = text)
            if (speed != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(speed.first, fontSize = 16.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = text)
                    Text(speed.second, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold, color = text)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                listOf(5, 8, 11, 14).forEach {
                    Box(
                        Modifier
                            .padding(end = 2.dp)
                            .width(3.dp)
                            .height(it.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(text)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(24.dp, 12.dp)
                    .border(1.5.dp, text, RoundedCornerShape(4.dp))
                    .padding(2.5.dp)
            ) {
                Box(Modifier.size(14.dp, 7.dp).clip(RoundedCornerShape(2.dp)).background(text))
            }
        }

        Spacer(Modifier.height(6.dp))
        PreviewLabel("Notification")
        SettingsGroup {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F1720)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.requiredSize(34.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildString {
                            append("NetMonitor")
                            if (!idle && st.showSsid) snap.wifiName?.let { append("  \u00B7  $it") }
                            append("  \u00B7  now")
                        },
                        fontSize = 12.sp,
                        color = sub,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ArrowText(title, 16.sp, text, FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                lines.forEach { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = sub) }
                if (stopShown) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text("Stop", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        PreviewLabel("Lockscreen")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (hiddenOnLock) {
                Text("Notification hidden on lockscreen", fontSize = 13.sp, color = sub)
            } else {
                ArrowText(title, 14.sp, text, FontWeight.SemiBold)
                lines.firstOrNull()?.let { Text(it, fontSize = 12.sp, color = sub) }
            }
        }
    }
}

@Composable
private fun PreviewLabel(label: String) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ArrowText(text: String, fontSize: TextUnit, color: Color, weight: FontWeight? = null) {
    val annotated = remember(text) {
        buildAnnotatedString {
            text.forEach { ch ->
                when (ch) {
                    '\u2193' -> appendInlineContent("down", "\u2193")
                    '\u2191' -> appendInlineContent("up", "\u2191")
                    else -> append(ch)
                }
            }
        }
    }
    val boxW = fontSize * 0.7f
    val inline = mapOf(
        "down" to InlineTextContent(
            Placeholder(boxW, fontSize, PlaceholderVerticalAlign.TextCenter)
        ) { ArrowShape(down = true, color = color) },
        "up" to InlineTextContent(
            Placeholder(boxW, fontSize, PlaceholderVerticalAlign.TextCenter)
        ) { ArrowShape(down = false, color = color) },
    )
    Text(
        text = annotated,
        inlineContent = inline,
        fontSize = fontSize,
        fontWeight = weight,
        color = color,
    )
}

@Composable
private fun ArrowShape(down: Boolean, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val sw = h * 0.11f
        val x = w / 2f
        val top = h * 0.18f
        val bottom = h * 0.82f
        val headY = h * 0.45f
        val tipY = if (down) bottom else top
        val baseY = if (down) top else bottom
        val wingY = if (down) bottom - (bottom - headY) * 0.55f else top + (bottom - headY) * 0.55f
        drawLine(color, Offset(x, baseY), Offset(x, tipY), sw, StrokeCap.Round)
        drawLine(color, Offset(x, tipY), Offset(w * 0.08f, wingY), sw, StrokeCap.Round)
        drawLine(color, Offset(x, tipY), Offset(w * 0.92f, wingY), sw, StrokeCap.Round)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun ExpandableCard(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    dot: Color? = null,
    content: @Composable () -> Unit,
) {
    val arrow by animateFloatAsState(if (expanded) 270f else 90f, tween(200), label = "arrow")
    SettingsGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dot != null) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "›",
                Modifier.rotate(arrow),
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
        ) {
            Column {
                RowDivider()
                content()
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .plainCard(shape)
    ) { content() }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
    dot: Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            if (dot != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(5.dp))
            }
            Text(value, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (dark) Color(0xFFB4BCC8) else Color.White,
                uncheckedTrackColor = if (dark) Color(0xFF3A4350) else Color(0xFFC9D1DC),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    CardDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        },
    ) {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            options.forEachIndexed { i, (value, label) ->
                if (i > 0) RowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(value)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        Modifier.weight(1f),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    RadioButton(selected = value == selected, onClick = null)
                }
            }
        }
    }
}

private fun limitText(mb: Int): String =
    if (mb >= 1024) {
        val gb = mb / 1024.0
        if (mb % 1024 == 0) "${mb / 1024} GB" else String.format(java.util.Locale.US, "%.1f GB", gb)
    } else "$mb MB"

@Composable
private fun DailyLimitDialog(
    usedBytes: Long,
    currentMb: Int,
    currentOn: Boolean,
    onSave: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var enabled by remember { mutableStateOf(currentOn) }
    var gb by remember { mutableStateOf(currentMb >= 1024 && currentMb % 1024 == 0) }
    var text by remember {
        mutableStateOf(
            when {
                currentMb <= 0 -> ""
                gb -> (currentMb / 1024).toString()
                else -> currentMb.toString()
            }
        )
    }
    var error by remember { mutableStateOf(false) }

    fun pick(valueMb: Int) {
        gb = valueMb >= 1024
        text = if (gb) (valueMb / 1024).toString() else valueMb.toString()
        enabled = true
        error = false
    }

    CardDialog(
        title = "Daily mobile data limit",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
            TextButton(onClick = {
                val amount = text.toDoubleOrNull() ?: 0.0
                val mb = (amount * if (gb) 1024.0 else 1.0).roundToInt().coerceIn(0, 9_999_999)
                if (enabled && mb <= 0) {
                    error = true
                } else {
                    onSave(if (mb > 0) mb else currentMb, enabled && mb > 0)
                    onDismiss()
                }
            }) { Text("Save", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Limit mobile data per day", Modifier.weight(1f), fontSize = 14.sp)
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; error = false },
                    colors = SwitchDefaults.colors(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(cs.primary))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(Format.size(usedBytes), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Used today", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v.filter { it.isDigit() || it == '.' }.take(7)
                    if (text.isNotEmpty()) enabled = true
                    error = false
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                placeholder = { Text("Select or input") },
                isError = error,
                suffix = {
                    Row {
                        listOf(false to "MB", true to "GB").forEach { (isGb, label) ->
                            Text(
                                label,
                                Modifier.clickable { gb = isGb }.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = if (gb == isGb) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (gb == isGb) cs.primary else cs.onSurfaceVariant,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (error) {
                Text("Enter a limit first", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = cs.error)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200 to "200 MB", 500 to "500 MB", 800 to "800 MB", 1024 to "1 GB").forEach { (mb, label) ->
                    val amount = text.toDoubleOrNull() ?: 0.0
                    val selected = (amount * if (gb) 1024.0 else 1.0).roundToInt() == mb
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) cs.primary.copy(alpha = 0.18f) else cs.surfaceVariant)
                            .clickable { pick(mb) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) cs.primary else cs.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "0") }
    CardDialog(
        title = "Data usage limit",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                onSave(text.toIntOrNull() ?: 0)
                onDismiss()
            }) { Text("Save", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "Monthly mobile data limit in MB. Set 0 to turn off.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                suffix = { Text("MB") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

private const val DEV_NAME = "Mainul Islam"
private const val FB_URL = "https://www.facebook.com/MainulsSandbox"
private const val X_URL = "https://x.com/MainulsSandbox"
private const val GITHUB_URL = "https://github.com/M41NUL"

@Composable
private fun AboutContent(version: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface

    update?.let { info ->
        UpdateDialog(
            info = info,
            currentVersion = version,
            onUpdate = { openUrl(context, info.url); update = null },
            onDismiss = { update = null },
        )
    }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF14181F)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.requiredSize(72.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("NetMonitor", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Live network usage", fontSize = 13.sp, color = muted)
            }
            Text("Version $version", fontSize = 13.sp, color = muted)
        }
        RowDivider()

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.ic_system_update),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(ink),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Check for updates", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Version $version", fontSize = 13.sp, color = muted)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (checking) "Checking…" else "Check",
                Modifier
                    .clickable(enabled = !checking) {
                        checking = true
                        scope.launch {
                            when (val r = UpdateChecker.check(context)) {
                                is UpdateResult.Available -> update = r.info
                                UpdateResult.UpToDate ->
                                    Toast.makeText(context, "You're on the latest version", Toast.LENGTH_SHORT).show()
                                UpdateResult.Failed ->
                                    Toast.makeText(context, "Couldn't check for updates. Try again.", Toast.LENGTH_SHORT).show()
                            }
                            checking = false
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        RowDivider()

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.ic_person),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(ink),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("About Developer", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(DEV_NAME, fontSize = 13.sp, color = muted)
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialButton("Facebook", { openUrl(context, FB_URL) }) {
                    Text("f", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink)
                }
                SocialButton("X", { openUrl(context, X_URL) }) {
                    Image(
                        painterResource(R.drawable.ic_x_social), null,
                        Modifier.size(16.dp), colorFilter = ColorFilter.tint(ink),
                    )
                }
                SocialButton("GitHub", { openUrl(context, GITHUB_URL) }) {
                    Image(
                        painterResource(R.drawable.ic_github), null,
                        Modifier.size(20.dp), colorFilter = ColorFilter.tint(ink),
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
internal fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    val shape = RoundedCornerShape(20.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        com.netmonitor.app.ui.theme.BlurBehind()
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(cs.surface)
                .border(1.dp, cs.outlineVariant, shape)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(R.drawable.ic_system_update),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(accent),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Update available", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("NetMonitor", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                Box(
                    Modifier.clip(CircleShape).background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("New", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = accent)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Current version", Modifier.weight(1f), fontSize = 14.sp, color = cs.onSurfaceVariant)
                Text(currentVersion, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("New version", Modifier.weight(1f), fontSize = 14.sp, color = cs.onSurfaceVariant)
                Text(info.versionName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accent)
            }

            if (info.notes.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = cs.outlineVariant)
                Spacer(Modifier.height(14.dp))
                Text("What's new", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                info.notes.take(6).forEach { note ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(18.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(note, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Later", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.weight(1.1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Update now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

internal fun openUrl(context: Context, url: String) {
    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }
}

private fun hourLabel(h: Int): String {
    val hour12 = if (h % 12 == 0) 12 else h % 12
    return "$hour12:00 ${if (h < 12) "AM" else "PM"}"
}

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun batteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openNotificationSettings(context: Context) {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }
}

private fun openBatterySettings(context: Context) {
    val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }
}

private fun versionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "1.0.0"
