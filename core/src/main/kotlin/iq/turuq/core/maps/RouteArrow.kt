package iq.turuq.core.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** سهم صغير (يشير إلى اليمين) يُكرَّر على خط المسار فيدلّ على اتجاه السير وعلى المسار المختار. */
object RouteArrow {
    fun bitmap(sizePx: Int = 48): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val k = sizePx / 24f
        val p = Path().apply {
            moveTo(7 * k, 5 * k); lineTo(18 * k, 12 * k); lineTo(7 * k, 19 * k)
            lineTo(10.5f * k, 12 * k); close()
        }
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.2f * k; color = Color.parseColor("#0A5A62"); strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawPath(p, edge); c.drawPath(p, fill)
        return bmp
    }
}
