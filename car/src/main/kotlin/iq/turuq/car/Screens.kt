package iq.turuq.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import iq.turuq.core.model.Place
import iq.turuq.core.model.ReportType
import iq.turuq.core.turuq
import iq.turuq.core.util.Fmt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private val panOnly get() = ActionStrip.Builder().addAction(Action.PAN).build()

/**
 * الشاشة الرئيسية: الخريطة مع ثلاثة أزرار فقط (بحث، بلاغ، حالة الخرائط). السائق لا يكتب أثناء القيادة.
 * القالب المستعمل NavigationTemplate هو المسموح للشاشة الأولى في تطبيقات الملاحة.
 */
class HomeScreen(car: CarContext) : Screen(car) {
    override fun onGetTemplate(): Template {
        val sm = carContext.getCarService(ScreenManager::class.java)
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("بحث").setIcon(carContext.icon(R.drawable.ic_search))
                .setOnClickListener { sm.push(SearchScreen(carContext)) }.build())
            .addAction(Action.Builder().setTitle("أبلغ").setIcon(carContext.icon(R.drawable.ic_report))
                .setOnClickListener { sm.push(ReportScreen(carContext)) }.build())
            .addAction(Action.Builder().setIcon(carContext.icon(R.drawable.ic_maps))
                .setOnClickListener { sm.push(MapsStatusScreen(carContext)) }.build())
            .build()
        return androidx.car.app.navigation.model.NavigationTemplate.Builder()
            .setActionStrip(strip)
            .setMapActionStrip(panOnly)
            .build()
    }
}

class SearchScreen(car: CarContext) : Screen(car) {
    private var results: List<Place> = emptyList()
    private var loading = false

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().apply {
            results.take(6).forEach { p ->
                addItem(Row.Builder().setTitle(p.name).addText(p.area.ifBlank { " " })
                    .setOnClickListener { go(p) }.build())
            }
        }.build()
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) = search(searchText)
        })
            .setHeaderAction(Action.BACK)
            .setShowKeyboardByDefault(true)
            .setSearchHint("إلى أين؟")
            .setLoading(loading)
            .apply { if (!loading) setItemList(list) }
            .build()
    }

    private fun search(q: String) {
        if (q.isBlank()) return
        loading = true; invalidate()
        val app = carContext.turuq()
        app.scope.launch {
            results = runCatching { app.api.search(q, app.here()) }.getOrDefault(emptyList())
            loading = false
            if (results.isEmpty()) CarToast.makeText(carContext, "لا توجد نتائج", CarToast.LENGTH_SHORT).show()
            invalidate()
        }
    }

    private fun go(p: Place) {
        carContext.turuq().plan(p)
        carContext.getCarService(ScreenManager::class.java).push(RoutePreviewScreen(carContext, p))
    }
}

class RoutePreviewScreen(car: CarContext, private val dest: Place) : Screen(car) {
    private var job: Job? = null
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val app = carContext.turuq()
                job = app.routes.onEach { invalidate() }.launchIn(app.scope)
                app.selected.onEach { invalidate() }.launchIn(app.scope)
            }
            override fun onStop(owner: LifecycleOwner) { job?.cancel() }
        })
    }

    override fun onGetTemplate(): Template {
        val app = carContext.turuq()
        val routes = app.routes.value
        val b = RoutePreviewNavigationTemplate.Builder()
            .setTitle(dest.name).setHeaderAction(Action.BACK)
            .setMapActionStrip(panOnly)
        if (routes.isEmpty()) return b.setLoading(true).build()

        val items = ItemList.Builder().apply {
            routes.take(3).forEach { r ->
                val warn = r.warnings.size
                addItem(Row.Builder().setTitle(Fmt.minutes(r.durationS) + " · " + Fmt.dist(r.distanceM))
                    .addText(r.label + if (warn > 0) " · " + Fmt.num(warn) + " تنبيه" else "").build())
            }
            setSelectedIndex(app.selected.value.coerceIn(0, routes.size - 1))
            setOnSelectedListener { i -> app.selected.value = i }
        }.build()
        return b.setItemList(items)
            .setNavigateAction(Action.Builder().setTitle("ابدأ")
                .setOnClickListener {
                    app.startNavigation()
                    carContext.getCarService(ScreenManager::class.java).push(NavigatingScreen(carContext))
                }.build())
            .build()
    }
}

/** ٦ أنواع فقط لتقليل التشتيت؛ القائمة الكاملة (١٠ أنواع) متاحة على الهاتف. */
class ReportScreen(car: CarContext) : Screen(car) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder().apply {
            ReportType.entries.filter { it.onCarGrid }.forEach { t ->
                addItem(GridItem.Builder().setTitle(t.labelAr)
                    .setImage(carContext.icon(R.drawable.ic_report), GridItem.IMAGE_TYPE_ICON)
                    .setOnClickListener {
                        carContext.turuq().report(t)
                        CarToast.makeText(carContext, "شكراً، وصل بلاغك", CarToast.LENGTH_SHORT).show()
                        carContext.getCarService(ScreenManager::class.java).pop()
                    }.build())
            }
        }.build()
        return GridTemplate.Builder().setTitle("ماذا ترى؟").setHeaderAction(Action.BACK)
            .setSingleList(list).build()
    }
}

class MapsStatusScreen(car: CarContext) : Screen(car) {
    override fun onGetTemplate(): Template {
        val installed = carContext.turuq().maps.installed()
        val text = if (installed.isEmpty()) "لا توجد خرائط محمّلة بعد. افتح التطبيق على الهاتف لتنزيل محافظتك؛ ويجري التحديث تلقائياً على الواي فاي."
        else "المحافظات المحمّلة: " + Fmt.num(installed.size) + "\nتُحدَّث تلقائياً على الواي فاي."
        return MessageTemplate.Builder(text).setTitle("الخرائط على الجهاز").setHeaderAction(Action.BACK).build()
    }
}
