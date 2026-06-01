package com.example.officepdf

data class PageOverlay(
    val id: String,
    val type: String, // "text", "image", "signature"
    val text: String = "",
    val imagePath: String = "",
    val imageBytes: ByteArray? = null,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val pageNumber: Int, // 1-indexed
    val rotation: Float = 0f
)

interface PdfEngine {
    suspend fun mergePdfs(files: List<PlatformFile>): ByteArray
    suspend fun splitPdf(file: PlatformFile, ranges: String): ByteArray
    suspend fun addWatermark(file: PlatformFile, watermarkText: String): ByteArray
    suspend fun rotatePdf(file: PlatformFile, rotationDegrees: Int): ByteArray
    suspend fun protectPdf(file: PlatformFile, password: String): ByteArray
    suspend fun unlockPdf(file: PlatformFile, password: String): ByteArray
    suspend fun organizePdf(file: PlatformFile, keptPageIndices: List<Int>): ByteArray
    suspend fun addPageNumbers(file: PlatformFile): ByteArray
    suspend fun cropPdf(file: PlatformFile, left: Float, right: Float, top: Float, bottom: Float): ByteArray
    suspend fun imagesToPdf(imageFiles: List<PlatformFile>): ByteArray
    suspend fun processPdfWorkspace(
        inputFile: PlatformFile,
        keptPageIndices: List<Int>,
        pageRotations: Map<Int, Int>,
        pageCrops: Map<Int, List<Float>>,
        overlays: List<PageOverlay>,
        watermarkText: String?,
        hasPageNumbers: Boolean
    ): ByteArray
    
    // Renders pages of a PDF to ImageBitmaps for previewing.
    // Returns list of byte arrays (images) or a platform-specific preview.
    suspend fun renderPdfPages(file: PlatformFile): List<ByteArray>
    
    suspend fun isProtected(file: PlatformFile): Boolean
}
