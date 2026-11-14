package iq.turuq.core

import android.content.Context
import iq.turuq.core.model.ReportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** إعدادات المستخدم، تُحفظ على الجهاز وتُقرأ من شاشة الهاتف وشاشة السيارة. */
class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("turuq_settings", Context.MODE_PRIVATE)

    private fun flag(key: String, def: Boolean): MutableStateFlow<Boolean> = MutableStateFlow(p.getBoolean(key, def))

    private val _voice = flag("voice", true)
    private val _voiceWarn = flag("voice_warn", true)
    private val _tilt = flag("tilt3d", true)
    private val _cams = flag("show_cams", true)
    private val _hazards = flag("show_hazards", true)
    private val _traffic = flag("show_traffic", true)
    private val _theme = MutableStateFlow(p.getInt("theme", 0))

    val voice: StateFlow<Boolean> get() = _voice
    val voiceWarnings: StateFlow<Boolean> get() = _voiceWarn
    val tilt3d: StateFlow<Boolean> get() = _tilt
    val showCameras: StateFlow<Boolean> get() = _cams
    val showHazards: StateFlow<Boolean> get() = _hazards
    val showTraffic: StateFlow<Boolean> get() = _traffic
    /** 0 تلقائي حسب الجهاز، 1 نهاري، 2 ليلي. */
    val theme: StateFlow<Int> get() = _theme

    private fun put(key: String, f: MutableStateFlow<Boolean>, v: Boolean) { f.value = v; p.edit().putBoolean(key, v).apply() }
    fun setVoice(v: Boolean) = put("voice", _voice, v)
    fun setVoiceWarnings(v: Boolean) = put("voice_warn", _voiceWarn, v)
    fun setTilt3d(v: Boolean) = put("tilt3d", _tilt, v)
    fun setShowCameras(v: Boolean) = put("show_cams", _cams, v)
    fun setShowHazards(v: Boolean) = put("show_hazards", _hazards, v)
    fun setShowTraffic(v: Boolean) = put("show_traffic", _traffic, v)
    fun setTheme(v: Int) { _theme.value = v; p.edit().putInt("theme", v).apply() }

    /** هل يُعرض هذا النوع من البلاغات ويُنبَّه إليه؟ */
    fun allows(typeKey: String): Boolean {
        val t = ReportType.fromKey(typeKey) ?: return true
        return when {
            t.isCamera -> _cams.value
            t.isHazard -> _hazards.value
            else -> _traffic.value
        }
    }
}
