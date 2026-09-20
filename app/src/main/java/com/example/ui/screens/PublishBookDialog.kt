package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
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
    var genre by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("English") }
    var isFree by remember { mutableStateOf(false) }
    var previewPagesStr by remember { mutableStateOf("3") }
    
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfFileName by remember { mutableStateOf<String?>(null) }
    var pdfFileSize by remember { mutableStateOf<String?>(null) }
    var pdfTotalPages by remember { mutableStateOf(0) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coverUri = uri
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
                        pdfFileSize = "${(sizeBytes / (1024 * 1024))} MB"
                    }
                    
                    // Copy to internal storage
                    val internalFile = File(context.filesDir, "${UUID.randomUUID()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    pdfUri = Uri.fromFile(internalFile)
                    
                    // Count pages
                    val fd = android.os.ParcelFileDescriptor.open(internalFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(fd)
                    pdfTotalPages = renderer.pageCount
                    renderer.close()
                    fd.close()
                    
                } catch (e: Exception) {
                    errorMessage = "Failed to process PDF: ${e.message}"
                    pdfFileName = null
                    pdfUri = null
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
                    Text("Publish New Manuscript", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                if (errorMessage != null) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(text = errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Book Title *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description *") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre *") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.weight(1f))
                    }
                    
                    OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text("Language *") }, modifier = Modifier.fillMaxWidth())
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isFree, onCheckedChange = { isFree = it })
                        Text("Make this book FREE")
                    }
                    
                    if (!isFree) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = priceStr, onValueChange = { priceStr = it }, label = { Text("Price (₹) *") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(value = previewPagesStr, onValueChange = { previewPagesStr = it }, label = { Text("Preview Pages *") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    }
                    
                    // Cover Image Upload
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clickable { coverPicker.launch("image/*") },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        if (coverUri != null) {
                            AsyncImage(model = coverUri, contentDescription = "Cover Image", modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Upload Cover Image *", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    
                    // PDF Upload
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clickable { pdfPicker.launch("application/pdf") },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (isProcessing) {
                                CircularProgressIndicator()
                                Text("Processing manuscript...", modifier = Modifier.padding(top = 8.dp))
                            } else if (pdfFileName != null) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(pdfFileName!!, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                Text("$pdfFileSize • $pdfTotalPages pages", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Upload Manuscript PDF *", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (title.isBlank() || author.isBlank() || description.isBlank() || genre.isBlank() || language.isBlank()) {
                            errorMessage = "Please fill in all text fields."
                            return@Button
                        }
                        if (coverUri == null) {
                            errorMessage = "Please upload a cover image."
                            return@Button
                        }
                        if (pdfUri == null) {
                            errorMessage = "Please upload a PDF manuscript."
                            return@Button
                        }
                        val finalPrice = if (isFree) 0.0 else priceStr.toDoubleOrNull()
                        if (finalPrice == null) {
                            errorMessage = "Please enter a valid price."
                            return@Button
                        }
                        val prevPages = previewPagesStr.toIntOrNull() ?: 3
                        
                        onPublish(
                            title, author, description, genre, category, finalPrice, language,
                            coverUri.toString(), pdfUri.toString(), isFree, prevPages, pdfTotalPages
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isProcessing
                ) {
                    Text("Publish Book")
                }
            }
        }
    }
}
