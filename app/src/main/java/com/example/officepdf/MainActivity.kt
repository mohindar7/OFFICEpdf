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
import androidx.compose.animation.core.Spring.StiffnessLow
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import kotlinx.coroutines.delay
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.res.painterResource
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.DpOffset

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

enum class ToolCategory {
    EDIT_SIGN, CONVERT_OCR, ORGANIZE, SECURITY, ANALYZE
}

// Data class representing each PDF tool
data class Tool(
    val name: String,
    val description: String,
    val type: ToolType,
    val icon: ImageVector,
    val color: Color,
    val category: ToolCategory
)

enum class ToolType {
    EDIT, COMBINE_SPLIT, CONVERT_OCR, SECURITY, COMPARE
}

enum class AppTab {
    HOME, RECENTS
}

data class RecentFile(
    val path: String,
    val name: String,
    val operation: String,
    val timestamp: Long,
    val size: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    initialApiKey: String,
    onSaveApiKey: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeTool by remember { mutableStateOf<Tool?>(null) }
    var geminiApiKey by remember { mutableStateOf(initialApiKey) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var topBarActionLabel by remember { mutableStateOf<String?>(null) }
    var topBarActionEnabled by remember { mutableStateOf(true) }
    var onTopBarActionClick by remember { mutableStateOf<(() -> Unit)?>(null) }
    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var toolsSubTab by remember { mutableStateOf("combine_split") } // combine_split, convert_ocr

    // Shared preloading states for clicking files
    var pendingFilesToLoad by remember { mutableStateOf<List<File>>(listOf()) }
    var targetToolForHomePicker by remember { mutableStateOf<Tool?>(null) }
    var homeConvertOcrMode by remember { mutableStateOf("img_to_pdf") }
    var homeCombineSplitMode by remember { mutableStateOf("combine") }
    
    var showCombineSplitSheet by remember { mutableStateOf(false) }
    var showConverterSheet by remember { mutableStateOf(false) }

    val docPickerHome = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val files = uris.mapNotNull { uri ->
                        PdfRendererHelper.copyUriToTempFile(context, uri, "input_${System.currentTimeMillis()}.pdf")
                    }
                    if (files.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            pendingFilesToLoad = files
                            activeTool = targetToolForHomePicker
                        }
                    }
                }
            }
        }
    )

    val imagePickerHome = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val files = uris.mapNotNull { uri ->
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val temp = File(context.cacheDir, "img_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(temp).use { outputStream ->
                            inputStream?.copyTo(outputStream)
                        }
                        temp
                    }
                    if (files.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            pendingFilesToLoad = files
                            activeTool = targetToolForHomePicker
                        }
                    }
                }
            }
        }
    )

    if (activeTool != null) {
        BackHandler {
            activeTool = null
        }
    } else if (currentTab != AppTab.HOME) {
        BackHandler {
            currentTab = AppTab.HOME
        }
    }

    val tools = remember {
        listOf(
            Tool("Edit & Organize", "Draw, sign, rotate, crop, watermark, and number pages", ToolType.EDIT, Icons.Rounded.Edit, Color(0xFFE57373), ToolCategory.EDIT_SIGN),
            Tool("Combine & Split", "Merge multiple PDFs into one, or split pages into a new PDF", ToolType.COMBINE_SPLIT, Icons.Rounded.CallSplit, Color(0xFF81C784), ToolCategory.ORGANIZE),
            Tool("Format Converter & OCR", "Convert PDF to JPG, images to PDF, or extract text using Gemini AI", ToolType.CONVERT_OCR, Icons.Rounded.Transform, Color(0xFFFFB74D), ToolCategory.CONVERT_OCR),
            Tool("Document Security", "Encrypt with password or remove password protection from PDFs", ToolType.SECURITY, Icons.Rounded.Lock, Color(0xFF64B5F6), ToolCategory.SECURITY),
            Tool("Compare PDF", "Visually compare draft document pages side-by-side", ToolType.COMPARE, Icons.Rounded.Compare, Color(0xFFBA68C8), ToolCategory.ANALYZE)
        )
    }

    val isEditing = activeTool?.type == ToolType.EDIT
    val workspaceBgColor = if (isEditing) {
        MaterialTheme.colorScheme.surfaceDim
    } else {
        MaterialTheme.colorScheme.background
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
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Home")
                        }
                    } else if (currentTab != AppTab.HOME) {
                        IconButton(
                            onClick = { currentTab = AppTab.HOME },
                            modifier = Modifier.bounceClick { currentTab = AppTab.HOME }
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to Home")
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
                    containerColor = workspaceBgColor
                )
            )
        },
        bottomBar = {
            if (activeTool == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveNavigationBarItem(
                            selected = currentTab == AppTab.HOME,
                            onClick = { currentTab = AppTab.HOME },
                            icon = Icons.Rounded.Home,
                            label = "Home"
                        )
                        ExpressiveNavigationBarItem(
                            selected = currentTab == AppTab.RECENTS,
                            onClick = { currentTab = AppTab.RECENTS },
                            icon = Icons.Rounded.History,
                            label = "Recents"
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(workspaceBgColor)
        ) {
            AnimatedContent(
                targetState = activeTool,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "Navigation"
            ) { tool ->
                if (tool == null) {
                    if (currentTab == AppTab.HOME) {
                        HomeScreen(
                            tools = tools,
                            onToolClick = { clickedTool ->
                                targetToolForHomePicker = clickedTool
                                when (clickedTool.type) {
                                    ToolType.EDIT -> {
                                        docPickerHome.launch(arrayOf("application/pdf"))
                                    }
                                    ToolType.COMBINE_SPLIT -> {
                                        showCombineSplitSheet = true
                                    }
                                    ToolType.CONVERT_OCR -> {
                                        showConverterSheet = true
                                    }
                                    ToolType.SECURITY -> {
                                        docPickerHome.launch(arrayOf("application/pdf"))
                                    }
                                    ToolType.COMPARE -> {
                                        docPickerHome.launch(arrayOf("application/pdf"))
                                    }
                                }
                            },
                            onRecentFileClick = { recent ->
                                val foundTool = tools.firstOrNull { it.name.equals(recent.operation, ignoreCase = true) } ?: tools[0]
                                pendingFilesToLoad = listOf(File(recent.path))
                                if (foundTool.type == ToolType.COMBINE_SPLIT) {
                                    toolsSubTab = "combine_split"
                                } else if (foundTool.type == ToolType.CONVERT_OCR) {
                                    toolsSubTab = "convert_ocr"
                                }
                                activeTool = foundTool
                            }
                        )
                    } else {
                        RecentFilesTab(onReRunTool = { toolName ->
                            val found = tools.firstOrNull { it.name.equals(toolName, ignoreCase = true) }
                            if (found != null) {
                                targetToolForHomePicker = found
                                when (found.type) {
                                    ToolType.EDIT -> {
                                        docPickerHome.launch(arrayOf("application/pdf"))
                                    }
                                    ToolType.COMBINE_SPLIT -> {
                                        showCombineSplitSheet = true
                                    }
                                    ToolType.CONVERT_OCR -> {
                                        showConverterSheet = true
                                    }
                                    ToolType.SECURITY -> {
                                        docPickerHome.launch(arrayOf("application/pdf"))
                                    }
                                    ToolType.COMPARE -> {
                                        docPickerHome.launch(arrayOf("application/pdf"))
                                    }
                                }
                            }
                        })
                    }
                } else {
                    val initialFiles = remember(pendingFilesToLoad) {
                        pendingFilesToLoad
                    }
                    LaunchedEffect(initialFiles) {
                        if (initialFiles.isNotEmpty()) pendingFilesToLoad = listOf()
                    }
                    
                    if (tool.type == ToolType.COMBINE_SPLIT || tool.type == ToolType.CONVERT_OCR) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            val items = remember {
                                listOf(
                                    SelectorItem("combine_split", "Combine & Split", Icons.Rounded.CallSplit),
                                    SelectorItem("convert_ocr", "Converter & OCR", Icons.Rounded.Transform)
                                )
                            }
                            ExpressiveSelector(
                                items = items,
                                selectedItem = toolsSubTab,
                                onSelectionChanged = { 
                                    toolsSubTab = it
                                    activeTool = if (it == "combine_split") tools[1] else tools[2]
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                            
                            key(tool.type.name) {
                                ToolWorkspace(
                                    tool = tool,
                                    geminiApiKey = geminiApiKey,
                                    initialFiles = initialFiles,
                                    initialConvertOcrMode = homeConvertOcrMode,
                                    initialCombineSplitMode = homeCombineSplitMode,
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
                    } else {
                        ToolWorkspace(
                            tool = tool,
                            geminiApiKey = geminiApiKey,
                            initialFiles = initialFiles,
                            initialConvertOcrMode = homeConvertOcrMode,
                            initialCombineSplitMode = homeCombineSplitMode,
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
            }

            // Combine & Split Selection Bottom Sheet
            if (showCombineSplitSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showCombineSplitSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Combine & Split Tools",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Choose an operation to perform on your PDF documents.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Combine Option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    showCombineSplitSheet = false
                                    homeCombineSplitMode = "combine"
                                    toolsSubTab = "combine_split"
                                    docPickerHome.launch(arrayOf("application/pdf"))
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Merge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Combine PDFs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Merge multiple PDF files into one clean document", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Split Option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    showCombineSplitSheet = false
                                    homeCombineSplitMode = "split"
                                    toolsSubTab = "combine_split"
                                    docPickerHome.launch(arrayOf("application/pdf"))
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.CallSplit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Split PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Extract page ranges into separate files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Format Converter & OCR Mode Selection Bottom Sheet
            if (showConverterSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showConverterSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Converter & OCR Tools",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Choose your file conversion or optical character recognition tool.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Images to PDF
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    showConverterSheet = false
                                    homeConvertOcrMode = "img_to_pdf"
                                    toolsSubTab = "convert_ocr"
                                    imagePickerHome.launch("image/*")
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Images ➔ PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Compile images from gallery into a single PDF document", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // PDF to Images
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    showConverterSheet = false
                                    homeConvertOcrMode = "pdf_to_img"
                                    toolsSubTab = "convert_ocr"
                                    docPickerHome.launch(arrayOf("application/pdf"))
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PDF ➔ Images", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Extract PDF pages as individual image files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Gemini AI OCR
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    showConverterSheet = false
                                    homeConvertOcrMode = "ocr"
                                    toolsSubTab = "convert_ocr"
                                    docPickerHome.launch(arrayOf("application/pdf"))
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Gemini AI OCR Text", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Scan scanned files into editable text using Gemini AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }

    val filteredTools = tools.filter { tool ->
        val matchesSearch = tool.name.contains(searchQuery, ignoreCase = true) ||
                            tool.description.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == null || tool.category == selectedCategory
        matchesSearch && matchesCategory
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // M3 Search Bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search tools...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Category Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = "All"
            )
            ToolCategory.values().forEach { cat ->
                CategoryChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = when (cat) {
                        ToolCategory.EDIT_SIGN -> "Edit & Sign"
                        ToolCategory.CONVERT_OCR -> "Convert & OCR"
                        ToolCategory.ORGANIZE -> "Organize"
                        ToolCategory.SECURITY -> "Security"
                        ToolCategory.ANALYZE -> "Analyze"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredTools.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "No tools found matching your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(filteredTools) { _, tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.2f)
                            .bounceClick { onToolClick(tool) },
                        shape = ShapeCache.card,
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(tool.color.copy(alpha = 0.2f), ShapeCache.icon),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.name,
                                    tint = tool.color,
                                    modifier = Modifier.size(22.dp)
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
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tool workspace view container
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ToolWorkspace(
    tool: Tool,
    geminiApiKey: String,
    initialFiles: List<File> = listOf(),
    initialConvertOcrMode: String = "img_to_pdf",
    initialCombineSplitMode: String = "combine",
    onRegisterEditExecute: (String, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
    onUnregisterEditExecute: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<Pair<String, LogType>>>(listOf()) }

    fun addLog(msg: String, type: LogType = LogType.INFO) {
        logs = logs + Pair(msg, type)
    }
    
    // Pickers states
    var selectedFiles by remember { mutableStateOf<List<File>>(listOf()) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Page overlays (text, image, signature)
    var pageOverlays by remember { mutableStateOf<List<PdfOperations.PageOverlay>>(listOf()) }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Unified Workspace States (Watermark, Page numbers, Page list, Rotations, Crops)
    var watermarkText by remember { mutableStateOf("") }
    var hasWatermark by remember { mutableStateOf(false) }
    var hasPageNumbers by remember { mutableStateOf(false) }
    
    // Page management
    var pdfPagesCount by remember { mutableStateOf(0) }
    var pdfPagesBitmaps by remember { mutableStateOf<List<Bitmap>>(listOf()) }
    var selectedPageIndices by remember { mutableStateOf<List<Int>>(listOf()) }
    var pageRotations by remember { mutableStateOf<Map<Int, Int>>(mapOf()) }
    var pageCrops by remember { mutableStateOf<Map<Int, List<Float>>>(mapOf()) }

    var currentPreviewPage by remember { mutableStateOf(1) } // 1-indexed page of the WORKING document
    var previewPageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Mode selectors for multi-purpose hubs
    var combineSplitMode by remember(initialCombineSplitMode) { mutableStateOf(initialCombineSplitMode) } // combine, split
    var convertOcrMode by remember(initialConvertOcrMode) { mutableStateOf(initialConvertOcrMode) } // img_to_pdf, pdf_to_img, ocr
    var securityIsLocked by remember { mutableStateOf(false) } // detected on import

    // Tool workspace inputs
    var passwordInput by remember { mutableStateOf("") }
    var splitsInput by remember { mutableStateOf("1, 2-3") }
    var extractedOcrText by remember { mutableStateOf("") }
    var compareDiffResult by remember { mutableStateOf<Bitmap?>(null) }

    // Dialog control states
    var showOrganizeDialog by remember { mutableStateOf(false) }
    var pageToCropIndex by remember { mutableStateOf<Int?>(null) }
    
    var targetInsertPosition by remember { mutableStateOf(0) }
    
    fun swapOverlays(fromPage: Int, toPage: Int) {
        pageOverlays = pageOverlays.map {
            when (it.pageNumber) {
                fromPage -> it.copy(pageNumber = toPage)
                toPage -> it.copy(pageNumber = fromPage)
                else -> it
            }
        }
    }

    fun insertPagesFromPdf(
        inputFile: File,
        insertFile: File,
        insertIndex: Int,
        outputFile: File
    ) {
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputFile).use { mainDoc ->
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(insertFile).use { insertDoc ->
                com.tom_roush.pdfbox.pdmodel.PDDocument().use { outputDoc ->
                    for (i in 0 until insertIndex) {
                        if (i < mainDoc.numberOfPages) {
                            outputDoc.addPage(mainDoc.getPage(i))
                        }
                    }
                    for (i in 0 until insertDoc.numberOfPages) {
                        outputDoc.addPage(insertDoc.getPage(i))
                    }
                    for (i in insertIndex until mainDoc.numberOfPages) {
                        outputDoc.addPage(mainDoc.getPage(i))
                    }
                    outputDoc.save(outputFile)
                }
            }
        }
    }

    val insertPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null && selectedFiles.isNotEmpty()) {
                isProcessing = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val insertFile = PdfRendererHelper.copyUriToTempFile(context, uri, "insert_${System.currentTimeMillis()}.pdf")
                        if (insertFile != null) {
                            val insertedCount = PdfRendererHelper.getPageCount(insertFile)
                            val mainFile = selectedFiles[0]
                            val outDir = context.cacheDir
                            val outFile = File(outDir, "merged_workspace_${System.currentTimeMillis()}.pdf")
                            
                            // 1. Physically insert pages
                            insertPagesFromPdf(mainFile, insertFile, targetInsertPosition, outFile)
                            
                            // 2. Load the new file and render its thumbnails
                            val count = PdfRendererHelper.getPageCount(outFile)
                            val list = mutableListOf<Bitmap>()
                            for (i in 0 until count) {
                                PdfRendererHelper.renderPageToBitmap(context, outFile, i, 0.4f)?.let { list.add(it) }
                            }
                            val firstPageBmp = PdfRendererHelper.renderPageToBitmap(context, outFile, 0, 1.5f)
                            
                            withContext(Dispatchers.Main) {
                                // 3. Shift state maps
                                val newRotations = mutableMapOf<Int, Int>()
                                pageRotations.forEach { entry ->
                                    val k = entry.key
                                    val v = entry.value
                                    if (k >= targetInsertPosition) {
                                        newRotations[k + insertedCount] = v
                                    } else {
                                        newRotations[k] = v
                                    }
                                }
                                pageRotations = newRotations

                                val newCrops = mutableMapOf<Int, List<Float>>()
                                pageCrops.forEach { entry ->
                                    val k = entry.key
                                    val v = entry.value
                                    if (k >= targetInsertPosition) {
                                        newCrops[k + insertedCount] = v
                                    } else {
                                        newCrops[k] = v
                                    }
                                }
                                pageCrops = newCrops

                                pageOverlays = pageOverlays.map {
                                    if (it.pageNumber > targetInsertPosition) {
                                        it.copy(pageNumber = it.pageNumber + insertedCount)
                                    } else {
                                        it
                                    }
                                }
                                
                                // 4. Set new files and selection
                                selectedFiles = listOf(outFile)
                                pdfPagesCount = count
                                pdfPagesBitmaps = list
                                selectedPageIndices = (0 until count).toList()
                                previewPageBitmap = firstPageBmp
                                currentPreviewPage = currentPreviewPage.coerceIn(1, count)
                                addLog("Inserted $insertedCount pages at position $targetInsertPosition.")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            addLog("Failed to insert PDF: ${e.message}", LogType.ERROR)
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                        }
                    }
                }
            }
        }
    )
    
    // Drag-and-drop Signature Canvas state
    var signatureMode by remember { mutableStateOf("signature") }
    var sigText by remember { mutableStateOf("John Doe") }
    var initText by remember { mutableStateOf("JD") }

    // Dynamic Hud & Options Bottom Sheet states
    var hudVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var showEditOptionsSheet by remember { mutableStateOf(false) }

    val notifyInteraction = {
        lastInteractionTime = System.currentTimeMillis()
        hudVisible = true
    }

    LaunchedEffect(lastInteractionTime) {
        delay(2000)
        hudVisible = false
    }

    val hudAlpha by animateFloatAsState(
        targetValue = if (hudVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )


    fun clearWorkspace() {
        selectedFiles = listOf()
        resultFile = null
        compareDiffResult = null
        extractedOcrText = ""
        pdfPagesBitmaps = listOf()
        pdfPagesCount = 0
        pageOverlays = listOf()
        selectedOverlayId = null
        previewPageBitmap = null
        logs = listOf()
        zoomScale = 1f
        panOffset = Offset.Zero
        pageRotations = mapOf()
        pageCrops = mapOf()
        selectedPageIndices = listOf()
        hasWatermark = false
        watermarkText = ""
        hasPageNumbers = false
    }

    // Preload initial files if supplied (must be defined after addLog and properties are declared)
    LaunchedEffect(initialFiles) {
        if (initialFiles.isNotEmpty()) {
            selectedFiles = initialFiles
            addLog("Loaded ${initialFiles.size} document(s) successfully.")
            if (tool.type == ToolType.EDIT) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val count = PdfRendererHelper.getPageCount(initialFiles[0])
                        pdfPagesCount = count
                        val list = mutableListOf<Bitmap>()
                        for (i in 0 until count) {
                            PdfRendererHelper.renderPageToBitmap(context, initialFiles[0], i, 0.4f)?.let { list.add(it) }
                        }
                        val firstPageBmp = PdfRendererHelper.renderPageToBitmap(context, initialFiles[0], 0, 1.5f)
                        withContext(Dispatchers.Main) {
                            pdfPagesBitmaps = list
                            selectedPageIndices = (0 until count).toList()
                            previewPageBitmap = firstPageBmp
                            currentPreviewPage = 1
                            addLog("PDF Workspace loaded. $count pages ready for editing.")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            addLog("Error loading PDF: ${e.message}", LogType.ERROR)
                        }
                    }
                }
            }
            if (tool.type == ToolType.SECURITY) {
                coroutineScope.launch(Dispatchers.IO) {
                    var isLocked = false
                    try {
                        com.tom_roush.pdfbox.pdmodel.PDDocument.load(initialFiles[0]).use { _ -> }
                    } catch (e: Exception) {
                        isLocked = true
                    }
                    withContext(Dispatchers.Main) {
                        securityIsLocked = isLocked
                        if (isLocked) {
                            addLog("Document is protected. Enter password to unlock.", LogType.INFO)
                        } else {
                            addLog("Document is unprotected. Enter password to lock.", LogType.INFO)
                        }
                    }
                }
            }
        }
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

                if (tool.type == ToolType.EDIT && files.isNotEmpty()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val count = PdfRendererHelper.getPageCount(files[0])
                            pdfPagesCount = count
                            val list = mutableListOf<Bitmap>()
                            for (i in 0 until count) {
                                PdfRendererHelper.renderPageToBitmap(context, files[0], i, 0.4f)?.let { list.add(it) }
                            }
                            val firstPageBmp = PdfRendererHelper.renderPageToBitmap(context, files[0], 0, 1.5f)
                            withContext(Dispatchers.Main) {
                                pdfPagesBitmaps = list
                                selectedPageIndices = (0 until count).toList()
                                previewPageBitmap = firstPageBmp
                                currentPreviewPage = 1
                                addLog("PDF Workspace loaded. $count pages ready for editing.")
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                addLog("Error loading PDF: ${e.message}", LogType.ERROR)
                            }
                        }
                    }
                }

                if (tool.type == ToolType.SECURITY && files.isNotEmpty()) {
                    // Check if document is password-protected
                    coroutineScope.launch(Dispatchers.IO) {
                        var isLocked = false
                        try {
                            com.tom_roush.pdfbox.pdmodel.PDDocument.load(files[0]).use { _ -> }
                        } catch (e: Exception) {
                            isLocked = true
                        }
                        withContext(Dispatchers.Main) {
                            securityIsLocked = isLocked
                            if (isLocked) {
                                addLog("Document is protected. Enter password to unlock.", LogType.INFO)
                            } else {
                                addLog("Document is unprotected. Enter password to lock.", LogType.INFO)
                            }
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

    val replaceOverlayImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null && selectedOverlayId != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val tempFile = File(context.cacheDir, "overlay_img_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri).use { input ->
                            FileOutputStream(tempFile).use { outputStream ->
                                input?.copyTo(outputStream)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            pageOverlays = pageOverlays.map {
                                if (it.id == selectedOverlayId) {
                                    it.copy(imagePath = tempFile.absolutePath)
                                } else it
                            }
                            addLog("Replaced overlay image.")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to replace image", Toast.LENGTH_SHORT).show()
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
            RecentFilesManager.saveRecentFile(context, destFile.absolutePath, tool.name)
        } catch (e: Exception) {
            addLog("Failed to export: ${e.message}", LogType.ERROR)
        }
    }

    // PDF Workspace Execution Registration (Save Button)
    LaunchedEffect(selectedFiles, isProcessing, pageOverlays, selectedPageIndices, pageRotations, pageCrops, watermarkText, hasWatermark, hasPageNumbers) {
        onUnregisterEditExecute() // Save action is now handled by the dual floating FABs inside full-screen canvas
    }

    DisposableEffect(Unit) {
        onDispose {
            onUnregisterEditExecute()
        }
    }

    val workspaceScrollState = rememberScrollState()
    LaunchedEffect(workspaceScrollState.isScrollInProgress) {
        if (workspaceScrollState.isScrollInProgress) {
            notifyInteraction()
        }
    }

    val buttonsVisible = zoomScale == 1f && !isProcessing
    val buttonsAlpha by animateFloatAsState(
        targetValue = if (buttonsVisible) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty()) {
            // Immersive Full-Screen PDF Editor Workspace (occupying 100% viewport)
            var showTextEditDialog by remember { mutableStateOf(false) }
            var editingTextId by remember { mutableStateOf<String?>(null) }
            var editingTextValue by remember { mutableStateOf("") }
            var showContextOptionsDialog by remember { mutableStateOf(false) }
            var contextTargetOverlay by remember { mutableStateOf<PdfOperations.PageOverlay?>(null) }

            if (showTextEditDialog) {
                AlertDialog(
                    onDismissRequest = { showTextEditDialog = false },
                    shape = RoundedCornerShape(24.dp),
                    title = { Text("Edit Text") },
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
                            },
                            shape = RoundedCornerShape(24.dp)
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTextEditDialog = false }) { Text("Cancel") }
                    }
                )
            }



            previewPageBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceDim),
                    contentAlignment = Alignment.Center
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center)
                    ) {
                        val parentWidth = constraints.maxWidth.toFloat()
                        val parentHeight = constraints.maxHeight.toFloat()
                        val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()

                        val canvasWidth: Float
                        val canvasHeight: Float
                        if (parentWidth / parentHeight > bmpRatio) {
                            canvasHeight = parentHeight * 0.95f
                            canvasWidth = canvasHeight * bmpRatio
                        } else {
                            canvasWidth = parentWidth * 0.95f
                            canvasHeight = canvasWidth / bmpRatio
                        }

                        val workspaceScrollState = rememberScrollState()
                        val currentOverlaysState = rememberUpdatedState(pageOverlays)
                        val currentCanvasWidthState = rememberUpdatedState(canvasWidth)
                        val currentCanvasHeightState = rememberUpdatedState(canvasHeight)
                        val currentZoomScaleState = rememberUpdatedState(zoomScale)

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
                                        notifyInteraction()
                                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 10f)
                                        val maxPanX = (zoomScale - 1f).coerceAtLeast(0f) * parentWidth / 2f
                                        val maxPanY = (zoomScale - 1f).coerceAtLeast(0f) * parentHeight / 2f
                                        panOffset = Offset(
                                            x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                            y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                        )
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            selectedOverlayId = null
                                            notifyInteraction()
                                        },
                                        onDoubleTap = {
                                            notifyInteraction()
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                        }
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(workspaceScrollState, enabled = zoomScale == 1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Spacer(modifier = Modifier.height(32.dp))
                                for (pageIdx in 0 until pdfPagesCount) {
                                    val isActive = currentPreviewPage == pageIdx + 1
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(
                                                width = (canvasWidth / context.resources.displayMetrics.density).dp,
                                                height = (canvasHeight / context.resources.displayMetrics.density).dp
                                            )
                                            .shadow(
                                                elevation = if (isActive) 12.dp else 4.dp,
                                                shape = RoundedCornerShape(4.dp),
                                                ambientColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Black
                                            )
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                            .combinedBounceClick(
                                                onDoubleClick = {
                                                    notifyInteraction()
                                                    zoomScale = 1f
                                                    panOffset = Offset.Zero
                                                },
                                                onClick = {
                                                    if (currentPreviewPage != pageIdx + 1) {
                                                        currentPreviewPage = pageIdx + 1
                                                        notifyInteraction()
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], selectedPageIndices[currentPreviewPage - 1], 1.5f)
                                                            withContext(Dispatchers.Main) {
                                                                previewPageBitmap = pageBmp
                                                            }
                                                        }
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        val displayBmp = if (isActive) (previewPageBitmap ?: pdfPagesBitmaps.getOrNull(pageIdx)) else pdfPagesBitmaps.getOrNull(pageIdx)
                                        if (displayBmp != null) {
                                            Image(
                                                bitmap = displayBmp.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(strokeWidth = 2.dp)
                                            }
                                        }
                                        
                                        pageOverlays.filter { it.pageNumber == pageIdx + 1 }.forEach { overlay ->
                                            val isSelected = selectedOverlayId == overlay.id
                                            val visualX = overlay.xFraction * canvasWidth
                                            val visualY = overlay.yFraction * canvasHeight
                                            val visualWidth = overlay.widthFraction * canvasWidth
                                            val visualHeight = overlay.heightFraction * canvasHeight

                                            Box(
                                                modifier = Modifier
                                                    .offset { IntOffset(visualX.roundToInt(), visualY.roundToInt()) }
                                                    .size(
                                                        width = (visualWidth / context.resources.displayMetrics.density).dp,
                                                        height = (visualHeight / context.resources.displayMetrics.density).dp
                                                    )
                                                    .graphicsLayer {
                                                        rotationZ = overlay.rotation
                                                    }
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 0.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .pointerInput(overlay.id) {
                                                        var startX = 0f
                                                        var startY = 0f
                                                        var accumulatedParentDragX = 0f
                                                        var accumulatedParentDragY = 0f
                                                        
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                selectedOverlayId = overlay.id
                                                                notifyInteraction()
                                                                val list = currentOverlaysState.value
                                                                val currentOverlay = list.firstOrNull { it.id == overlay.id } ?: overlay
                                                                startX = currentOverlay.xFraction * currentCanvasWidthState.value
                                                                startY = currentOverlay.yFraction * currentCanvasHeightState.value
                                                                accumulatedParentDragX = 0f
                                                                accumulatedParentDragY = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                notifyInteraction()
                                                                change.consume()
                                                                val list = currentOverlaysState.value
                                                                val canvasW = currentCanvasWidthState.value
                                                                val canvasH = currentCanvasHeightState.value
                                                                val zScale = currentZoomScaleState.value
                                                                
                                                                val currentOverlay = list.firstOrNull { it.id == overlay.id } ?: overlay
                                                                val rad = Math.toRadians(currentOverlay.rotation.toDouble())
                                                                val cos = Math.cos(rad)
                                                                val sin = Math.sin(rad)
                                                                
                                                                val dragXParent = ((dragAmount.x * cos - dragAmount.y * sin) / zScale).toFloat()
                                                                val dragYParent = ((dragAmount.x * sin + dragAmount.y * cos) / zScale).toFloat()
                                                                
                                                                accumulatedParentDragX += dragXParent
                                                                accumulatedParentDragY += dragYParent
                                                                
                                                                val newXFrac = (startX + accumulatedParentDragX) / canvasW
                                                                val newYFrac = (startY + accumulatedParentDragY) / canvasH
                                                                
                                                                pageOverlays = list.map {
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
                                                                notifyInteraction()
                                                                if (overlay.type == "text" || overlay.type == "signature") {
                                                                    editingTextId = overlay.id
                                                                    editingTextValue = overlay.text
                                                                    showTextEditDialog = true
                                                                }
                                                            },
                                                            onTap = {
                                                                selectedOverlayId = overlay.id
                                                                notifyInteraction()
                                                            },
                                                            onLongPress = {
                                                                selectedOverlayId = overlay.id
                                                                contextTargetOverlay = overlay
                                                                showContextOptionsDialog = true
                                                                notifyInteraction()
                                                            }
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (overlay.type == "text" || overlay.type == "signature") {
                                                    Text(
                                                        text = overlay.text,
                                                        color = if (overlay.type == "signature") Color(10, 10, 100) else Color.Black,
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
                                                    }
                                                }

                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .size(48.dp)
                                                            .offset(16.dp, 16.dp)
                                                            .pointerInput(overlay.id) {
                                                                var startWidth = 0f
                                                                var startHeight = 0f
                                                                var accumulatedParentDragX = 0f
                                                                var accumulatedParentDragY = 0f
                                                                
                                                                detectDragGestures(
                                                                    onDragStart = {
                                                                        notifyInteraction()
                                                                        val list = currentOverlaysState.value
                                                                        val currentOverlay = list.firstOrNull { it.id == overlay.id } ?: overlay
                                                                        startWidth = currentOverlay.widthFraction * currentCanvasWidthState.value
                                                                        startHeight = currentOverlay.heightFraction * currentCanvasHeightState.value
                                                                        accumulatedParentDragX = 0f
                                                                        accumulatedParentDragY = 0f
                                                                    },
                                                                    onDrag = { change, dragAmount ->
                                                                        notifyInteraction()
                                                                        change.consume()
                                                                        val list = currentOverlaysState.value
                                                                        val canvasW = currentCanvasWidthState.value
                                                                        val canvasH = currentCanvasHeightState.value
                                                                        val zScale = currentZoomScaleState.value
                                                                        
                                                                        val currentOverlay = list.firstOrNull { it.id == overlay.id } ?: overlay
                                                                        val rad = Math.toRadians(currentOverlay.rotation.toDouble())
                                                                        val cos = Math.cos(rad)
                                                                        val sin = Math.sin(rad)
                                                                        
                                                                        val dragXParent = ((dragAmount.x * cos - dragAmount.y * sin) / zScale).toFloat()
                                                                        val dragYParent = ((dragAmount.x * sin + dragAmount.y * cos) / zScale).toFloat()
                                                                        
                                                                        accumulatedParentDragX += dragXParent
                                                                        accumulatedParentDragY += dragYParent
                                                                        
                                                                        val deltaW = (accumulatedParentDragX * cos + accumulatedParentDragY * sin).toFloat()
                                                                        val deltaH = (-accumulatedParentDragX * sin + accumulatedParentDragY * cos).toFloat()
                                                                        
                                                                        val newWidth = startWidth + deltaW
                                                                        val newHeight = startHeight + deltaH
                                                                        
                                                                        val newWidthFrac = newWidth / canvasW
                                                                        val newHeightFrac = newHeight / canvasH
                                                                        
                                                                        pageOverlays = list.map {
                                                                            if (it.id == overlay.id) {
                                                                                it.copy(
                                                                                    widthFraction = newWidthFrac.coerceIn(0.05f, 1f - currentOverlay.xFraction),
                                                                                    heightFraction = newHeightFrac.coerceIn(0.05f, 1f - currentOverlay.yFraction)
                                                                                )
                                                                            } else it
                                                                        }
                                                                    }
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .shadow(2.dp, shape = RoundedCornerShape(4.dp))
                                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                                        )
                                                    }
 
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .size(48.dp)
                                                            .offset(16.dp, (-16).dp)
                                                            .pointerInput(overlay.id) {
                                                                var trackedHandleX = 0f
                                                                var trackedHandleY = 0f
                                                                
                                                                detectDragGestures(
                                                                    onDragStart = {
                                                                        notifyInteraction()
                                                                        val list = currentOverlaysState.value
                                                                        val currentOverlay = list.firstOrNull { it.id == overlay.id } ?: overlay
                                                                        val canvasW = currentCanvasWidthState.value
                                                                        val canvasH = currentCanvasHeightState.value
                                                                        
                                                                        val overlayW = currentOverlay.widthFraction * canvasW
                                                                        val overlayH = currentOverlay.heightFraction * canvasH
                                                                        val cx = (currentOverlay.xFraction * canvasW) + overlayW / 2f
                                                                        val cy = (currentOverlay.yFraction * canvasH) + overlayH / 2f
                                                                        
                                                                        val rad = Math.toRadians(currentOverlay.rotation.toDouble())
                                                                        val cos = Math.cos(rad).toFloat()
                                                                        val sin = Math.sin(rad).toFloat()
                                                                        
                                                                        val rx = (overlayW / 2f) * cos + (overlayH / 2f) * sin
                                                                        val ry = (overlayW / 2f) * sin - (overlayH / 2f) * cos
                                                                        
                                                                        trackedHandleX = cx + rx
                                                                        trackedHandleY = cy + ry
                                                                    },
                                                                    onDrag = { change, dragAmount ->
                                                                        notifyInteraction()
                                                                        change.consume()
                                                                        val list = currentOverlaysState.value
                                                                        val canvasW = currentCanvasWidthState.value
                                                                        val canvasH = currentCanvasHeightState.value
                                                                        val zScale = currentZoomScaleState.value
                                                                        
                                                                        val currentOverlay = list.firstOrNull { it.id == overlay.id } ?: overlay
                                                                        val rad = Math.toRadians(currentOverlay.rotation.toDouble())
                                                                        val cos = Math.cos(rad)
                                                                        val sin = Math.sin(rad)
                                                                        
                                                                        val dragXParent = ((dragAmount.x * cos - dragAmount.y * sin) / zScale).toFloat()
                                                                        val dragYParent = ((dragAmount.x * sin + dragAmount.y * cos) / zScale).toFloat()
                                                                        
                                                                        trackedHandleX += dragXParent
                                                                        trackedHandleY += dragYParent
                                                                        
                                                                        val overlayW = currentOverlay.widthFraction * canvasW
                                                                        val overlayH = currentOverlay.heightFraction * canvasH
                                                                        val cx = (currentOverlay.xFraction * canvasW) + overlayW / 2f
                                                                        val cy = (currentOverlay.yFraction * canvasH) + overlayH / 2f
                                                                        
                                                                        val refAngle = Math.toDegrees(Math.atan2(-overlayH / 2.0, overlayW / 2.0)).toFloat()
                                                                        val currentAngle = Math.toDegrees(Math.atan2((trackedHandleY - cy).toDouble(), (trackedHandleX - cx).toDouble())).toFloat()
                                                                        
                                                                        val newRotation = (currentAngle - refAngle) % 360f
                                                                        val normalizedRotation = if (newRotation < 0) newRotation + 360f else newRotation
                                                                        
                                                                        pageOverlays = list.map {
                                                                            if (it.id == overlay.id) {
                                                                                it.copy(rotation = normalizedRotation)
                                                                            } else it
                                                                        }
                                                                    }
                                                                )
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .shadow(4.dp, shape = RoundedCornerShape(12.dp))
                                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.RotateRight,
                                                                contentDescription = "Rotate",
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                if (showContextOptionsDialog && contextTargetOverlay?.id == overlay.id) {
                                                    Popup(
                                                        onDismissRequest = { showContextOptionsDialog = false },
                                                        properties = PopupProperties(focusable = true),
                                                        alignment = Alignment.TopStart,
                                                        offset = IntOffset(x = 0, y = visualHeight.roundToInt())
                                                    ) {
                                                        Card(
                                                            shape = RoundedCornerShape(24.dp),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                                            ),
                                                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                                            modifier = Modifier
                                                                .width(180.dp)
                                                                .padding(4.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(vertical = 8.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(48.dp)
                                                                        .bounceClick {
                                                                            showContextOptionsDialog = false
                                                                            if (overlay.type == "image") {
                                                                                selectedOverlayId = overlay.id
                                                                                replaceOverlayImagePicker.launch("image/*")
                                                                            } else {
                                                                                editingTextId = overlay.id
                                                                                editingTextValue = overlay.text
                                                                                showTextEditDialog = true
                                                                            }
                                                                        }
                                                                        .padding(horizontal = 16.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = if (overlay.type == "image") Icons.Rounded.AddPhotoAlternate else Icons.Rounded.Edit,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                    Text(
                                                                        text = if (overlay.type == "image") "Replace" else "Edit",
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                }
                                                                
                                                                HorizontalDivider(
                                                                    modifier = Modifier.padding(horizontal = 8.dp),
                                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                                )
                                                                
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(48.dp)
                                                                        .bounceClick {
                                                                            showContextOptionsDialog = false
                                                                            pageOverlays = pageOverlays.filter { it.id != overlay.id }
                                                                            selectedOverlayId = null
                                                                            addLog("Deleted ${overlay.type} element.")
                                                                        }
                                                                        .padding(horizontal = 16.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Rounded.Delete,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.error,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                    Text(
                                                                        text = "Delete",
                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = MaterialTheme.colorScheme.error
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(120.dp))
                            }
                        }
            }

                // Floating page indicators & HUD
                    if (hudAlpha > 0.01f) {
                        UnifiedCanvasHud(
                            currentPage = currentPreviewPage,
                            totalPages = selectedPageIndices.size,
                            onPrevPage = {
                                notifyInteraction()
                                currentPreviewPage--
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                coroutineScope.launch(Dispatchers.IO) {
                                    val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], selectedPageIndices[currentPreviewPage - 1], 1.5f)
                                    withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                }
                            },
                            onNextPage = {
                                notifyInteraction()
                                currentPreviewPage++
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                coroutineScope.launch(Dispatchers.IO) {
                                    val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], selectedPageIndices[currentPreviewPage - 1], 1.5f)
                                    withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                }
                            },
                            zoomScale = zoomScale,
                            onZoomIn = {
                                notifyInteraction()
                                zoomScale = (zoomScale + 0.25f).coerceIn(0.5f, 10f)
                            },
                            onZoomOut = {
                                notifyInteraction()
                                zoomScale = (zoomScale - 0.25f).coerceIn(0.5f, 10f)
                                if (zoomScale <= 1f) panOffset = Offset.Zero
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 100.dp)
                                .graphicsLayer {
                                    alpha = hudAlpha
                                    scaleX = 0.85f + 0.15f * hudAlpha
                                    scaleY = 0.85f + 0.15f * hudAlpha
                                }
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(workspaceScrollState, enabled = selectedOverlayId == null && zoomScale == 1f)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    )
            ) {
            // Immersive title
            if (tool.type != ToolType.EDIT || selectedFiles.isEmpty()) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Mode selectors for hubs
            if (selectedFiles.isEmpty()) {
                if (tool.type == ToolType.COMBINE_SPLIT) {
                    val modeItems = remember {
                        listOf(
                            SelectorItem("combine", "Combine PDFs", Icons.Rounded.Merge),
                            SelectorItem("split", "Split PDF", Icons.Rounded.CallSplit)
                        )
                    }
                    ExpressiveSelector(
                        items = modeItems,
                        selectedItem = combineSplitMode,
                        onSelectionChanged = { combineSplitMode = it },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else if (tool.type == ToolType.CONVERT_OCR) {
                    val convertModeItems = remember {
                        listOf(
                            SelectorItem("img_to_pdf", "Images ➔ PDF", Icons.Rounded.PictureAsPdf),
                            SelectorItem("pdf_to_img", "PDF ➔ Images", Icons.Rounded.Image),
                            SelectorItem("ocr", "Gemini OCR", Icons.Rounded.DocumentScanner)
                        )
                    }
                    ExpressiveSelector(
                        items = convertModeItems,
                        selectedItem = convertOcrMode,
                        onSelectionChanged = { convertOcrMode = it },
                        modifier = Modifier.padding(bottom = 16.dp),
                        height = 64.dp
                    )
                }
            }

            // File Pickers
            if (selectedFiles.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .bounceClick {
                            val pickImages = (tool.type == ToolType.CONVERT_OCR && convertOcrMode == "img_to_pdf")
                            val pickMultiple = (tool.type == ToolType.COMBINE_SPLIT && combineSplitMode == "combine")
                            if (pickImages) {
                                imagePicker.launch("image/*")
                            } else {
                                docPicker.launch(arrayOf("application/pdf"))
                            }
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
                            text = if (tool.type == ToolType.CONVERT_OCR && convertOcrMode == "img_to_pdf") "Select images from gallery" else "Select PDF files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (tool.type == ToolType.COMBINE_SPLIT && combineSplitMode == "combine") "Supports selecting multiple files" else "Single file only",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (tool.type != ToolType.EDIT) {
                // Loaded file display for other tools
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
                        selectedFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (tool.type == ToolType.CONVERT_OCR && convertOcrMode == "img_to_pdf") Icons.Rounded.Image else Icons.Rounded.InsertDriveFile,
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

            // Edit Workspace Layout
            if (selectedFiles.isNotEmpty() && tool.type == ToolType.EDIT) {
                // Visual page canvas editor
                var showTextEditDialog by remember { mutableStateOf(false) }
                var editingTextId by remember { mutableStateOf<String?>(null) }
                var editingTextValue by remember { mutableStateOf("") }
                var showContextOptionsDialog by remember { mutableStateOf(false) }
                var contextTargetOverlay by remember { mutableStateOf<PdfOperations.PageOverlay?>(null) }

                if (showTextEditDialog) {
                    AlertDialog(
                        onDismissRequest = { showTextEditDialog = false },
                        title = { Text("Edit Text") },
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
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTextEditDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                if (showContextOptionsDialog) {
                    contextTargetOverlay?.let { overlay ->
                        AlertDialog(
                            onDismissRequest = { showContextOptionsDialog = false },
                            title = { Text("Element Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                            text = {
                                Text(
                                    text = "Choose what you want to do with this ${overlay.type} element.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            showContextOptionsDialog = false
                                            if (overlay.type == "image") {
                                                selectedOverlayId = overlay.id
                                                replaceOverlayImagePicker.launch("image/*")
                                            } else {
                                                editingTextId = overlay.id
                                                editingTextValue = overlay.text
                                                showTextEditDialog = true
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (overlay.type == "image") Icons.Rounded.AddPhotoAlternate else Icons.Rounded.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (overlay.type == "image") "Replace" else "Edit")
                                    }

                                    Button(
                                        onClick = {
                                            showContextOptionsDialog = false
                                            pageOverlays = pageOverlays.filter { it.id != overlay.id }
                                            selectedOverlayId = null
                                            addLog("Deleted ${overlay.type} element.")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete")
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showContextOptionsDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    previewPageBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
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
                                                notifyInteraction()
                                                zoomScale = (zoomScale * zoom).coerceIn(0.5f, 10f)
                                                val maxPanX = (zoomScale - 1f).coerceAtLeast(0f) * size.width / 2f
                                                val maxPanY = (zoomScale - 1f).coerceAtLeast(0f) * size.height / 2f
                                                panOffset = Offset(
                                                    x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    selectedOverlayId = null
                                                    notifyInteraction()
                                                },
                                                onDoubleTap = { tapOffset ->
                                                    notifyInteraction()
                                                    if (zoomScale != 1f) {
                                                        zoomScale = 1f
                                                        panOffset = Offset.Zero
                                                    } else {
                                                        zoomScale = 2f
                                                        val centerX = canvasWidth / 2f
                                                        val centerY = canvasHeight / 2f
                                                        val targetPanX = (centerX - tapOffset.x)
                                                        val targetPanY = (centerY - tapOffset.y)
                                                        val maxPanX = canvasWidth / 2f
                                                        val maxPanY = canvasHeight / 2f
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
                                                    width = (visualWidth / context.resources.displayMetrics.density).dp,
                                                    height = (visualHeight / context.resources.displayMetrics.density).dp
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
                                                        onDragStart = {
                                                            selectedOverlayId = overlay.id
                                                            notifyInteraction()
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            notifyInteraction()
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
                                                            notifyInteraction()
                                                            if (overlay.type == "text" || overlay.type == "signature") {
                                                                editingTextId = overlay.id
                                                                editingTextValue = overlay.text
                                                                showTextEditDialog = true
                                                            }
                                                        },
                                                        onTap = {
                                                            selectedOverlayId = overlay.id
                                                            notifyInteraction()
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (overlay.type == "text" || overlay.type == "signature") {
                                                Text(
                                                    text = overlay.text,
                                                    color = if (overlay.type == "signature") Color(10, 10, 100) else Color.Black,
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
                                                }
                                            }

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .size(48.dp)
                                                        .offset(16.dp, 16.dp)
                                                        .pointerInput(overlay.id) {
                                                            detectDragGestures(
                                                                onDragStart = { notifyInteraction() },
                                                                onDrag = { change, dragAmount ->
                                                                    notifyInteraction()
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
                                                            )
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .shadow(2.dp, shape = RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Unified Canvas HUD Floating Overlay
                                if (hudAlpha > 0.01f) {
                                    UnifiedCanvasHud(
                                        currentPage = currentPreviewPage,
                                        totalPages = selectedPageIndices.size,
                                        onPrevPage = {
                                            notifyInteraction()
                                            currentPreviewPage--
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], selectedPageIndices[currentPreviewPage - 1], 1.5f)
                                                withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                            }
                                        },
                                        onNextPage = {
                                            notifyInteraction()
                                            currentPreviewPage++
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val pageBmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], selectedPageIndices[currentPreviewPage - 1], 1.5f)
                                                withContext(Dispatchers.Main) { previewPageBitmap = pageBmp }
                                            }
                                        },
                                        zoomScale = zoomScale,
                                        onZoomIn = {
                                            notifyInteraction()
                                            zoomScale = (zoomScale + 0.25f).coerceIn(0.5f, 10f)
                                        },
                                        onZoomOut = {
                                            notifyInteraction()
                                            zoomScale = (zoomScale - 0.25f).coerceIn(0.5f, 10f)
                                            if (zoomScale <= 1f) panOffset = Offset.Zero
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                            .graphicsLayer {
                                                alpha = hudAlpha
                                                scaleX = 0.85f + 0.15f * hudAlpha
                                                scaleY = 0.85f + 0.15f * hudAlpha
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // Dialogs moved to top level

            }

            // Other Workspaces parameter triggers
            if (selectedFiles.isNotEmpty() && tool.type != ToolType.EDIT) {
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
                            ToolType.COMBINE_SPLIT -> {
                                if (combineSplitMode == "split") {
                                    TextField(
                                        value = splitsInput,
                                        onValueChange = { splitsInput = it },
                                        label = { Text("Split Page Ranges") },
                                        placeholder = { Text("e.g. 1, 3-5") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                } else {
                                    Text("Merges all listed documents sequentially. Arrange your inputs appropriately.")
                                }
                            }
                            ToolType.CONVERT_OCR -> {
                                if (convertOcrMode == "ocr") {
                                    Text("Gemini OCR converts first page visually to scanned text. Set your API Key in Settings.")
                                } else if (convertOcrMode == "pdf_to_img") {
                                    Text("Converts PDF pages into separate images saved to Downloads folder.")
                                } else {
                                    Text("Compiles uploaded images into a clean PDF document.")
                                }
                            }
                            ToolType.SECURITY -> {
                                TextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    label = { Text("Document Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            }
                            ToolType.COMPARE -> {
                                if (selectedFiles.size < 2) {
                                    Text(
                                        text = "Please upload a second PDF to run comparison. Tap uploader again.",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { docPicker.launch(arrayOf("application/pdf")) },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Select Document B") }
                                } else {
                                    Text("Ready to run pixel comparison between Draft A and Draft B.")
                                }
                            }
                            else -> {}
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Large execution button
                        val canExecute = tool.type != ToolType.COMPARE || selectedFiles.size >= 2
                        Button(
                            onClick = {
                                if (isProcessing) return@Button
                                coroutineScope.launch(Dispatchers.IO) {
                                    withContext(Dispatchers.Main) {
                                        isProcessing = true
                                        resultFile = null
                                        addLog("Processing document...", LogType.INFO)
                                    }
                                    try {
                                        val outDir = context.cacheDir
                                        val outFile = File(outDir, "out_${System.currentTimeMillis()}.${if (tool.type == ToolType.CONVERT_OCR && convertOcrMode == "pdf_to_img") "jpg" else "pdf"}")

                                        when (tool.type) {
                                            ToolType.EDIT -> {}
                                            ToolType.COMBINE_SPLIT -> {
                                                if (combineSplitMode == "combine") {
                                                    PdfOperations.mergePdfs(selectedFiles, outFile)
                                                    withContext(Dispatchers.Main) {
                                                        resultFile = outFile
                                                        addLog("Documents merged.", LogType.SUCCESS)
                                                        saveToDownloads(outFile, "merged_output.pdf")
                                                    }
                                                } else {
                                                    PdfOperations.splitPdf(selectedFiles[0], splitsInput, outFile)
                                                    withContext(Dispatchers.Main) {
                                                        resultFile = outFile
                                                        addLog("Document pages split successfully.", LogType.SUCCESS)
                                                        saveToDownloads(outFile, "split_output.pdf")
                                                    }
                                                }
                                            }
                                            ToolType.CONVERT_OCR -> {
                                                if (convertOcrMode == "img_to_pdf") {
                                                    PdfOperations.imagesToPdf(selectedFiles, outFile)
                                                    withContext(Dispatchers.Main) {
                                                        resultFile = outFile
                                                        addLog("Images compiled to PDF.", LogType.SUCCESS)
                                                        saveToDownloads(outFile, "compiled_images.pdf")
                                                    }
                                                } else if (convertOcrMode == "pdf_to_img") {
                                                    val bitmap = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], 0, 2.0f)
                                                    if (bitmap != null) {
                                                        FileOutputStream(outFile).use { out ->
                                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            resultFile = outFile
                                                            addLog("PDF page 1 exported to image.", LogType.SUCCESS)
                                                            saveToDownloads(outFile, "pdf_page_1.jpg")
                                                        }
                                                    } else {
                                                        throw Exception("Failed to render page to image.")
                                                    }
                                                } else { // OCR Mode
                                                    if (geminiApiKey.isEmpty()) {
                                                        throw Exception("Gemini API Key missing in Settings dialog.")
                                                    }
                                                    val bmp = PdfRendererHelper.renderPageToBitmap(context, selectedFiles[0], 0, 2.0f)
                                                        ?: throw Exception("Failed to render page for OCR scanner.")
                                                    val ocrResult = OcrHelper.performOcr(bmp, geminiApiKey)
                                                    withContext(Dispatchers.Main) {
                                                        ocrResult.fold(
                                                            onSuccess = { text ->
                                                                extractedOcrText = text
                                                                addLog("OCR scan completed.", LogType.SUCCESS)
                                                            },
                                                            onFailure = { err ->
                                                                addLog("OCR failed: ${err.message}", LogType.ERROR)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            ToolType.SECURITY -> {
                                                if (passwordInput.isEmpty()) {
                                                    throw Exception("Password parameter cannot be blank.")
                                                }
                                                if (securityIsLocked) {
                                                    PdfOperations.unlockPdf(selectedFiles[0], passwordInput, outFile)
                                                    withContext(Dispatchers.Main) {
                                                        resultFile = outFile
                                                        addLog("Document unlocked successfully.", LogType.SUCCESS)
                                                        saveToDownloads(outFile, "unlocked_output.pdf")
                                                    }
                                                } else {
                                                    PdfOperations.protectPdf(selectedFiles[0], passwordInput, outFile)
                                                    withContext(Dispatchers.Main) {
                                                        resultFile = outFile
                                                        addLog("Document encrypted successfully.", LogType.SUCCESS)
                                                        saveToDownloads(outFile, "encrypted_output.pdf")
                                                    }
                                                }
                                            }
                                            ToolType.COMPARE -> {
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

            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    // Dual floating premium FABs for Options (Edit Tools) and Save (with spring auto-hide on zoom)
    if (tool.type == ToolType.EDIT && selectedFiles.isNotEmpty() && buttonsAlpha > 0.01f) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .graphicsLayer {
                    alpha = buttonsAlpha
                    scaleX = 0.85f + 0.15f * buttonsAlpha
                    scaleY = 0.85f + 0.15f * buttonsAlpha
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: Edit Options (opens bottom sheet)
            ExtendedFloatingActionButton(
                onClick = { showEditOptionsSheet = true },
                icon = { Icon(Icons.Rounded.Edit, contentDescription = "Edit Tools") },
                text = { Text("Edit Tools", fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.bounceClick { showEditOptionsSheet = true }
            )

            // Button 2: Save Document changes
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isProcessing) {
                        coroutineScope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main) {
                                isProcessing = true
                                resultFile = null
                                addLog("Saving document changes...", LogType.INFO)
                            }
                            try {
                                val outDir = context.cacheDir
                                val outFile = File(outDir, "edited_${System.currentTimeMillis()}.pdf")
                                PdfOperations.processPdfWorkspace(
                                    inputFile = selectedFiles[0],
                                    keptPageIndices = selectedPageIndices,
                                    pageRotations = pageRotations,
                                    pageCrops = pageCrops,
                                    overlays = pageOverlays,
                                    watermarkText = if (hasWatermark) watermarkText else null,
                                    hasPageNumbers = hasPageNumbers,
                                    outputFile = outFile
                                )
                                withContext(Dispatchers.Main) {
                                    resultFile = outFile
                                    addLog("PDF Workspace changes applied.", LogType.SUCCESS)
                                    saveToDownloads(outFile, "edited_output.pdf")
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    addLog("Failed to save PDF changes: ${e.message}", LogType.ERROR)
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                }
                            }
                        }
                    }
                },
                icon = { Icon(Icons.Rounded.Save, contentDescription = "Save Changes") },
                text = { Text("Save Document", fontWeight = FontWeight.Bold) },
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.bounceClick { }
            )
        }
    }

    // Organize & Layout Workspace (Full Screen with Drag-and-Drop + Menu Options)
    if (showOrganizeDialog) {
        var draggedIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        val itemBounds = remember { mutableStateMapOf<Int, Rect>() }
        var activeMenuPageIdx by remember { mutableStateOf<Int?>(null) }

        Dialog(
            onDismissRequest = { showOrganizeDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Tonal Button
                            Surface(
                                onClick = { showOrganizeDialog = false },
                                modifier = Modifier.size(48.dp).bounceClick { showOrganizeDialog = false },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                                }
                            }

                            Text(
                                text = "Organize Pages",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Append PDF
                                ExpressiveActionButton(
                                    onClick = {
                                        targetInsertPosition = selectedPageIndices.size
                                        insertPdfPicker.launch(arrayOf("application/pdf"))
                                    },
                                    label = "Add PDF",
                                    icon = Icons.Rounded.Add,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )

                                // Done button
                                ExpressiveActionButton(
                                    onClick = { showOrganizeDialog = false },
                                    label = "Done"
                                )
                            }
                        }

                        // Help labels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${selectedPageIndices.size} Pages active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Long press & drag to reorder",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Thumbnails grid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    count = selectedPageIndices.size,
                                    key = { index -> selectedPageIndices[index] }
                                ) { gridIdx ->
                                    val pageIdx = selectedPageIndices[gridIdx]
                                    val pagePos = gridIdx
                                    val rotation = pageRotations[pageIdx] ?: 0
                                    val isDragged = draggedIndex == gridIdx

                                    Card(
                                        modifier = Modifier
                                            .animateItemPlacement()
                                            .fillMaxWidth()
                                            .aspectRatio(0.7f)
                                            .onGloballyPositioned { coords ->
                                                itemBounds[gridIdx] = coords.boundsInRoot()
                                            }
                                            .zIndex(if (isDragged) 1f else 0f)
                                            .graphicsLayer {
                                                if (isDragged) {
                                                    translationX = dragOffset.x
                                                    translationY = dragOffset.y
                                                    scaleX = 1.15f
                                                    scaleY = 1.15f
                                                    shadowElevation = 12f
                                                }
                                            }
                                            .pointerInput(gridIdx) {
                                                detectTapGestures(
                                                    onTap = {
                                                        activeMenuPageIdx = gridIdx
                                                    }
                                                )
                                            }
                                            .pointerInput(gridIdx) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggedIndex = gridIdx
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffset += dragAmount

                                                        val currentBounds = itemBounds[gridIdx]
                                                        if (currentBounds != null) {
                                                            val currentCenter = currentBounds.center + dragOffset
                                                            var targetIndex: Int? = null
                                                            for ((idx, rect) in itemBounds) {
                                                                if (idx != gridIdx && rect.contains(currentCenter)) {
                                                                    targetIndex = idx
                                                                    break
                                                                }
                                                            }
                                                            if (targetIndex != null) {
                                                                val mutableList = selectedPageIndices.toMutableList()
                                                                val temp = mutableList[gridIdx]
                                                                mutableList[gridIdx] = mutableList[targetIndex]
                                                                mutableList[targetIndex] = temp
                                                                selectedPageIndices = mutableList

                                                                swapOverlays(gridIdx + 1, targetIndex + 1)

                                                                draggedIndex = targetIndex
                                                                dragOffset = Offset.Zero
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggedIndex = null
                                                        dragOffset = Offset.Zero
                                                    },
                                                    onDragCancel = {
                                                        draggedIndex = null
                                                        dragOffset = Offset.Zero
                                                    }
                                                )
                                            },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (pageIdx < pdfPagesBitmaps.size) {
                                                Image(
                                                    bitmap = pdfPagesBitmaps[pageIdx].asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer { rotationZ = rotation.toFloat() }
                                                )
                                            }

                                            // Page index badge
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(8.dp)
                                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            ) {
                                                Text(
                                                    text = "${pagePos + 1}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            // Overflow Menu button (with larger touch target and theme background)
                                            IconButton(
                                                onClick = { activeMenuPageIdx = gridIdx },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                                    .size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MoreVert,
                                                    contentDescription = "Options",
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom sheet for individual page options
        if (activeMenuPageIdx != null) {
            val pageIdx = selectedPageIndices[activeMenuPageIdx!!]
            val pagePos = activeMenuPageIdx!!
            val pageNumber = pagePos + 1
            val pageOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { activeMenuPageIdx = null },
                sheetState = pageOptionsSheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Page $pageNumber Options",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Modify rotation, margins, insert other documents, or delete this page from compilation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Rotate 90
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    coroutineScope.launch {
                                        try { pageOptionsSheetState.hide() } catch(e: Exception) {}
                                        activeMenuPageIdx = null
                                        pageRotations = pageRotations + (pageIdx to ((pageRotations[pageIdx] ?: 0) + 90) % 360)
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.RotateRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Rotate 90°", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Crop Margins
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    coroutineScope.launch {
                                        try { pageOptionsSheetState.hide() } catch(e: Exception) {}
                                        activeMenuPageIdx = null
                                        pageToCropIndex = pageIdx
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Crop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Crop Margins", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Insert Before
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    coroutineScope.launch {
                                        try { pageOptionsSheetState.hide() } catch(e: Exception) {}
                                        val targetIdx = pagePos
                                        activeMenuPageIdx = null
                                        targetInsertPosition = targetIdx
                                        insertPdfPicker.launch(arrayOf("application/pdf"))
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Insert Before", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Insert After
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    coroutineScope.launch {
                                        try { pageOptionsSheetState.hide() } catch(e: Exception) {}
                                        val targetIdx = pagePos + 1
                                        activeMenuPageIdx = null
                                        targetInsertPosition = targetIdx
                                        insertPdfPicker.launch(arrayOf("application/pdf"))
                                    }
                                },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Insert After", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Exclude Page
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick {
                                coroutineScope.launch {
                                    try { pageOptionsSheetState.hide() } catch(e: Exception) {}
                                    val targetIdx = pagePos
                                    activeMenuPageIdx = null
                                    selectedPageIndices = selectedPageIndices.filterIndexed { index, _ -> index != targetIdx }
                                    currentPreviewPage = currentPreviewPage.coerceIn(1, selectedPageIndices.size.coerceAtLeast(1))
                                }
                            },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exclude Page", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                Text("Remove this page from the compiled document", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
    }

    // Inline Crop margins Dialog
    pageToCropIndex?.let { pageIdx ->
        val cropsList = pageCrops[pageIdx] ?: listOf(0f, 0f, 0f, 0f)
        var left by remember { mutableStateOf(cropsList[0]) }
        var right by remember { mutableStateOf(cropsList[1]) }
        var top by remember { mutableStateOf(cropsList[2]) }
        var bottom by remember { mutableStateOf(cropsList[3]) }

        AlertDialog(
            onDismissRequest = { pageToCropIndex = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Crop Margins (Page ${pageIdx + 1})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Specify crop margins in points (pt):")
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf(
                        "Left Margin" to left,
                        "Right Margin" to right,
                        "Top Margin" to top,
                        "Bottom Margin" to bottom
                    ).forEachIndexed { idx, (label, value) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("$label: ${value.toInt()} pt", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = value,
                                onValueChange = { newVal ->
                                    when (idx) {
                                        0 -> left = newVal
                                        1 -> right = newVal
                                        2 -> top = newVal
                                        3 -> bottom = newVal
                                    }
                                },
                                valueRange = 0f..200f
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (left == 0f && right == 0f && top == 0f && bottom == 0f) {
                            pageCrops = pageCrops - pageIdx
                        } else {
                            pageCrops = pageCrops + (pageIdx to listOf(left, right, top, bottom))
                        }
                        pageToCropIndex = null
                    },
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Apply Crop") }
            },
            dismissButton = {
                TextButton(onClick = { pageToCropIndex = null }) { Text("Cancel") }
            }
        )
    }

        // True ModalBottomSheet for edit options (E:\journal style)
        if (showEditOptionsSheet) {
            val editOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showEditOptionsSheet = false },
                sheetState = editOptionsSheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Edit Page Options",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = "Choose a tool to modify the document elements or arrange pages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Grid of options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Organize
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    android.util.Log.d("OFFICEPDF", "Organize clicked, launching coroutine")
                                    coroutineScope.launch {
                                        try {
                                            android.util.Log.d("OFFICEPDF", "Calling editOptionsSheetState.hide()")
                                            editOptionsSheetState.hide()
                                            android.util.Log.d("OFFICEPDF", "editOptionsSheetState.hide() completed")
                                        } catch (e: Exception) {
                                            android.util.Log.e("OFFICEPDF", "Error hiding sheet: ${e.message}", e)
                                        }
                                        android.util.Log.d("OFFICEPDF", "Setting showEditOptionsSheet = false")
                                        showEditOptionsSheet = false
                                        android.util.Log.d("OFFICEPDF", "Delaying 150ms")
                                        delay(150)
                                        android.util.Log.d("OFFICEPDF", "Setting showOrganizeDialog = true")
                                        showOrganizeDialog = true
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Organize", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Add Text
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    showEditOptionsSheet = false
                                    val newText = PdfOperations.PageOverlay(
                                        type = "text",
                                        text = "Double tap to edit",
                                        xFraction = 0.3f,
                                        yFraction = 0.3f,
                                        widthFraction = 0.4f,
                                        heightFraction = 0.08f,
                                        pageNumber = currentPreviewPage
                                    )
                                    pageOverlays = pageOverlays + newText
                                    selectedOverlayId = newText.id
                                    addLog("Added text overlay.")
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Add Text", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Add Image
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    coroutineScope.launch {
                                        try {
                                            editOptionsSheetState.hide()
                                        } catch (e: Exception) {}
                                        showEditOptionsSheet = false
                                        overlayImagePicker.launch("image/*")
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Add Image", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Add Signature
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .bounceClick {
                                    showEditOptionsSheet = false
                                    val newSig = PdfOperations.PageOverlay(
                                        type = "signature",
                                        text = sigText,
                                        xFraction = 0.3f,
                                        yFraction = 0.6f,
                                        widthFraction = 0.3f,
                                        heightFraction = 0.1f,
                                        pageNumber = currentPreviewPage
                                    )
                                    pageOverlays = pageOverlays + newSig
                                    selectedOverlayId = newSig.id
                                    addLog("Added signature.")
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Add Sign", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // Dynamic Delete Overlay option (slides in when overlay is selected)
                    if (selectedOverlayId != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick {
                                    showEditOptionsSheet = false
                                    pageOverlays = pageOverlays.filter { it.id != selectedOverlayId }
                                    selectedOverlayId = null
                                    addLog("Removed overlay.")
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Selected Element", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Text(
                        text = "Document Configuration",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Watermark toggle Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WaterDrop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Add Watermark",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Overlay diagonal text across pages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = hasWatermark,
                                    onCheckedChange = { hasWatermark = it },
                                    thumbContent = if (hasWatermark) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        }
                                    } else {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        }
                                    }
                                )
                            }

                            // Conditional Watermark text input
                            if (hasWatermark) {
                                TextField(
                                    value = watermarkText,
                                    onValueChange = { watermarkText = it },
                                    label = { Text("Watermark Text") },
                                    placeholder = { Text("e.g. CONFIDENTIAL") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    singleLine = true
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Page Numbers toggle Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FormatListNumbered,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Add Page Numbers",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Stamp sequentially at the bottom center",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = hasPageNumbers,
                                    onCheckedChange = { hasPageNumbers = it },
                                    thumbContent = if (hasPageNumbers) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        }
                                    } else {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
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

@Composable
fun RowScope.ExpressiveNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    val weight by animateFloatAsState(
        targetValue = if (selected) 2.0f else 1f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, StiffnessLow),
        label = "navWeight"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        label = "navBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navTint"
    )

    Surface(
        modifier = Modifier.weight(weight).height(52.dp).bounceClick { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp))
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryChip(selected: Boolean, onClick: () -> Unit, label: String) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.bounceClick { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RecentFilesTab(
    onReRunTool: (String) -> Unit
) {
    val context = LocalContext.current
    var filesList by remember { mutableStateOf(RecentFilesManager.getRecentFiles(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Files",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (filesList.isNotEmpty()) {
                TextButton(
                    onClick = {
                        filesList.forEach { RecentFilesManager.deleteRecentFile(context, it.path) }
                        filesList = listOf()
                    }
                ) {
                    Text("Clear All")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filesList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your output history will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filesList.forEachIndexed { index, recent ->
                    val shape = groupedListItemShape(index, filesList.size)
                    
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recent.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ) {
                                        Text(
                                            text = recent.operation,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "${(recent.size / 1024f).roundToInt()} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row {
                                IconButton(
                                    onClick = { viewFileExternally(context, File(recent.path)) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = "View",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { shareFile(context, File(recent.path)) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        RecentFilesManager.deleteRecentFile(context, recent.path)
                                        filesList = RecentFilesManager.getRecentFiles(context)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

object RecentFilesManager {
    private const val PREFS_NAME = "RecentFilesPrefs"
    private const val KEY_FILES = "recent_files"

    fun saveRecentFile(context: Context, filePath: String, operationName: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = sharedPrefs.getString(KEY_FILES, "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(currentJson)
            val entry = org.json.JSONObject()
            entry.put("path", filePath)
            entry.put("operation", operationName)
            entry.put("timestamp", System.currentTimeMillis())
            
            val newList = mutableListOf<org.json.JSONObject>()
            newList.add(entry)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("path") != filePath) {
                    newList.add(obj)
                }
            }
            
            val saveArray = org.json.JSONArray()
            newList.take(20).forEach { saveArray.put(it) }
            sharedPrefs.edit().putString(KEY_FILES, saveArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRecentFiles(context: Context): List<RecentFile> {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = sharedPrefs.getString(KEY_FILES, "[]") ?: "[]"
        val list = mutableListOf<RecentFile>()
        try {
            val jsonArray = org.json.JSONArray(currentJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val file = File(obj.getString("path"))
                if (file.exists()) {
                    list.add(
                        RecentFile(
                            path = obj.getString("path"),
                            name = file.name,
                            operation = obj.getString("operation"),
                            timestamp = obj.getLong("timestamp"),
                            size = file.length()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun deleteRecentFile(context: Context, filePath: String) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentJson = sharedPrefs.getString(KEY_FILES, "[]") ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(currentJson)
            val saveArray = org.json.JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("path") != filePath) {
                    saveArray.put(obj)
                }
            }
            sharedPrefs.edit().putString(KEY_FILES, saveArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun groupedListItemShape(index: Int, totalCount: Int): Shape {
    if (totalCount == 1) return RoundedCornerShape(28.dp)
    return when (index) {
        0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        totalCount - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(4.dp)
    }
}

fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun viewFileExternally(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open PDF files", Toast.LENGTH_SHORT).show()
    }
}

data class SelectorItem(
    val id: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun ExpressiveSelector(
    items: List<SelectorItem>,
    selectedItem: String,
    onSelectionChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            val segmentWidth = totalWidth / items.size
            val selectedIndex = items.indexOfFirst { it.id == selectedItem }.coerceAtLeast(0)

            // Animated background slider using spring
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "indicatorOffset"
            )

            // Active sliding background
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp)
                    )
            )

            // Items Row
            Row(modifier = Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val isSelected = item.id == selectedItem
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                      else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "contentColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bounceClick { onSelectionChanged(item.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                color = contentColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.ExpressiveIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp).bounceClick { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PeakBottomSheetLayout(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Centered Drag handle
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            content()
        }
    }
}

@Composable
fun UnifiedCanvasHud(
    currentPage: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    zoomScale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Page controls
            IconButton(
                onClick = onPrevPage,
                enabled = currentPage > 1,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = "Previous Page",
                    modifier = Modifier.size(20.dp),
                    tint = if (currentPage > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            Text(
                text = "Page $currentPage of $totalPages",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
                onClick = onNextPage,
                enabled = currentPage < totalPages,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Next Page",
                    modifier = Modifier.size(20.dp),
                    tint = if (currentPage < totalPages) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            // Divider
            VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Zoom controls
            IconButton(
                onClick = onZoomOut,
                enabled = zoomScale > 1f,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(20.dp),
                    tint = if (zoomScale > 1f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            Text(
                text = "${(zoomScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onZoomIn,
                enabled = zoomScale < 5f,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Zoom In",
                    modifier = Modifier.size(20.dp),
                    tint = if (zoomScale < 5f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    tools: List<Tool>,
    onToolClick: (Tool) -> Unit,
    onRecentFileClick: (RecentFile) -> Unit
) {
    val context = LocalContext.current
    var filesList by remember { mutableStateOf(RecentFilesManager.getRecentFiles(context)) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Welcome and time-based greeting
        Column {
            val greeting = remember {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                when {
                    hour < 12 -> "Good morning"
                    hour < 17 -> "Good afternoon"
                    else -> "Good evening"
                }
            }
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Access your documents and professional tools.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Bento Grid of tools
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Professional Suites",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Edit suite takes a larger full-width card
            BentoCard(
                tool = tools[0],
                modifier = Modifier.fillMaxWidth(),
                onClick = { onToolClick(tools[0]) }
            )

            // Split and Converter in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BentoCard(
                    tool = tools[1],
                    modifier = Modifier.weight(1f),
                    onClick = { onToolClick(tools[1]) }
                )
                BentoCard(
                    tool = tools[2],
                    modifier = Modifier.weight(1f),
                    onClick = { onToolClick(tools[2]) }
                )
            }

            // Security and Compare in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BentoCard(
                    tool = tools[3],
                    modifier = Modifier.weight(1f),
                    onClick = { onToolClick(tools[3]) }
                )
                BentoCard(
                    tool = tools[4],
                    modifier = Modifier.weight(1f),
                    onClick = { onToolClick(tools[4]) }
                )
            }
        }

        // Recent Documents list
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Documents",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (filesList.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            filesList.forEach { RecentFilesManager.deleteRecentFile(context, it.path) }
                            filesList = listOf()
                        }
                    ) {
                        Text("Clear All")
                    }
                }
            }

            if (filesList.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your output history will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filesList.take(5).forEachIndexed { index, recent ->
                        val shape = groupedListItemShape(index, filesList.size.coerceAtMost(5))
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { onRecentFileClick(recent) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.InsertDriveFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = recent.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        ) {
                                            Text(
                                                text = recent.operation,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "${(recent.size / 1024f).roundToInt()} KB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row {
                                    IconButton(
                                        onClick = { viewFileExternally(context, File(recent.path)) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Visibility,
                                            contentDescription = "View",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { shareFile(context, File(recent.path)) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "Share",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BentoCard(
    tool: Tool,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = tool.color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = tool.color,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
