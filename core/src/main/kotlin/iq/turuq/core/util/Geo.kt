package iq.turuq.core.util

import iq.turuq.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val R = 6_371_000.0

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * asin(sqrt(h))
    }

    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val y = sin(Math.toRadians(b.lon - a.lon)) * cos(Math.toRadians(b.lat))
        val x = cos(Math.toRadians(a.lat)) * sin(Math.toRadians(b.lat)) -
            sin(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * cos(Math.toRadians(b.lon - a.lon))
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * يسقط نقطة على قطعة مستقيمة (تقريب مستوٍ محلي يكفي للمسافات القصيرة).
     * يعيد (الكسر على القطعة، المسافة العمودية بالمتر).
     */
    fun project(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<Double, Double> {
        val k = cos(Math.toRadians(a.lat))
        val ax = a.lon * k * PI / 180 * R; val ay = a.lat * PI / 180 * R
        val bx = b.lon * k * PI / 180 * R; val by = b.lat * PI / 180 * R
        val px = p.lon * k * PI / 180 * R; val py = p.lat * PI / 180 * R
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val qx = ax + dx * t; val qy = ay + dy * t
        return t to sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy))
    }

    /** يفك ترميز polyline بدقة ست خانات عشرية (الصيغة التي يعيدها Valhalla). */
    fun decodePolyline6(s: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        var i = 0; var lat = 0; var lon = 0
        while (i < s.length) {
            var shift = 0; var r = 0; var b: Int
            do { b = s[i++].code - 63; r = r or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lat += if (r and 1 != 0) (r shr 1).inv() else r shr 1
            shift = 0; r = 0
            do { b = s[i++].code - 63; r = r or ((b and 0x1f) shl shift); shift += 5 } while (b >= 0x20)
            lon += if (r and 1 != 0) (r shr 1).inv() else r shr 1
            out.add(GeoPoint(lat / 1e6, lon / 1e6))
        }
        return out
    }
}
