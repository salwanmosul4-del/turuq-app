package iq.turuq.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * نقطة دخول تطبيق السيارة. تعمل بالكود نفسه مع Android Auto (من الهاتف)
 * ومع Android Automotive OS (داخل السيارة).
 */
class TuruqCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext).addAllowedHosts(R.array.hosts_allowlist).build()
    }

    override fun onCreateSession(): Session = TuruqSession()
}
