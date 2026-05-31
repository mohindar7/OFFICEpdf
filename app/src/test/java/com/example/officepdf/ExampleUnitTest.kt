package com.example.officepdf

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun generateTestPdf() {
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        val page = com.tom_roush.pdfbox.pdmodel.PDPage()
        doc.addPage(page)
        val file = java.io.File("test_sample.pdf")
        doc.save(file)
        doc.close()
        println("Generated test PDF at: ${file.absolutePath}")
    }
}