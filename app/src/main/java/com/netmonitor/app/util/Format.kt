package com.netmonitor.app.util

import com.netmonitor.app.data.AppSettings
import com.netmonitor.app.data.SpeedUnit
import java.util.Locale

object Format {
    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun speed(bytesPerSec: Long): String {
        if (AppSettings.current.speedUnit == SpeedUnit.BITS) return bitSpeed(bytesPerSec)
        val b = bytesPerSec.coerceAtLeast(0).toDouble()
        return when {
            b < KB -> "0 KB/s"
            b < MB -> String.format(Locale.US, "%.0f KB/s", b / KB)
            else -> String.format(Locale.US, "%.1f MB/s", b / MB)
        }
    }

    private fun bitSpeed(bytesPerSec: Long): String {
        val bits = bytesPerSec.coerceAtLeast(0) * 8.0
        return when {
            bits < 1_000 -> "0 Kb/s"
            bits < 1_000_000 -> String.format(Locale.US, "%.0f Kb/s", bits / 1_000)
            else -> String.format(Locale.US, "%.1f Mb/s", bits / 1_000_000)
        }
    }

    fun size(bytes: Long): String {
        val b = bytes.coerceAtLeast(0).toDouble()
        return when {
            b == 0.0 -> "0 MB"
            b < GB -> String.format(Locale.US, "%.1f MB", b / MB)
            else -> String.format(Locale.US, "%.2f GB", b / GB)
        }
    }
}
