package com.netmonitor.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.os.Looper
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.netmonitor.app.MainActivity
import com.netmonitor.app.R
import com.netmonitor.app.data.AppSettings
import com.netmonitor.app.data.Snapshot
import com.netmonitor.app.data.SpeedState
import com.netmonitor.app.data.UsageStore
import com.netmonitor.app.util.Format
import com.netmonitor.app.util.NotifText
import com.netmonitor.app.util.StatusIcon
import com.netmonitor.app.util.WifiName
import com.netmonitor.app.widget.GlassWidgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SpeedService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private lateinit var store: UsageStore

    private var lastTotalRx = 0L
    private var lastTotalTx = 0L
    private var lastMobileRx = 0L
    private var lastMobileTx = 0L
    private var lastTickMs = 0L
    @Volatile private var pendingWifi = 0L
    @Volatile private var pendingMobile = 0L
    private var pendingTicks = 0
    private val history = ArrayDeque<Long>()
    private val upHistory = ArrayDeque<Long>()
    private val wifiHistory = ArrayDeque<Long>()
    private val wifiUpHistory = ArrayDeque<Long>()
    private val mobileHistory = ArrayDeque<Long>()
    private val mobileUpHistory = ArrayDeque<Long>()
    @Volatile private var lastSnap = Snapshot()
    @Volatile private var forceRefresh = false
    private var lastPostKey = ""
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshNetwork() }

    @Volatile private var cellPercent = -1
    private var telephony: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var phoneListener: Any? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!AppSettings.current.monitoringOn) return
            if (job?.isActive != true) startLoop()
            forceRefresh = true
            scheduleRefresh()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        store = UsageStore(this)
        store.prune()
        createChannel()
        registerSignalListener()
        registerNetworkWatchers()
        try {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppSettings.update { it.copy(monitoringOn = false) }
            cancelWatchdog()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!AppSettings.current.monitoringOn) {
            promote(buildNotification(lastSnap))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        applyNetwork()
        lastPostKey = ""
        promote(buildNotification(lastSnap))
        SpeedState.update(lastSnap)

        if (job?.isActive != true) startLoop()
        scheduleWatchdog(WATCHDOG_MS)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleWatchdog(2_000)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterNetworkWatchers()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        if (!AppSettings.current.monitoringOn) {
            cancelWatchdog()

            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                        .unregisterNetworkCallback(wakeIntent())
                } catch (e: Exception) {
                }
            }
            SpeedState.update(Snapshot())
        }
        unregisterSignalListener()
        job?.cancel()
        flushUsage()
        scope.cancel()
        super.onDestroy()
    }

    private fun startLoop() {
        readCounters()
        job?.cancel()
        job = scope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            try {
                loop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delay(2_000)
                readCounters()
            }
        }
    }

    private fun watchdogIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 9, Intent(this, NetworkReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleWatchdog(delayMs: Long) {
        if (!AppSettings.current.monitoringOn) return
        try {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMs,
                watchdogIntent()
            )
        } catch (e: Exception) {
        }
    }

    private fun cancelWatchdog() {
        try {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(watchdogIntent())
        } catch (e: Exception) {
        }
    }

    private fun flushUsage() {
        if (pendingWifi != 0L || pendingMobile != 0L) {
            store.add(wifiBytes = pendingWifi, mobileBytes = pendingMobile)
        }
        pendingWifi = 0L
        pendingMobile = 0L
        pendingTicks = 0
    }

    private fun readCounters() {
        lastTotalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
        lastTotalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
        lastMobileRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0)
        lastMobileTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0)
        lastTickMs = SystemClock.elapsedRealtime()
    }

    private suspend fun loop() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        while (scope.isActive) {
            val wait = lastTickMs + 1000 - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            val now = SystemClock.elapsedRealtime()

            val elapsed = (now - lastTickMs).coerceAtLeast(1)
            lastTickMs = now

            val totalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0)
            val totalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0)
            val mobRx = TrafficStats.getMobileRxBytes().coerceAtLeast(0)
            val mobTx = TrafficStats.getMobileTxBytes().coerceAtLeast(0)

            val dTotalRx = delta(totalRx, lastTotalRx)
            val dTotalTx = delta(totalTx, lastTotalTx)
            val dMobRx = delta(mobRx, lastMobileRx).coerceAtMost(dTotalRx)
            val dMobTx = delta(mobTx, lastMobileTx).coerceAtMost(dTotalTx)
            lastTotalRx = totalRx
            lastTotalTx = totalTx
            lastMobileRx = mobRx
            lastMobileTx = mobTx

            val dWifiRx = dTotalRx - dMobRx
            val dWifiTx = dTotalTx - dMobTx

            pendingWifi += dWifiRx + dWifiTx
            pendingMobile += dMobRx + dMobTx
            if (++pendingTicks >= 5) flushUsage()

            val rTotalRx = dTotalRx * 1000 / elapsed
            val rTotalTx = dTotalTx * 1000 / elapsed
            val rWifiRx = dWifiRx * 1000 / elapsed
            val rWifiTx = dWifiTx * 1000 / elapsed
            val rMobRx = dMobRx * 1000 / elapsed
            val rMobTx = dMobTx * 1000 / elapsed

            history.addLast(rTotalRx)
            while (history.size > 30) history.removeFirst()
            upHistory.addLast(rTotalTx)
            while (upHistory.size > 30) upHistory.removeFirst()
            for ((q, v) in listOf(
                wifiHistory to rWifiRx, wifiUpHistory to rWifiTx,
                mobileHistory to rMobRx, mobileUpHistory to rMobTx,
            )) {
                q.addLast(v)
                while (q.size > 30) q.removeFirst()
            }

            val net = networkInfo()
            val snap = Snapshot(
                totalDown = rTotalRx,
                totalUp = rTotalTx,
                wifiDown = rWifiRx,
                wifiUp = rWifiTx,
                mobileDown = rMobRx,
                mobileUp = rMobTx,
                todayWifi = store.todayWifi() + pendingWifi,
                todayMobile = store.todayMobile() + pendingMobile,
                monthWifi = store.monthWifi() + pendingWifi,
                monthMobile = store.monthMobile() + pendingMobile,
                onWifi = net.first,
                networkLabel = net.second,
                signalPercent = net.third,
                wifiName = ssidFor(net.first),
                mobileSignalPercent = cellPercent,
                mobileOperator = operatorName(),
                history = history.toList(),
                upHistory = upHistory.toList(),
                wifiHistory = wifiHistory.toList(),
                wifiUpHistory = wifiUpHistory.toList(),
                mobileHistory = mobileHistory.toList(),
                mobileUpHistory = mobileUpHistory.toList(),
            )
            lastSnap = snap
            SpeedState.update(snap)
            publish(nm, snap)
        }
    }

    private fun promote(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun publish(nm: NotificationManager, snap: Snapshot) {
        val st = AppSettings.current
        val key = listOf(
            isIdle(snap),
            StatusIcon.parts(maxOf(snap.totalDown, snap.totalUp), st.speedUnit),
            NotifText.title(snap, st),
            NotifText.line2(snap, st),
            NotifText.expanded(snap, st),
            st.hashCode(),
            snap.wifiName,
        ).joinToString("|")
        if (key != lastPostKey) {
            lastPostKey = key
            nm.notify(NOTIF_ID, buildNotification(snap))
        }
        checkLimits(nm, snap)
        pushWidgets(snap)
    }

    private fun checkLimits(nm: NotificationManager, s: Snapshot) {
        val st = AppSettings.current
        if (!st.limitAlerts) return
        val prefs = getSharedPreferences("limit_alerts", Context.MODE_PRIVATE)
        val cal = store.today()
        val dayKey = String.format(
            java.util.Locale.US, "%04d%02d%02d",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        val monthKey = dayKey.substring(0, 6)

        if (st.dailyLimitOn && st.dailyLimitMb > 0) {
            val limit = st.dailyLimitMb * 1024L * 1024L
            val key = "$dayKey:${st.dailyLimitMb}"
            if (s.todayMobile >= limit && prefs.getString("daily", null) != key) {
                prefs.edit().putString("daily", key).apply()
                postLimitAlert(
                    nm, ALERT_DAILY_ID, "Daily data limit reached",
                    "You've used ${Format.size(s.todayMobile)} of ${Format.size(limit)} mobile data today."
                )
            }
        }
        if (st.limitMb > 0) {
            val limit = st.limitMb * 1024L * 1024L
            val key = "$monthKey:${st.limitMb}"
            if (s.monthMobile >= limit && prefs.getString("monthly", null) != key) {
                prefs.edit().putString("monthly", key).apply()
                postLimitAlert(
                    nm, ALERT_MONTHLY_ID, "Monthly data limit reached",
                    "You've used ${Format.size(s.monthMobile)} of ${Format.size(limit)} mobile data this month."
                )
            }
        }
    }

    private fun postLimitAlert(nm: NotificationManager, id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_net)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(AppSettings.current.tapAction.tab, id))
            .build()
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
        }
    }

    private var lastWidgetMs = 0L

    private fun pushWidgets(snap: Snapshot) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWidgetMs < 2_000) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        lastWidgetMs = now
        try {
            GlassWidgets.updateAll(this, snap)
        } catch (e: Exception) {
        }
    }

    private fun ssidFor(onWifi: Boolean): String? =
        if (onWifi) WifiName.read(this).name else null

    private fun isIdle(s: Snapshot) = NotifText.isIdle(s, AppSettings.current)

    private fun applyNetwork() {
        val net = networkInfo()
        lastSnap = lastSnap.copy(
            onWifi = net.first,
            networkLabel = net.second,
            signalPercent = net.third,
            wifiName = ssidFor(net.first),
            mobileSignalPercent = cellPercent,
                mobileOperator = operatorName(),
            todayWifi = store.todayWifi() + pendingWifi,
            todayMobile = store.todayMobile() + pendingMobile,
            monthWifi = store.monthWifi() + pendingWifi,
            monthMobile = store.monthMobile() + pendingMobile,
        )
    }

    private fun refreshNetwork() {
        val before = Triple(lastSnap.onWifi, lastSnap.networkLabel, lastSnap.wifiName)
        applyNetwork()
        val snap = lastSnap
        SpeedState.update(snap)
        val same = before == Triple(snap.onWifi, snap.networkLabel, snap.wifiName)
        if (same && !forceRefresh) return
        forceRefresh = false
        publish(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager, snap)
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable)
        handler.postDelayed(refreshRunnable, 150)
    }

    private fun registerNetworkWatchers() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = scheduleRefresh()
                override fun onLost(network: Network) = scheduleRefresh()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    scheduleRefresh()
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
        }

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val pi = wakeIntent()
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, pi)
            } catch (e: Exception) {
            }
        }
    }

    private fun wakeIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(this, 7, Intent(this, NetworkReceiver::class.java), flags)
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this, 8,
        Intent(this, SpeedService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun unregisterNetworkWatchers() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            netCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun registerSignalListener() {
        try {
            val base = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
            if (base.phoneType == TelephonyManager.PHONE_TYPE_NONE) return
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            val tm = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(subId) else base
            telephony = tm
            if (Build.VERSION.SDK_INT >= 31) {
                val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        cellPercent = cellPercentOf(signalStrength)
                    }
                }
                tm.registerTelephonyCallback(java.util.concurrent.Executor { it.run() }, cb)
                telephonyCallback = cb
            } else {
                val l = object : PhoneStateListener() {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                        cellPercent = signalStrength?.let { cellPercentOf(it) } ?: -1
                    }
                }
                tm.listen(l, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
                phoneListener = l
            }
        } catch (e: Exception) {
            cellPercent = -1
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterSignalListener() {
        try {
            val tm = telephony ?: return
            if (Build.VERSION.SDK_INT >= 31) {
                (telephonyCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
            } else {
                (phoneListener as? PhoneStateListener)?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (e: Exception) {
        }
    }

    private fun cellPercentOf(s: SignalStrength): Int {
        if (Build.VERSION.SDK_INT >= 29) {
            val dbm = s.cellSignalStrengths
                .map { it.dbm }
                .firstOrNull { it != Int.MAX_VALUE && it < 0 }
            if (dbm != null) return ((dbm + 120) * 2).coerceIn(0, 100)
        }
        return (s.level * 25).coerceIn(0, 100)
    }

    private fun operatorName(): String? = try {
        val base = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val tm = if (base != null && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) base.createForSubscriptionId(subId) else base
        tm?.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: tm?.simOperatorName?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun delta(now: Long, last: Long): Long = if (now >= last) now - last else now

    private fun networkInfo(): Triple<Boolean, String, Int> {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps == null -> Triple(false, "No connection", -1)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                @Suppress("DEPRECATION")
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val rssi = wm.connectionInfo?.rssi ?: -100
                val pct = WifiManager.calculateSignalLevel(rssi, 101).coerceIn(0, 100)
                Triple(true, "WiFi", pct)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                Triple(false, "Mobile data", cellPercent)
            else -> Triple(false, "Connected", -1)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),

                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            nm.deleteNotificationChannel(OLD_CHANNEL_ID)
            nm.createNotificationChannel(ch)

            val idle = NotificationChannel(
                IDLE_CHANNEL_ID,
                "Waiting for internet",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Shown quietly while there is no internet connection"
                setShowBadge(false)
            }
            nm.createNotificationChannel(idle)

            val quiet = NotificationChannel(
                QUIET_CHANNEL_ID,
                "Live speed (no status bar icon)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Same live notification, without any icon in the status bar"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            nm.createNotificationChannel(quiet)

            val alert = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Data limit alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tells you when mobile data reaches your daily or monthly limit"
                setShowBadge(false)
            }
            nm.createNotificationChannel(alert)
        }
    }

    private fun buildNotification(s: Snapshot): Notification {
        val st = AppSettings.current
        if (isIdle(s)) {
            return NotificationCompat.Builder(this, IDLE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_net)
                .setContentTitle("NetMonitor")
                .setContentText("Waiting for internet…")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp(st.tapAction.tab, 0))
                .addAction(0, "Stop", stopIntent())
                .build()
        }
        val builder = NotificationCompat.Builder(this, if (st.statusBarSpeed) CHANNEL_ID else QUIET_CHANNEL_ID)
            .setContentTitle(NotifText.title(s, st))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(if (st.statusBarSpeed) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(
                if (st.hideLockscreen) NotificationCompat.VISIBILITY_SECRET
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .setContentIntent(openApp(st.tapAction.tab, 0))
        val body = NotifText.line2(s, st)
        if (body.isNotEmpty()) builder.setContentText(body)
        val big = NotifText.expanded(s, st)
        if (big.isNotEmpty()) builder.setStyle(NotificationCompat.BigTextStyle().bigText(big))
        if (st.showStopButton) builder.addAction(0, "Stop", stopIntent())
        if (st.statusBarSpeed) {
            builder.setSmallIcon(StatusIcon.create(s.totalDown, s.totalUp, st.speedUnit))
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_net)
        }
        if (st.showSsid) s.wifiName?.let { builder.setSubText(it) }
        return builder.build()
    }

    private fun openApp(tab: String, requestCode: Int): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_TAB, tab)
        }
        return PendingIntent.getActivity(
            this, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "live_speed_v2"
        private const val OLD_CHANNEL_ID = "live_speed"
        const val IDLE_CHANNEL_ID = "live_speed_idle"
        const val QUIET_CHANNEL_ID = "live_speed_quiet"
        const val ALERT_CHANNEL_ID = "limit_alerts"
        private const val ALERT_DAILY_ID = 1002
        private const val ALERT_MONTHLY_ID = 1003
        const val ACTION_STOP = "com.netmonitor.app.STOP"
        const val NOTIF_ID = 1001
        private const val WATCHDOG_MS = 5 * 60 * 1000L

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, SpeedService::class.java))
            } catch (e: Exception) {
            }
        }

        fun start(context: Context) {
            AppSettings.init(context)
            if (!AppSettings.current.monitoringOn) return
            try {
                val i = Intent(context, SpeedService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                else context.startService(i)
            } catch (e: Exception) {
            }
        }
    }
}
