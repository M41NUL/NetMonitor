package com.netmonitor.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.netmonitor.app.util.Format

object WidgetCharts {
    private class Pal(dark: Boolean) {
        val down = if (dark) 0xFF9CCBFF.toInt() else 0xFF1565C0.toInt()
        val up = if (dark) 0xFFB5E87C.toInt() else 0xFF2E7D32.toInt()
        val mobile = if (dark) 0xFFFF8A5C.toInt() else 0xFFE8590C.toInt()
        val track = if (dark) 0x33FFFFFF else 0x1A000000
        val text = if (dark) 0xFFFFFFFF.toInt() else 0xFF14181F.toInt()
        val text2 = if (dark) 0xB3FFFFFF.toInt() else 0xFF5A626E.toInt()
    }

    fun graph(down: List<Long>, up: List<Long>, wDp: Int, hDp: Int, dark: Boolean, density: Float): Bitmap {
        val pal = Pal(dark)
        val w = ((wDp - 28) * density).toInt().coerceIn(120, 900)
        val h = (((hDp - 28) * 5f / 9f) * density).toInt().coerceIn(40, 450)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val peak = maxOf(8192L, down.maxOrNull() ?: 0L, up.maxOrNull() ?: 0L).toFloat()
        val pad = 3f * density
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.track
            strokeWidth = 1f * density
        }
        c.drawLine(0f, h - pad, w.toFloat(), h - pad, base)
        line(c, down, peak, pal.down, w, h, pad, density)
        line(c, up, peak, pal.up, w, h, pad, density)
        return bmp
    }

    private fun line(c: Canvas, v: List<Long>, peak: Float, color: Int, w: Int, h: Int, pad: Float, density: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val n = v.size
        val path = Path()
        fun x(i: Int) = if (n <= 1) 0f else i * (w - 1f) / (n - 1)
        fun y(i: Int) = (h - pad) - (v[i] / peak) * (h - 2 * pad - 4f * density)
        if (n < 2) {
            path.moveTo(0f, h - pad)
            path.lineTo(w.toFloat(), h - pad)
        } else {
            path.moveTo(x(0), y(0))
            for (i in 1 until n) {
                val mx = (x(i - 1) + x(i)) / 2f
                path.cubicTo(mx, y(i - 1), mx, y(i), x(i), y(i))
            }
        }
        c.drawPath(path, p)
    }

    fun ring(wifi: Long, mobile: Long, wDp: Int, hDp: Int, dark: Boolean, density: Float): Bitmap {
        val pal = Pal(dark)
        val dp = minOf(wDp - 24, ((hDp - 24) * 6f / 8f).toInt())
        val s = (dp * density).toInt().coerceIn(120, 520)
        val k = s / 300f
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = 30f * k
        val inset = stroke / 2f + 4f * k
        val r = RectF(inset, inset, s - inset, s - inset)
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        arc.color = pal.track
        c.drawArc(r, 0f, 360f, false, arc)
        val total = wifi + mobile
        if (total > 0) {
            val wSweep = 360f * wifi / total
            if (wifi > 0) {
                arc.color = pal.down
                c.drawArc(r, -90f, wSweep, false, arc)
            }
            if (mobile > 0) {
                arc.color = pal.mobile
                c.drawArc(r, -90f + wSweep, 360f - wSweep, false, arc)
            }
        }
        val parts = Format.size(total).split(" ")
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.text
            textAlign = Paint.Align.CENTER
            textSize = 64f * k
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.text2
            textAlign = Paint.Align.CENTER
            textSize = 30f * k
        }
        c.drawText(parts.firstOrNull() ?: "0", s / 2f, s / 2f + 6f * k, num)
        c.drawText(parts.getOrNull(1) ?: "MB", s / 2f, s / 2f + 44f * k, unit)
        return bmp
    }
}
