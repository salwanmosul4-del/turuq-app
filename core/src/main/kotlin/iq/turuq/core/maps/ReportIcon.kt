package iq.turuq.core.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** أيقونة بلاغ دائرية ملوّنة بداخلها رمز النوع، تُسجَّل في الخريطة كصورة. */
object ReportIcon {
    fun bitmap(emoji: String, colorHex: String, sizePx: Int = 84): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
        c.drawCircle(r, r + sizePx * 0.03f, r * 0.92f, shadow)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colorHex) }
        c.drawCircle(r, r, r * 0.9f, fill)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sizePx * 0.07f; color = Color.WHITE }
        c.drawCircle(r, r, r * 0.86f, ring)
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sizePx * 0.5f; textAlign = Paint.Align.CENTER }
        val y = r - (txt.ascent() + txt.descent()) / 2f
        c.drawText(emoji, r, y, txt)
        return bmp
    }
}
