package iq.turuq.core.maps

import android.content.Context
import iq.turuq.core.model.GeoPoint
import iq.turuq.core.model.Report
import iq.turuq.core.model.ReportType
import iq.turuq.core.model.emoji
import iq.turuq.core.model.Route
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * غلاف واحد حول MapLibre تستعمله شاشة الهاتف وشاشة السيارة، فيبدو المسار والبلاغات
 * وسهم العلم متطابقة في الاثنتين.
 */
class MapController(private val context: Context, private val map: MapLibreMap) {
    private var style: Style? = null
    private var pending: (() -> Unit)? = null

    fun load(files: List<java.io.File>, dark: Boolean, onReady: () -> Unit = {}) {
        val json = MapStyle.build(context, files, dark)
        map.setStyle(Style.Builder().fromJson(json)) { st ->
            style = st
            st.addImage("flag-arrow", FlagArrow.bitmap())
            st.addImage("route-arrow", RouteArrow.bitmap())
            for (t in ReportType.entries) st.addImage("rep-" + t.key, ReportIcon.bitmap(t.emoji, colorOf(t)))
            st.addSource(GeoJsonSource("alt-routes"))
            st.addSource(GeoJsonSource("main-route"))
            st.addSource(GeoJsonSource("reports"))
            st.addSource(GeoJsonSource("vehicle"))
            st.addSource(GeoJsonSource("dest"))
            st.addLayer(LineLayer("alt-casing", "alt-routes").withProperties(lineColor("#FFFFFF"), lineWidth(12f), lineCap("round")))
            st.addLayer(LineLayer("alt-line", "alt-routes").withProperties(lineColor("#8AA0A3"), lineWidth(8f), lineCap("round")))
            st.addLayer(LineLayer("main-casing", "main-route").withProperties(lineColor("#FFFFFF"), lineWidth(19f), lineCap("round"), lineJoin("round")))
            st.addLayer(LineLayer("main-line", "main-route").withProperties(lineColor("#0E7C86"), lineWidth(13f), lineCap("round"), lineJoin("round")))
            // أسهم متكررة على المسار المختار حتى لا يلتبس مع المسارات المجاورة
            st.addLayer(
                SymbolLayer("route-arrows", "main-route").withProperties(
                    symbolPlacement(Property.SYMBOL_PLACEMENT_LINE), symbolSpacing(55f),
                    iconImage("route-arrow"), iconSize(0.85f), iconAllowOverlap(true), iconIgnorePlacement(true),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                )
            )
            st.addLayer(
                SymbolLayer("reports-icon", "reports").withProperties(
                    iconImage(Expression.concat(Expression.literal("rep-"), Expression.get("t"))),
                    iconSize(1f), iconAllowOverlap(true), iconIgnorePlacement(true),
                )
            )
            st.addLayer(CircleLayer("dest-dot", "dest").withProperties(circleRadius(11f), circleColor("#CE1126"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)))
            st.addLayer(
                SymbolLayer("vehicle-arrow", "vehicle").withProperties(
                    iconImage("flag-arrow"), iconSize(0.9f), iconAllowOverlap(true), iconIgnorePlacement(true),
                    iconRotate(Expression.get("b")), iconRotationAlignment("map"), iconPitchAlignment("map"),
                )
            )
            pending?.invoke(); pending = null
            onReady()
        }
    }

    private fun colorOf(t: ReportType) = when {
        t.isCamera -> "#5B3FD1"
        t == ReportType.ACCIDENT || t == ReportType.CLOSED -> "#D32F2F"
        t == ReportType.JAM -> "#E8912D"
        t.isHazard -> "#C28A00"
        else -> "#0E7C86"
    }

    private fun whenReady(f: () -> Unit) { if (style != null) f() else pending = f }

    fun showRoutes(routes: List<Route>, selected: Int) = whenReady {
        val st = style ?: return@whenReady
        fun ls(r: Route) = Feature.fromGeometry(LineString.fromLngLats(r.shape.map { Point.fromLngLat(it.lon, it.lat) }))
        val mainFc: FeatureCollection = routes.getOrNull(selected)?.let { FeatureCollection.fromFeature(ls(it)) } ?: FeatureCollection.fromFeatures(emptyList<Feature>())
        (st.getSource("main-route") as? GeoJsonSource)?.setGeoJson(mainFc.toJson())
        (st.getSource("alt-routes") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(routes.filterIndexed { i, _ -> i != selected }.map { ls(it) }).toJson())
    }

    fun showDestination(p: GeoPoint?) = whenReady {
        val fc: FeatureCollection = if (p == null) FeatureCollection.fromFeatures(emptyList<Feature>())
        else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))
        (style?.getSource("dest") as? GeoJsonSource)?.setGeoJson(fc.toJson())
    }

    fun showReports(list: List<Report>) = whenReady {
        val fs = list.map { r -> Feature.fromGeometry(Point.fromLngLat(r.lon, r.lat)).also { it.addStringProperty("t", r.type); it.addStringProperty("id", r.id) } }
        (style?.getSource("reports") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(fs).toJson())
    }

    fun setVehicle(p: GeoPoint, bearing: Double) = whenReady {
        val f = Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also { it.addNumberProperty("b", bearing) }
        (style?.getSource("vehicle") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeature(f).toJson())
    }

    /** كاميرا الملاحة: تتبّع مع ميلان واتجاه الحركة. */
    fun follow(p: GeoPoint, bearing: Double, zoom: Double = 17.0, tilt: Double = 50.0) {
        map.easeCamera(
            CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(p.lat, p.lon)).zoom(zoom).bearing(bearing).tilt(tilt).build()), 800
        )
    }

    fun overview(p: GeoPoint, zoom: Double = 12.5) {
        map.easeCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(p.lat, p.lon)).zoom(zoom).bearing(0.0).tilt(0.0).build()), 600)
    }

    fun fit(route: Route, paddingPx: Int = 120) {
        if (route.shape.isEmpty()) return
        val b = LatLngBounds.Builder().apply { route.shape.forEach { include(LatLng(it.lat, it.lon)) } }.build()
        map.easeCamera(CameraUpdateFactory.newLatLngBounds(b, paddingPx), 700)
    }
}
