package com.example.officepdf

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * ShapeCache — Caches frequently-used RoundedCornerShape instances to optimize memory usage,
 * minimize GC pressure, and prevent repeated Path construction in Jetpack Compose layouts.
 */
object ShapeCache {
    /** 24dp standard corner shape for Hero cards, main dialog sheets, and operation success/error banners */
    val card = RoundedCornerShape(24.dp)

    /** 16dp container shape for icon boxes, list selections, and settings containers */
    val container = RoundedCornerShape(16.dp)

    /** 12dp icon squircle shape for tool indicators and smaller list elements */
    val icon = RoundedCornerShape(12.dp)

    /** 4dp flat shape for inline labels and fine outlines */
    val flat = RoundedCornerShape(4.dp)
}
