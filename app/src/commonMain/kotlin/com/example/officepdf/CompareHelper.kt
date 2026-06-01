package com.example.officepdf

import androidx.compose.ui.graphics.ImageBitmap

expect object CompareHelper {
    suspend fun compareBitmaps(bytes1: ByteArray, bytes2: ByteArray): Pair<ImageBitmap, Int>
}
