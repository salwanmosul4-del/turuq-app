package iq.turuq.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import iq.turuq.app.ui.TuruqRoot
import iq.turuq.core.TuruqAppProvider

class MainActivity : ComponentActivity() {
    private fun hasPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun gpsOn() = LocationManagerCompat.isLocationEnabled(getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    private val appProvider get() = application as TuruqAppProvider

    override fun onStart() {
        super.onStart()
        if (hasPermission() && gpsOn()) { appProvider.turuq.location.start(); appProvider.turuq.startPolling() }
    }

    override fun onStop() {
        super.onStop()
        // خارج التطبيق ودون ملاحة: لا داعي لاستهلاك البطارية والبيانات
        val a = appProvider.turuq
        if (!a.navigating.value) { a.stopPolling(); a.location.stop() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = (application as TuruqAppProvider).turuq
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                var granted by remember { mutableStateOf(hasPermission()) }
                var gps by remember { mutableStateOf(gpsOn()) }
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    granted = hasPermission()
                }
                // نعيد الفحص كلما عاد المستخدم من إعدادات الموقع
                val owner = LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val ob = LifecycleEventObserver { _, e ->
                        if (e == Lifecycle.Event.ON_RESUME) { granted = hasPermission(); gps = gpsOn() }
                    }
                    owner.lifecycle.addObserver(ob)
                    onDispose { owner.lifecycle.removeObserver(ob) }
                }
                val ready = granted && gps
                var askExit by remember { mutableStateOf(false) }
                BackHandler(enabled = ready) { askExit = true }
                if (askExit) {
                    AlertDialog(
                        onDismissRequest = { askExit = false },
                        title = { Text("هل تريد إيقاف التطبيق؟") },
                        text = { Text("الإيقاف ينهي الملاحة والتنبيهات الصوتية. أو يمكنك تركه يعمل في الخلفية.") },
                        confirmButton = {
                            Row {
                                TextButton(onClick = {
                                    askExit = false
                                    app.stopAll(); stopService(Intent(this@MainActivity, NavForegroundService::class.java)); finishAndRemoveTask()
                                }) { Text("إيقاف التطبيق") }
                                TextButton(onClick = { askExit = false; moveTaskToBack(true) }) { Text("في الخلفية") }
                            }
                        },
                        dismissButton = { TextButton(onClick = { askExit = false }) { Text("إلغاء") } },
                    )
                }
                LaunchedEffect(ready) { if (ready) { app.location.start(); app.startPolling() } }

                if (!ready) {
                    // لا يعمل التطبيق إلا بعد تفعيل الموقع
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF0F2A2E)).padding(32.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("📍", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("فعّل الموقع لبدء الاستخدام", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (!granted) "يجمع «طرق عراقنا» موقعك الدقيق أثناء استخدام التطبيق، وأثناء الملاحة حتى لو أُطفئت الشاشة، وذلك فقط لعرض موقعك على الخريطة والتوجيه الصوتي وتنبيهك بالكاميرات والمخاطر القريبة. لا نبيع موقعك ولا نشاركه مع أي جهة إعلانية."
                            else "خدمة الموقع (GPS) مطفأة في هاتفك. فعّلها ثم ارجع إلى التطبيق.",
                            color = Color(0xFFCFE3E0), fontSize = 15.sp, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (!granted) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                else startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E7C86)),
                        ) { Text(if (!granted) "السماح بالموقع" else "فتح إعدادات الموقع", fontSize = 17.sp) }
                    }
                } else {
                    TuruqRoot(app, onNavStateChanged = { on ->
                        val i = Intent(this@MainActivity, NavForegroundService::class.java)
                        if (on) ContextCompat.startForegroundService(this@MainActivity, i) else stopService(i)
                    })
                }
            }
        }
    }
}
