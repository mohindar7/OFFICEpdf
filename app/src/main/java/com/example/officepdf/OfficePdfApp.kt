package com.example.officepdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class OfficePdfApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize PDFBox resource loader
        PDFBoxResourceLoader.init(applicationContext)
    }
}
