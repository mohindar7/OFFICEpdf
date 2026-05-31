package com.example.officepdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileInputStream

object PdfOperations {

    /**
     * Combines multiple PDF files into one output PDF.
     */
    fun mergePdfs(files: List<File>, output: File) {
        PDDocument().use { mergedDoc ->
            for (file in files) {
                PDDocument.load(file).use { doc ->
                    for (i in 0 until doc.numberOfPages) {
                        mergedDoc.addPage(doc.getPage(i))
                    }
                }
            }
            mergedDoc.save(output)
        }
    }

    /**
     * Splits pages out of a PDF based on standard range queries (e.g. "1, 3-5, 8")
     */
    fun splitPdf(file: File, ranges: String, output: File) {
        PDDocument.load(file).use { originalDoc ->
            val totalPages = originalDoc.numberOfPages
            val targetIndices = parseRanges(ranges, totalPages)
            if (targetIndices.isEmpty()) {
                throw Exception("No valid pages selected by range: $ranges")
            }

            PDDocument().use { newDoc ->
                for (idx in targetIndices) {
                    newDoc.addPage(originalDoc.getPage(idx))
                }
                newDoc.save(output)
            }
        }
    }

    /**
     * Adds a semi-transparent text watermark running diagonally.
     */
    fun addWatermark(file: File, watermarkText: String, output: File) {
        PDDocument.load(file).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val mediaBox = page.mediaBox
                val width = mediaBox.width
                val height = mediaBox.height

                // Open content stream in Append mode
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                    contentStream.saveGraphicsState()
                    
                    // Set 30% alpha opacity transparency parameters
                    val extGState = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = 0.3f
                    }
                    contentStream.setGraphicsStateParameters(extGState)

                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA_BOLD, 45f)
                    contentStream.setNonStrokingColor(220, 50, 50) // Tonal dark red color

                    // Center rotation matrix matrix
                    val radians = Math.toRadians(45.0)
                    val x = width / 2f - 180f
                    val y = height / 2f - 30f
                    contentStream.setTextMatrix(Matrix.getRotateInstance(radians, x, y))
                    contentStream.showText(watermarkText)
                    contentStream.endText()
                    
                    contentStream.restoreGraphicsState()
                }
            }
            doc.save(output)
        }
    }

    /**
     * Rotates all pages in a PDF clockwise by increments of 90 degrees.
     */
    fun rotatePdf(file: File, rotationDegrees: Int, output: File) {
        PDDocument.load(file).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val currentRotation = page.rotation
                page.rotation = (currentRotation + rotationDegrees) % 360
            }
            doc.save(output)
        }
    }

    /**
     * Encrypts a PDF file using a password.
     */
    fun protectPdf(file: File, password: String, output: File) {
        PDDocument.load(file).use { doc ->
            val ap = AccessPermission()
            val policy = StandardProtectionPolicy(password, password, ap).apply {
                encryptionKeyLength = 128
            }
            doc.protect(policy)
            doc.save(output)
        }
    }

    /**
     * Unlocks/decrypts a password protected PDF.
     */
    fun unlockPdf(file: File, password: String, output: File) {
        PDDocument.load(file, password).use { doc ->
            // Clear password protection by saving standard document directly
            doc.save(output)
        }
    }

    /**
     * Visual organizer. Saves only specified page numbers.
     */
    fun organizePdf(file: File, keptPageIndices: List<Int>, output: File) {
        PDDocument.load(file).use { originalDoc ->
            PDDocument().use { newDoc ->
                for (idx in keptPageIndices) {
                    if (idx in 0 until originalDoc.numberOfPages) {
                        newDoc.addPage(originalDoc.getPage(idx))
                    }
                }
                newDoc.save(output)
            }
        }
    }

    /**
     * Stamps sequential page numbers at bottom center of all pages.
     */
    fun addPageNumbers(file: File, output: File) {
        PDDocument.load(file).use { doc ->
            val totalPages = doc.numberOfPages
            for (i in 0 until totalPages) {
                val page = doc.getPage(i)
                val mediaBox = page.mediaBox
                val width = mediaBox.width
                
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA, 12f)
                    contentStream.setNonStrokingColor(100, 100, 100) // Secondary gray color
                    
                    val text = "${i + 1} / $totalPages"
                    val textWidth = 30f // Estimated
                    val x = width / 2f - textWidth
                    val y = 25f
                    
                    contentStream.newLineAtOffset(x, y)
                    contentStream.showText(text)
                    contentStream.endText()
                }
            }
            doc.save(output)
        }
    }

    /**
     * Crops margins of PDF pages.
     */
    fun cropPdf(file: File, left: Float, right: Float, top: Float, bottom: Float, output: File) {
        PDDocument.load(file).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val mediaBox = page.mediaBox
                val newCropBox = PDRectangle(
                    mediaBox.lowerLeftX + left,
                    mediaBox.lowerLeftY + bottom,
                    mediaBox.width - left - right,
                    mediaBox.height - top - bottom
                )
                page.cropBox = newCropBox
            }
            doc.save(output)
        }
    }

    /**
     * Converts a list of image files into a single consolidated PDF document.
     */
    fun imagesToPdf(imageFiles: List<File>, output: File) {
        PDDocument().use { doc ->
            for (imageFile in imageFiles) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: continue
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                doc.addPage(page)
                
                val pdImage = LosslessFactory.createFromImage(doc, bitmap)
                PDPageContentStream(doc, page).use { contentStream ->
                    contentStream.drawImage(pdImage, 0f, 0f)
                }
                bitmap.recycle() // Clean up native memory
            }
            doc.save(output)
        }
    }

    /**
     * Adds static text overlay to the first page of the document.
     */
    fun addTextToFirstPage(file: File, text: String, output: File) {
        PDDocument.load(file).use { doc ->
            if (doc.numberOfPages > 0) {
                val page = doc.getPage(0)
                val mediaBox = page.mediaBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA_BOLD, 20f)
                    contentStream.setNonStrokingColor(244, 67, 54) // Material red
                    
                    // Position at top margin
                    contentStream.newLineAtOffset(50f, mediaBox.height - 50f)
                    contentStream.showText(text)
                    contentStream.endText()
                }
            }
            doc.save(output)
        }
    }

    /**
     * Placements are defined in relative fractions (0.0 to 1.0) of the preview container.
     * We map these fractions to the actual PDF page coordinates.
     */
    data class SignaturePlacement(
        val text: String,
        val xFraction: Float,
        val yFraction: Float,
        val widthFraction: Float,
        val heightFraction: Float,
        val pageNumber: Int // 1-indexed
    )

    fun applySignatures(file: File, placements: List<SignaturePlacement>, output: File) {
        PDDocument.load(file).use { doc ->
            for (placement in placements) {
                val pageIndex = placement.pageNumber - 1
                if (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val mediaBox = page.mediaBox
                    val pdfWidth = mediaBox.width
                    val pdfHeight = mediaBox.height

                    // Convert visual coordinate fractions to PDF coordinates
                    // PDF coordinates start at bottom-left corner!
                    val pdfX = placement.xFraction * pdfWidth
                    val pdfY = pdfHeight - (placement.yFraction * pdfHeight) - (placement.heightFraction * pdfHeight)

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12f)
                        contentStream.setNonStrokingColor(10, 10, 100) // Deep corporate blue

                        // Draw inside the placement boundary box with a 4pt padding offset
                        contentStream.newLineAtOffset(pdfX + 4f, pdfY + 4f)
                        contentStream.showText(placement.text)
                        contentStream.endText()
                    }
                }
            }
            doc.save(output)
        }
    }

    /**
     * Helper to parse range strings: "1, 3-5, 8" -> [0, 2, 3, 4, 7] (0-indexed indices)
     */
    private fun parseRanges(rangeQuery: String, totalPages: Int): List<Int> {
        val result = mutableSetOf<Int>()
        val segments = rangeQuery.split(",")
        for (segment in segments) {
            val clean = segment.trim()
            if (clean.isEmpty()) continue
            try {
                if (clean.contains("-")) {
                    val bounds = clean.split("-")
                    if (bounds.size == 2) {
                        val start = bounds[0].trim().toInt()
                        val end = bounds[1].trim().toInt()
                        for (i in start..end) {
                            if (i in 1..totalPages) {
                                result.add(i - 1)
                            }
                        }
                    }
                } else {
                    val pageNum = clean.toInt()
                    if (pageNum in 1..totalPages) {
                        result.add(pageNum - 1)
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing exceptions
            }
        }
        return result.sorted()
    }

    data class PageOverlay(
        val id: String = java.util.UUID.randomUUID().toString(),
        val type: String, // "text" or "image"
        val text: String = "",
        val imagePath: String = "",
        val xFraction: Float,
        val yFraction: Float,
        val widthFraction: Float,
        val heightFraction: Float,
        val pageNumber: Int // 1-indexed
    )

    fun applyPageOverlays(file: File, overlays: List<PageOverlay>, output: File) {
        PDDocument.load(file).use { doc ->
            for (overlay in overlays) {
                val pageIndex = overlay.pageNumber - 1
                if (pageIndex in 0 until doc.numberOfPages) {
                    val page = doc.getPage(pageIndex)
                    val mediaBox = page.mediaBox
                    val pdfWidth = mediaBox.width
                    val pdfHeight = mediaBox.height

                    // Convert visual coordinate fractions to PDF coordinates
                    // PDF coordinates start at bottom-left corner!
                    val pdfX = overlay.xFraction * pdfWidth
                    val pdfY = pdfHeight - (overlay.yFraction * pdfHeight) - (overlay.heightFraction * pdfHeight)
                    val drawWidth = overlay.widthFraction * pdfWidth
                    val drawHeight = overlay.heightFraction * pdfHeight

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                        if (overlay.type == "text") {
                            contentStream.beginText()
                            // Estimate appropriate font size based on height
                            val fontSize = (drawHeight * 0.6f).coerceIn(8f, 72f)
                            contentStream.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                            contentStream.setNonStrokingColor(0, 0, 0) // Default black text

                            // Position inside bounds with minor vertical padding
                            contentStream.newLineAtOffset(pdfX, pdfY + (drawHeight * 0.15f))
                            contentStream.showText(overlay.text)
                            contentStream.endText()
                        } else if (overlay.type == "image") {
                            val imgFile = File(overlay.imagePath)
                            if (imgFile.exists()) {
                                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                                if (bitmap != null) {
                                    val pdImage = LosslessFactory.createFromImage(doc, bitmap)
                                    contentStream.drawImage(pdImage, pdfX, pdfY, drawWidth, drawHeight)
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            }
            doc.save(output)
        }
    }
}
