package iq.turuq.core.model

/** رمز كل نوع بلاغ: يظهر على الخريطة وفي قائمة البلاغ وفي التنبيهات. */
val ReportType.emoji: String
    get() = when (this) {
        ReportType.JAM -> "🚗"
        ReportType.ACCIDENT -> "💥"
        ReportType.CLOSED -> "⛔"
        ReportType.POTHOLE -> "🕳️"
        ReportType.SPEED_CAM -> "📷"
        ReportType.OBJECT -> "🧱"
        ReportType.FLOOD -> "🌊"
        ReportType.WORKS -> "🚧"
        ReportType.CAM -> "🎥"
        ReportType.BREAKDOWN -> "🔧"
        ReportType.SPEED_BUMP -> "〰️"
        ReportType.EMERGENCY -> "🚑"
    }
