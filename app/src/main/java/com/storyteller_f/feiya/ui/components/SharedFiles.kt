package com.storyteller_f.feiya.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.service.SharedFileInfo

@Composable
fun SharedFiles(
    infoList: List<SharedFileInfo>,
    deleteItem: (SharedFileInfo) -> Unit = {},
    saveToLocal: (SharedFileInfo) -> Unit = {},
    viewInfo: (SharedFileInfo) -> Unit = {},
    addFiles: () -> Unit = {},
) {
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = infoList.firstOrNull { it.uri == selectedUri }
    // A removed entry must not silently select its old index or reappear later.
    LaunchedEffect(infoList, selectedUri) {
        if (selectedUri != null && selected == null) selectedUri = null
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val dualPane = maxWidth >= 720.dp
        val generous = maxWidth >= 1000.dp
        val listWidth = (maxWidth * 0.42f).coerceIn(280.dp, 480.dp)
        BackHandler(enabled = !dualPane && selected != null) { selectedUri = null }
        Row(Modifier.fillMaxSize()) {
            if (dualPane || selected == null) {
                Column(Modifier.then(if (dualPane) Modifier.width(listWidth) else Modifier.fillMaxWidth()).fillMaxHeight().padding(16.dp).testTag("file_list")) {
                    Button(onClick = addFiles, modifier = Modifier.fillMaxWidth().heightIn(min = if (generous) 64.dp else 52.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_file))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (infoList.isEmpty()) EmptyFiles() else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(infoList, key = { it.uri }) { info ->
                            Surface(
                                selected = selected?.uri == info.uri,
                                onClick = { selectedUri = info.uri },
                                color = if (selected?.uri == info.uri) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("file_${info.uri}"),
                            ) {
                                Row(Modifier.padding(if (generous) 20.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FileSymbol(info, Modifier.size(if (generous) 56.dp else 40.dp))
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(info.name, style = if (generous) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(info.name.substringAfterLast('.', "FILE").uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (dualPane) VerticalDivider()
            if (selected != null) {
                Column(Modifier.weight(1f).fillMaxHeight().testTag("file_detail")) {
                    if (!dualPane) TextButton(onClick = { selectedUri = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Text(stringResource(R.string.back_to_files))
                    }
                    FileDetail(selected, deleteItem, saveToLocal, viewInfo)
                }
            } else if (dualPane) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.select_file), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyFiles() {
    Column(Modifier.padding(vertical = 40.dp, horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.files_empty), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.files_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FileSymbol(info: SharedFileInfo, modifier: Modifier = Modifier) {
    // Use the shared, library-supplied Phosphor asset for each file type.
    val drawable = when (info.name.substringAfterLast('.').lowercase()) {
        "pdf" -> R.drawable.file_pdf
        "png", "jpg", "jpeg", "webp", "gif" -> R.drawable.file_image
        "zip", "gz", "rar", "7z" -> R.drawable.file_zip
        else -> R.drawable.file_generic
    }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val tint = when (drawable) {
        R.drawable.file_pdf -> androidx.compose.ui.graphics.Color(if (dark) 0xFFFFB4AB else 0xFFBA352E)
        R.drawable.file_image -> androidx.compose.ui.graphics.Color(if (dark) 0xFFADC6FF else 0xFF315FA8)
        R.drawable.file_zip -> androidx.compose.ui.graphics.Color(if (dark) 0xFFFFD98B else 0xFF875D00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(androidx.compose.ui.res.painterResource(drawable), null, modifier, tint = tint)
}

@Composable
fun FileDetail(
    info: SharedFileInfo,
    deleteItem: (SharedFileInfo) -> Unit,
    saveToLocal: (SharedFileInfo) -> Unit,
    viewInfo: (SharedFileInfo) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val roomy = maxWidth >= 560.dp
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(if (roomy) 32.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(16.dp))
        FileSymbol(info, Modifier.size(if (roomy) 160.dp else 112.dp).align(Alignment.CenterHorizontally))
        Text(info.name, Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        HorizontalDivider()
        Text(stringResource(R.string.file_details), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.file_location), color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(info.uri, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = { viewInfo(info) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.file_share_code)) }
        if (Uri.parse(info.uri).scheme != "file") OutlinedButton(onClick = { saveToLocal(info) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.save_to_local))
        }
        OutlinedButton(onClick = { deleteItem(info) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Icon(Icons.Default.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.remove_share))
        }
        Text(stringResource(R.string.share_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
}
