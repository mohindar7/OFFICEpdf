package com.example.officepdf

@JsFun("(bytes, apiKey) => window.jsPerformOcr(bytes, apiKey)")
external fun jsPerformOcr(bytes: JsAny, apiKey: String): JsAny

actual object OcrHelper {
    actual suspend fun performOcr(imageBytes: ByteArray, apiKey: String): Result<String> {
        return try {
            val jsBytes = imageBytes.toJsUint8Array()
            val promise = jsPerformOcr(jsBytes, apiKey)
            val resultText = awaitPromise(promise)
            Result.success(resultText.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
