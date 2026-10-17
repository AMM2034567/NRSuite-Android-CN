package com.swp81x.nrsuite.ui.util

enum class ProximityTier(val label: String) {
    VERY_CLOSE("极近"),
    NEARBY("较近"),
    FAR("较远"),
    VERY_FAR("极远"),
}

fun rssiToProximity(rssi: Int): ProximityTier = when {
    rssi >= -50 -> ProximityTier.VERY_CLOSE
    rssi >= -65 -> ProximityTier.NEARBY
    rssi >= -80 -> ProximityTier.FAR
    else -> ProximityTier.VERY_FAR
}
