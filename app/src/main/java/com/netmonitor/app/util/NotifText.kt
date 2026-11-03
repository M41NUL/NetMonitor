package com.netmonitor.app.util

import com.netmonitor.app.data.SettingsData
import com.netmonitor.app.data.Snapshot

object NotifText {
    fun isIdle(s: Snapshot, st: SettingsData): Boolean =
        st.onlyWhenConnected && s.networkLabel == "No connection"

    fun title(s: Snapshot, st: SettingsData): String {
        val parts = mutableListOf<String>()
        if (st.showSpeed) parts += "\u2193 ${Format.speed(s.totalDown)}   \u2191 ${Format.speed(s.totalUp)}"
        if (st.showSignal && s.signalPercent >= 0) parts += "Signal ${s.signalPercent}%"
        return if (parts.isEmpty()) "NetMonitor" else parts.joinToString("   ")
    }

    private fun usageLine(s: Snapshot, st: SettingsData): String? =
        if (st.showUsage) "WiFi: ${Format.size(s.todayWifi)}     Mobile: ${Format.size(s.todayMobile)}" else null

    private fun totalsLine(s: Snapshot, st: SettingsData): String? =
        if (st.showTotals) {
            "Today: ${Format.size(s.todayMobile + s.todayWifi)}" +
                "     This month: ${Format.size(s.monthMobile + s.monthWifi)}"
        } else null

    private fun dailyLimitLine(s: Snapshot, st: SettingsData): String? {
        if (!st.dailyLimitOn || st.dailyLimitMb <= 0) return null
        val used = s.todayMobile
        val limit = st.dailyLimitMb * 1024L * 1024L
        return "Daily limit: ${Format.size(used)} of ${Format.size(limit)}" +
            if (used >= limit) "  (limit reached)" else ""
    }

    private fun limitLine(s: Snapshot, st: SettingsData): String? {
        if (st.limitMb <= 0) return null
        val used = s.monthMobile
        val limit = st.limitMb * 1024L * 1024L
        return "Mobile limit: ${Format.size(used)} of ${Format.size(limit)}" +
            if (used >= limit) "  (limit reached)" else ""
    }

    fun lines(s: Snapshot, st: SettingsData): List<String> =
        listOfNotNull(usageLine(s, st), totalsLine(s, st), dailyLimitLine(s, st), limitLine(s, st))

    fun line2(s: Snapshot, st: SettingsData): String = usageLine(s, st) ?: totalsLine(s, st) ?: ""

    fun expanded(s: Snapshot, st: SettingsData): String = lines(s, st).joinToString("\n")
}
