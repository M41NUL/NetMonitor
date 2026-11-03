package com.netmonitor.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SpeedService.start(context)
    }
}
