package iq.turuq.core.net

import android.content.Context
import iq.turuq.core.model.GeoPoint
import iq.turuq.core.model.Place
import iq.turuq.core.model.Report
import iq.turuq.core.model.ReportType
import iq.turuq.core.model.Route
import iq.turuq.core.model.RouteDto
import iq.turuq.core.model.RouteStep
import iq.turuq.core.model.RouteWarning
import iq.turuq.core.util.Geo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.max

/**
 * الوضع التجريبي: يعمل التطبيق دون خادم خاص، فيستعمل خدمات OpenStreetMap العامة:
 *  - البحث: Nominatim   - المسارات: خادم OSRM التجريبي
 *  - البلاغات: تُحفظ على الجهاز فقط (المشاركة بين السائقين تحتاج خادمنا؛ اضبط apiBase عند البناء)
 * هذه الخدمات العامة للتجربة فقط ولا تصلح لإطلاق واسع على المتجر.
 */
class PublicBackend(context: Context, private val http: OkHttpClient, private val json: Json) {
    private val prefs = context.getSharedPreferences("turuq_local", Context.MODE_PRIVATE)
    private val reportSer = ListSerializer(Report.serializer())

    // ---------- المحافظات: وجهات فورية دون إنترنت ----------
    private val govs = listOf(
        Triple("بغداد", 33.3152, 44.3661), Triple("البصرة", 30.5085, 47.7804), Triple("نينوى (الموصل)", 36.3350, 43.1189),
        Triple("أربيل", 36.1911, 44.0092), Triple("السليمانية", 35.5558, 45.4329), Triple("كركوك", 35.4681, 44.3922),
        Triple("النجف", 31.9892, 44.3362), Triple("كربلاء", 32.6160, 44.0249), Triple("بابل (الحلة)", 32.4637, 44.4196),
        Triple("القادسية (الديوانية)", 31.9889, 44.9266), Triple("ذي قار (الناصرية)", 31.0439, 46.2573), Triple("ميسان (العمارة)", 31.8356, 47.1440),
        Triple("واسط (الكوت)", 32.5128, 45.8182), Triple("الأنبار (الرمادي)", 33.4258, 43.2999), Triple("المثنى (السماوة)", 31.3320, 45.2940),
        Triple("صلاح الدين (تكريت)", 34.6071, 43.6782), Triple("ديالى (بعقوبة)", 33.7500, 44.6333), Triple("دهوك", 36.8679, 42.9884), Triple("حلبجة", 35.1778, 45.9861),
    )

