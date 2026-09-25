package com.example.ui.components

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class DeviceZipItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val pathDisplay: String
)

/**
 * Espaço exclusivo do próprio app onde são exibidos todos os arquivos .zip que o usuário tem no dispositivo.
 * - Se o usuário selecionar um arquivo .zip que não contém mídias de vídeos/imagens suportadas,
 *   exibe um aviso por 5 segundos dizendo "Formato não suportado".
 */
@Composable
fun AppZipBrowserDialog(
    isOpen: Boolean,
    zipWarningMessage: String?,
    onClearZipWarning: () -> Unit,
    onDismiss: () -> Unit,
    onSelectZipUri: (Uri) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var zipItems by remember { mutableStateOf<List<DeviceZipItem>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Seletor complementar de documentos caso o usuário queira buscar em pasta restrita do sistema
    val systemZipDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectZipUri(uri)
        }
    }

    LaunchedEffect(isOpen, refreshKey) {
        if (isOpen) {
            zipItems = scanDeviceZipFiles(context)
        }
    }

    // Exibe mensagem de aviso por 5 segundos caso o ZIP não contenha mídias de vídeos/imagens
    LaunchedEffect(zipWarningMessage) {
        if (zipWarningMessage != null) {
            delay(5000L)
            onClearZipWarning()
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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("app_zip_browser_dialog"),
            color = Color(0xFF141722),
            border = BorderStroke(1.5.dp, Color(0xFF2E354B)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cabeçalho do Espaço de Arquivos ZIP do App
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
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Arquivos ZIP no Dispositivo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Selecione um arquivo ZIP contendo vídeos ou imagens",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { refreshKey++ }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar lista de ZIPs",
                                tint = Color(0xFF00E5FF)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_zip_browser_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Banner de aviso de 5 segundos quando selecionar ZIP sem mídias suportadas
                if (zipWarningMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF3B1219),
                        border = BorderStroke(1.5.dp, Color(0xFFFF5252)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("zip_unsupported_warning_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Formato não suportado",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Formato não suportado",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Selecione um arquivo ZIP que contenha mídias de vídeos ou imagens para o projeto.",
                                    color = Color(0xFFFFCDD2),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Lista de todos os arquivos ZIP encontrados no dispositivo
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (zipItems.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nenhum arquivo .zip encontrado na listagem rápida.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    systemZipDocumentLauncher.launch(
                                        arrayOf("application/zip", "application/x-zip-compressed", "*/*")
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF00E5FF))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buscar Arquivo ZIP nas Pastas", color = Color(0xFF00E5FF))
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("device_zip_files_list")
                        ) {
                            items(zipItems, key = { it.uri.toString() }) { zipItem ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectZipUri(zipItem.uri) }
                                        .testTag("zip_item_${zipItem.name}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2132)),
                                    border = BorderStroke(1.dp, Color(0xFF2D3650))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF252E46),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Archive,
                                                        contentDescription = null,
                                                        tint = Color(0xFF00E5FF),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = zipItem.name,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatFileSize(zipItem.sizeBytes)} • ${zipItem.pathDisplay}",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = { onSelectZipUri(zipItem.uri) },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF00E5FF),
                                                contentColor = Color.Black
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Importar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF262C3E))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            systemZipDocumentLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed", "*/*")
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("browse_other_zip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Explorar Outras Pastas", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF23283A),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Fechar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

/**
 * Busca todos os arquivos .zip disponíveis no dispositivo do usuário (MediaStore, Downloads, Documents e armazenamento do app).
 */
private suspend fun scanDeviceZipFiles(context: Context): List<DeviceZipItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<DeviceZipItem>()
    val seenPaths = mutableSetOf<String>()

    // 1. Consulta MediaStore.Files para todos os arquivos .zip do dispositivo
    try {
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("%.zip", "application/zip")
        context.contentResolver.query(
            filesUri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "arquivo_$id.zip"
                val size = cursor.getLong(sizeCol)
                val path = if (dataCol != -1) cursor.getString(dataCol) else null
                val uri = ContentUris.withAppendedId(filesUri, id)
                if (path == null || seenPaths.add(path)) {
                    results.add(
                        DeviceZipItem(
                            uri = uri,
                            name = name,
                            sizeBytes = size,
                            pathDisplay = path?.substringBeforeLast('/') ?: "Armazenamento do Dispositivo"
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {}

    // 2. Varre pastas comuns do dispositivo (Download, Documents, Armazenamento Interno e pasta do App)
    val dirsToCheck = listOfNotNull(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        Environment.getExternalStorageDirectory(),
        context.getExternalFilesDir(null),
        context.filesDir
    )

    for (dir in dirsToCheck) {
        try {
            if (dir.exists()) {
                dir.walkTopDown().maxDepth(3).forEach { file ->
                    if (file.isFile && file.extension.lowercase() == "zip") {
                        if (seenPaths.add(file.absolutePath)) {
                            results.add(
                                DeviceZipItem(
                                    uri = Uri.fromFile(file),
                                    name = file.name,
                                    sizeBytes = file.length(),
                                    pathDisplay = file.parentFile?.name ?: "Dispositivo"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    results
}
