package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.SampleData
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishBookDialog(
    onDismiss: () -> Unit,
    onPublish: (title: String, author: String, desc: String, genre: String, category: String, price: Double, language: String, coverUri: String, pdfUri: String, isFree: Boolean, previewPages: Int, totalPages: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Fiction") }
    var category by remember { mutableStateOf("Literature") }
    var priceStr by remember { mutableStateOf("299") }
    var language by remember { mutableStateOf("English") }
    var isFree by remember { mutableStateOf(false) }
    var previewPagesStr by remember { mutableStateOf("3") }
    
    val defaultCovers: List<Pair<String, String>> = remember {
        listOf(
            Pair("Cosmic", SampleData.sampleCelestialCartographer.coverUrl),
            Pair("Atlas", SampleData.staffPickBook.coverUrl),
            Pair("Sci-Fi", SampleData.trendingBooks.firstOrNull()?.coverUrl ?: SampleData.sampleCelestialCartographer.coverUrl),
            Pair("Fantasy", SampleData.trendingBooks.getOrNull(1)?.coverUrl ?: SampleData.sampleCelestialCartographer.coverUrl)
        )
    }
    
    var selectedCoverUrl by remember { mutableStateOf(defaultCovers[0].second) }
    var customCoverUri by remember { mutableStateOf<Uri?>(null) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFileName by remember { mutableStateOf<String?>("Sample_Manuscript_Draft.pdf") }
    var pdfFileSize by remember { mutableStateOf<String?>("2.4 MB") }
    var pdfTotalPages by remember { mutableStateOf(180) }
    var isUsingPresetManuscript by remember { mutableStateOf(true) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            customCoverUri = uri
            selectedCoverUrl = uri.toString()
        }
    }
    
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessing = true
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        cursor.moveToFirst()
                        pdfFileName = cursor.getString(nameIndex)
                        val sizeBytes = cursor.getLong(sizeIndex)
                        pdfFileSize = "${(sizeBytes / (1024 * 1024)).coerceAtLeast(1)} MB"
                    }
                    
                    // Copy to internal storage
                    val internalFile = File(context.filesDir, "${UUID.randomUUID()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    pdfUri = Uri.fromFile(internalFile)
                    
                    // Count pages if possible
                    try {
                        val fd = android.os.ParcelFileDescriptor.open(internalFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(fd)
                        pdfTotalPages = renderer.pageCount
                        renderer.close()
                        fd.close()
                    } catch (_: Exception) {
                        pdfTotalPages = 150
                    }
                    isUsingPresetManuscript = false
                } catch (e: Exception) {
                    errorMessage = "Could not parse selected PDF: ${e.message}"
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Publish New Manuscript",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Syncs directly to Supabase PostgREST & Storage for all readers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Book / Manuscript Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author Display Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Synopsis / Description *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text("Genre *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("Language *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = isFree, onCheckedChange = { isFree = it })
                        Text(
                            "Distribute as 100% Free / Open Access",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    if (!isFree) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = priceStr,
                                onValueChange = { priceStr = it },
                                label = { Text("Price (₹) *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = previewPagesStr,
                                onValueChange = { previewPagesStr = it },
                                label = { Text("Free Preview Pages *") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                    
                    // Cover Art Selector
                    Text(
                        text = "Cover Artwork",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        defaultCovers.forEach { (label, url) ->
                            val isSelected = selectedCoverUrl == url && customCoverUri == null
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .clickable {
                                        customCoverUri = null
                                        selectedCoverUrl = url
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(2.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    OutlinedButton(
                        onClick = { coverPicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (customCoverUri != null) "Custom Image Selected" else "Or Upload Custom Cover Image")
                    }
                    
                    // Manuscript PDF Section
                    Text(
                        text = "Manuscript Document (PDF / ePub)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isUsingPresetManuscript) Icons.Default.AutoStories else Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pdfFileName ?: "Manuscript Ready",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (isUsingPresetManuscript) "Preset Fair-Standard Manuscript ($pdfTotalPages pages)" else "$pdfFileSize • $pdfTotalPages pages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { pdfPicker.launch("application/pdf") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick PDF File", fontSize = 12.sp)
                                }
                                
                                Button(
                                    onClick = {
                                        isUsingPresetManuscript = true
                                        pdfFileName = "Draft_${title.ifBlank { "Manuscript" }}.pdf"
                                        pdfTotalPages = 180
                                        pdfUri = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isUsingPresetManuscript) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isUsingPresetManuscript) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("Use Auto-Draft", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            errorMessage = "Please enter a book title."
                            return@Button
                        }
                        if (description.isBlank()) {
                            errorMessage = "Please enter a synopsis or description."
                            return@Button
                        }
                        val finalAuthor = author.ifBlank { "Independent Author" }
                        val finalPrice = if (isFree) 0.0 else priceStr.toDoubleOrNull()
                        if (finalPrice == null) {
                            errorMessage = "Please enter a valid price."
                            return@Button
                        }
                        val prevPages = previewPagesStr.toIntOrNull() ?: 3
                        val chosenCover = customCoverUri?.toString() ?: selectedCoverUrl
                        val chosenPdf = pdfUri?.toString() ?: "internal_preset_pdf"
                        
                        onPublish(
                            title, finalAuthor, description, genre, category, finalPrice, language,
                            chosenCover, chosenPdf, isFree, prevPages, pdfTotalPages
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isProcessing,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publish to BookSphere (Supabase)")
                }
            }
        }
    }
}
