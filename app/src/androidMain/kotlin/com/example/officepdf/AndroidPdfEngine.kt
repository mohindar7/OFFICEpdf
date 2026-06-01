package com.example.officepdf

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPdfEngine(private val context: Context) : PdfEngine {

    private suspend fun getLocalFile(platformFile: PlatformFile): File = withContext(Dispatchers.IO) {
        val f = File(platformFile.path)
        if (f.exists()) return@withContext f
        
        // Write bytes to temp file if path doesn't exist
        val temp = File(context.cacheDir, "temp_${System.currentTimeMillis()}_${platformFile.name}")
        temp.writeBytes(platformFile.readBytes())
        temp
    }

    override suspend fun mergePdfs(files: List<PlatformFile>): ByteArray = withContext(Dispatchers.IO) {
        val localFiles = files.map { getLocalFile(it) }
        val out = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
        PdfOperations.mergePdfs(localFiles, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun splitPdf(file: PlatformFile, ranges: String): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "split_${System.currentTimeMillis()}.pdf")
        PdfOperations.splitPdf(localFile, ranges, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun addWatermark(file: PlatformFile, watermarkText: String): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "watermarked_${System.currentTimeMillis()}.pdf")
        PdfOperations.addWatermark(localFile, watermarkText, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun rotatePdf(file: PlatformFile, rotationDegrees: Int): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "rotated_${System.currentTimeMillis()}.pdf")
        PdfOperations.rotatePdf(localFile, rotationDegrees, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun protectPdf(file: PlatformFile, password: String): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "protected_${System.currentTimeMillis()}.pdf")
        PdfOperations.protectPdf(localFile, password, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun unlockPdf(file: PlatformFile, password: String): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
        PdfOperations.unlockPdf(localFile, password, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun organizePdf(file: PlatformFile, keptPageIndices: List<Int>): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "organized_${System.currentTimeMillis()}.pdf")
        PdfOperations.organizePdf(localFile, keptPageIndices, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun addPageNumbers(file: PlatformFile): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "numbered_${System.currentTimeMillis()}.pdf")
        PdfOperations.addPageNumbers(localFile, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun cropPdf(
        file: PlatformFile,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ): ByteArray = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val out = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.pdf")
        PdfOperations.cropPdf(localFile, left, right, top, bottom, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun imagesToPdf(imageFiles: List<PlatformFile>): ByteArray = withContext(Dispatchers.IO) {
        val localFiles = imageFiles.map { getLocalFile(it) }
        val out = File(context.cacheDir, "images_${System.currentTimeMillis()}.pdf")
        PdfOperations.imagesToPdf(localFiles, out)
        val bytes = out.readBytes()
        out.delete()
        return@withContext bytes
    }

    override suspend fun processPdfWorkspace(
        inputFile: PlatformFile,
        keptPageIndices: List<Int>,
        pageRotations: Map<Int, Int>,
        pageCrops: Map<Int, List<Float>>,
        overlays: List<PageOverlay>,
        watermarkText: String?,
        hasPageNumbers: Boolean
    ): ByteArray = withContext(Dispatchers.IO) {
        val localInput = getLocalFile(inputFile)
        val out = File(context.cacheDir, "workspace_${System.currentTimeMillis()}.pdf")
        
        // Map overlays, saving any image bytes to temp paths if imagePath is empty
        val tempOverlayFiles = mutableListOf<File>()
        val mappedOverlays = overlays.map { overlay ->
            var finalImagePath = overlay.imagePath
            if (overlay.type == "image" && finalImagePath.isEmpty() && overlay.imageBytes != null) {
                val tempImg = File(context.cacheDir, "overlay_${System.currentTimeMillis()}_${overlay.id}.png")
                tempImg.writeBytes(overlay.imageBytes)
                tempOverlayFiles.add(tempImg)
                finalImagePath = tempImg.absolutePath
            }
            
            PdfOperations.PageOverlay(
                id = overlay.id,
                type = overlay.type,
                text = overlay.text,
                imagePath = finalImagePath,
                xFraction = overlay.xFraction,
                yFraction = overlay.yFraction,
                widthFraction = overlay.widthFraction,
                heightFraction = overlay.heightFraction,
                pageNumber = overlay.pageNumber,
                rotation = overlay.rotation
            )
        }
        
        PdfOperations.processPdfWorkspace(
            inputFile = localInput,
            keptPageIndices = keptPageIndices,
            pageRotations = pageRotations,
            pageCrops = pageCrops,
            overlays = mappedOverlays,
            watermarkText = watermarkText,
            hasPageNumbers = hasPageNumbers,
            outputFile = out
        )
        
        val bytes = out.readBytes()
        
        // Clean up temp files
        out.delete()
        for (f in tempOverlayFiles) {
            f.delete()
        }
        
        return@withContext bytes
    }

    override suspend fun renderPdfPages(file: PlatformFile): List<ByteArray> = withContext(Dispatchers.IO) {
        val localFile = getLocalFile(file)
        val count = PdfRendererHelper.getPageCount(localFile)
        val list = mutableListOf<ByteArray>()
        for (i in 0 until count) {
            val bitmap = PdfRendererHelper.renderPageToBitmap(context, localFile, i) ?: continue
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            list.add(stream.toByteArray())
            bitmap.recycle()
        }
        return@withContext list
    }

    override suspend fun isProtected(file: PlatformFile): Boolean = withContext(Dispatchers.IO) {
        var isLocked = false
        try {
            val localFile = getLocalFile(file)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(localFile).use { _ -> }
        } catch (e: Exception) {
            isLocked = true
        }
        isLocked
    }
}
