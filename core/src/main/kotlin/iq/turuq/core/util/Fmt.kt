package iq.turuq.core.util

import java.text.NumberFormat
import java.util.Locale

/** تنسيق الأرقام والمسافات بالعربية وبالأرقام العربية-الهندية. */
object Fmt {
    private val nf: NumberFormat = NumberFormat.getInstance(Locale.forLanguageTag("ar-u-nu-arab")).apply { maximumFractionDigits = 1 }

    fun num(n: Number): String = nf.format(n)

    fun dist(meters: Double): String = when {
        meters < 950 -> num((Math.round(meters / 50.0) * 50).toInt()) + " م"
        else -> num(Math.round(meters / 100.0) / 10.0) + " كم"
    }

    fun minutes(seconds: Double): String {
        val m = Math.max(1, Math.round(seconds / 60.0).toInt())
        return if (m < 60) num(m) + " د" else num(m / 60) + " س " + num(m % 60) + " د"
    }

    fun ago(ms: Long): String {
        val m = Math.max(0L, ms / 60_000)
        return when {
            m < 1 -> "الآن"
            m < 60 -> "قبل " + num(m) + " د"
            m < 2880 -> "قبل " + num(m / 60) + " ساعة"
            else -> "قبل " + num(m / 1440) + " يوم"
        }
    }
}
