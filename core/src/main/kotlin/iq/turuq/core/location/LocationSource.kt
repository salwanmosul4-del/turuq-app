package iq.turuq.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * موقع السيارة/الهاتف عبر LocationManager مباشرة (دون خدمات Google) حتى يعمل نفسه على
 * هاتف عبر Android Auto وعلى Android Automotive OS الذي قد يخلو من GMS.
 * التطبيق مسؤول عن طلب إذن الموقع قبل [start].
 */
class LocationSource(private val context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _last = MutableStateFlow<Location?>(null)
    val last: StateFlow<Location?> = _last

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { _last.value = location }
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }
    private var started = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (started) return
        started = true
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (lm.isProviderEnabled(p)) {
                lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
                lm.getLastKnownLocation(p)?.let { if (_last.value == null) _last.value = it }
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        lm.removeUpdates(listener)
    }
}
