package com.example.officepdf

expect object OcrHelper {
    suspend fun performOcr(imageBytes: ByteArray, apiKey: String): Result<String>
}
