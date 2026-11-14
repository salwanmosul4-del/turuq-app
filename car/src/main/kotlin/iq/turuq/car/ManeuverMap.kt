package iq.turuq.car

import androidx.car.app.navigation.model.Maneuver

/** يترجم رقم مناورة Valhalla إلى نوع مناورة شاشة السيارة. السير في العراق على اليمين. */
object ManeuverMap {
    fun type(valhalla: Int): Int = when (valhalla) {
        1, 2, 3 -> Maneuver.TYPE_DEPART
        4 -> Maneuver.TYPE_DESTINATION
        5 -> Maneuver.TYPE_DESTINATION_RIGHT
        6 -> Maneuver.TYPE_DESTINATION_LEFT
        7, 8, 17, 22 -> Maneuver.TYPE_STRAIGHT
        9 -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
        10 -> Maneuver.TYPE_TURN_NORMAL_RIGHT
        11 -> Maneuver.TYPE_TURN_SHARP_RIGHT
        12 -> Maneuver.TYPE_U_TURN_RIGHT
        13 -> Maneuver.TYPE_U_TURN_LEFT
        14 -> Maneuver.TYPE_TURN_SHARP_LEFT
        15 -> Maneuver.TYPE_TURN_NORMAL_LEFT
        16 -> Maneuver.TYPE_TURN_SLIGHT_LEFT
        18 -> Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT
        19 -> Maneuver.TYPE_ON_RAMP_NORMAL_LEFT
        20 -> Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT
        21 -> Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT
        23 -> Maneuver.TYPE_KEEP_RIGHT
        24 -> Maneuver.TYPE_KEEP_LEFT
        25, 37, 38 -> Maneuver.TYPE_MERGE_SIDE_UNSPECIFIED
        // الدوار في بلد يسير على اليمين يُدار عكس عقارب الساعة.
        26 -> Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW
        27 -> Maneuver.TYPE_ROUNDABOUT_EXIT_CCW
        28 -> Maneuver.TYPE_FERRY_BOAT
        else -> Maneuver.TYPE_UNKNOWN
    }
}
