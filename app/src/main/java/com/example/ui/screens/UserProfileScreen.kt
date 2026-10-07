package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.model.*
import com.example.ui.BookSphereViewModel
import com.example.ui.components.bookSpineEffect
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun UserProfileScreen(
    viewModel: BookSphereViewModel,
    allBooks: List<Book>,
    onSelectBook: (Book) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currentProfile by viewModel.currentProfile.collectAsState()
    val userRole by viewModel.userRole.collectAsState()

    // Editable state
    var isEditingBio by remember { mutableStateOf(false) }
    var editedName by remember(currentProfile?.name) { mutableStateOf(currentProfile?.name ?: "Literary Reader") }
    var editedBio by remember(currentProfile?.bio) {
        mutableStateOf(currentProfile?.bio ?: "Passionate literary enthusiast, avid reader of speculative fiction & philosophy.")
    }
    var avatarUriString by remember(currentProfile?.photoUrl) { mutableStateOf(currentProfile?.photoUrl) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var showCameraSourceDialog by remember { mutableStateOf(false) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var selectedListForDetails by remember { mutableStateOf<ReadingList?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Camera Image File & URI State
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraPhotoFile by remember { mutableStateOf<File?>(null) }

    // Helper to create camera temp file
    fun prepareCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        cameraPhotoFile = file
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        cameraPhotoUri = uri
        return uri
    }

    // Camera Take Picture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val file = cameraPhotoFile
            if (file != null && file.exists()) {
                isUploadingPhoto = true
                statusMessage = "Uploading photo to BookSphere..."
                viewModel.uploadProfilePhotoFile(
                    file = file,
                    onSuccess = { photoUrl ->
                        isUploadingPhoto = false
                        avatarUriString = photoUrl
                        statusMessage = "Profile picture updated!"
                        val current = currentProfile
                        if (current != null) {
                            viewModel.updateUserProfile(
                                name = editedName,
                                bio = editedBio,
                                photoUrl = photoUrl,
                                readingLists = current.readingLists
                            )
                        }
                    },
                    onError = { err ->
                        isUploadingPhoto = false
                        statusMessage = "Photo upload: $err"
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Camera Bitmap Fallback Launcher (Take Snapshot)
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "avatar_snap_${System.currentTimeMillis()}.jpg")
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                isUploadingPhoto = true
                statusMessage = "Uploading profile snapshot..."
                viewModel.uploadProfilePhotoFile(
                    file = file,
                    onSuccess = { photoUrl ->
                        isUploadingPhoto = false
                        avatarUriString = photoUrl
                        statusMessage = "Profile photo updated!"
                        val current = currentProfile
                        if (current != null) {
                            viewModel.updateUserProfile(
                                name = editedName,
                                bio = editedBio,
                                photoUrl = photoUrl,
                                readingLists = current.readingLists
                            )
                        }
                    },
                    onError = { err ->
                        isUploadingPhoto = false
                        statusMessage = "Photo upload: $err"
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving picture: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val uri = prepareCameraUri()
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                // If FileProvider issues arise, fallback to preview bitmap launcher
                takePicturePreviewLauncher.launch()
            }
        } else {
            Toast.makeText(context, "Camera permission is needed to take a profile picture", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery Picker Launcher
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheDir, "gallery_avatar_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                isUploadingPhoto = true
                statusMessage = "Uploading image..."
                viewModel.uploadProfilePhotoFile(
                    file = tempFile,
                    onSuccess = { photoUrl ->
                        isUploadingPhoto = false
                        avatarUriString = photoUrl
                        statusMessage = "Avatar updated successfully!"
                        val current = currentProfile
                        if (current != null) {
                            viewModel.updateUserProfile(
                                name = editedName,
                                bio = editedBio,
                                photoUrl = photoUrl,
                                readingLists = current.readingLists
                            )
                        }
                    },
                    onError = { err ->
                        isUploadingPhoto = false
                        statusMessage = "Photo error: $err"
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val readingLists = currentProfile?.readingLists ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("user_profile_screen")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Navigation Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (userRole == UserRole.AUTHOR) "Author & Reader" else "Verified Reader",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Hero Profile Card with Avatar & Camera Button
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Avatar Box with Camera Overlay
                    Box(
                        modifier = Modifier.size(110.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            if (!avatarUriString.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarUriString,
                                    contentDescription = "Reader Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Default Avatar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(76.dp)
                                    )
                                }
                            }
                        }

                        // Camera Action Button
                        FilledIconButton(
                            onClick = { showCameraSourceDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("camera_upload_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = stringResource(R.string.profile_take_photo),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isUploadingPhoto) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Uploading image to cloud storage...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Reader Name & Email
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentProfile?.name ?: "BookSphere Reader",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentProfile?.email?.ifBlank { "reader@booksphere.app" } ?: "reader@booksphere.app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Literary Reading Badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileStatBadge(icon = Icons.Outlined.AutoStories, label = "${readingLists.sumOf { it.bookIds.size }} Books Saved")
                        ProfileStatBadge(icon = Icons.Outlined.Bookmarks, label = "${readingLists.size} Lists")
                        ProfileStatBadge(icon = Icons.Outlined.Star, label = "Fair Patron")
                    }
                }
            }
        }

        // Bio & Reading Philosophy Section (Editable)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.profile_bio_label),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(
                            onClick = {
                                if (isEditingBio) {
                                    // Save changes
                                    val current = currentProfile
                                    viewModel.updateUserProfile(
                                        name = editedName,
                                        bio = editedBio,
                                        photoUrl = avatarUriString,
                                        readingLists = current?.readingLists ?: emptyList(),
                                        onSuccess = {
                                            statusMessage = "Profile updated successfully!"
                                        }
                                    )
                                    isEditingBio = false
                                } else {
                                    isEditingBio = true
                                }
                            },
                            modifier = Modifier.testTag("edit_bio_button")
                        ) {
                            Icon(
                                imageVector = if (isEditingBio) Icons.Default.Check else Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isEditingBio) "Save" else "Edit Bio")
                        }
                    }

                    if (isEditingBio) {
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { editedName = it },
                            label = { Text("Display Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_name_input"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = editedBio,
                            onValueChange = { editedBio = it },
                            label = { Text("Reader Bio") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_bio_input"),
                            minLines = 3,
                            maxLines = 5,
                            placeholder = { Text("Write about your favorite genres, philosophy, or what books mean to you...") }
                        )

                        Button(
                            onClick = {
                                val current = currentProfile
                                viewModel.updateUserProfile(
                                    name = editedName,
                                    bio = editedBio,
                                    photoUrl = avatarUriString,
                                    readingLists = current?.readingLists ?: emptyList(),
                                    onSuccess = {
                                        statusMessage = "Profile updated!"
                                    }
                                )
                                isEditingBio = false
                            },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("save_bio_button")
                        ) {
                            Text(stringResource(R.string.profile_save_changes))
                        }
                    } else {
                        Text(
                            text = currentProfile?.bio ?: editedBio,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Favorite Genres Tags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (currentProfile?.favoriteGenres ?: listOf("Speculative Fiction", "Classic Literature", "Philosophy")).forEach { genre ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Reading Lists Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.profile_reading_lists_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Curate, organize, and share your personalized book collections",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = { showCreateListDialog = true },
                    modifier = Modifier.testTag("create_reading_list_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New List")
                }
            }
        }

        // Reading Lists Items
        if (readingLists.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No reading lists yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tap 'New List' above to curate your first themed reading collection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(readingLists) { list ->
                ReadingListCard(
                    readingList = list,
                    allBooks = allBooks,
                    onOpenDetails = { selectedListForDetails = list },
                    onDelete = { viewModel.removeReadingList(list.id) },
                    onSelectBook = onSelectBook
                )
            }
        }

        // Status Toast Feedback
        statusMessage?.let { msg ->
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Camera / Source Choice Dialog
    if (showCameraSourceDialog) {
        AlertDialog(
            onDismissRequest = { showCameraSourceDialog = false },
            title = {
                Text(
                    text = "Update Profile Picture",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose how you would like to capture or select your photo:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Option 1: Take Photo with Camera
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showCameraSourceDialog = false
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    try {
                                        val uri = prepareCameraUri()
                                        takePictureLauncher.launch(uri)
                                    } catch (_: Exception) {
                                        takePicturePreviewLauncher.launch()
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Take Photo with Camera",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Snap a new picture right now using your device camera",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Gallery Picker
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showCameraSourceDialog = false
                                pickPhotoLauncher.launch("image/*")
                            },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "Choose from Gallery",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Select an existing image from your photo album",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCameraSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create New Reading List Dialog
    if (showCreateListDialog) {
        var listName by remember { mutableStateOf("") }
        var listDesc by remember { mutableStateOf("") }
        var isPublic by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = {
                Text("Create Reading List", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = listName,
                        onValueChange = { listName = it },
                        label = { Text("List Title") },
                        placeholder = { Text("e.g. Philosophical Sci-Fi") },
                        modifier = Modifier.fillMaxWidth().testTag("new_list_name_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = listDesc,
                        onValueChange = { listDesc = it },
                        label = { Text("Description") },
                        placeholder = { Text("Brief note about this curated collection...") },
                        modifier = Modifier.fillMaxWidth().testTag("new_list_desc_input"),
                        minLines = 2
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isPublic) "Public (Shareable)" else "Private Collection",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = isPublic,
                            onCheckedChange = { isPublic = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (listName.isNotBlank()) {
                            viewModel.addReadingList(listName.trim(), listDesc.trim(), isPublic)
                            showCreateListDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_list_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detailed Reading List Management Dialog
    selectedListForDetails?.let { readingList ->
        ReadingListDetailDialog(
            readingList = readingList,
            allBooks = allBooks,
            onDismiss = { selectedListForDetails = null },
            onToggleBook = { bookId ->
                viewModel.toggleBookInReadingList(readingList.id, bookId)
                // Refresh local dialog snapshot
                selectedListForDetails = viewModel.currentProfile.value?.readingLists?.find { it.id == readingList.id }
            },
            onSelectBook = { book ->
                selectedListForDetails = null
                onSelectBook(book)
            }
        )
    }
}

@Composable
fun ReadingListCard(
    readingList: ReadingList,
    allBooks: List<Book>,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit,
    onSelectBook: (Book) -> Unit
) {
    val booksInList = remember(readingList.bookIds, allBooks) {
        allBooks.filter { readingList.bookIds.contains(it.id) }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reading_list_card_${readingList.id}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (readingList.isPublic) Icons.Outlined.Public else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = readingList.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${booksInList.size} books",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onOpenDetails,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircleOutline,
                            contentDescription = "Manage Books",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete List",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (readingList.description.isNotBlank()) {
                Text(
                    text = readingList.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Preview Row of Books in this List
            if (booksInList.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(booksInList) { book ->
                        Surface(
                            modifier = Modifier
                                .width(80.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectBook(book) },
                            shape = RoundedCornerShape(6.dp),
                            shadowElevation = 2.dp
                        ) {
                            Column {
                                AsyncImage(
                                    model = book.coverUrl,
                                    contentDescription = book.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .height(110.dp)
                                        .fillMaxWidth()
                                )
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetails() },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "List is empty. Tap to add books from the catalog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReadingListDetailDialog(
    readingList: ReadingList,
    allBooks: List<Book>,
    onDismiss: () -> Unit,
    onToggleBook: (String) -> Unit,
    onSelectBook: (Book) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = readingList.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Check/uncheck books to add or remove them from this list",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allBooks) { book ->
                        val isInList = readingList.bookIds.contains(book.id)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleBook(book.id) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isInList) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isInList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AsyncImage(
                                    model = book.coverUrl,
                                    contentDescription = book.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 56.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = book.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${book.genre} • $${String.format("%.2f", book.price)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Checkbox(
                                    checked = isInList,
                                    onCheckedChange = { onToggleBook(book.id) }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun ProfileStatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
