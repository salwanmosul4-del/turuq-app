package iq.turuq.core.maps

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import iq.turuq.core.BuildConfig
import iq.turuq.core.model.MapManifest
import iq.turuq.core.model.MapRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * يدير خرائط المحافظات المحفوظة على الجهاز.
 *
 * المبدأ: الخادم ينشر ملف manifest.json فيه رقم إصدار لكل منطقة. الجهاز يقارن إصداراته المثبتة
 * بالمانيفست، وينزّل ما تغيّر فقط على شبكة غير محدودة الاستهلاك، ثم يتحقق من SHA-256 ويستبدل
 * الملف القديم باستبدال ذرّي، فلا تبقى الخريطة في حالة نصف محمّلة أبداً.
 * على Android Auto تعمل هذه المهمة على الهاتف؛ وعلى Android Automotive تعمل داخل السيارة نفسها.
 */
class MapUpdateManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val dir = File(context.filesDir, "maps").apply { mkdirs() }
    private val installedFile = File(dir, "installed.json")

    fun installed(): Map<String, Int> = runCatching {
        json.decodeFromString<Map<String, Int>>(installedFile.readText())
    }.getOrDefault(emptyMap())

    private fun saveInstalled(m: Map<String, Int>) {
        val tmp = File(dir, "installed.json.tmp")
        tmp.writeText(json.encodeToString(kotlinx.serialization.builtins.MapSerializer(String.serializer(), Int.serializer()), m))
        Files.move(tmp.toPath(), installedFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun localFiles(): List<File> = installed().keys.map { File(dir, "$it.mbtiles") }.filter { it.exists() }

    suspend fun manifest(): MapManifest = withContext(Dispatchers.IO) {
        if (BuildConfig.MAPS_BASE.isBlank()) throw IOException("maps server not configured")
        http.newCall(Request.Builder().url(BuildConfig.MAPS_BASE + "manifest.json").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            json.decodeFromString(MapManifest.serializer(), r.body!!.string())
        }
    }

    /** المناطق المثبتة التي صدر لها إصدار أحدث. المنطقة غير المثبتة لا تُنزَّل تلقائياً إلا إذا طلبها المستخدم. */
    suspend fun outdated(): List<MapRegion> {
        val have = installed()
        return manifest().regions.filter { (have[it.id] ?: return@filter false) < it.version }
    }

    /** ينزّل منطقة مع استئناف الانقطاع، ويتحقق من البصمة، ثم يثبّتها ذرّياً. */
    class NoSpaceException : IOException("not enough storage")

    suspend fun install(region: MapRegion, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val part = File(dir, "${region.id}.part")
        var have = if (part.exists()) part.length() else 0L
        // نحتاج حجم الملف مرة ونصفاً تقريباً (الملف الجزئي + النسخة القديمة أثناء الاستبدال)
        if (dir.usableSpace < (region.bytes - have) + region.bytes / 2) throw NoSpaceException()
        if (have > region.bytes) { part.delete(); have = 0L }
        if (have < region.bytes) {
            val req = Request.Builder().url(region.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val resumed = r.code == 206
                RandomAccessFile(part, "rw").use { raf ->
                    if (resumed) raf.seek(have) else { raf.setLength(0); have = 0 }
                    val src = r.body!!.byteStream(); val buf = ByteArray(64 * 1024); var total = have
                    while (true) {
                        val n = src.read(buf); if (n < 0) break
                        raf.write(buf, 0, n); total += n
                        onProgress((total.toFloat() / region.bytes).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (sha256(part) != region.sha256.lowercase()) { part.delete(); throw IOException("checksum mismatch for ${region.id}") }
        val target = File(dir, "${region.id}.mbtiles")
        Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        saveInstalled(installed() + (region.id to region.version))
    }

    fun remove(regionId: String) {
        File(dir, "$regionId.mbtiles").delete()
        saveInstalled(installed() - regionId)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** يجدول فحصاً كل ١٢ ساعة على شبكة غير محدودة الاستهلاك ومع بطارية غير منخفضة. */
    fun schedule() {
        val c = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build()
        val req = PeriodicWorkRequestBuilder<MapUpdateWorker>(12, TimeUnit.HOURS).setConstraints(c).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("map-updates", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class MapUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val m = MapUpdateManager(applicationContext)
        return try {
            m.outdated().forEach { m.install(it) }
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
