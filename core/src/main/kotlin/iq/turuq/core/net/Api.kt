package iq.turuq.core.net

import android.content.Context
import iq.turuq.core.BuildConfig
import iq.turuq.core.model.GeoPoint
import iq.turuq.core.model.NewReport
import iq.turuq.core.model.Place
import iq.turuq.core.model.Report
import iq.turuq.core.model.ReportsResponse
import iq.turuq.core.model.Route
import iq.turuq.core.model.RouteRequest
import iq.turuq.core.model.RouteResponse
import iq.turuq.core.model.SearchResponse
import iq.turuq.core.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** عميل الخادم: بحث، مسارات، وبلاغات. كل النداءات تعمل خارج الخيط الرئيسي. */
class Api(context: Context, private val base: String = BuildConfig.API_BASE) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val prefs = context.getSharedPreferences("turuq", Context.MODE_PRIVATE)

    /** إن لم يُضبط خادم عند البناء (apiBase) يعمل التطبيق بالوضع التجريبي عبر الخدمات العامة. */
    val demo: Boolean = base.isBlank()
    private val pub by lazy { PublicBackend(context, http, json) }

    /** معرّف عشوائي للجهاز لتقليل التكرار ومنع التصويت المتعدد؛ لا يرتبط بهوية الشخص. */
    val deviceId: String = prefs.getString("device", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }

    private suspend fun <T> call(req: Request, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            parse(r.body?.string() ?: throw IOException("empty"))
        }
    }

    /** تصفّح فئة من المعالم: uni / gov / hosp. */
    suspend fun browse(kind: String, near: GeoPoint, cityQuery: String): List<Place> = pub.browse(kind, near, cityQuery)

    suspend fun search(q: String, near: GeoPoint?): List<Place> {
        if (demo) return pub.search(q, near)
        val url = base.toHttpUrl().newBuilder().addPathSegments("v1/search").addQueryParameter("q", q).apply {
            if (near != null) { addQueryParameter("lat", near.lat.toString()); addQueryParameter("lon", near.lon.toString()) }
        }.build()
        return call(Request.Builder().url(url).build()) { json.decodeFromString<SearchResponse>(it).places }
    }

    suspend fun route(from: GeoPoint, to: GeoPoint, avoidBridges: Boolean): List<Route> {
        if (demo) return pub.route(from, to)
        val body = json.encodeToString(RouteRequest(from.lat, from.lon, to.lat, to.lon, avoidBridges)).toRequestBody(jsonType)
        val req = Request.Builder().url(base + "v1/route").post(body).build()
        val dtos = call(req) { json.decodeFromString<RouteResponse>(it).routes }
        return dtos.map { dto ->
            val shape = Geo.decodePolyline6(dto.shape)
            val cum = DoubleArray(shape.size)
            for (i in 1 until shape.size) cum[i] = cum[i - 1] + Geo.distanceM(shape[i - 1], shape[i])
            Route(dto, shape, cum)
        }
    }

    suspend fun reportsNear(c: GeoPoint, radiusKm: Double = 25.0): List<Report> {
        if (demo) return pub.reportsNear(c, radiusKm)
        val url = base.toHttpUrl().newBuilder().addPathSegments("v1/reports")
            .addQueryParameter("lat", c.lat.toString()).addQueryParameter("lon", c.lon.toString())
            .addQueryParameter("radius_km", radiusKm.toString()).build()
        return call(Request.Builder().url(url).build()) { json.decodeFromString<ReportsResponse>(it).reports }
    }

    suspend fun postReport(type: String, at: GeoPoint) {
        if (demo) { pub.postReport(type, at); return }
        val body = json.encodeToString(NewReport(type, at.lat, at.lon, deviceId)).toRequestBody(jsonType)
        call(Request.Builder().url(base + "v1/reports").post(body).build()) { }
    }

    suspend fun vote(reportId: String, stillThere: Boolean) {
        if (demo) { pub.vote(reportId, stillThere); return }
        val body = """{"device":"$deviceId","value":${if (stillThere) 1 else -1}}""".toRequestBody(jsonType)
        call(Request.Builder().url(base + "v1/reports/$reportId/vote").post(body).build()) { }
    }

    /** إبلاغ عن بلاغ مسيء أو كاذب. مطلوب لسياسة المحتوى المُنشأ من المستخدمين في Google Play. */
    suspend fun flag(reportId: String, reason: String) {
        if (demo) { pub.flag(reportId); return }
        val body = """{"device":"$deviceId","reason":"$reason"}""".toRequestBody(jsonType)
        call(Request.Builder().url(base + "v1/reports/$reportId/flag").post(body).build()) { }
    }
}
