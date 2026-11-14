package iq.turuq.car

import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

fun CarContext.icon(res: Int): CarIcon = CarIcon.Builder(IconCompat.createWithResource(this, res)).build()
