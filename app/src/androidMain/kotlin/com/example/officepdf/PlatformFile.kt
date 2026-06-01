package com.example.officepdf

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

class AndroidPlatformFile(val file: File) : PlatformFile {
    override val name: String get() = file.name
    override val size: Long get() = file.length()
    override val path: String get() = file.absolutePath
    override suspend fun readBytes(): ByteArray = file.readBytes()
}

actual fun createPlatformFile(path: String): PlatformFile {
    return AndroidPlatformFile(File(path))
}

@Composable
actual fun rememberPlatformFilePicker(): FilePicker {
    val context = LocalContext.current
    var currentCallback by remember { mutableStateOf<((List<PlatformFile>) -> Unit)?>(null) }
    
    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.mapNotNull { uri ->
                val tempFile = PdfRendererHelper.copyUriToTempFile(context, uri, "input_${System.currentTimeMillis()}.pdf")
                tempFile?.let { AndroidPlatformFile(it) }
            }
            currentCallback?.invoke(files)
        }
    }
    
    val imgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.mapNotNull { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, "img_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream?.copyTo(outputStream)
                    }
                    AndroidPlatformFile(tempFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            currentCallback?.invoke(files)
        }
    }
    
    return remember {
        object : FilePicker {
            override fun pickFiles(
                allowedTypes: List<String>,
                multiple: Boolean,
                onFilesPicked: (List<PlatformFile>) -> Unit
            ) {
                currentCallback = onFilesPicked
                if (allowedTypes.contains("jpg") || allowedTypes.contains("jpeg") || allowedTypes.contains("png")) {
                    imgLauncher.launch("image/*")
                } else {
                    docLauncher.launch(arrayOf("application/pdf"))
                }
            }
        }
    }
}

actual fun getCurrentHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun ByteArray.toImageBitmap(): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(this, 0, size)
    return bitmap.asImageBitmap()
}


