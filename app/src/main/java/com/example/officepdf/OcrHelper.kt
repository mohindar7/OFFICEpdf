package com.example.officepdf

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OcrHelper {

    /**
     * Converts a Bitmap to a Base64 encoded PNG string and requests
     * text extraction from the Gemini API model.
     */
    suspend fun performOcr(bitmap: Bitmap, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Compress bitmap to PNG bytes
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val bytes = outputStream.toByteArray()
            val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)

            // Construct Gemini endpoint
            val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Construct payload with JSON API
            val partsArray = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("text", "Extract all text from this image of a document page. Maintain spacing and layout where possible.")
                })
                put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/png")
                        put("data", base64Data)
                    })
                })
            }

            val contentsArray = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", partsArray)
                })
            }

            val payload = JSONObject().apply {
                put("contents", contentsArray)
            }

            // Stream request payload
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val responseJson = JSONObject(responseText)
                
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content")
                    val partsList = contentObj?.optJSONArray("parts")
                    if (partsList != null && partsList.length() > 0) {
                        val textResult = partsList.getJSONObject(0).optString("text")
                        return@withContext Result.success(textResult)
                    }
                }
                Result.failure(Exception("OCR failed: API did not return text content in candidates list."))
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Result.failure(Exception("API Error Code $responseCode: $errorMsg"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
