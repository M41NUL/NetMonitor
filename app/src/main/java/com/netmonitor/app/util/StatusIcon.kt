package com.netmonitor.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import com.netmonitor.app.data.SpeedUnit
import java.util.Locale

object StatusIcon {
    private const val SIZE = 96
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun create(down: Long, up: Long, unit: SpeedUnit): IconCompat {
        val (num, label) = parts(maxOf(down, up), unit)
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawFitted(c, num, 60f, SIZE * 0.49f)
        drawFitted(c, label, 44f, SIZE * 0.93f)
        return IconCompat.createWithBitmap(bmp)
    }

    private fun drawFitted(c: Canvas, text: String, maxSize: Float, baseline: Float) {
        paint.textSize = maxSize
        val w = paint.measureText(text)
        if (w > SIZE) paint.textSize = maxSize * SIZE / w
        c.drawText(text, SIZE / 2f, baseline, paint)
    }

    fun parts(bytesPerSec: Long, unit: SpeedUnit): Pair<String, String> {
        val bits = unit == SpeedUnit.BITS
        val v = bytesPerSec.coerceAtLeast(0).toDouble() * if (bits) 8.0 else 1.0
        val k = if (bits) 1_000.0 else 1024.0
        val kilo = v / k
        val useMega = kilo >= 1000.0
        val x = if (useMega) kilo / k else kilo
        val label = when {
            bits -> if (useMega) "Mb/s" else "Kb/s"
            else -> if (useMega) "MB/S" else "KB/S"
        }
        val num = when {
            x < 10 -> String.format(Locale.US, "%.2f", x)
            x < 100 -> String.format(Locale.US, "%.1f", x)
            else -> String.format(Locale.US, "%.0f", x)
        }
        return num to label
    }
}
