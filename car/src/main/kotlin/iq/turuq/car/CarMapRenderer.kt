package iq.turuq.car

import android.app.Presentation
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import iq.turuq.core.TuruqApp
import iq.turuq.core.maps.MapController
import iq.turuq.core.model.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/**
 * يرسم خريطة MapLibre على سطح السيارة. تُنشأ شاشة افتراضية فوق السطح الذي يمنحه المضيف
 * ثم تُعرض فيها MapView داخل Presentation. هذا أخطر جزء تقنياً في المشروع ويجب اختباره على
 * جهاز حقيقي (Desktop Head Unit ثم سيارة حقيقية)، وهو يعتمد على أن MapLibre يعمل في TextureView.
 */
class CarMapRenderer(private val car: CarContext, private val app: TuruqApp) : SurfaceCallback {
    private var vd: VirtualDisplay? = null
    private var pres: Presentation? = null
    private var view: MapView? = null
    private var map: MapLibreMap? = null
    private var ctl: MapController? = null
    private val jobs = ArrayList<Job>()
    private var stable = Rect()

    override fun onSurfaceAvailable(c: SurfaceContainer) {
        val surface = c.surface ?: return
        MapLibre.getInstance(car.applicationContext)
        val dm = car.getSystemService(DisplayManager::class.java)
        vd = dm.createVirtualDisplay("turuq-map", c.width, c.height, c.dpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
        val p = Presentation(car, vd!!.display)
        val mv = MapView(p.context, MapLibreMapOptions.createFromAttributes(p.context).textureMode(true))
        p.setContentView(mv); p.show()
        mv.onCreate(null); mv.onStart(); mv.onResume()
        pres = p; view = mv
        mv.getMapAsync { m ->
            map = m
            val controller = MapController(car, m).also { ctl = it }
            controller.load(app.maps.localFiles(), dark = car.isDarkMode) { bind(controller) }
        }
    }

    private fun bind(c: MapController) {
        jobs += combine(app.routes, app.selected) { r, s -> r to s }.onEach { (r, s) ->
            c.showRoutes(r, s); if (!app.navigating.value) r.getOrNull(s)?.let { c.fit(it, 160) }
        }.launchIn(app.scope)
        jobs += app.destination.onEach { c.showDestination(it?.let { d -> GeoPoint(d.lat, d.lon) }) }.launchIn(app.scope)
        jobs += app.reports.onEach { c.showReports(it) }.launchIn(app.scope)
        jobs += app.location.last.onEach { loc ->
            loc ?: return@onEach
            val st = app.nav.state.value
            val p = st?.position ?: GeoPoint(loc.latitude, loc.longitude)
            val b = st?.bearing ?: loc.bearing.toDouble()
            c.setVehicle(p, b)
            if (st != null) c.follow(p, b) else if (app.routes.value.isEmpty()) c.overview(p)
        }.launchIn(app.scope)
    }

    override fun onStableAreaChanged(r: Rect) {
        stable = r
        map?.setPadding(r.left, r.top, (view?.width ?: r.right) - r.right, (view?.height ?: r.bottom) - r.bottom)
    }

    override fun onVisibleAreaChanged(r: Rect) {}

    override fun onScroll(dx: Float, dy: Float) {
        val m = map ?: return
        val c = m.projection.toScreenLocation(m.cameraPosition.target!!)
        val t = m.projection.fromScreenLocation(android.graphics.PointF(c.x + dx, c.y + dy))
        m.moveCamera(CameraUpdateFactory.newLatLng(t))
    }
    override fun onScale(fx: Float, fy: Float, factor: Float) {
        map?.moveCamera(CameraUpdateFactory.zoomBy(Math.log(factor.toDouble()) / Math.log(2.0), android.graphics.Point(fx.toInt(), fy.toInt())))
    }

    override fun onSurfaceDestroyed(c: SurfaceContainer) {
        jobs.forEach { it.cancel() }; jobs.clear()
        view?.let { it.onPause(); it.onStop(); it.onDestroy() }
        pres?.dismiss(); vd?.release()
        view = null; map = null; ctl = null; pres = null; vd = null
    }
}
