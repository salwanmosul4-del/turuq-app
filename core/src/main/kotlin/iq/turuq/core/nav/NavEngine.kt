package iq.turuq.core.nav

import iq.turuq.core.model.GeoPoint
import iq.turuq.core.model.Route
import iq.turuq.core.model.RouteStep
import iq.turuq.core.model.RouteWarning
import iq.turuq.core.util.Geo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/** حالة الملاحة الحالية التي تقرأها شاشة الهاتف وشاشة السيارة معاً. */
data class NavState(
    val route: Route,
    val position: GeoPoint,
    val bearing: Double,
    val alongM: Double,
    val stepIndex: Int,
    val distToManeuverM: Double,
    val remainingM: Double,
    val remainingS: Double,
    /** أقرب تحذير (كاميرا/حفرة/ازدحام) أمامك ضمن المدى، أو null. */
    val nextWarning: RouteWarning?,
    val warningDistM: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
) {
    val step: RouteStep get() = route.steps[stepIndex]
    val nextStep: RouteStep? get() = route.steps.getOrNull(stepIndex + 1)
}

/**
 * يتابع تقدّم السائق على مسار محسوب مسبقاً. لا يحسب مساراً بنفسه؛ عند الخروج عن المسار
 * يرفع [offRoute] ليطلب المستدعي مساراً جديداً من الخادم.
 */
class NavEngine {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state

    private var route: Route? = null
    private var lastIdx = 0
    private var offCount = 0
    private var wrongCount = 0

    fun start(r: Route, from: GeoPoint) {
        route = r; lastIdx = 0; offCount = 0; wrongCount = 0
        update(from, 0.0)
    }

    fun stop() { route = null; _state.value = null }

    /** [speedMps] يُستعمل لتقدير زمن الوصول عند تباطؤ الحركة. */
    fun update(p: GeoPoint, speedMps: Double, headingDeg: Double = Double.NaN) {
        val r = route ?: return
        val shape = r.shape
        if (shape.size < 2) return

        // أقرب قطعة من المسار ضمن نافذة أمامية لتجنّب القفز عند المسارات المتقاطعة.
        var bestI = lastIdx; var bestD = Double.MAX_VALUE; var bestT = 0.0
        val hi = minOf(shape.size - 2, lastIdx + 60)
        for (i in max(0, lastIdx - 2)..hi) {
            val (t, d) = Geo.project(p, shape[i], shape[i + 1])
            if (d < bestD) { bestD = d; bestI = i; bestT = t }
        }
        lastIdx = bestI
        val segLen = r.cumulative[bestI + 1] - r.cumulative[bestI]
        val along = r.cumulative[bestI] + segLen * bestT

        offCount = if (bestD > 40.0) offCount + 1 else 0
        // اتجاه معاكس على الطريق نفسه (دوران للخلف): المسافة عن الخط صغيرة لكن الاتجاه معاكس
        val routeBearing = Geo.bearing(shape[bestI], shape[bestI + 1])
        if (!headingDeg.isNaN() && speedMps > 2.5) {
            val diff = kotlin.math.abs(((headingDeg - routeBearing + 540.0) % 360.0) - 180.0)
            wrongCount = if (diff > 110.0) wrongCount + 1 else 0
        }
        val off = offCount >= 3 || wrongCount >= 4
        // نثبّت الأيقونة على منتصف الطريق (خط المسار) إذا كنا قريبين منه
        val a = shape[bestI]; val b = shape[bestI + 1]
        val snapped = if (bestD < 35.0) GeoPoint(a.lat + (b.lat - a.lat) * bestT, a.lon + (b.lon - a.lon) * bestT) else p

        var si = r.steps.indexOfLast { it.beginShapeIndex <= bestI }.coerceAtLeast(0)
        if (si >= r.steps.size) si = r.steps.size - 1
        val step = r.steps[si]
        val stepEnd = r.cumulative[step.endShapeIndex.coerceIn(0, shape.size - 1)]
        val toManeuver = max(0.0, stepEnd - along)
        val remaining = max(0.0, r.distanceM - along)
        val avg = r.distanceM / max(1.0, r.durationS)
        val eff = if (speedMps > 3.0) (speedMps * 0.4 + avg * 0.6) else avg
        val warn = r.warnings.filter { it.distAlongM > along && it.distAlongM - along < WARN_RANGE_M }.minByOrNull { it.distAlongM }

        _state.value = NavState(
            route = r, position = snapped,
            bearing = Geo.bearing(shape[bestI], shape[bestI + 1]),
            alongM = along, stepIndex = si, distToManeuverM = toManeuver,
            remainingM = remaining, remainingS = remaining / max(0.5, eff),
            nextWarning = warn, warningDistM = warn?.let { it.distAlongM - along } ?: 0.0,
            offRoute = off, arrived = remaining < 25.0,
        )
    }

    companion object { const val WARN_RANGE_M = 600.0 }
}
