package com.example.officepdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Image

actual fun createPlatformFile(path: String): PlatformFile {
    return WebPlatformFile("", 0, path, ByteArray(0))
}

@Composable
actual fun rememberPlatformFilePicker(): FilePicker {
    return remember {
        object : FilePicker {
            override fun pickFiles(
                allowedTypes: List<String>,
                multiple: Boolean,
                onFilesPicked: (List<PlatformFile>) -> Unit
            ) {
                val acceptString = allowedTypes.joinToString(",") { 
                    if (it.startsWith(".")) it else if (it.contains("/")) it else "." + it 
                }
                jsPickFiles(acceptString, multiple) { filesArray ->
                    val len = getJsArrayLength(filesArray)
                    val list = mutableListOf<PlatformFile>()
                    for (i in 0 until len) {
                        val fileObj = getJsArrayElement(filesArray, i)
                        val name = getJsFileName(fileObj)
                        val size = getJsFileSize(fileObj).toLong()
                        val jsBytes = getJsFileBytes(fileObj)
                        val bytes = jsUint8ArrayToKotlinByteArray(jsBytes)
                        val mime = if (name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true)) "image/jpeg"
                                   else if (name.endsWith(".png", ignoreCase = true)) "image/png"
                                   else "application/pdf"
                        val blobUrl = createBlobUrl(jsBytes, mime)
                        list.add(WebPlatformFile(name, size, blobUrl, bytes))
                    }
                    onFilesPicked(list)
                }
            }
        }
    }
}

@JsFun("() => new Date().getHours()")
external fun getJsCurrentHour(): Int

@JsFun("() => Date.now()")
external fun getJsCurrentTimeMillis(): Double

actual fun getCurrentHour(): Int = getJsCurrentHour()
actual fun getCurrentTimeMillis(): Long = getJsCurrentTimeMillis().toLong()

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val skiaImage = Image.makeFromEncoded(this)
    val skiaBitmap = org.jetbrains.skia.Bitmap.makeFromImage(skiaImage)
    return skiaBitmap.asComposeImageBitmap()
}


