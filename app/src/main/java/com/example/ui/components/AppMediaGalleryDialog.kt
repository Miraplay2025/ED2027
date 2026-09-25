package com.example.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.engine.MediaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class GalleryMediaItem(
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val filePath: String? = null
)

/**
 * Galeria Exclusiva da Própria App:
 * - O usuário visualiza exclusivamente mídias de imagens e vídeos para selecionar e incluir no projeto.
 * - Permite filtrar por Todas, Imagens ou Vídeos e selecionar múltiplas mídias de uma só vez.
 */
@Composable
fun AppMediaGalleryDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onConfirmSelection: (List<Uri>) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var allMediaItems by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var filterTab by remember { mutableIntStateOf(0) } // 0 = Todas, 1 = Imagens, 2 = Vídeos

    // Seletor nativo Android Photo Picker restrito estritamente a Imagens e Vídeos
    val visualMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onConfirmSelection(uris)
            onDismiss()
        }
    }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            selectedUris.clear()
            allMediaItems = loadDeviceImagesAndVideos(context)
        }
    }

    val filteredItems = remember(allMediaItems, filterTab) {
        when (filterTab) {
            1 -> allMediaItems.filter { !it.isVideo }
            2 -> allMediaItems.filter { it.isVideo }
            else -> allMediaItems
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("app_exclusive_media_gallery_dialog"),
            color = Color(0xFF141722),
            border = BorderStroke(1.5.dp, Color(0xFF2E354B)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cabeçalho da Galeria Exclusiva
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E2638),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Galeria Exclusiva do App",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Apenas mídias de imagens e vídeos para o projeto",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_media_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Galeria",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filtros: Todas (Imagens + Vídeos), Apenas Imagens, Apenas Vídeos + Botão Explorar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filterTab == 0,
                            onClick = { filterTab = 0 },
                            label = { Text("Todas (${allMediaItems.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF00E5FF)
                            )
                        )
                        FilterChip(
                            selected = filterTab == 1,
                            onClick = { filterTab = 1 },
                            label = { Text("Imagens", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF00E5FF)
                            )
                        )
                        FilterChip(
                            selected = filterTab == 2,
                            onClick = { filterTab = 2 },
                            label = { Text("Vídeos", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF00E5FF)
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            visualMediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("system_visual_media_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mais Mídias", fontSize = 10.sp, color = Color(0xFF00E5FF))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Grade de Imagens e Vídeos
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (filteredItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhuma mídia encontrada nesta categoria.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 102.dp),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("gallery_media_grid")
                        ) {
                            items(filteredItems, key = { it.uri.toString() }) { item ->
                                val isSelected = item.uri in selectedUris
                                val selectionOrder = if (isSelected) selectedUris.indexOf(item.uri) + 1 else 0

                                GalleryMediaGridCard(
                                    item = item,
                                    isSelected = isSelected,
                                    selectionOrder = selectionOrder,
                                    onClick = {
                                        if (isSelected) {
                                            selectedUris.remove(item.uri)
                                        } else {
                                            selectedUris.add(item.uri)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF262C3E))
                Spacer(modifier = Modifier.height(10.dp))

                // Rodapé de Confirmação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedUris.size} mídia(s) selecionada(s)",
                        color = if (selectedUris.isNotEmpty()) Color(0xFF00E676) else Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancelar", fontSize = 12.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                if (selectedUris.isNotEmpty()) {
                                    onConfirmSelection(selectedUris.toList())
                                    onDismiss()
                                }
                            },
                            enabled = selectedUris.isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("confirm_gallery_selection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Incluir no Projeto",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryMediaGridCard(
    item: GalleryMediaItem,
    isSelected: Boolean,
    selectionOrder: Int,
    onClick: () -> Unit
) {
    val videoThumb = remember(item.filePath, item.isVideo) {
        if (item.isVideo && item.filePath != null) {
            MediaHelper.loadInitialFrame(item.filePath, 240, 240)
        } else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("gallery_item_${item.displayName}"),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2E354B)
        ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2434))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.isVideo) {
                if (videoThumb != null) {
                    Image(
                        bitmap = videoThumb.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A2438)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = item.filePath?.let { File(it) } ?: item.uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Badge de tipo (FOTO ou VÍDEO)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Default.Videocam else Icons.Default.Image,
                        contentDescription = null,
                        tint = if (item.isVideo) Color(0xFFFFD54F) else Color(0xFF00E5FF),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (item.isVideo) "VÍDEO" else "IMAGEM",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Indicador de seleção numerado
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF00E5FF) else Color.Black.copy(alpha = 0.55f))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Text(
                        text = "$selectionOrder",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Nome do arquivo na base
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.displayName,
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Carrega exclusivamente mídias de imagens e vídeos disponíveis no dispositivo e na galeria interna do app.
 */
private suspend fun loadDeviceImagesAndVideos(context: Context): List<GalleryMediaItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<GalleryMediaItem>()
    val seenPaths = mutableSetOf<String>()

    // 1. Consulta MediaStore.Images
    try {
        val imgProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imgProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "imagem_$id.jpg"
                val path = if (dataCol != -1) cursor.getString(dataCol) else null
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                if (path == null || seenPaths.add(path)) {
                    items.add(GalleryMediaItem(uri = contentUri, displayName = name, isVideo = false, filePath = path))
                }
            }
        }
    } catch (_: Exception) {}

    // 2. Consulta MediaStore.Video
    try {
        val vidProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            vidProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "video_$id.mp4"
                val path = if (dataCol != -1) cursor.getString(dataCol) else null
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                if (path == null || seenPaths.add(path)) {
                    items.add(GalleryMediaItem(uri = contentUri, displayName = name, isVideo = true, filePath = path))
                }
            }
        }
    } catch (_: Exception) {}

    // 3. Verifica diretórios de mídia locais do dispositivo e da galeria exclusiva do app
    val appGalleryDir = ensureAppSampleGalleryMedia(context)
    val dirsToScan = listOfNotNull(
        appGalleryDir,
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    )

    for (dir in dirsToScan) {
        try {
            if (dir.exists()) {
                dir.walkTopDown().maxDepth(3).forEach { file ->
                    if (file.isFile && (MediaHelper.isImage(file.name) || MediaHelper.isVideo(file.name))) {
                        if (seenPaths.add(file.absolutePath)) {
                            items.add(
                                GalleryMediaItem(
                                    uri = Uri.fromFile(file),
                                    displayName = file.name,
                                    isVideo = MediaHelper.isVideo(file.name),
                                    filePath = file.absolutePath
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    items
}

/**
 * Garante que a galeria exclusiva do app sempre possua mídias de imagens prontas para seleção imediata.
 */
private fun ensureAppSampleGalleryMedia(context: Context): File {
    val galleryDir = File(context.filesDir, "galeria_exclusiva_midias").apply { mkdirs() }
    val existing = galleryDir.listFiles()?.filter { it.isFile } ?: emptyList()
    if (existing.size >= 4) return galleryDir

    val palettes = listOf(
        Triple("cena_01_horizonte.jpg", intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt()), "CENA 01 • HORIZONTE"),
        Triple("cena_02_neon_city.jpg", intArrayOf(0xFF1A002C.toInt(), 0xFF4A0E4E.toInt(), 0xFF00E5FF.toInt()), "CENA 02 • NEON STUDIO"),
        Triple("cena_03_sunset_gold.jpg", intArrayOf(0xFF3E1E04.toInt(), 0xFF8E3B00.toInt(), 0xFFFF8F00.toInt()), "CENA 03 • GOLDEN HOUR"),
        Triple("cena_04_emerald_forest.jpg", intArrayOf(0xFF072218.toInt(), 0xFF0E4D34.toInt(), 0xFF00E676.toInt()), "CENA 04 • EMERALD")
    )

    for ((fileName, colors, title) in palettes) {
        val file = File(galleryDir, fileName)
        if (!file.exists()) {
            try {
                val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(
                    0f, 0f, 640f, 360f,
                    colors,
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, 640f, 360f, paint)
                paint.shader = null
                paint.color = android.graphics.Color.WHITE
                paint.textSize = 28f
                paint.isFakeBoldText = true
                canvas.drawText(title, 40f, 190f, paint)
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bmp.recycle()
            } catch (_: Exception) {}
        }
    }
    return galleryDir
}
