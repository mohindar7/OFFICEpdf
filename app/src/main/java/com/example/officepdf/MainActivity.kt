package com.example.officepdf

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.res.painterResource
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Load Gemini API Key
        val sharedPrefs = getSharedPreferences("OfficePrefs", Context.MODE_PRIVATE)
        val savedApiKey = sharedPrefs.getString("gemini_api_key", "") ?: ""

        setContent {
            OfficePdfTheme {
                MainAppScreen(
                    initialApiKey = savedApiKey,
                    onSaveApiKey = { newKey ->
                        sharedPrefs.edit().putString("gemini_api_key", newKey).apply()
                    }
                )
            }
        }
    }
}

// Data class representing each PDF tool
data class Tool(
    val name: String,
    val description: String,
    val type: ToolType,
    val icon: ImageVector,
    val color: Color
)

enum class ToolType {
    MERGE, SPLIT, WATERMARK, ROTATE, PDF_TO_JPG, JPG_TO_PDF,
    PROTECT, UNLOCK, ORGANIZE, PAGE_NUMBERS, OCR, COMPARE, EDIT, CROP, SIGN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    initialApiKey: String,
    onSaveApiKey: (String) -> Unit
) {
    val context = LocalContext.current
    var activeTool by remember { mutableStateOf<Tool?>(null) }
    var geminiApiKey by remember { mutableStateOf(initialApiKey) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var topBarActionLabel by remember { mutableStateOf<String?>(null) }
    var topBarActionEnabled by remember { mutableStateOf(true) }
    var onTopBarActionClick by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(activeTool) {
        if (activeTool == null) {
            topBarActionLabel = null
            onTopBarActionClick = null
        }
    }

    if (activeTool != null) {
        BackHandler {
            activeTool = null
        }
    }

    val tools = remember {
        listOf(
            Tool("Merge PDF", "Combine multiple PDFs into one", ToolType.MERGE, Icons.Rounded.Merge, Color(0xFFE3F2FD)),
            Tool("Split PDF", "Extract specific pages from a PDF", ToolType.SPLIT, Icons.Rounded.CallSplit, Color(0xFFF1F8E9)),
            Tool("Watermark PDF", "Stamp text overlay over PDF pages", ToolType.WATERMARK, Icons.Rounded.WaterDrop, Color(0xFFFFF3E0)),
            Tool("Rotate PDF", "Rotate pages of a PDF document", ToolType.ROTATE, Icons.Rounded.RotateRight, Color(0xFFEDE7F6)),
            Tool("PDF to JPG", "Convert PDF pages into JPG images", ToolType.PDF_TO_JPG, Icons.Rounded.Image, Color(0xFFFCE4EC)),
            Tool("JPG to PDF", "Convert multiple images into a PDF", ToolType.JPG_TO_PDF, Icons.Rounded.PictureAsPdf, Color(0xFFE8F5E9)),
            Tool("Protect PDF", "Encrypt a PDF with a password", ToolType.PROTECT, Icons.Rounded.Lock, Color(0xFFFFEBEE)),
            Tool("Unlock PDF", "Remove password protection from PDF", ToolType.UNLOCK, Icons.Rounded.LockOpen, Color(0xFFE0F7FA)),
            Tool("Organize PDF", "Delete or reorder PDF document pages", ToolType.ORGANIZE, Icons.Rounded.ViewModule, Color(0xFFFFFDE7)),
            Tool("Page Numbers", "Stamp page numbers on the document", ToolType.PAGE_NUMBERS, Icons.Rounded.FormatListNumbered, Color(0xFFF3E5F5)),
            Tool("OCR PDF", "Extract text using Gemini AI", ToolType.OCR, Icons.Rounded.DocumentScanner, Color(0xFFE0F2F1)),
            Tool("Compare PDF", "Visually compare draft document pages", ToolType.COMPARE, Icons.Rounded.Compare, Color(0xFFFFF8E1)),
            Tool("Edit PDF", "Stamp text annotations on first page", ToolType.EDIT, Icons.Rounded.Edit, Color(0xFFE8EAF6)),
            Tool("Crop PDF", "Crop visible boundaries of PDF pages", ToolType.CROP, Icons.Rounded.Crop, Color(0xFFF9FBF7)),
            Tool("Sign PDF", "Visually drag and drop signature text", ToolType.SIGN, Icons.Rounded.Gesture, Color(0xFFECEFF1))
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                        Text(
                            text = "office pdf",
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 24.sp
                        )
                    }
                },
                navigationIcon = {
                    if (activeTool != null) {
                        IconButton(
                            onClick = { activeTool = null },
                            modifier = Modifier.bounceClick { activeTool = null }
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (topBarActionLabel != null && onTopBarActionClick != null) {
                        ExpressiveActionButton(
                            onClick = { onTopBarActionClick?.invoke() },
                            label = topBarActionLabel!!,
                            enabled = topBarActionEnabled,
                            icon = Icons.Rounded.Save
                        )
                    } else {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.bounceClick { showSettingsDialog = true }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = activeTool,
                transitionSpec = {
                    slideInHorizontally { width -> if (targetState != null) width else -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> if (targetState != null) -width else width } + fadeOut()
                },
                label = "Navigation"
            ) { tool ->
                if (tool == null) {
                    DashboardGrid(tools = tools, onToolClick = { activeTool = it })
                } else {
                    ToolWorkspace(
                        tool = tool,
                        geminiApiKey = geminiApiKey,
                        onRegisterEditExecute = { label, isProcessing, onExecute ->
                            topBarActionLabel = label
                            topBarActionEnabled = !isProcessing
                            onTopBarActionClick = onExecute
                        },
                        onUnregisterEditExecute = {
                            topBarActionLabel = null
                            onTopBarActionClick = null
                        }
                    )
                }
            }

            if (showSettingsDialog) {
                SettingsDialog(
                    currentKey = geminiApiKey,
                    onDismiss = { showSettingsDialog = false },
                    onSave = {
                        geminiApiKey = it
                        onSaveApiKey(it)
                        showSettingsDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardGrid(
    tools: List<Tool>,
    onToolClick: (Tool) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .bounceClick { },
            shape = ShapeCache.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = ShapeCache.container
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column {
                    Text(
                        text = "Office PDF Workspace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Files are processed 100% client-side. Completely private and secure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(tools) { _, tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.1f)
                        .bounceClick { onToolClick(tool) },
                    shape = ShapeCache.card, // M3 Expressive card corners
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Custom squircle icon container
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(tool.color, ShapeCache.icon),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tool.icon,
                                contentDescription = tool.name,
                                tint = Color(0xFF1E202C),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Column {
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

// Tool workspace view container
@Composable
fun ToolWorkspace(
    tool: Tool,
    geminiApiKey: String,
    onRegisterEditExecute: (String, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
    onUnregisterEditExecute: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<Pair<String, LogType>>>(listOf()) }
    
    // Pickers states
    var selectedFiles by remember { mutableStateOf<List<File>>(listOf()) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Parameters states
    var textInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var rotationValue by remember { mutableStateOf(90) }
    var marginsInput by remember { mutableStateOf(listOf(50f, 50f, 50f, 50f)) } // left, right, top, bottom
    var splitsInput by remember { mutableStateOf("1, 2-3") }
    var extractedOcrText by remember { mutableStateOf("") }
    var compareDiffResult by remember { mutableStateOf<Bitmap?>(null) }

    // Page thumbnails for Organize tool
    var pdfPagesCount by remember { mutableStateOf(0) }
    var pdfPagesBitmaps by remember { mutableStateOf<List<Bitmap>>(listOf()) }
    var selectedPageIndices by remember { mutableStateOf<List<Int>>(listOf()) }

    // Drag-and-drop Signature Canvas state
    var signaturePlacements by remember { mutableStateOf<List<PdfOperations.SignaturePlacement>>(listOf()) }
    var signatureMode by remember { mutableStateOf("signature") } // signature, initials, date
    var sigText by remember { mutableStateOf("John Doe") }
    var initText by remember { mutableStateOf("JD") }
    var currentPreviewPage by remember { mutableStateOf(1) }
    var previewPageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Rich Page Editor Canvas state (Text & Image overlays)
    var pageOverlays by remember { mutableStateOf<List<PdfOperations.PageOverlay>>(listOf()) }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    fun addLog(msg: String, type: LogType = LogType.INFO) {
        logs = logs + Pair(msg, type)
    }

    fun clearWorkspace() {
        selectedFiles = listOf()
        resultFile = null
        compareDiffResult = null
        extractedOcrText = ""
        pdfPagesBitmaps = listOf()
        pdfPagesCount = 0
        signaturePlacements = listOf()
        pageOverlays = listOf()
        selectedOverlayId = null
        previewPageBitmap = null
        logs = listOf()
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    // Launch pickers
    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                addLog("Processing selected file(s)...")
                val files = uris.mapNotNull { uri ->
                    val temp = PdfRendererHelper.copyUriToTempFile(context, uri, "input_${System.currentTimeMillis()}.pdf")
                    if (temp == null) {
                        addLog("Error copying PDF file.", LogType.ERROR)
                    }
                    temp
                }
                selectedFiles = files
                addLog("Loaded ${files.size} document(s) successfully.")

                // Pre-calculate page structures if Organize or Sign/Edit tools selected
                if (tool.type == ToolType.ORGANIZE && files.isNotEmpty()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val count = PdfRendererHelper.getPageCount(files[0])
                        pdfPagesCount = count
                        val list = mutableListOf<Bitmap>()
                        for (i in 0 until count) {
                            PdfRendererHelper.renderPageToBitmap(context, files[0], i, 0.4f)?.let { list.add(it) }
                        }
                        withContext(Dispatchers.Main) {
                            pdfPagesBitmaps = list
                            selectedPageIndices = (0 until count).toList()
                            addLog("Rendered $count page thumbnails for organization.")
                        }
                    }
                }
                
                if ((tool.type == ToolType.SIGN || tool.type == ToolType.EDIT) && files.isNotEmpty()) {
                    pdfPagesCount = PdfRendererHelper.getPageCount(files[0])
                    coroutineScope.launch(Dispatchers.IO) {
                        val firstPageBmp = PdfRendererHelper.renderPageToBitmap(context, files[0], 0, 1.5f)
                        withContext(Dispatchers.Main) {
                            previewPageBitmap = firstPageBmp
                            currentPreviewPage = 1
                            addLog("${tool.name} Canvas editor ready.")
                        }
                    }
                }
            }
        }
    )

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                addLog("Processing selected image(s)...")
                val files = uris.mapNotNull { uri ->
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val temp = File(context.cacheDir, "img_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(temp).use { outputStream ->
                        inputStream?.copyTo(outputStream)
                    }
                    temp
                }
                selectedFiles = files
                addLog("Loaded ${files.size} image(s) successfully.")
            }
        }
    )

    // Image Picker for custom overlay additions in Edit tool
    val overlayImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val tempFile = File(context.cacheDir, "overlay_img_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri).use { input ->
                            FileOutputStream(tempFile).use { outputStream ->
                                input?.copyTo(outputStream)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            val newOverlay = PdfOperations.PageOverlay(
                                type = "image",
                                imagePath = tempFile.absolutePath,
                                xFraction = 0.1f,
                                yFraction = 0.1f,
                                widthFraction = 0.3f,
                                heightFraction = 0.2f,
                                pageNumber = currentPreviewPage
                            )
                            pageOverlays = pageOverlays + newOverlay
                            selectedOverlayId = newOverlay.id
                            addLog("Imported custom overlay image.")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to load overlay image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    fun saveToDownloads(file: File, finalName: String) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadDir, finalName)
            FileInputStream(file).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            addLog("Saved to Downloads: $finalName", LogType.SUCCESS)
            Toast.makeText(context, "Exported successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            addLog("Failed to export: ${e.message}", LogType.ERROR)
        }
    }

    LaunchedEffect(selectedFiles, isProcessing, pageOverlays) {
        if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty()) {
            onRegisterEditExecute("Save", isProcessing) {
                if (isProcessing) return@onRegisterEditExecute
                coroutineScope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        isProcessing = true
                        resultFile = null
                        addLog("Executing operation...", LogType.INFO)
                    }
                    try {
                        val outDir = context.cacheDir
                        val outFile = File(outDir, "output_${System.currentTimeMillis()}.pdf")
                        PdfOperations.applyPageOverlays(selectedFiles[0], pageOverlays, outFile)
                        withContext(Dispatchers.Main) {
                            resultFile = outFile
                            addLog("Page overlays applied successfully.", LogType.SUCCESS)
                            saveToDownloads(outFile, "edited_output.pdf")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            addLog("Execution failed: ${e.message}", LogType.ERROR)
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                        }
                    }
                }
            }
        } else {
            onUnregisterEditExecute()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onUnregisterEditExecute()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState(), enabled = selectedOverlayId == null && zoomScale == 1f)
                .padding(
                    start = if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty()) 0.dp else 16.dp,
                    end = if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty()) 0.dp else 16.dp,
                    top = if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty()) 0.dp else 16.dp,
                    bottom = 16.dp
                )
        ) {
        // Immersive tool title (hidden in edit mode with loaded file to maximize space)
        if (tool.type != ToolType.EDIT || selectedFiles.isEmpty()) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Document Upload Picker Area
        if (selectedFiles.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .bounceClick {
                        clearWorkspace()
                        if (tool.type == ToolType.JPG_TO_PDF) {
                            imagePicker.launch("image/*")
                        } else {
                            docPicker.launch(arrayOf("application/pdf"))
                        }
                    },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = "Upload",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (tool.type == ToolType.JPG_TO_PDF) "Select images from gallery" else "Select PDF files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (tool.type == ToolType.MERGE || tool.type == ToolType.JPG_TO_PDF) "Supports selecting multiple files" else "Single file only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (tool.type != ToolType.EDIT) {
            // Selected files list
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected Inputs (${selectedFiles.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { clearWorkspace() }) {
                            Text("Clear")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    selectedFiles.forEachIndexed { idx, file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (tool.type == ToolType.JPG_TO_PDF) Icons.Rounded.Image else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Configuration Parameters Panel
        if (selectedFiles.isNotEmpty()) {
            if (tool.type == ToolType.EDIT) {
                // VISUAL PAGE EDITOR (un-nested, direct layout)
                
                // Visual Page Editor States
                var showTextEditDialog by remember { mutableStateOf(false) }
                var editingTextId by remember { mutableStateOf<String?>(null) }
                var editingTextValue by remember { mutableStateOf("") }

                if (showTextEditDialog) {
                    AlertDialog(
                        onDismissRequest = { showTextEditDialog = false },
                        title = { Text("Edit Text Overlay") },
                        text = {
                            TextField(
                                value = editingTextValue,
                                onValueChange = { editingTextValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    pageOverlays = pageOverlays.map {
                                        if (it.id == editingTextId) it.copy(text = editingTextValue) else it
                                    }
                                    showTextEditDialog = false
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTextEditDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Desk Frame containing Canvas & Floating Zoom HUD (full screen optimizingly, no Card)
                    previewPageBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                                // PDF Page Canvas container with clipToBounds
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                        .clipToBounds()
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                ) {
                                    val canvasWidth = constraints.maxWidth.toFloat()
                                    val canvasHeight = constraints.maxHeight.toFloat()

                                    // Zoomable / Pannable Inner Canvas
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = zoomScale
                                                scaleY = zoomScale
                                                translationX = panOffset.x
                                                translationY = panOffset.y
                                            }
                                            .pointerInput(Unit) {
                                                detectTransformGestures { _, pan, zoom, _ ->
                                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                                    val maxPanX = (zoomScale - 1f) * size.width / 2f
                                                    val maxPanY = (zoomScale - 1f) * size.height / 2f
                                                    panOffset = Offset(
                                                        x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                                        y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                                    )
                                                }
                                            }
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { selectedOverlayId = null },
                                                    onDoubleTap = { tapOffset ->
                                                        if (zoomScale > 1f) {
                                                            zoomScale = 1f
                                                            panOffset = Offset.Zero
                                                        } else {
                                                            zoomScale = 2f
                                                            val centerX = canvasWidth / 2f
                                                            val centerY = canvasHeight / 2f
                                                            val targetPanX = (centerX - tapOffset.x) * 1f
                                                            val targetPanY = (centerY - tapOffset.y) * 1f
                                                            val maxPanX = (2f - 1f) * canvasWidth / 2f
                                                            val maxPanY = (2f - 1f) * canvasHeight / 2f
                                                            panOffset = Offset(
                                                                targetPanX.coerceIn(-maxPanX, maxPanX),
                                                                targetPanY.coerceIn(-maxPanY, maxPanY)
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Render page overlays
                                        pageOverlays.filter { it.pageNumber == currentPreviewPage }.forEach { overlay ->
                                            val isSelected = selectedOverlayId == overlay.id

                                            val visualX = overlay.xFraction * canvasWidth
                                            val visualY = overlay.yFraction * canvasHeight
                                            val visualWidth = overlay.widthFraction * canvasWidth
                                            val visualHeight = overlay.heightFraction * canvasHeight

                                            Box(
                                                modifier = Modifier
                                                    .offset { IntOffset(visualX.roundToInt(), visualY.roundToInt()) }
                                                    .size(
                                                        width = (visualWidth / LocalContext.current.resources.displayMetrics.density).dp,
                                                        height = (visualHeight / LocalContext.current.resources.displayMetrics.density).dp
                                                    )
                                                    .border(
                                                        width = if (isSelected) 2.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.4f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                        else Color.Black.copy(alpha = 0.02f)
                                                    )
                                                    .pointerInput(overlay.id) {
                                                        detectDragGestures(
                                                            onDragStart = { selectedOverlayId = overlay.id },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val currentOverlay = pageOverlays.firstOrNull { it.id == overlay.id } ?: overlay
                                                                val dragXScaled = dragAmount.x / zoomScale
                                                                val dragYScaled = dragAmount.y / zoomScale
                                                                val newXFrac = ((currentOverlay.xFraction * canvasWidth) + dragXScaled) / canvasWidth
                                                                val newYFrac = ((currentOverlay.yFraction * canvasHeight) + dragYScaled) / canvasHeight
                                                                pageOverlays = pageOverlays.map {
                                                                    if (it.id == overlay.id) {
                                                                        it.copy(
                                                                            xFraction = newXFrac.coerceIn(0f, 1f - currentOverlay.widthFraction),
                                                                            yFraction = newYFrac.coerceIn(0f, 1f - currentOverlay.heightFraction)
                                                                        )
                                                                    } else it
                                                                }
                                                            }
                                                        )
                                                    }
                                                    .pointerInput(overlay.id) {
                                                        detectTapGestures(
                                                            onDoubleTap = {
                                                                if (overlay.type == "text") {
                                                                    editingTextId = overlay.id
                                                                    editingTextValue = overlay.text
                                                                    showTextEditDialog = true
                                                                }
                                                            },
                                                            onTap = {
                                                                selectedOverlayId = overlay.id
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (overlay.type == "text") {
                                                    Text(
                                                        text = overlay.text,
                                                        color = Color.Black,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(4.dp)
                                                    )
                                                } else if (overlay.type == "image") {
                                                    val oImgFile = File(overlay.imagePath)
                                                    if (oImgFile.exists()) {
                                                        val oBmp = BitmapFactory.decodeFile(oImgFile.absolutePath)
                                                        if (oBmp != null) {
                                                            Image(
                                                                bitmap = oBmp.asImageBitmap(),
                                                                contentDescription = null,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.FillBounds
                                                            )
                                                        }
                                                    } else {
                                                        Text("Image Missing", fontSize = 10.sp, color = Color.Red)
                                                    }
                                                }

                                                // Resizing Handle (Bottom-Right Corner)
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .size(48.dp)
                                                            .offset(16.dp, 16.dp)
                                                            .pointerInput(overlay.id) {
                                                                detectDragGestures { change, dragAmount ->
                                                                    change.consume()
                                                                    val currentOverlay = pageOverlays.firstOrNull { it.id == overlay.id } ?: overlay
                                                                    val dragXScaled = dragAmount.x / zoomScale
                                                                    val dragYScaled = dragAmount.y / zoomScale
                                                                    val newWidth = ((currentOverlay.widthFraction * canvasWidth) + dragXScaled)
                                                                    val newHeight = ((currentOverlay.heightFraction * canvasHeight) + dragYScaled)
                                                                    
                                                                    val newWidthFrac = newWidth / canvasWidth
                                                                    val newHeightFrac = newHeight / canvasHeight

                                                                    pageOverlays = pageOverlays.map {
                                                                        if (it.id == overlay.id) {
                                                                            it.copy(
                                                                                widthFraction = newWidthFrac.coerceIn(0.05f, 1f - currentOverlay.xFraction),
                                                                                heightFraction = newHeightFrac.coerceIn(0.05f, 1f - currentOverlay.yFraction)
                                                                            )
                                                                        } else it
                                                                    }
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .shadow(2.dp, shape = RoundedCornerShape(4.dp))
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Floating Static Zoom HUD Toolbar
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                                    tonalElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                zoomScale = (zoomScale - 0.25f).coerceIn(1f, 5f)
                                                if (zoomScale == 1f) panOffset = Offset.Zero
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Remove,
                                                contentDescription = "Zoom Out",
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Text(
                                            text = "${(zoomScale * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.width(42.dp),
                                            textAlign = TextAlign.Center
                                        )

                                        IconButton(
                                            onClick = {
                                                zoomScale = (zoomScale + 0.25f).coerceIn(1f, 5f)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "Zoom In",
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (zoomScale > 1f) {
                                            IconButton(
                                                onClick = {
                                                    zoomScale = 1f
                                                    panOffset = Offset.Zero
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.RestartAlt,
                                                    contentDescription = "Reset Zoom",
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }



                    // Page Navigator Centered Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            enabled = currentPreviewPage > 1,
                            onClick = {
                                currentPreviewPage--
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                coroutineScope.launch(Dispatchers.IO) {
                                    val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], currentPreviewPage - 1, 1.5f)
                                    withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                }
                            },
                            modifier = Modifier.bounceClick { },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Prev")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = "Page $currentPreviewPage of $pdfPagesCount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        FilledTonalIconButton(
                            enabled = currentPreviewPage < pdfPagesCount,
                            onClick = {
                                currentPreviewPage++
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                coroutineScope.launch(Dispatchers.IO) {
                                    val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], currentPreviewPage - 1, 1.5f)
                                    withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                }
                            },
                            modifier = Modifier.bounceClick { },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next")
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Configure Parameters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        when (tool.type) {
                            ToolType.SPLIT -> {
                                TextField(
                                    value = splitsInput,
                                    onValueChange = { splitsInput = it },
                                    label = { Text("Page Ranges") },
                                    placeholder = { Text("e.g. 1, 3-5, 8") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                            ToolType.WATERMARK -> {
                                TextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    label = { Text("Watermark Text") },
                                    placeholder = { Text("e.g. CONFIDENTIAL") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                            ToolType.ROTATE -> {
                                Text("Rotation Angle: $rotationValue°")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf(90, 180, 270).forEach { angle ->
                                        FilterChip(
                                            selected = rotationValue == angle,
                                            onClick = { rotationValue = angle },
                                            label = { Text("$angle°") }
                                        )
                                    }
                                }
                            }
                            ToolType.PROTECT -> {
                                TextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    label = { Text("Set PDF Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                            ToolType.UNLOCK -> {
                                TextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    label = { Text("Enter Document Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                            ToolType.EDIT -> {
                                // Handled at top level
                            }
                            ToolType.CROP -> {
                                Text("Crop boundaries (in points):")
                                Column {
                                    listOf("Left" to 0, "Right" to 1, "Top" to 2, "Bottom" to 3).forEach { (side, idx) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            Text(text = "$side: ${marginsInput[idx].toInt()} pt", modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = marginsInput[idx],
                                                onValueChange = { newVal ->
                                                    val list = marginsInput.toMutableList()
                                                    list[idx] = newVal
                                                    marginsInput = list
                                                },
                                                valueRange = 0f..200f,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                            ToolType.COMPARE -> {
                                Text("Upload a second PDF using the main uploader to run cross-comparison.")
                            }
                            ToolType.ORGANIZE -> {
                                Text("Page layout organization grid. Toggle cards to exclude them:")
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                ) {
                                    itemsIndexed(pdfPagesBitmaps) { pageIdx, bmp ->
                                        val isSelected = selectedPageIndices.contains(pageIdx)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.75f)
                                                .bounceClick {
                                                    selectedPageIndices = if (isSelected) {
                                                        selectedPageIndices.filter { it != pageIdx }
                                                    } else {
                                                        selectedPageIndices + pageIdx
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                if (!isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(0.4f))
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Delete,
                                                            contentDescription = "Deleted",
                                                            tint = Color.Red,
                                                            modifier = Modifier.align(Alignment.Center)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            ToolType.SIGN -> {
                                Text("Setup placements signature:")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = if (signatureMode == "signature") sigText else if (signatureMode == "initials") initText else "Date stamp",
                                        onValueChange = {
                                            if (signatureMode == "signature") sigText = it
                                            else if (signatureMode == "initials") initText = it
                                        },
                                        label = { Text("Annotation text") },
                                        enabled = signatureMode != "date",
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    listOf("signature" to "Sig", "initials" to "Initials", "date" to "Date").forEach { (mode, lbl) ->
                                        FilterChip(
                                            selected = signatureMode == mode,
                                            onClick = { signatureMode = mode },
                                            label = { Text(lbl) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // visual preview container canvas
                                previewPageBitmap?.let { bmp ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat())
                                            .background(Color.White)
                                            .border(1.dp, Color.Gray)
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Drag signature box visual
                                        var visualX by remember { mutableStateOf(50f) }
                                        var visualY by remember { mutableStateOf(50f) }

                                        val signVal = if (signatureMode == "signature") sigText else if (signatureMode == "initials") initText else java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US).format(java.util.Date())

                                        Box(
                                            modifier = Modifier
                                                .offset { IntOffset(visualX.roundToInt(), visualY.roundToInt()) }
                                                .size(width = 120.dp, height = 36.dp)
                                                .background(Color.Yellow.copy(0.3f))
                                                .border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                                                .pointerInput(Unit) {
                                                    detectDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        visualX += dragAmount.x
                                                        visualY += dragAmount.y
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = signVal, color = Color.Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Custom Add button
                                        Button(
                                            onClick = {
                                                // Relative coordinate placement mapping
                                                val xFrac = visualX / bmp.width.toFloat()
                                                val yFrac = visualY / bmp.height.toFloat()
                                                val wFrac = 120f / bmp.width.toFloat()
                                                val hFrac = 36f / bmp.height.toFloat()

                                                val newPlacement = PdfOperations.SignaturePlacement(
                                                    text = signVal,
                                                    xFraction = xFrac.coerceIn(0f, 1f),
                                                    yFraction = yFrac.coerceIn(0f, 1f),
                                                    widthFraction = wFrac,
                                                    heightFraction = hFrac,
                                                    pageNumber = currentPreviewPage
                                                )
                                                signaturePlacements = signaturePlacements + newPlacement
                                                addLog("Placed static signature overlay on visual index.")
                                            },
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                        ) {
                                            Text("Place")
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            enabled = currentPreviewPage > 1,
                                            onClick = {
                                                currentPreviewPage--
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], currentPreviewPage - 1, 1.5f)
                                                    withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Prev")
                                        }
                                        Text("Page $currentPreviewPage of $pdfPagesCount")
                                        IconButton(
                                            enabled = currentPreviewPage < pdfPagesCount,
                                            onClick = {
                                                currentPreviewPage++
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], currentPreviewPage - 1, 1.5f)
                                                    withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next")
                                        }
                                    }
                                }
                            }
                            else -> {
                                Text("Ready to run processing.")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Run Action Button for other tools
                        Button(
                            onClick = {
                                if (isProcessing) return@Button
                                coroutineScope.launch(Dispatchers.IO) {
                                    withContext(Dispatchers.Main) {
                                        isProcessing = true
                                        resultFile = null
                                        addLog("Executing operation...", LogType.INFO)
                                    }
                                    try {
                                        val outDir = context.cacheDir
                                        val outFile = File(outDir, "output_${System.currentTimeMillis()}.pdf")

                                        when (tool.type) {
                                            ToolType.MERGE -> {
                                                if (selectedFiles.size < 2) {
                                                    withContext(Dispatchers.Main) { addLog("Please upload at least 2 files.", LogType.ERROR) }
                                                    return@launch
                                                }
                                                PdfOperations.mergePdfs(selectedFiles, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Operation succeeded.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "merged_output.pdf")
                                                }
                                            }
                                            ToolType.SPLIT -> {
                                                PdfOperations.splitPdf(selectedFiles[0], splitsInput, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Operation succeeded.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "split_output.pdf")
                                                }
                                            }
                                            ToolType.WATERMARK -> {
                                                if (textInput.isEmpty()) {
                                                    withContext(Dispatchers.Main) { addLog("Please specify watermark text.", LogType.ERROR) }
                                                    return@launch
                                                }
                                                PdfOperations.addWatermark(selectedFiles[0], textInput, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Watermark stamped.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "watermarked_output.pdf")
                                                }
                                            }
                                            ToolType.ROTATE -> {
                                                PdfOperations.rotatePdf(selectedFiles[0], rotationValue, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Document rotated successfully.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "rotated_output.pdf")
                                                }
                                            }
                                            ToolType.PROTECT -> {
                                                if (passwordInput.isEmpty()) {
                                                    withContext(Dispatchers.Main) { addLog("Please enter security password.", LogType.ERROR) }
                                                    return@launch
                                                }
                                                PdfOperations.protectPdf(selectedFiles[0], passwordInput, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Document password protected successfully.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "protected_output.pdf")
                                                }
                                            }
                                            ToolType.UNLOCK -> {
                                                PdfOperations.unlockPdf(selectedFiles[0], passwordInput, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Password encryption removed.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "unlocked_output.pdf")
                                                }
                                            }
                                            ToolType.EDIT -> {
                                                // Handled in separate top-level branch
                                            }
                                            ToolType.CROP -> {
                                                PdfOperations.cropPdf(
                                                    selectedFiles[0],
                                                    marginsInput[0], marginsInput[1], marginsInput[2], marginsInput[3],
                                                    outFile
                                                )
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Document cropped successfully.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "cropped_output.pdf")
                                                }
                                            }
                                            ToolType.PAGE_NUMBERS -> {
                                                PdfOperations.addPageNumbers(selectedFiles[0], outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Stamped page numbers successfully.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "numbered_output.pdf")
                                                }
                                            }
                                            ToolType.JPG_TO_PDF -> {
                                                PdfOperations.imagesToPdf(selectedFiles, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Images compiled to PDF successfully.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "compiled_images.pdf")
                                                }
                                            }
                                            ToolType.ORGANIZE -> {
                                                PdfOperations.organizePdf(selectedFiles[0], selectedPageIndices, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Pages organized successfully.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "organized_output.pdf")
                                                }
                                            }
                                            ToolType.SIGN -> {
                                                if (signaturePlacements.isEmpty()) {
                                                    withContext(Dispatchers.Main) { addLog("Please place at least one signature annotation box first.", LogType.ERROR) }
                                                    return@launch
                                                }
                                                PdfOperations.applySignatures(selectedFiles[0], signaturePlacements, outFile)
                                                withContext(Dispatchers.Main) {
                                                    resultFile = outFile
                                                    addLog("Placed visual annotations finalized.", LogType.SUCCESS)
                                                    saveToDownloads(outFile, "signed_output.pdf")
                                                }
                                            }
                                            ToolType.PDF_TO_JPG -> {
                                                val bitmap = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], 0, 2.0f)
                                                if (bitmap != null) {
                                                    val imageFile = File(outDir, "rendered_page_1.jpg")
                                                    FileOutputStream(imageFile).use { out ->
                                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        resultFile = imageFile
                                                        addLog("Rendered first page to image successfully.", LogType.SUCCESS)
                                                        saveToDownloads(imageFile, "page_1.jpg")
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) { addLog("Failed to render page to image.", LogType.ERROR) }
                                                }
                                            }
                                            ToolType.OCR -> {
                                                if (geminiApiKey.isEmpty()) {
                                                    withContext(Dispatchers.Main) { addLog("Please enter your Gemini API Key in the top-right settings first.", LogType.ERROR) }
                                                    return@launch
                                                }
                                                val bmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], 0, 2.0f)
                                                if (bmp == null) {
                                                    withContext(Dispatchers.Main) { addLog("Failed to render page to scan.", LogType.ERROR) }
                                                    return@launch
                                                }
                                                val ocrResult = OcrHelper.performOcr(bmp, geminiApiKey)
                                                withContext(Dispatchers.Main) {
                                                    ocrResult.fold(
                                                        onSuccess = { text ->
                                                            extractedOcrText = text
                                                            addLog("OCR text extraction completed.", LogType.SUCCESS)
                                                        },
                                                        onFailure = { err ->
                                                            addLog("OCR failed: ${err.message}", LogType.ERROR)
                                                        }
                                                    )
                                                }
                                            }
                                            ToolType.COMPARE -> {
                                                if (selectedFiles.size < 2) {
                                                    withContext(Dispatchers.Main) { addLog("Please select 2 files to compare.", LogType.ERROR) }
                                                    return@launch
                                                }
                                                val bmp1 = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], 0, 1.5f)
                                                val bmp2 = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[1], 0, 1.5f)
                                                if (bmp1 != null && bmp2 != null) {
                                                    val (diffBmp, count) = CompareHelper.compareBitmaps(bmp1, bmp2)
                                                    withContext(Dispatchers.Main) {
                                                        compareDiffResult = diffBmp
                                                        addLog("Compared pages. Detected $count differing pixels.", LogType.SUCCESS)
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) { addLog("Error rendering inputs for comparison.", LogType.ERROR) }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            addLog("Execution failed: ${e.message}", LogType.ERROR)
                                        }
                                    } finally {
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .bounceClick { },
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Execute ${tool.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Comparison Result Viewer
        compareDiffResult?.let { bmp ->
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Visual Comparison Output (Diffs in Red)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Comparison result",
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.Gray)
                    )
                }
            }
        }

        // Extracted OCR text viewer
        if (extractedOcrText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Extracted AI OCR Text",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = extractedOcrText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceDim)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }

        // Material 3 Expressive Success or Error Cards (Replaces standard terminal window)
        if (resultFile != null) {
            val file = resultFile!!
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success icon with primary container background
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Document Ready",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Your document has been processed and successfully saved to the Downloads folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Open Document Action
                        Button(
                            onClick = {
                                try {
                                    val authority = "com.example.officepdf.fileprovider"
                                    val uri = FileProvider.getUriForFile(context, authority, file)
                                    val mimeType = context.contentResolver.getType(uri) ?: when (file.extension.lowercase()) {
                                        "pdf" -> "application/pdf"
                                        "jpg", "jpeg" -> "image/jpeg"
                                        "png" -> "image/png"
                                        else -> "*/*"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No app found to open this file format.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .bounceClick {
                                    try {
                                        val authority = "com.example.officepdf.fileprovider"
                                        val uri = FileProvider.getUriForFile(context, authority, file)
                                        val mimeType = context.contentResolver.getType(uri) ?: when (file.extension.lowercase()) {
                                            "pdf" -> "application/pdf"
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "png" -> "image/png"
                                            else -> "*/*"
                                        }
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No app found to open this file format.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Share Action
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val authority = "com.example.officepdf.fileprovider"
                                    val uri = FileProvider.getUriForFile(context, authority, file)
                                    val mimeType = context.contentResolver.getType(uri) ?: when (file.extension.lowercase()) {
                                        "pdf" -> "application/pdf"
                                        "jpg", "jpeg" -> "image/jpeg"
                                        "png" -> "image/png"
                                        else -> "*/*"
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Document"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to share file.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .bounceClick {
                                    try {
                                        val authority = "com.example.officepdf.fileprovider"
                                        val uri = FileProvider.getUriForFile(context, authority, file)
                                        val mimeType = context.contentResolver.getType(uri) ?: when (file.extension.lowercase()) {
                                            "pdf" -> "application/pdf"
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "png" -> "image/png"
                                            else -> "*/*"
                                        }
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = mimeType
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Document"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to share file.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Share",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            val errorLog = logs.lastOrNull { it.second == LogType.ERROR }
            if (errorLog != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Error icon with error container background
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Operation Failed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = errorLog.first,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Dismiss Button
                        Button(
                            onClick = { logs = listOf() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .bounceClick { logs = listOf() },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        }

        // Speed Dial FAB (options revealed under one '+' fab)
        if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty()) {
            var isFabExpanded by remember { mutableStateOf(false) }
            val rotationAngle by animateFloatAsState(
                targetValue = if (isFabExpanded) 135f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "fabRotation"
            )

            val onAddTextClick = {
                val newText = PdfOperations.PageOverlay(
                    type = "text",
                    text = "Double tap to edit",
                    xFraction = 0.2f,
                    yFraction = 0.2f,
                    widthFraction = 0.4f,
                    heightFraction = 0.08f,
                    pageNumber = currentPreviewPage
                )
                pageOverlays = pageOverlays + newText
                selectedOverlayId = newText.id
                isFabExpanded = false
            }

            val onAddImageClick = {
                overlayImagePicker.launch("image/*")
                isFabExpanded = false
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                // Expanded Sub-options
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 72.dp)
                ) {
                    AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                        ),
                        exit = fadeOut() + slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.bounceClick { onAddTextClick() }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 2.dp
                            ) {
                                Text(
                                    text = "Add Text",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = onAddTextClick,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(Icons.Rounded.TextFields, contentDescription = "Add Text")
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                        ),
                        exit = fadeOut() + slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.bounceClick { onAddImageClick() }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                tonalElevation = 2.dp
                            ) {
                                Text(
                                    text = "Add Image",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = onAddImageClick,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Add Image")
                            }
                        }
                    }
                }

                // Main Add FAB
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = rotationAngle }
                        .bounceClick { isFabExpanded = !isFabExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Options Menu",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Floating Delete FAB (on the opposite side: bottom-left)
        if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty() && selectedOverlayId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        pageOverlays = pageOverlays.filter { it.id != selectedOverlayId }
                        selectedOverlayId = null
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.bounceClick {
                        pageOverlays = pageOverlays.filter { it.id != selectedOverlayId }
                        selectedOverlayId = null
                    }
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected")
                }
            }
        }
    }
}

enum class LogType { INFO, SUCCESS, ERROR }

@Composable
fun SettingsDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.Unspecified
                )
                Text("Settings")
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Configure Gemini AI API Key for PDF OCR feature:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(keyInput) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExpressiveActionButton(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.height(48.dp)
            .then(if (enabled) Modifier.bounceClick { onClick() } else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = if (enabled) containerColor else MaterialTheme.colorScheme.onSurface.copy(0.12f),
        contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(0.38f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}
