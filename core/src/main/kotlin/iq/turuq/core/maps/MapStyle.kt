package iq.turuq.core.maps

import android.content.Context
import iq.turuq.core.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * يبني ستايل الخريطة من قالب assets/style/base.json.
 * - إن وُجدت مناطق محمّلة: كل منطقة مصدر mbtiles محلي، وتُنسخ طبقات القالب لكل مصدر بالترتيب نفسه.
 * - إن لم توجد: يستعمل مصدر البلاطات عبر الإنترنت.
 * القالب يتبع مخطط OpenMapTiles، وهو المخطط الذي ينتجه Planetiler في سكربت الخادم.
 */
object MapStyle {
    fun build(context: Context, regions: List<File>, dark: Boolean): String {
        val tpl = JSONObject(context.assets.open("style/base.json").bufferedReader().use { it.readText() })
        val layers = tpl.getJSONArray("layers")
        val sources = JSONObject()
        val out = JSONArray()
        val srcIds = ArrayList<String>()

        if (regions.isEmpty()) {
            sources.put("omt", JSONObject().put("type", "vector").put("url", BuildConfig.TILES_ONLINE))
            srcIds.add("omt")
            // اتصال متاح: خطوط كاملة من الخادم العام لتغطية كل الحروف
            tpl.put("glyphs", "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf")
        } else {
            regions.forEachIndexed { i, f ->
                val id = "omt$i"
                sources.put(id, JSONObject().put("type", "vector").put("url", "mbtiles://" + f.absolutePath))
                srcIds.add(id)
            }
        }

        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            if (!l.has("source")) { out.put(l); continue }
            for ((k, s) in srcIds.withIndex()) {
                val c = JSONObject(l.toString())
                c.put("source", s)
                if (k > 0) c.put("id", l.getString("id") + "#" + k)
                out.put(c)
            }
        }
        tpl.put("sources", sources)
        tpl.put("layers", out)
        if (dark) applyDark(tpl)
        return tpl.toString()
    }

    /** ألوان ليلية بسيطة: تُستبدل قيم الألوان المعروفة في القالب. */
    private fun applyDark(style: JSONObject) {
        val map = mapOf(
            "#ECE6D6" to "#16393E", "#B9D5DC" to "#1F5A66", "#D9E3C6" to "#1C4A3B",
            "#FFFFFF" to "#3A6A70", "#D8CFB8" to "#0E2A2E", "#3B4F52" to "#C9DAD5",
        )
        var s = style.toString()
        for ((a, b) in map) s = s.replace(a, b, ignoreCase = true)
        val n = JSONObject(s)
        style.keys().asSequence().toList().forEach { style.remove(it) }
        n.keys().forEach { style.put(it, n.get(it)) }
    }
}
