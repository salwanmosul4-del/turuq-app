package iq.turuq.core.nav

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.TextToSpeech
import iq.turuq.core.model.ReportType
import iq.turuq.core.util.Fmt
import java.util.Locale

/**
 * التوجيه الصوتي بالعربية. يعتمد على محرك TTS في الجهاز (يجب أن تتوفر حزمة صوت عربية).
 * يُنطق كل تنبيه مرة واحدة: المناورة عند ٥٠٠ م و١٥٠ م و٣٠ م، والتحذيرات عند ظهورها.
 */
class Voice(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null
    private val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focus: AudioFocusRequest? = null

    init {
        tts = TextToSpeech(context.applicationContext) { st ->
            val r = if (st == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED else TextToSpeech.LANG_NOT_SUPPORTED
            ready = r >= TextToSpeech.LANG_AVAILABLE
            tts?.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { release() }
                @Deprecated("deprecated") override fun onError(id: String?) { release() }
            })
        }
    }

    /** لا نتكلم أثناء مكالمة هاتفية أو رنين أو اتصال صوتي. */
    private fun inCall(): Boolean = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_RINGTONE ||
        am.mode == AudioManager.MODE_IN_COMMUNICATION

    private fun acquire(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }.build()
        val ok = am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (ok) focus = req
        return ok
    }

    private fun release() { focus?.let { am.abandonAudioFocusRequest(it) }; focus = null }

    var enabled = true
    /** يُعيَّن من الإعدادات: هل يُنطق تنبيه هذا النوع من البلاغات؟ */
    var warningAllowed: (String) -> Boolean = { true }
    private var lastStep = -1
    private val said = HashSet<String>()

    fun onState(st: NavState) {
        if (!enabled || !ready) return
        if (st.stepIndex != lastStep) { lastStep = st.stepIndex; said.removeAll { it.startsWith("m") } }
        for (th in intArrayOf(500, 150, 30)) {
            val key = "m$lastStep-$th"
            if (st.distToManeuverM <= th && st.distToManeuverM > 0 && said.add(key)) {
                // نتجاوز العتبات الأبعد إذا كنا أقرب منها أصلاً
                say(if (th == 30) st.step.instruction else "بعد " + Fmt.dist(st.distToManeuverM) + "، " + st.step.instruction)
                break
            }
        }
        st.nextWarning?.let { w ->
            if (warningAllowed(w.type) && said.add("w" + w.reportId)) {
                val t = ReportType.fromKey(w.type)?.labelAr ?: return@let
                say("انتبه، $t بعد " + Fmt.dist(st.warningDistM))
            }
        }
        if (st.arrived && said.add("arrived")) say("وصلت إلى وجهتك")
    }

    fun reset() { said.clear(); lastStep = -1 }
    fun say(text: String) {
        if (!enabled || !ready || inCall()) return
        if (!acquire()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }
    fun stop() { tts?.stop(); release() }
    fun shutdown() { tts?.shutdown(); release() }
}
