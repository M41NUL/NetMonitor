package com.netmonitor.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.netmonitor.app.MainActivity
import com.netmonitor.app.R
import com.netmonitor.app.data.AppSettings
import com.netmonitor.app.data.Snapshot
import com.netmonitor.app.data.SpeedState
import com.netmonitor.app.service.SpeedService
import com.netmonitor.app.util.Format

abstract class GlassWidget : AppWidgetProvider() {
    protected abstract val layoutId: Int
    protected abstract val tab: String

    protected abstract fun bind(v: RemoteViews, s: Snapshot, wDp: Int, hDp: Int, dark: Boolean, density: Float)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        AppSettings.init(context)
        push(context, manager, ids, SpeedState.state.value)
    }

    override fun onEnabled(context: Context) {
        SpeedService.start(context)
    }

    fun push(context: Context, manager: AppWidgetManager, ids: IntArray, snap: Snapshot) {
        val ui = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val dark = ui == Configuration.UI_MODE_NIGHT_YES
        val density = context.resources.displayMetrics.density
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_TAB, tab)
        }
        val pi = PendingIntent.getActivity(
            context, layoutId, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            val o = manager.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 110
            val v = RemoteViews(context.packageName, layoutId)
            bind(v, snap, wDp, hDp, dark, density)
            v.setOnClickPendingIntent(R.id.root, pi)
            manager.updateAppWidget(id, v)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle,
    ) {
        AppSettings.init(context)
        push(context, manager, intArrayOf(id), SpeedState.state.value)
    }

    protected fun networkName(s: Snapshot): String = s.wifiName ?: s.networkLabel

    protected fun pct(part: Long, total: Long): Int =
        if (total <= 0) 0 else Math.round(part * 100f / total)
}

class SpeedWidget : GlassWidget() {
    override val layoutId = R.layout.widget_speed
    override val tab = MainActivity.TAB_HOME
    override fun bind(v: RemoteViews, s: Snapshot, wDp: Int, hDp: Int, dark: Boolean, density: Float) {
        v.setTextViewText(R.id.tv_down, "\u2193 ${Format.speed(s.totalDown)}")
        v.setTextViewText(R.id.tv_up, "\u2191 ${Format.speed(s.totalUp)}")
    }
}

class UsageWidget : GlassWidget() {
    override val layoutId = R.layout.widget_usage
    override val tab = MainActivity.TAB_HISTORY
    override fun bind(v: RemoteViews, s: Snapshot, wDp: Int, hDp: Int, dark: Boolean, density: Float) {
        v.setTextViewText(R.id.tv_label, "Today")
        v.setTextViewText(R.id.tv_value, Format.size(s.todayWifi + s.todayMobile))
        v.setTextViewText(
            R.id.tv_sub,
            "WiFi ${Format.size(s.todayWifi).removeSuffix(" MB")} \u00B7 Mobile ${Format.size(s.todayMobile).removeSuffix(" MB")}"
        )
    }
}

class GraphWidget : GlassWidget() {
    override val layoutId = R.layout.widget_graph
    override val tab = MainActivity.TAB_HOME
    override fun bind(v: RemoteViews, s: Snapshot, wDp: Int, hDp: Int, dark: Boolean, density: Float) {
        val sig = if (s.signalPercent >= 0) " \u00B7 ${s.signalPercent}%" else ""
        v.setTextViewText(R.id.tv_net, networkName(s) + sig)
        v.setTextViewText(R.id.tv_down, "\u2193 ${Format.speed(s.totalDown)}")
        v.setTextViewText(R.id.tv_up, "\u2191 ${Format.speed(s.totalUp)}")
        v.setImageViewBitmap(R.id.iv_graph, WidgetCharts.graph(s.history, s.upHistory, wDp, hDp, dark, density))
    }
}

class MonthWidget : GlassWidget() {
    override val layoutId = R.layout.widget_month
    override val tab = MainActivity.TAB_HISTORY
    override fun bind(v: RemoteViews, s: Snapshot, wDp: Int, hDp: Int, dark: Boolean, density: Float) {
        val total = s.monthWifi + s.monthMobile
        v.setImageViewBitmap(R.id.iv_ring, WidgetCharts.ring(s.monthWifi, s.monthMobile, wDp, hDp, dark, density))
        v.setTextViewText(
            R.id.tv_sub,
            "WiFi ${pct(s.monthWifi, total)}% \u00B7 Mobile ${pct(s.monthMobile, total)}%"
        )
    }
}

class SignalWidget : GlassWidget() {
    override val layoutId = R.layout.widget_signal
    override val tab = MainActivity.TAB_HOME
    override fun bind(v: RemoteViews, s: Snapshot, wDp: Int, hDp: Int, dark: Boolean, density: Float) {
        v.setTextViewText(R.id.tv_net, networkName(s))
        v.setTextViewText(R.id.tv_signal, if (s.signalPercent >= 0) "${s.signalPercent}%" else "--")
        v.setTextViewText(
            R.id.tv_speed,
            "\u2193 ${Format.speed(s.totalDown)}  \u2191 ${Format.speed(s.totalUp)}"
        )
    }
}

class StripWidget : GlassWidget() {
    override val layoutId = R.layout.widget_strip
    override val tab = MainActivity.TAB_HOME
    override fun bind(v: RemoteViews, s: Snapshot, wDp: Int, hDp: Int, dark: Boolean, density: Float) {
        v.setTextViewText(R.id.tv_down, "\u2193 ${Format.speed(s.totalDown)}")
        v.setTextViewText(R.id.tv_up, "\u2191 ${Format.speed(s.totalUp)}")
        v.setTextViewText(R.id.tv_today, "Today ${Format.size(s.todayWifi + s.todayMobile)}")
    }
}

object GlassWidgets {
    private val all: List<GlassWidget> = listOf(
        SpeedWidget(), UsageWidget(), GraphWidget(), MonthWidget(), SignalWidget(), StripWidget(),
    )

    fun updateAll(context: Context, snap: Snapshot) {
        val manager = AppWidgetManager.getInstance(context)
        for (w in all) {
            val ids = manager.getAppWidgetIds(ComponentName(context, w.javaClass))
            if (ids.isNotEmpty()) w.push(context, manager, ids, snap)
        }
    }
}