    private fun norm(s: String) = s.replace(Regex("[ً-ٰٟـ]"), "").replace(Regex("[أإآ]"), "ا")
        .replace('ى', 'ي').replace('ة', 'ه').lowercase()

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "TuruqAraqna/1.0 (Android test build)").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("empty")
        }
    }

    /** تسمية عربية لنوع المكان القادم من OpenStreetMap. */
    private fun kindAr(key: String?, value: String?): String = when (value) {
        "hospital" -> "مستشفى"; "clinic", "doctors" -> "عيادة"; "pharmacy" -> "صيدلية"
        "university" -> "جامعة"; "college" -> "كلية"; "school" -> "مدرسة"; "kindergarten" -> "روضة"
        "townhall", "government", "courthouse", "public_building" -> "دائرة حكومية"
        "police" -> "شرطة"; "fire_station" -> "إطفاء"; "post_office" -> "بريد"; "bank" -> "مصرف"
        "mosque" -> "جامع"; "place_of_worship" -> "دار عبادة"; "church" -> "كنيسة"
        "fuel" -> "محطة وقود"; "restaurant" -> "مطعم"; "cafe" -> "مقهى"; "hotel" -> "فندق"
        "mall", "supermarket", "marketplace" -> "سوق"; "bus_station" -> "كراج"; "aerodrome", "airport" -> "مطار"
        "stadium" -> "ملعب"; "park" -> "منتزه"; "bridge" -> "جسر"; "monument", "memorial" -> "نصب"
        "city" -> "مدينة"; "town" -> "بلدة"; "village" -> "قرية"; "suburb", "neighbourhood", "quarter" -> "منطقة"
        "residential", "primary", "secondary", "tertiary", "trunk", "motorway" -> "شارع"
        else -> when (key) { "highway" -> "شارع"; "building" -> "بناية"; "place" -> "منطقة"; else -> "" }
    }

    private fun mk(id: String, name: String, kind: String, city: String?, lat: Double, lon: Double): Place {
        val area = listOfNotNull(kind.ifBlank { null }, city?.ifBlank { null }).joinToString(" · ")
        return Place(id, name, area, lat, lon)
    }

    private fun photonSearch(q: String, near: GeoPoint?): List<Place> {
        val b = "https://photon.komoot.io/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", q).addQueryParameter("limit", "10").addQueryParameter("bbox", "38.7,29.0,48.7,37.5")
        if (near != null) { b.addQueryParameter("lat", near.lat.toString()); b.addQueryParameter("lon", near.lon.toString()) }
        val feats = json.parseToJsonElement(get(b.build().toString())).jsonObject["features"]?.jsonArray ?: return emptyList()
        return feats.mapNotNull { e ->
            val o = e.jsonObject; val pr = o["properties"]?.jsonObject ?: return@mapNotNull null
            if (pr["countrycode"]?.jsonPrimitive?.content?.uppercase() != "IQ") return@mapNotNull null
            val name = pr["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val c = o["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            val city = pr["city"]?.jsonPrimitive?.content ?: pr["district"]?.jsonPrimitive?.content ?: pr["state"]?.jsonPrimitive?.content
            mk("ph-${pr["osm_id"]?.jsonPrimitive?.content}", name, kindAr(pr["osm_key"]?.jsonPrimitive?.content, pr["osm_value"]?.jsonPrimitive?.content),
                city, c[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null, c[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null)
        }
    }

    private fun nominatimSearch(q: String, near: GeoPoint?): List<Place> {
        val b = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("format", "jsonv2").addQueryParameter("limit", "10").addQueryParameter("accept-language", "ar")
            .addQueryParameter("countrycodes", "iq").addQueryParameter("addressdetails", "1").addQueryParameter("q", q)
        if (near != null) b.addQueryParameter("viewbox", "${near.lon - 1.0},${near.lat + 1.0},${near.lon + 1.0},${near.lat - 1.0}")
        return json.parseToJsonElement(get(b.build().toString())).jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val full = o["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val addr = o["address"]?.jsonObject
            val city = listOf("city", "town", "suburb", "state").firstNotNullOfOrNull { addr?.get(it)?.jsonPrimitive?.content }
            val name = o["name"]?.jsonPrimitive?.content?.ifBlank { null } ?: full.substringBefore("،").substringBefore(",")
            mk("osm-${o["place_id"]?.jsonPrimitive?.content}", name, kindAr(o["category"]?.jsonPrimitive?.content, o["type"]?.jsonPrimitive?.content),
                city, o["lat"]!!.jsonPrimitive.content.toDouble(), o["lon"]!!.jsonPrimitive.content.toDouble())
        }
    }

    /**
     * بحث المعالم: المحافظات فوراً دون إنترنت، ثم Photon (يقبل كتابة جزئية) وNominatim معاً
     * ضمن حدود العراق فقط، مع إزالة المكرر والترتيب بالتطابق ثم القرب من موقعك.
     */
    suspend fun search(q: String, near: GeoPoint?): List<Place> = withContext(Dispatchers.IO) {
        val qq = q.trim()
        val nq = norm(qq)
        val local = govs.filter { norm(it.first).contains(nq) }.map { Place("gov-${it.first}", it.first, "محافظة", it.second, it.third) }
        if (qq.length < 2) return@withContext local
        val remote = ArrayList<Place>()
        runCatching { remote += photonSearch(qq, near) }
        if (qq.length >= 3) runCatching { remote += nominatimSearch(qq, near) }
        val seen = ArrayList<Place>()
        for (p in remote) {
            if (seen.none { norm(it.name) == norm(p.name) && Geo.distanceM(GeoPoint(it.lat, it.lon), GeoPoint(p.lat, p.lon)) < 400.0 }) seen += p
        }
        fun score(p: Place): Double {
            val n = norm(p.name)
            val match = when { n == nq -> 0.0; n.startsWith(nq) -> 1.0; n.contains(nq) -> 2.0; else -> 3.0 }
            val dist = if (near != null) Geo.distanceM(near, GeoPoint(p.lat, p.lon)) / 1000.0 else 0.0
            return match * 100000.0 + dist
        }
        (local + seen.sortedBy { score(it) }).take(10)
    }

    /** تصفّح فئة (جامعات، دوائر حكومية، مستشفيات) حول موقعك أو حول محافظة كتبتَ اسمها. */
    suspend fun browse(kind: String, near: GeoPoint, cityQuery: String): List<Place> = withContext(Dispatchers.IO) {
        val nq = norm(cityQuery.trim())
        val gov = if (nq.length >= 2) govs.firstOrNull { norm(it.first).contains(nq) } else null
        val c = if (gov != null) GeoPoint(gov.second, gov.third) else near
        val sel = when (kind) {
            "uni" -> listOf("""["amenity"~"^(university|college)${'$'}"]""")
            "gov" -> listOf("""["amenity"~"^(townhall|courthouse)${'$'}"]""", """["office"="government"]""", """["building"="government"]""")
            "hosp" -> listOf("""["amenity"="hospital"]""")
            else -> emptyList()
        }
        if (sel.isEmpty()) return@withContext emptyList()
        val kindLabel = when (kind) { "uni" -> "جامعة / كلية"; "gov" -> "دائرة حكومية"; else -> "مستشفى" }
        val body = sel.joinToString("") { "nwr$it[\"name\"](around:35000,${c.lat},${c.lon});" }
        val ql = "[out:json][timeout:25];($body);out center 120;"
        val hosts = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
        var text: String? = null
        for (h in hosts) {
            text = runCatching {
                val req = Request.Builder().url(h.toHttpUrl().newBuilder().addQueryParameter("data", ql).build())
                    .header("User-Agent", "TuruqAraqna/1.0 (Android test build)").build()
                http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw IOException("HTTP ${r.code}"); r.body?.string() }
            }.getOrNull()
            if (text != null) break
        }
        val root = json.parseToJsonElement(text ?: throw IOException("overpass unreachable")).jsonObject
        val els = root["elements"]?.jsonArray ?: return@withContext emptyList()
        val out = ArrayList<Place>()
        for (e in els) {
            val o = e.jsonObject
            val tags = o["tags"]?.jsonObject ?: continue
            val name = tags["name:ar"]?.jsonPrimitive?.content ?: tags["name"]?.jsonPrimitive?.content ?: continue
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull ?: continue
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull ?: continue
            if (out.none { it.name == name && Geo.distanceM(GeoPoint(it.lat, it.lon), GeoPoint(lat, lon)) < 400.0 })
                out += Place("ov-${o["id"]?.jsonPrimitive?.content}", name, kindLabel, lat, lon)
        }
        out.sortedBy { Geo.distanceM(c, GeoPoint(it.lat, it.lon)) }.take(30)
    }

    // ---------- المسارات ----------
    suspend fun route(from: GeoPoint, to: GeoPoint): List<Route> = withContext(Dispatchers.IO) {
        val hosts = listOf(
            "https://router.project-osrm.org/route/v1/driving",
            "https://routing.openstreetmap.de/routed-car/route/v1/driving",
        )
        val tail = "/${from.lon},${from.lat};${to.lon},${to.lat}?alternatives=3&steps=true&geometries=polyline6&overview=full"
        var err = "غير معروف"
        for (h in hosts) {
            try {
                val root = json.parseToJsonElement(get(h + tail)).jsonObject
                val code = root["code"]?.jsonPrimitive?.content
                if (code != "Ok") { err = if (code == "NoRoute") "لا يوجد طريق بري بين النقطتين" else "رد الخدمة: $code"; continue }
                val routes = root["routes"]?.jsonArray ?: continue
                val active = activeReports()
                val built = routes.map { buildRoute(it.jsonObject, active) }
                return@withContext built.sortedWith(compareBy({ it.second }, { it.first.durationS })).mapIndexed { i, (r, blocked) ->
                    val label = (if (i == 0) "الأسرع" else "بديل $i") + if (blocked) " · ⚠ يمر بطريق مقطوع أو مغمور" else ""
                    r.copy(dto = r.dto.copy(id = "r$i", label = label))
                }
            } catch (e: Exception) {
                err = when (e) {
                    is IOException -> e.message ?: "انقطاع الاتصال"
                    else -> "خطأ في قراءة المسار (" + e.javaClass.simpleName + ")"
                }
            }
        }
        throw IOException(err)
    }

    private fun buildRoute(o: JsonObject, active: List<Report>): Pair<Route, Boolean> {
        val geom = o["geometry"]!!.jsonPrimitive.content
        val shape = Geo.decodePolyline6(geom)
        val cum = DoubleArray(shape.size)
        for (i in 1 until shape.size) cum[i] = cum[i - 1] + Geo.distanceM(shape[i - 1], shape[i])
        val rawSteps = o["legs"]!!.jsonArray[0].jsonObject["steps"]!!.jsonArray.map { it.jsonObject }
        // نحدد فهرس بداية كل خطوة بأقرب نقطة في الشكل مع بقاء الترتيب تصاعدياً
        var from = 0
        val begins = rawSteps.map { s ->
            val loc = s["maneuver"]!!.jsonObject["location"]!!.jsonArray
            val p = GeoPoint(loc[1].jsonPrimitive.doubleOrNull ?: 0.0, loc[0].jsonPrimitive.doubleOrNull ?: 0.0)
            var best = from; var bd = Double.MAX_VALUE
            val hi = minOf(shape.size - 1, from + 4000)
            for (i in from..hi) { val d = Geo.distanceM(p, shape[i]); if (d < bd) { bd = d; best = i }; if (d < 2.0) break }
            from = best; best
        }
        val steps = rawSteps.mapIndexed { i, s ->
            val m = s["maneuver"]!!.jsonObject
            val type = valhallaType(m["type"]?.jsonPrimitive?.content ?: "", m["modifier"]?.jsonPrimitive?.content)
            val street = s["name"]?.jsonPrimitive?.content?.ifBlank { null }
            val exit = m["exit"]?.jsonPrimitive?.intOrNull
            val end = if (i + 1 < begins.size) begins[i + 1] else shape.size - 1
            RouteStep(ArabicText.instruction(type, street, exit), type, s["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                s["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0, begins[i], max(begins[i], end), street, exit)
        }
        val warns = ArrayList<RouteWarning>(); var blocked = false
        for (r in active) {
            val p = GeoPoint(r.lat, r.lon); var best = Double.MAX_VALUE; var along = 0.0
            for (i in 0 until shape.size - 1) {
                val (t, d) = Geo.project(p, shape[i], shape[i + 1])
                if (d < best) { best = d; along = cum[i] + (cum[i + 1] - cum[i]) * t }
            }
            if (best <= 40.0) {
                warns += RouteWarning(r.type, along, r.id)
                if (r.type == "closed" || r.type == "flood") blocked = true
            }
        }
        warns.sortBy { it.distAlongM }
        val dto = RouteDto("r", "", o["distance"]!!.jsonPrimitive.doubleOrNull ?: cum.last(), o["duration"]!!.jsonPrimitive.doubleOrNull ?: 0.0, geom, steps, warns)
        return Route(dto, shape, cum) to blocked
    }

    /** يحوّل مناورة OSRM إلى رقم مناورة Valhalla الذي تفهمه بقية الشيفرة (ManeuverMap). */
    private fun valhallaType(type: String, mod: String?): Int = when (type) {
        "depart" -> 1
        "arrive" -> when (mod) { "right" -> 5; "left" -> 6; else -> 4 }
        "roundabout", "rotary", "roundabout turn" -> 26
        "exit roundabout", "exit rotary" -> 27
        "merge" -> 25
        "on ramp" -> if (mod?.contains("right") == true) 18 else 19
        "off ramp" -> if (mod?.contains("right") == true) 20 else 21
        "fork" -> if (mod?.contains("left") == true) 24 else 23
        else -> when (mod) {
            "left" -> 15; "right" -> 10; "slight left" -> 16; "slight right" -> 9
            "sharp left" -> 14; "sharp right" -> 11; "uturn" -> 13; else -> 8
        }
    }

    // ---------- البلاغات المحلية ----------
    private val ttlMin = mapOf("jam" to 60, "accident" to 90, "closed" to 360, "pothole" to 10080, "speedcam" to 43200, "object" to 120,
        "flood" to 360, "works" to 1440, "cam" to 43200, "breakdown" to 60, "speedbump" to 43200, "emergency" to 30)

    private fun all(): MutableList<Report> = runCatching { json.decodeFromString(reportSer, prefs.getString("reports", "[]")!!) }.getOrDefault(emptyList()).toMutableList()
    private fun save(l: List<Report>) { prefs.edit().putString("reports", json.encodeToString(reportSer, l)).apply() }
    private fun hidden(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()

    fun activeReports(): List<Report> {
        val now = System.currentTimeMillis(); val hid = hidden()
        return all().filter { r ->
            val ttl = (ttlMin[r.type] ?: 60) * 60_000L
            now - r.createdAt < ttl && r.id !in hid && !(r.down >= 2 && r.down > r.up)
        }
    }

    fun reportsNear(c: GeoPoint, radiusKm: Double): List<Report> =
        activeReports().filter { Geo.distanceM(c, GeoPoint(it.lat, it.lon)) <= radiusKm * 1000 }

    fun postReport(type: String, at: GeoPoint) {
        if (ReportType.fromKey(type) == null) return
        val l = all()
        // دمج مع بلاغ مماثل قريب بدلاً من التكرار
        val dup = l.indexOfFirst { it.type == type && Geo.distanceM(at, GeoPoint(it.lat, it.lon)) < 100 && System.currentTimeMillis() - it.createdAt < 3_600_000 }
        if (dup >= 0) l[dup] = l[dup].copy(up = l[dup].up + 1)
        else l += Report("l" + System.currentTimeMillis(), type, at.lat, at.lon, System.currentTimeMillis(), 1, 0)
        save(l.takeLast(500))
    }

    fun vote(id: String, up: Boolean) { save(all().map { if (it.id == id) (if (up) it.copy(up = it.up + 1) else it.copy(down = it.down + 1)) else it }) }
    fun flag(id: String) { prefs.edit().putStringSet("hidden", hidden() + id).apply() }
}

/** تعليمات عربية للمناورات (نفس نصوص الخادم). */
object ArabicText {
    private val phrase = mapOf(
        1 to "انطلق", 2 to "انطلق", 3 to "انطلق", 4 to "وصلت إلى وجهتك", 5 to "وجهتك على يمينك", 6 to "وجهتك على يسارك",
        7 to "تابع", 8 to "تابع بشكل مستقيم", 9 to "انحرف قليلاً يميناً", 10 to "انعطف يميناً", 11 to "انعطف يميناً بحدّة",
        12 to "استدر للخلف", 13 to "استدر للخلف", 14 to "انعطف يساراً بحدّة", 15 to "انعطف يساراً", 16 to "انحرف قليلاً يساراً",
        17 to "تابع على المنحدر", 18 to "خذ المنحدر يميناً", 19 to "خذ المنحدر يساراً", 20 to "اتجه إلى المخرج يميناً",
        21 to "اتجه إلى المخرج يساراً", 22 to "ابقَ مستقيماً", 23 to "ابقَ يميناً", 24 to "ابقَ يساراً", 25 to "اندمج",
        26 to "ادخل الدوّار", 27 to "اخرج من الدوّار",
    )
    private val ord = mapOf(1 to "الأول", 2 to "الثاني", 3 to "الثالث", 4 to "الرابع", 5 to "الخامس", 6 to "السادس")

    fun instruction(type: Int, street: String?, exit: Int?): String {
        var s = phrase[type] ?: "تابع"
        if (type == 26 && exit != null) s = "في الدوّار خذ المخرج " + (ord[exit] ?: exit.toString())
        if (street != null && type !in listOf(4, 5, 6)) s += " إلى $street"
        return s
    }
}
