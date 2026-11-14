package iq.turuq.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import iq.turuq.app.R
import iq.turuq.core.AppSettings

private val Teal = Color(0xFF0E7C86)


@Composable
fun SettingsScreen(s: AppSettings, onClose: () -> Unit) {
    val voice by s.voice.collectAsState()
    val warn by s.voiceWarnings.collectAsState()
    val tilt by s.tilt3d.collectAsState()
    val cams by s.showCameras.collectAsState()
    val hazards by s.showHazards.collectAsState()
    val traffic by s.showTraffic.collectAsState()
    val theme by s.theme.collectAsState()

    Surface(Modifier.fillMaxSize(), color = if (LocalDark.current) Color(0xFF0F2A2E) else Color(0xFFF2F4F4)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(surf()).statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("✕", fontSize = 22.sp, color = inkc()) }
                Text("الإعدادات", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = inkc())
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()) {
                Section("🗺  عرض الخريطة")
                Card {
                    Text("المظهر", Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp), fontWeight = FontWeight.Bold, color = inkc())
                    Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "تلقائي", 1 to "نهاري", 2 to "ليلي").forEach { (v, label) ->
                            FilterChip(selected = theme == v, onClick = { s.setTheme(v) }, label = { Text(label) })
                        }
                    }
                    Divider()
                    SwitchRow("خريطة مائلة ثلاثية الأبعاد أثناء الملاحة", tilt) { s.setTilt3d(it) }
                }

                Section("🔊  الأصوات")
                Card {
                    SwitchRow("الإرشاد الصوتي بالعربية", voice) { s.setVoice(it) }
                    Divider()
                    SwitchRow("نطق تنبيهات الطريق (كاميرا، حفرة، حادث…)", warn) { s.setVoiceWarnings(it) }
                    Text("لا ينطق التطبيق أثناء المكالمات الهاتفية، ويخفّض صوت الموسيقى مؤقتاً فقط.",
                        Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp), fontSize = 12.sp, color = Color.Gray)
                }

                Section("⚠  التنبيهات والبلاغات")
                Card {
                    SwitchRow("كاميرات السرعة والمراقبة", cams) { s.setShowCameras(it) }
                    Divider()
                    SwitchRow("الحفر والمطبات والأجسام والمياه", hazards) { s.setShowHazards(it) }
                    Divider()
                    SwitchRow("الازدحام والحوادث والطرق المقطوعة وأعمال الطرق", traffic) { s.setShowTraffic(it) }
                    Text("إيقاف نوع يخفيه من الخريطة ويوقف التنبيه الصوتي عنه.",
                        Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp), fontSize = 12.sp, color = Color.Gray)
                }

                Section("🚗  تفضيلات القيادة")
                Card {
                    SoonRow("التنقل (تجنّب أنواع من الطرق)")
                    Divider()
                    SoonRow("تفاصيل المركبة")
                    Divider()
                    SoonRow("مشغّل الصوتيات")
                }

                val ctx = LocalContext.current
                Section("🛡  الأمان والخصوصية")
                Card {
                    LinkRow("الإبلاغ عن بلاغ أو محتوى مخالف") {
                        val mail = ctx.getString(R.string.support_email)
                        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + mail)).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "إبلاغ عن محتوى مخالف - طرق عراقنا")
                            putExtra(Intent.EXTRA_TEXT, "اكتب هنا نوع البلاغ ومكانه ووقته:\n")
                        }
                        runCatching { ctx.startActivity(i) }
                    }
                    Divider()
                    LinkRow("سياسة الخصوصية") {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ctx.getString(R.string.privacy_url)))) }
                    }
                    Text("يستعمل التطبيق موقعك أثناء فتح التطبيق وأثناء الملاحة فقط لعرض الخريطة والتوجيه والتنبيهات، ولا نحتفظ بسجل لتنقلاتك.",
                        Modifier.padding(16.dp, 4.dp, 16.dp, 12.dp), fontSize = 12.sp, color = Color.Gray)
                }

                Section("ℹ  حول")
                Card {
                    Text("طرق عراقنا · نسخة تجريبية 1.0.0", Modifier.padding(16.dp), color = inkc())
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable private fun Section(t: String) =
    Text(t, Modifier.padding(16.dp, 18.dp, 16.dp, 8.dp), fontSize = 13.sp, color = Color(0xFF5E7377), fontWeight = FontWeight.Bold)

@Composable private fun Card(content: @Composable ColumnScope.() -> Unit) =
    Surface(Modifier.fillMaxWidth(), color = surf()) { Column(content = content) }

@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = inkc(), fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Teal))
    }
}

@Composable private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = inkc(), fontSize = 15.sp)
        Text("‹", color = Color.Gray, fontSize = 20.sp)
    }
}

@Composable private fun SoonRow(label: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Color.Gray, fontSize = 15.sp)
        Surface(shape = RoundedCornerShape(10.dp), color = selBg()) { Text("قريباً", Modifier.padding(8.dp, 3.dp), fontSize = 11.sp, color = Teal) }
    }
}
