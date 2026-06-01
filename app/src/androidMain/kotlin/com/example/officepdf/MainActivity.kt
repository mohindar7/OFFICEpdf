package com.example.officepdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Load Gemini API Key
        val sharedPrefs = getSharedPreferences("OfficePrefs", Context.MODE_PRIVATE)
        val savedApiKey = sharedPrefs.getString("gemini_api_key", "") ?: ""

        val pdfEngine = AndroidPdfEngine(this)
        
        val fileOperations = object : FileOperations {
            override fun saveFile(name: String, bytes: ByteArray, mimeType: String) {
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val destFile = File(downloadDir, name)
                    FileOutputStream(destFile).use { it.write(bytes) }
                    Toast.makeText(this@MainActivity, "Exported to Downloads successfully!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error exporting file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
            override fun shareFile(path: String) {
                try {
                    val file = File(path)
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Share PDF Document"))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun viewFile(path: String) {
                try {
                    val file = File(path)
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "No app found to open PDF files", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val toastMessenger = object : ToastMessenger {
            override fun showToast(message: String) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        val settingsStorage = object : SettingsStorage {
            override fun getString(key: String, defaultValue: String): String {
                return sharedPrefs.getString(key, defaultValue) ?: defaultValue
            }
            override fun putString(key: String, value: String) {
                sharedPrefs.edit().putString(key, value).apply()
            }
        }

        setContent {
            val filePicker = rememberPlatformFilePicker()
            OfficePdfTheme {
                MainAppScreen(
                    settingsStorage = settingsStorage,
                    pdfEngine = pdfEngine,
                    fileOperations = fileOperations,
                    filePicker = filePicker,
                    toastMessenger = toastMessenger
                )
            }
        }
    }
}
