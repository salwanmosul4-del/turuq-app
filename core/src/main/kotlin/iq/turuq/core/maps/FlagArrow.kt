package iq.turuq.core.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/**
 * سهم الحركة بشكل مثلث كما في Waze، لكن داخله العلم العراقي كاملاً:
 * أحمر/أبيض/أسود أفقياً مع «الله أكبر» بالأخضر في الوسط، وبحدّ داكن حول المثلث.
 * يُرسم برمجياً ثم يُسجَّل في ستايل الخريطة كصورة.
 */
object FlagArrow {
    fun bitmap(sizePx: Int = 96): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = sizePx / 44f
        val tri = Path().apply {
            moveTo(22 * k, 3 * k); lineTo(41 * k, 42 * k); lineTo(22 * k, 34 * k); lineTo(3 * k, 42 * k); close()
        }
        // ظل خفيف
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
        c.save(); c.translate(0f, 2f * k); c.drawPath(tri, shadow); c.restore()

        c.save()
        c.clipPath(tri)
        val top = 3 * k; val h = 39 * k / 3f
        fun band(i: Int, color: Int) = c.drawRect(0f, top + h * i, sizePx.toFloat(), top + h * (i + 1) + 1f, Paint().apply { this.color = color })
        band(0, Color.parseColor("#CE1126"))
        band(1, Color.WHITE)
        band(2, Color.BLACK)
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#007A3D"); textSize = 5.5f * k; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        c.drawText("الله أكبر", 22 * k, top + h * 1.5f + 2f * k, txt)
        c.restore()

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f * k; color = Color.parseColor("#0F2A2E"); strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(tri, line)
        return bmp
    }
}
