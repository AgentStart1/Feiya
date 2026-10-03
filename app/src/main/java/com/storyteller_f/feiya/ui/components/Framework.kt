package com.storyteller_f.feiya.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jamal.composeprefs3.ui.PrefsScreen
import com.jamal.composeprefs3.ui.prefs.EditTextPref
import com.storyteller_f.feiya.BootSettingsState
import com.storyteller_f.feiya.R
import com.storyteller_f.feiya.dataStore
import com.storyteller_f.feiya.service.AppService
import com.storyteller_f.feiya.service.ServerState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainToolbar(
    title: String,
    port: String,
    state: ServerState,
    restartService: () -> Unit,
    stopService: () -> Unit,
    sendText: (String) -> Unit,
    deleteAll: () -> Unit,
    showFileActions: Boolean,
    about: () -> Unit,
) {
    var showQr by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val status = stringResource(when (state) {
        is ServerState.Started -> R.string.server_running
        is ServerState.Stopped -> R.string.server_stopped
        is ServerState.Error -> R.string.server_error
        else -> R.string.service_starting
    })
    androidx.compose.foundation.layout.BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        TopAppBar(
            expandedHeight = if (wide) 80.dp else 64.dp,
            title = {
                Column {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (!wide) Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                TextButton(onClick = { if (state is ServerState.Error) showError = true else showQr = true }, enabled = state is ServerState.Started || state is ServerState.Error) {
                    Text(if (wide) "$status · $port" else port, color = if (state is ServerState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                if (state is ServerState.Started && wide) TextButton(onClick = { showQr = true }) { Text(stringResource(R.string.qrcode)) }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.service_actions)) }
                    androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text(status) }, onClick = { showMenu = false; if (state is ServerState.Error) showError = true }, enabled = state is ServerState.Error)
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.restart_service)) }, onClick = { showMenu = false; restartService() })
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.stop_service)) }, onClick = { showMenu = false; stopService() })
                        if (showFileActions) androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.delete_all)) }, onClick = { showMenu = false; confirmClear = true })
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, onClick = { showMenu = false; about() })
                    }
                }
            },
        )
    }
    if (showQr) AlertDialog(
        onDismissRequest = { showQr = false },
        title = { Text(stringResource(R.string.qrcode)) },
        text = { ShowQrCode("", port, sendText = sendText) },
        confirmButton = { TextButton(onClick = { showQr = false }) { Text(stringResource(android.R.string.ok)) } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_shares_title)) },
        text = { Text(stringResource(R.string.clear_shares_help)) },
        confirmButton = { TextButton(onClick = { confirmClear = false; deleteAll() }) { Text(stringResource(R.string.delete_all)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel_action)) } },
    )
    if (showError) AlertDialog(
        onDismissRequest = { showError = false },
        title = { Text(stringResource(R.string.server_error)) },
        text = { Text((state as? ServerState.Error)?.cause?.message.orEmpty(), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showError = false }) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Preview
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SettingPage(
    port: String = AppService.DEFAULT_PORT.toString(),
    bootSettings: BootSettingsState = BootSettingsState(),
    setStartOnBoot: (Boolean) -> Unit = {},
    retryBootSettings: () -> Unit = {},
) {
    PrefsScreen(dataStore = LocalContext.current.dataStore) {
        prefsItem {
            BootPreference(bootSettings, setStartOnBoot, retryBootSettings)
        }
        prefsItem {
            EditTextPref(
                key = "port",
                title = stringResource(R.string.port),
                summary = stringResource(R.string.listen_port_hint, port),
                dialogTitle = stringResource(R.string.port_setting),
                dialogMessage = stringResource(R.string.please_input_a_valid_port),
                defaultValue = AppService.DEFAULT_PORT.toString()
            )
        }
    }
}

@Composable
fun BootPreference(state: BootSettingsState, onToggle: (Boolean) -> Unit, onRetry: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(
                value = state.enabled,
                enabled = !state.loading && !state.saving,
                role = Role.Switch,
                onValueChange = onToggle,
            ).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(R.string.start_on_boot), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(when {
                        state.error -> R.string.boot_setting_error
                        state.loading -> R.string.boot_setting_loading
                        state.saving -> R.string.boot_setting_saving
                        else -> R.string.start_on_boot_summary
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = null, enabled = !state.loading && !state.saving)
        }
        if (state.error && state.loading) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.boot_setting_retry))
            }
        }
    }
}

@Composable
fun OneCenter(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(), contentAlignment = Alignment.Center, content = content
    )
}
