package iq.turuq.app

import android.app.Application
import iq.turuq.core.TuruqApp
import iq.turuq.core.TuruqAppProvider
import org.maplibre.android.MapLibre

class TuruqApplication : Application(), TuruqAppProvider {
    override lateinit var turuq: TuruqApp
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        turuq = TuruqApp(this)
        turuq.maps.schedule()
    }
}
