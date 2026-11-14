package iq.turuq.core

import android.content.Context
import iq.turuq.core.location.LocationSource
import iq.turuq.core.maps.MapUpdateManager
import iq.turuq.core.model.GeoPoint
import iq.turuq.core.model.Place
import iq.turuq.core.model.Report
import iq.turuq.core.model.ReportType
import iq.turuq.core.model.Route
import iq.turuq.core.nav.NavEngine
import iq.turuq.core.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * حاوية الخدمات المشتركة بين شاشة الهاتف وشاشة السيارة. يمتلكها كائن Application
 * فتبقى حيّة عند تبديل الشاشات أو توصيل السيارة.
 */
class TuruqApp(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val api = Api(appContext)
    val location = LocationSource(appContext)
    val nav = NavEngine()
    val maps = MapUpdateManager(appContext)
    val settings = AppSettings(appContext)
    val voice = iq.turuq.core.nav.Voice(appContext).also { v ->
        v.enabled = settings.voice.value
        v.warningAllowed = { t -> settings.voiceWarnings.value && settings.allows(t) }
    }

    init {
        scope.launch { settings.voice.collect { voice.enabled = it; if (!it) voice.stop() } }
    }

    val routes = MutableStateFlow<List<Route>>(emptyList())
    val selected = MutableStateFlow(0)
    val destination = MutableStateFlow<Place?>(null)
    /** نقطة الانطلاق. null تعني «موقعي الحالي». يمكن اختيار أي منطقة أو مبنى أو دائرة. */
    val origin = MutableStateFlow<Place?>(null)
    val reports = MutableStateFlow<List<Report>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val navigating: StateFlow<Boolean> get() = _navigating
    private val _navigating = MutableStateFlow(false)

    // بلاغات مرئية على الخريطة: تُحدَّث كل ٢٠ ثانية حول الموقع الحالي.
    private var pollJob: kotlinx.coroutines.Job? = null
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                val here = location.last.value?.let { GeoPoint(it.latitude, it.longitude) } ?: DEFAULT_CENTER
                runCatching { api.reportsNear(here) }.onSuccess { reports.value = it }
                delay(20_000)
            }
        }
    }

    fun stopPolling() { pollJob?.cancel(); pollJob = null }

    /** إيقاف كامل: الملاحة والموقع وتحديث البلاغات. */
    fun stopAll() { stopNavigation(); stopPolling(); location.stop() }

    fun here(): GeoPoint = location.last.value?.let { GeoPoint(it.latitude, it.longitude) } ?: DEFAULT_CENTER

    private fun start(): GeoPoint = origin.value?.let { GeoPoint(it.lat, it.lon) } ?: here()

    fun setOrigin(p: Place?) { origin.value = p; destination.value?.let { plan(it) } }

    fun plan(to: Place, avoidBridges: Boolean = false) {
        destination.value = to
        scope.launch {
            runCatching { api.route(start(), GeoPoint(to.lat, to.lon), avoidBridges) }
                .onSuccess { routes.value = it; selected.value = 0 }
                .onFailure { message.value = "تعذّر حساب المسار: " + (it.message ?: "تحقق من الاتصال") }
        }
    }

    /** إن كانت نقطة الانطلاق غير موقعي فالمسار للمعاينة فقط؛ عند «ابدأ» يُعاد حسابه من موقعك الفعلي. */
    fun startNavigation() {
        val to = destination.value
        if (origin.value != null && to != null) {
            origin.value = null
            scope.launch {
                runCatching { api.route(here(), GeoPoint(to.lat, to.lon), false) }
                    .onSuccess { routes.value = it; selected.value = 0; begin() }
                    .onFailure { message.value = "تعذّر حساب المسار من موقعك: " + (it.message ?: "") }
            }
        } else begin()
    }

    private fun begin() {
        val r = routes.value.getOrNull(selected.value) ?: return
        voice.reset(); nav.start(r, here()); _navigating.value = true
        location.start()
        followJob?.cancel()
        followJob = scope.launch {
            var lastReroute = 0L
            location.last.collect { loc ->
                loc ?: return@collect
                nav.update(GeoPoint(loc.latitude, loc.longitude), loc.speed.toDouble(), if (loc.hasBearing()) loc.bearing.toDouble() else Double.NaN)
                val st = nav.state.value
                st?.let { voice.onState(it) }
                val now = System.currentTimeMillis()
                if (st?.offRoute == true && now - lastReroute > 8_000) { lastReroute = now; reroute() }
                if (st?.arrived == true) stopNavigation()
            }
        }
    }

    private var followJob: kotlinx.coroutines.Job? = null

    fun stopNavigation() { followJob?.cancel(); nav.stop(); voice.stop(); _navigating.value = false }

    private var rerouting = false

    fun reroute() {
        val to = destination.value ?: return
        if (rerouting) return
        rerouting = true
        message.value = "تم الخروج عن المسار، جارٍ إعادة الحساب…"
        voice.say("جارٍ إعادة حساب المسار")
        scope.launch {
            runCatching { api.route(here(), GeoPoint(to.lat, to.lon), false) }
                .onSuccess {
                    routes.value = it; selected.value = 0
                    it.firstOrNull()?.let { r -> nav.start(r, here()) }
                    message.value = null
                }
                .onFailure { message.value = "تعذّرت إعادة الحساب: " + (it.message ?: "") }
            rerouting = false
        }
    }

    fun report(type: ReportType) {
        val at = here()
        scope.launch {
            runCatching { api.postReport(type.key, at) }
                .onSuccess { message.value = "وصل بلاغك للسائقين"; runCatching { api.reportsNear(at) }.onSuccess { reports.value = it } }
                .onFailure { message.value = "تعذّر إرسال البلاغ" }
        }
    }

    companion object {
        /** وسط بغداد؛ يُستعمل حين لا يتوفر موقع بعد. */
        val DEFAULT_CENTER = GeoPoint(33.3152, 44.3661)
    }
}
