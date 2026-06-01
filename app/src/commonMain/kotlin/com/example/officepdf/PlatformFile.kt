package com.example.officepdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

interface PlatformFile {
    val name: String
    val size: Long
    val path: String
    suspend fun readBytes(): ByteArray
}

interface FileOperations {
    fun saveFile(name: String, bytes: ByteArray, mimeType: String)
    fun shareFile(path: String)
    fun viewFile(path: String)
}

interface FilePicker {
    fun pickFiles(
        allowedTypes: List<String>,
        multiple: Boolean,
        onFilesPicked: (List<PlatformFile>) -> Unit
    )
}

@Composable
expect fun rememberPlatformFilePicker(): FilePicker

expect fun createPlatformFile(path: String): PlatformFile

expect fun getCurrentHour(): Int
expect fun getCurrentTimeMillis(): Long

expect fun ByteArray.toImageBitmap(): ImageBitmap

class InMemoryPlatformFile(
    override val name: String,
    override val size: Long,
    override val path: String,
    private val bytes: ByteArray
) : PlatformFile {
    override suspend fun readBytes(): ByteArray = bytes
}


