package com.netmonitor.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Snapshot(
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    val wifiDown: Long = 0,
    val wifiUp: Long = 0,
    val mobileDown: Long = 0,
    val mobileUp: Long = 0,
    val todayWifi: Long = 0,
    val todayMobile: Long = 0,
    val monthWifi: Long = 0,
    val monthMobile: Long = 0,
    val onWifi: Boolean = false,
    val networkLabel: String = "No connection",
    val signalPercent: Int = -1,

    val wifiName: String? = null,

    val mobileSignalPercent: Int = -1,

    val mobileOperator: String? = null,

    val history: List<Long> = emptyList(),
    val upHistory: List<Long> = emptyList(),
    val wifiHistory: List<Long> = emptyList(),
    val wifiUpHistory: List<Long> = emptyList(),
    val mobileHistory: List<Long> = emptyList(),
    val mobileUpHistory: List<Long> = emptyList(),
)

object SpeedState {
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun update(snapshot: Snapshot) {
        _state.value = snapshot
    }
}
