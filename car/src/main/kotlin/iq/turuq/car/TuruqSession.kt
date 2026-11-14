package iq.turuq.car

import android.content.Intent
import android.net.Uri
import androidx.car.app.Screen
import androidx.car.app.Session
import iq.turuq.core.model.Place
import iq.turuq.core.turuq

class TuruqSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        carContext.turuq().apply { startPolling(); location.start(); maps.schedule() }
        val app = carContext.turuq()
        carContext.getCarService(androidx.car.app.AppManager::class.java)
            .setSurfaceCallback(CarMapRenderer(carContext, app))
        val home = HomeScreen(carContext)
        parseGeo(intent)?.let { place ->
            // تم استلام وجهة من الهاتف أو من تطبيق آخر: نعرض معاينة المسار مباشرة.
            carContext.turuq().plan(place)
            carContext.getCarService(androidx.car.app.ScreenManager::class.java).push(RoutePreviewScreen(carContext, place))
        }
        return home
    }

    override fun onNewIntent(intent: Intent) {
        parseGeo(intent)?.let { place ->
            carContext.turuq().plan(place)
            carContext.getCarService(androidx.car.app.ScreenManager::class.java).push(RoutePreviewScreen(carContext, place))
        }
    }

    /** يدعم geo:lat,lon و geo:0,0?q=lat,lon(name). */
    private fun parseGeo(intent: Intent): Place? {
        val uri: Uri = intent.data ?: return null
        if (uri.scheme != "geo") return null
        val ssp = uri.schemeSpecificPart
        val q = uri.getQueryParameter("q")
        val coords = Regex("(-?\\d+\\.?\\d*),\\s*(-?\\d+\\.?\\d*)").find(q ?: ssp) ?: return null
        val lat = coords.groupValues[1].toDoubleOrNull() ?: return null
        val lon = coords.groupValues[2].toDoubleOrNull() ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        val name = Regex("\\((.+)\\)").find(q ?: "")?.groupValues?.get(1) ?: "الوجهة"
        return Place(id = "geo", name = name, area = "", lat = lat, lon = lon)
    }
}
