package com.netmonitor.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpeedUnit(val label: String) {
    BYTES("Bytes per second"),
    BITS("Bits per second"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class TapAction(val label: String, val tab: String) {
    HOME("Open app", "home"),
    SPEED("Run speed test", "speed"),
    HISTORY("Open history", "history"),
}

enum class Accent(val label: String, val argb: Int?) {
    SYSTEM("System default", null),
    BLUE("Blue", 0xFF185FA5.toInt()),
    GREEN("Green", 0xFF639922.toInt()),
    ORANGE("Orange", 0xFFD85A30.toInt()),
    RED("Red", 0xFFC0392B.toInt()),
    PURPLE("Purple", 0xFF7F4FC9.toInt()),
}

enum class ChartStyle(val label: String) {
    CLASSIC("Classic (smooth + fill)"),
    SMOOTH("Smooth line"),
    MULTI("Multi-line"),
    STEP("Step line"),
    BAR("Bar chart"),
}

data class SettingsData(
    val speedUnit: SpeedUnit = SpeedUnit.BYTES,
    val theme: ThemeMode = ThemeMode.SYSTEM,

    val limitMb: Int = 0,

    val dailyLimitMb: Int = 0,
    val dailyLimitOn: Boolean = false,

    val limitAlerts: Boolean = true,

    val startHour: Int = 0,
    val hideLockscreen: Boolean = true,
    val onlyWhenConnected: Boolean = true,
    val showSpeed: Boolean = true,

    val statusBarSpeed: Boolean = true,

    val showSsid: Boolean = false,

    val showSignal: Boolean = true,
    val showUsage: Boolean = true,
    val showTotals: Boolean = true,
    val showStopButton: Boolean = true,

    val monitoringOn: Boolean = true,
    val tapAction: TapAction = TapAction.HOME,
    val accent: Accent = Accent.SYSTEM,
    val chartStyle: ChartStyle = ChartStyle.CLASSIC,
)

object AppSettings {
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(SettingsData())
    val state: StateFlow<SettingsData> = _state.asStateFlow()
    val current: SettingsData get() = _state.value

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        val d = SettingsData()
        _state.value = SettingsData(
            speedUnit = enumOf(p.getString("speedUnit", null), d.speedUnit),
            theme = enumOf(p.getString("theme", null), d.theme),
            limitMb = p.getInt("limitMb", d.limitMb),
            dailyLimitMb = p.getInt("dailyLimitMb", d.dailyLimitMb),
            dailyLimitOn = p.getBoolean("dailyLimitOn", d.dailyLimitOn),
            limitAlerts = p.getBoolean("limitAlerts", d.limitAlerts),
            startHour = p.getInt("startHour", d.startHour).coerceIn(0, 23),
            hideLockscreen = p.getBoolean("hideLockscreen", d.hideLockscreen),
            onlyWhenConnected = p.getBoolean("onlyWhenConnected", d.onlyWhenConnected),
            showSpeed = p.getBoolean("showSpeed", d.showSpeed),
            statusBarSpeed = p.getBoolean("statusBarSpeed", d.statusBarSpeed),
            showSsid = p.getBoolean("showSsid", d.showSsid),
            showSignal = p.getBoolean("showSignal", d.showSignal),
            showUsage = p.getBoolean("showUsage", d.showUsage),
            showTotals = p.getBoolean("showTotals", d.showTotals),
            showStopButton = p.getBoolean("showStopButton", d.showStopButton),
            monitoringOn = p.getBoolean("monitoringOn", d.monitoringOn),
            tapAction = enumOf(p.getString("tapAction", null), d.tapAction),
            accent = enumOf(p.getString("accent", null), d.accent),
            chartStyle = enumOf(p.getString("chartStyle", null), d.chartStyle),
        )
    }

    fun update(change: (SettingsData) -> SettingsData) {
        val s = change(_state.value)
        _state.value = s
        prefs?.edit()
            ?.putString("speedUnit", s.speedUnit.name)
            ?.putString("theme", s.theme.name)
            ?.putInt("limitMb", s.limitMb)
            ?.putInt("dailyLimitMb", s.dailyLimitMb)
            ?.putBoolean("dailyLimitOn", s.dailyLimitOn)
            ?.putBoolean("limitAlerts", s.limitAlerts)
            ?.putInt("startHour", s.startHour)
            ?.putBoolean("hideLockscreen", s.hideLockscreen)
            ?.putBoolean("onlyWhenConnected", s.onlyWhenConnected)
            ?.putBoolean("showSpeed", s.showSpeed)
            ?.putBoolean("statusBarSpeed", s.statusBarSpeed)
            ?.putBoolean("showSsid", s.showSsid)
            ?.putBoolean("showSignal", s.showSignal)
            ?.putBoolean("showUsage", s.showUsage)
            ?.putBoolean("showTotals", s.showTotals)
            ?.putBoolean("showStopButton", s.showStopButton)
            ?.putBoolean("monitoringOn", s.monitoringOn)
            ?.putString("tapAction", s.tapAction.name)
            ?.putString("accent", s.accent.name)
            ?.putString("chartStyle", s.chartStyle.name)
            ?.apply()
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
