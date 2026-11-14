package iq.turuq.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class GeoPoint(val lat: Double, val lon: Double)

/** نوع البلاغ كما يعرفه الخادم. المفتاح [key] هو القيمة المرسلة في JSON. */
enum class ReportType(
    val key: String,
    val labelAr: String,
    /** يظهر في شاشة السيارة المختصرة (٦ أنواع فقط لتقليل تشتيت السائق). */
    val onCarGrid: Boolean,
    val isCamera: Boolean = false,
    val isHazard: Boolean = false,
) {
    JAM("jam", "ازدحام", true),
    ACCIDENT("accident", "حادث", true),
    CLOSED("closed", "طريق مقطوع", true),
    POTHOLE("pothole", "حفرة", true, isHazard = true),
    SPEED_CAM("speedcam", "كاميرا سرعة", true, isCamera = true),
    OBJECT("object", "جسم على الطريق", true, isHazard = true),
    FLOOD("flood", "مياه وأمطار", false, isHazard = true),
    WORKS("works", "أعمال طرق", false),
    CAM("cam", "كاميرا مراقبة", false, isCamera = true),
    BREAKDOWN("breakdown", "عطل مركبة", false),
    SPEED_BUMP("speedbump", "مطب", false, isHazard = true),
    EMERGENCY("emergency", "مركبة طوارئ", false);

    companion object {
        fun fromKey(k: String): ReportType? = entries.firstOrNull { it.key == k }
    }
}

@Serializable
data class Report(
    val id: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    @SerialName("created_at") val createdAt: Long,
    val up: Int = 0,
    val down: Int = 0,
)

@Serializable
data class ReportsResponse(val reports: List<Report>)

@Serializable
data class NewReport(val type: String, val lat: Double, val lon: Double, @SerialName("device") val device: String)

@Serializable
data class Place(
    val id: String,
    val name: String,
    val area: String = "",
    val lat: Double,
    val lon: Double,
)

@Serializable
data class SearchResponse(val places: List<Place>)

@Serializable
data class RouteStep(
    val instruction: String,
    /** رقم المناورة كما في Valhalla؛ يُترجم إلى مناورة السيارة في [iq.turuq.core.nav.ManeuverMap]. */
    val type: Int,
    @SerialName("length_m") val lengthM: Double,
    @SerialName("time_s") val timeS: Double,
    @SerialName("begin_shape_index") val beginShapeIndex: Int,
    @SerialName("end_shape_index") val endShapeIndex: Int,
    val street: String? = null,
    @SerialName("roundabout_exit") val roundaboutExit: Int? = null,
)

@Serializable
data class RouteWarning(
    val type: String,
    @SerialName("dist_along_m") val distAlongM: Double,
    @SerialName("report_id") val reportId: String,
)

@Serializable
data class RouteDto(
    val id: String,
    val label: String,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
    val shape: String,
    val steps: List<RouteStep>,
    val warnings: List<RouteWarning> = emptyList(),
)

@Serializable
data class RouteResponse(val routes: List<RouteDto>)

@Serializable
data class RouteRequest(
    @SerialName("from_lat") val fromLat: Double,
    @SerialName("from_lon") val fromLon: Double,
    @SerialName("to_lat") val toLat: Double,
    @SerialName("to_lon") val toLon: Double,
    @SerialName("avoid_bridges") val avoidBridges: Boolean = false,
)

/** مسار جاهز للاستخدام على الجهاز بعد فك ترميز الشكل. */
data class Route(
    val dto: RouteDto,
    val shape: List<GeoPoint>,
    /** المسافة التراكمية بالأمتار عند كل نقطة من [shape]. */
    val cumulative: DoubleArray,
) {
    val id get() = dto.id
    val label get() = dto.label
    val distanceM get() = dto.distanceM
    val durationS get() = dto.durationS
    val steps get() = dto.steps
    val warnings get() = dto.warnings
}

@Serializable
data class MapRegion(
    val id: String,
    val name: String,
    val version: Int,
    val url: String,
    val sha256: String,
    val bytes: Long,
    val bbox: List<Double>,
)

@Serializable
data class MapManifest(val schema: Int, val generated: String, val regions: List<MapRegion>)
