package iq.turuq.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.AppManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Alert
import androidx.car.app.model.AlertCallback
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarText
import androidx.car.app.model.Distance
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import iq.turuq.core.model.ReportType
import iq.turuq.core.nav.NavState
import iq.turuq.core.turuq
import iq.turuq.core.util.Fmt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executor

class NavigatingScreen(car: CarContext) : Screen(car) {
    private var job: Job? = null
    private var navManager: NavigationManager? = null
    private val alerted = HashSet<String>()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                val nm = carContext.getCarService(NavigationManager::class.java)
                nm.setNavigationManagerCallback(Executor { it.run() }, object : NavigationManagerCallback {
                    override fun onStopNavigation() { stop() }
                    override fun onAutoDriveEnabled() {}
                })
                nm.navigationStarted()
                navManager = nm
            }
            override fun onStart(owner: LifecycleOwner) {
                val app = carContext.turuq()
                job = app.nav.state.onEach { st -> onState(st) }.launchIn(app.scope)
            }
            override fun onStop(owner: LifecycleOwner) { job?.cancel() }
            override fun onDestroy(owner: LifecycleOwner) { navManager?.navigationEnded() }
        })
    }

    private fun stop() {
        carContext.turuq().stopNavigation()
        navManager?.navigationEnded()
        val sm = carContext.getCarService(ScreenManager::class.java)
        sm.popToRoot()
    }

    private fun onState(st: NavState?) {
        if (st == null) { if (!carContext.turuq().navigating.value) stop(); return }
        invalidate()
        pushTrip(st)
        warn(st)
    }

    private fun carStep(st: NavState, idx: Int): Step? {
        val s = st.route.steps.getOrNull(idx) ?: return null
        val man = Maneuver.Builder(ManeuverMap.type(s.type)).setIcon(carContext.icon(R.drawable.ic_arrow)).apply {
            if (s.type == 26 && s.roundaboutExit != null) setRoundaboutExitNumber(s.roundaboutExit!!)
        }.build()
        return Step.Builder(s.instruction).setManeuver(man).apply { s.street?.let { setRoad(it) } }.build()
    }

    private fun estimate(st: NavState): TravelEstimate {
        val eta = ZonedDateTime.now(ZoneId.systemDefault()).plusSeconds(st.remainingS.toLong())
        return TravelEstimate.Builder(
            Distance.create(st.remainingM / 1000.0, Distance.UNIT_KILOMETERS),
            DateTimeWithZone.create(eta.toInstant().toEpochMilli(), java.util.TimeZone.getTimeZone(eta.zone))
        ).setRemainingTimeSeconds(st.remainingS.toLong()).build()
    }

    /** يغذّي لوحة العدّادات في السيارة بالمناورة التالية. */
    private fun pushTrip(st: NavState) {
        val step = carStep(st, st.stepIndex) ?: return
        val dest = carContext.turuq().destination.value ?: return
        val d = Destination.Builder().setName(dest.name).build()
        navManager?.updateTrip(
            Trip.Builder().addDestination(d, estimate(st)).addStep(step, TravelEstimate.Builder(
                Distance.create(st.distToManeuverM, Distance.UNIT_METERS),
                estimate(st).arrivalTimeAtDestination!!).build())
                .setCurrentRoad(st.step.street ?: "").setLoading(false).build()
        )
    }

    /** تنبيه على الشاشة قبل كاميرا أو خطر أو ازدحام على المسار. */
    private fun warn(st: NavState) {
        val w = st.nextWarning ?: return
        if (!alerted.add(w.reportId)) return
        val t = ReportType.fromKey(w.type) ?: return
        val alert = Alert.Builder(w.reportId.hashCode(), CarText.create(t.labelAr + " بعد " + Fmt.dist(st.warningDistM)), 6000L)
            .setCallback(object : AlertCallback {
                override fun onCancel(reason: Int) {}
                override fun onDismiss() {}
            }).build()
        runCatching { carContext.getCarService(AppManager::class.java).showAlert(alert) }
    }

    override fun onGetTemplate(): Template {
        val app = carContext.turuq()
        val st = app.nav.state.value
        val b = NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder()
                .addAction(Action.Builder().setTitle("أبلغ").setOnClickListener {
                    carContext.getCarService(ScreenManager::class.java).push(ReportScreen(carContext))
                }.build())
                .addAction(Action.Builder().setTitle("إنهاء").setOnClickListener { stop() }.build())
                .build())
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
        if (st == null) return b.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build()).build()

        val cur = carStep(st, st.stepIndex)!!
        val info = RoutingInfo.Builder()
            .setCurrentStep(cur, Distance.create(st.distToManeuverM, Distance.UNIT_METERS))
            .apply { carStep(st, st.stepIndex + 1)?.let { setNextStep(it) } }
            .build()
        return b.setNavigationInfo(info).setDestinationTravelEstimate(estimate(st)).build()
    }
}
