package iq.turuq.core.maps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.Constraints
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * تنزيل خريطة طلبه المستخدم. يعمل كخدمة أمامية بإشعار تقدّم، فيستمر لو أغلق التطبيق،
 * ويُستأنف عند انقطاع الشبكة (الاستئناف بحسب Range في MapUpdateManager.install).
 */
class MapDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val mgr = MapUpdateManager(applicationContext)
        return try {
            val region = mgr.manifest().regions.firstOrNull { it.id == id } ?: return Result.failure()
            setForeground(info(region.name, 0))
            var last = -1
            mgr.install(region) { f ->
                val pct = (f * 100).toInt()
                if (pct != last) { last = pct; setProgressAsync(workDataOf("p" to f)) }
                if (pct % 5 == 0) runCatching { setForegroundAsync(info(region.name, pct)) }
            }
            Result.success()
        } catch (e: MapUpdateManager.NoSpaceException) {
            Result.failure(workDataOf("error" to "space"))
        } catch (e: IOException) {
            if (runAttemptCount < 8) Result.retry() else Result.failure(workDataOf("error" to "network"))
        }
    }

    /** مطلوب للعمل المعجّل على أندرويد أقل من ١٢. */
    override suspend fun getForegroundInfo(): ForegroundInfo = info("الخريطة", 0)

    private fun info(name: String, pct: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("maps", "تنزيل الخرائط", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, "maps")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("تنزيل خريطة $name")
            .setProgress(100, pct, pct == 0).setOngoing(true).setOnlyAlertOnce(true).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(7, n)
    }

    companion object {
        fun enqueue(context: Context, regionId: String, wifiOnly: Boolean) {
            val c = Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build()
            val req = OneTimeWorkRequestBuilder<MapDownloadWorker>()
                .setInputData(workDataOf("id" to regionId)).setConstraints(c)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
            WorkManager.getInstance(context).enqueueUniqueWork("dl-$regionId", ExistingWorkPolicy.KEEP, req)
        }

        /** تقدّم 0..1 أثناء التنزيل، أو null إن لم يكن قيد التنفيذ. */
        fun progress(context: Context, regionId: String): Flow<Float?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("dl-$regionId").map { list ->
                val w = list.firstOrNull()
                when (w?.state) {
                    WorkInfo.State.RUNNING -> w.progress.getFloat("p", 0f)
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> 0f
                    else -> null
                }
            }
    }
}
