package com.example.officepdf

import androidx.compose.ui.graphics.ImageBitmap

@JsFun("(bytes1, bytes2) => window.compareImages(bytes1, bytes2)")
external fun jsCompareImages(bytes1: JsAny, bytes2: JsAny): JsAny

@JsFun("(res) => res.diffBytes")
external fun getJsCompareDiffBytes(res: JsAny): JsAny

@JsFun("(res) => res.count")
external fun getJsCompareCount(res: JsAny): Int

actual object CompareHelper {
    actual suspend fun compareBitmaps(bytes1: ByteArray, bytes2: ByteArray): Pair<ImageBitmap, Int> {
        val promise = jsCompareImages(bytes1.toJsUint8Array(), bytes2.toJsUint8Array())
        val result = awaitPromise(promise)
        val jsDiffBytes = getJsCompareDiffBytes(result)
        val count = getJsCompareCount(result)
        val diffBytes = jsUint8ArrayToKotlinByteArray(jsDiffBytes)
        val diffImageBitmap = diffBytes.toImageBitmap()
        return Pair(diffImageBitmap, count)
    }
}
