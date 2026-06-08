package com.rebelroot.omni.tools.scanner

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult as MlScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions as MlScannerOptions
import com.rebelroot.omni.tools.locker.PrivateLockerManager
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hazeState = remember { HazeState() }
    
    // States
    var scannedPages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val lockerManager = remember { PrivateLockerManager(context) }

    // Setup ML Kit Scanner client
    val scannerOptions = remember {
        MlScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(
                MlScannerOptions.RESULT_FORMAT_JPEG,
                MlScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(MlScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scannerClient = remember { GmsDocumentScanning.getClient(scannerOptions) }

    // Activity launcher for the Document Scanning pre-built bottom sheet
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = MlScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.let { pages ->
                scannedPages = pages.map { it.imageUri }
            }
            scanResult?.pdf?.let { pdf ->
                currentPdfUri = pdf.uri
                showSaveDialog = true
            }
        }
    }

    // Save helper: copy PDF Uri to Public Downloads directory securely via MediaStore
    fun saveToDownloads(pdfUri: Uri) {
        coroutineScope.launch {
            val resolver = context.contentResolver
            val displayName = "Omni_Scan_${System.currentTimeMillis()}.pdf"
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (targetUri != null) {
                withContext(Dispatchers.IO) {
                    try {
                        resolver.openInputStream(pdfUri)?.use { input ->
                            resolver.openOutputStream(targetUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Saved scan to Downloads: $displayName", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to copy file to downloads.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Save helper: copy PDF Uri to Sandboxed encrypted locker room
    fun saveToPrivateLocker(pdfUri: Uri) {
        coroutineScope.launch {
            try {
                val displayName = "Scan_${System.currentTimeMillis()}.pdf"
                lockerManager.saveUriToLocker(pdfUri, displayName, "application/pdf")
                Toast.makeText(context, "Scan encrypted and saved to Vault.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Locker encryption failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .hazeChild(state = hazeState)
                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "📄 Document Scanner",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Glassmorphic background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState)
                    .background(MaterialTheme.colorScheme.background)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (scannedPages.isEmpty()) {
                    // Empty landing state card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Scan Physical Documents",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Auto-detects edges, corrects perspective, and optimizes contrast dynamically. Makes clean multipage PDFs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            
                            Button(
                                onClick = {
                                    val activity = context as ComponentActivity
                                    scannerClient.getStartScanIntent(activity)
                                        .addOnSuccessListener { intentSender ->
                                            scannerLauncher.launch(
                                                IntentSenderRequest.Builder(intentSender).build()
                                            )
                                        }
                                        .addOnFailureListener { e ->
                                            Toast.makeText(context, "Scanning initiation error.", Toast.LENGTH_SHORT).show()
                                        }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Launch Scanner")
                            }
                        }
                    }
                } else {
                    // Previews of scanned sheets
                    Text(
                        text = "${scannedPages.size} Page(s) Scanned",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(16.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(360.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(scannedPages) { pageUri ->
                            Box(
                                modifier = Modifier
                                    .width(240.dp)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(pageUri),
                                    contentDescription = "Scanned Page",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                scannedPages = emptyList()
                                currentPdfUri = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text("Discard Scan")
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Save Document")
                        }
                    }
                }
            }

            // Save PDF dialog
            if (showSaveDialog && currentPdfUri != null) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Save Scanned PDF") },
                    text = { Text("Choose a destination path. Normal Downloads can be read by other apps. Private Locker is encrypted and locked.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSaveDialog = false
                                saveToPrivateLocker(currentPdfUri!!)
                                scannedPages = emptyList()
                                currentPdfUri = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("🔒 Save Privately")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                showSaveDialog = false
                                saveToDownloads(currentPdfUri!!)
                                scannedPages = emptyList()
                                currentPdfUri = null
                            }
                        ) {
                            Text("Save to Downloads")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(16.dp))
                )
            }
        }
    }
}
