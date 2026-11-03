package com.netmonitor.app.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class UsageBucket(

    val label: String,

    val short: String,
    val wifi: Long,
    val mobile: Long,

    val isNow: Boolean = false,

    val future: Boolean = false,

    val tip: String = "",
) {
    val total: Long get() = wifi + mobile
}

class UsageStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)

    private val formats = object : ThreadLocal<HashMap<String, SimpleDateFormat>>() {
        override fun initialValue() = HashMap<String, SimpleDateFormat>()
    }

    private fun fmt(pattern: String, date: Date = Date()): String {
        val map = formats.get()!!
        val f = map.getOrPut(pattern) { SimpleDateFormat(pattern, Locale.US) }
        return f.format(date)
    }

    private fun dayDate(date: Date = Date()): Date =
        Date(date.time - AppSettings.current.startHour * 3_600_000L)

    fun add(wifiBytes: Long, mobileBytes: Long) {
        val w = wifiBytes.coerceAtLeast(0)
        val m = mobileBytes.coerceAtLeast(0)
        if (w == 0L && m == 0L) return
        val now = Date()
        val h = fmt("yyyyMMddHH", now)
        val d = fmt("yyyyMMdd", dayDate(now))
        val mo = fmt("yyyyMM", dayDate(now))
        prefs.edit()
            .putLong("h_${h}_w", prefs.getLong("h_${h}_w", 0) + w)
            .putLong("h_${h}_m", prefs.getLong("h_${h}_m", 0) + m)
            .putLong("d_${d}_w", prefs.getLong("d_${d}_w", 0) + w)
            .putLong("d_${d}_m", prefs.getLong("d_${d}_m", 0) + m)
            .putLong("m_${mo}_w", prefs.getLong("m_${mo}_w", 0) + w)
            .putLong("m_${mo}_m", prefs.getLong("m_${mo}_m", 0) + m)
            .apply()
    }

    fun todayWifi(): Long = prefs.getLong("d_${fmt("yyyyMMdd", dayDate())}_w", 0)
    fun todayMobile(): Long = prefs.getLong("d_${fmt("yyyyMMdd", dayDate())}_m", 0)
    fun monthWifi(): Long = prefs.getLong("m_${fmt("yyyyMM", dayDate())}_w", 0)
    fun monthMobile(): Long = prefs.getLong("m_${fmt("yyyyMM", dayDate())}_m", 0)

    fun hoursToday(): List<UsageBucket> {
        val day = fmt("yyyyMMdd")
        return (0..23).map { h ->
            val key = "h_$day${"%02d".format(h)}"
            UsageBucket(
                label = hourLabel(h),
                short = if (h % 6 == 0) h.toString() else "",
                wifi = prefs.getLong("${key}_w", 0),
                mobile = prefs.getLong("${key}_m", 0),
            )
        }
    }

    fun days(count: Int): List<UsageBucket> =
        (count - 1 downTo 0).map { back ->
            val date = Calendar.getInstance().apply { time = dayDate(); add(Calendar.DAY_OF_YEAR, -back) }.time
            val key = "d_${fmt("yyyyMMdd", date)}"
            val dayName = fmt("EEE", date)
            UsageBucket(
                label = if (back == 0) "Today, ${fmt("MMM d", date)}" else "$dayName, ${fmt("MMM d", date)}",
                short = dayName.take(1),
                wifi = prefs.getLong("${key}_w", 0),
                mobile = prefs.getLong("${key}_m", 0),
            )
        }

    fun months(count: Int): List<UsageBucket> =
        (count - 1 downTo 0).map { back ->
            val date = Calendar.getInstance().apply { time = dayDate(); add(Calendar.MONTH, -back) }.time
            val key = "m_${fmt("yyyyMM", date)}"
            UsageBucket(
                label = fmt("MMM yyyy", date),
                short = fmt("MMM", date),
                wifi = prefs.getLong("${key}_w", 0),
                mobile = prefs.getLong("${key}_m", 0),
            )
        }

    fun today(): Calendar = Calendar.getInstance().apply { time = dayDate() }

    fun firstYear(): Int {
        val cur = today().get(Calendar.YEAR)
        val first = prefs.all.keys
            .filter { it.startsWith("m_") && it.length >= 6 }
            .mapNotNull { it.substring(2, 6).toIntOrNull() }
            .minOrNull()
        return minOf(first ?: cur, cur)
    }

    private fun dayBucket(date: Date, todayKey: String): UsageBucket {
        val k = fmt("yyyyMMdd", date)
        return UsageBucket(
            label = fmt("d MMM, EEE", date),
            short = fmt("d", date),
            wifi = prefs.getLong("d_${k}_w", 0),
            mobile = prefs.getLong("d_${k}_m", 0),
            isNow = k == todayKey,
            future = k > todayKey,
            tip = fmt("d MMM", date),
        )
    }

    fun daysOfMonth(year: Int, month: Int): List<UsageBucket> {
        val todayKey = fmt("yyyyMMdd", dayDate())
        val cal = Calendar.getInstance().apply { clear(); set(year, month, 1, 12, 0, 0) }
        val count = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..count).map { d ->
            cal.set(Calendar.DAY_OF_MONTH, d)
            dayBucket(cal.time, todayKey)
        }
    }

    fun monthsOfYear(year: Int): List<UsageBucket> {
        val nowKey = fmt("yyyyMM", dayDate())
        return (0..11).map { m ->
            val date = Calendar.getInstance().apply { clear(); set(year, m, 1, 12, 0, 0) }.time
            val k = fmt("yyyyMM", date)
            UsageBucket(
                label = fmt("MMMM", date),
                short = fmt("MMM", date),
                wifi = prefs.getLong("m_${k}_w", 0),
                mobile = prefs.getLong("m_${k}_m", 0),
                isNow = k == nowKey,
                future = k > nowKey,
                tip = fmt("MMM yyyy", date),
            )
        }
    }

    fun years(first: Int, last: Int): List<UsageBucket> {
        val nowYear = today().get(Calendar.YEAR)
        return (first..last).map { y ->
            var w = 0L
            var m = 0L
            for (mo in 1..12) {
                val k = String.format(Locale.US, "%d%02d", y, mo)
                w += prefs.getLong("m_${k}_w", 0)
                m += prefs.getLong("m_${k}_m", 0)
            }
            UsageBucket(
                label = y.toString(),
                short = y.toString(),
                wifi = w,
                mobile = m,
                isNow = y == nowYear,
                future = y > nowYear,
                tip = y.toString(),
            )
        }
    }

    fun range(startMs: Long, endMs: Long): List<UsageBucket> {
        val todayKey = fmt("yyyyMMdd", dayDate())
        val endKey = fmt("yyyyMMdd", Date(endMs))
        val cal = Calendar.getInstance().apply {
            timeInMillis = startMs
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        val out = ArrayList<UsageBucket>()
        while (out.size < 366 && fmt("yyyyMMdd", cal.time) <= endKey) {
            out.add(dayBucket(cal.time, todayKey))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun prune() {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -62) }
        val cutoff = fmt("yyyyMMdd", cal.time) + "00"
        val old = prefs.all.keys.filter { it.startsWith("h_") && it.substring(2, 12) < cutoff }
        if (old.isEmpty()) return
        prefs.edit().apply { old.forEach { remove(it) } }.apply()
    }

    private fun hourLabel(h: Int): String = when {
        h == 0 -> "12 AM"
        h < 12 -> "$h AM"
        h == 12 -> "12 PM"
        else -> "${h - 12} PM"
    }
}
