package com.example.officepdf

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object CompareHelper {

    /**
     * Compares two bitmaps pixel-by-pixel.
     * Highlights differences in bright red, and renders unchanged regions as faded gray.
     * Returns a Pair of the resulting diff Bitmap and the count of differing pixels.
     */
    fun compareBitmaps(img1: Bitmap, img2: Bitmap, threshold: Float = 0.1f): Pair<Bitmap, Int> {
        val width = maxOf(img1.width, img2.width)
        val height = maxOf(img1.height, img2.height)

        // Rescale img1 if needed to match sizes
        val b1 = if (img1.width != width || img1.height != height) {
            Bitmap.createScaledBitmap(img1, width, height, true)
        } else img1

        // Rescale img2 if needed to match sizes
        val b2 = if (img2.width != width || img2.height != height) {
            Bitmap.createScaledBitmap(img2, width, height, true)
        } else img2

        val diffBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var diffPixelsCount = 0

        // Calculate threshold criteria
        val maxColorDiff = 255 * 3 * threshold

        for (y in 0 until height) {
            for (x in 0 until width) {
                val c1 = b1.getPixel(x, y)
                val c2 = b2.getPixel(x, y)

                val r1 = Color.red(c1)
                val g1 = Color.green(c1)
                val b_1 = Color.blue(c1)

                val r2 = Color.red(c2)
                val g2 = Color.green(c2)
                val b_2 = Color.blue(c2)

                val diff = abs(r1 - r2) + abs(g1 - g2) + abs(b_1 - b_2)

                if (diff > maxColorDiff) {
                    // Bright red for changes
                    diffBitmap.setPixel(x, y, Color.RED)
                    diffPixelsCount++
                } else {
                    // Faded light gray of background for readability
                    val gray = ((r1 + g1 + b_1) / 3 * 0.4f + 150).toInt().coerceIn(0, 255)
                    diffBitmap.setPixel(x, y, Color.rgb(gray, gray, gray))
                }
            }
        }

        return Pair(diffBitmap, diffPixelsCount)
    }
}
