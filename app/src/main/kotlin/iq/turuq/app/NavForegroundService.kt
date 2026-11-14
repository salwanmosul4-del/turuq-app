package iq.turuq.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * خدمة أمامية تبقي الملاحة حيّة والموقع مستمراً عند إطفاء الشاشة. مطلوبة أيضاً
 * لتطبيقات الملاحة على Android Auto (إشعار ملاحة ظاهر أثناء التوجيه).
 */
class NavForegroundService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nav", getString(R.string.nav_channel), NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, "nav")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("الملاحة قيد التشغيل")
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .extend(androidx.car.app.notification.CarAppExtender.Builder().setImportance(NotificationManager.IMPORTANCE_HIGH).build())
            .setOngoing(true).build()
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, n)
        return START_NOT_STICKY
    }
}
