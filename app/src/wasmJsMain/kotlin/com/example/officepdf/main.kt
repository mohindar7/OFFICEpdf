package com.example.officepdf

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow

@JsFun("(bytes, mimeType) => { const blob = new Blob([bytes], {type: mimeType}); const url = URL.createObjectURL(blob); window.open(url, '_blank'); }")
external fun openBlobInNewTab(bytes: JsAny, mimeType: String)

@JsFun("(bytes, mimeType) => { const blob = new Blob([bytes], {type: mimeType}); return URL.createObjectURL(blob); }")
external fun createBlobUrl(bytes: JsAny, mimeType: String): String

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val settingsStorage = object : SettingsStorage {
        override fun getString(key: String, defaultValue: String): String {
            return kotlinx.browser.window.localStorage.getItem(key) ?: defaultValue
        }
        override fun putString(key: String, value: String) {
            kotlinx.browser.window.localStorage.setItem(key, value)
        }
    }
    
    val pdfEngine = WebPdfEngine()
    
    val fileOperations = object : FileOperations {
        override fun saveFile(name: String, bytes: ByteArray, mimeType: String) {
            triggerDownload(name, bytes.toJsUint8Array(), mimeType)
        }
        override fun shareFile(path: String) {
            // Web share fallback to download
            // Since we store web files as blob URLs, we can just save it or open it
            if (path.startsWith("blob:")) {
                kotlinx.browser.window.open(path, "_blank")
            }
        }
        override fun viewFile(path: String) {
            if (path.startsWith("blob:")) {
                kotlinx.browser.window.open(path, "_blank")
            }
        }
    }
    
    val filePicker = object : FilePicker {
        override fun pickFiles(allowedTypes: List<String>, multiple: Boolean, onFilesPicked: (List<PlatformFile>) -> Unit) {
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
                    // Create blob URL for in-memory PDF page viewing
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
    
    CanvasBasedWindow(title = "Office PDF", canvasElementId = "compose-canvas") {
        OfficePdfTheme {
            MainAppScreen(
                settingsStorage = settingsStorage,
                pdfEngine = pdfEngine,
                fileOperations = fileOperations,
                filePicker = filePicker
            )
        }
    }
}
