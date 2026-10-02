package com.storyteller_f.feiya.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.storyteller_f.feiya.HidSendTask
import com.storyteller_f.feiya.HidTaskKind
import com.storyteller_f.feiya.HidTaskStatus
import com.storyteller_f.feiya.HidTaskFailure
import com.storyteller_f.feiya.R

@Composable
internal fun HidTaskPanel(tasks: List<HidSendTask>, cancelTask: (Long) -> Unit, cancelAll: () -> Unit) {
    if (tasks.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(8.dp).testTag("hid_tasks")) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hid_tasks), style = MaterialTheme.typography.titleMedium)
            if (tasks.any { it.status.canCancel }) {
                TextButton(onClick = cancelAll) { Text(stringResource(R.string.hid_cancel_all)) }
            }
        }
        Text(stringResource(R.string.hid_progress_help), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max = 200.dp)) {
            items(tasks.filter { !it.status.isFinished } + tasks.filter { it.status.isFinished }.asReversed(), key = { it.id }) { task ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("hid_task_${task.id}")) {
                    val kind = stringResource(when (task.kind) {
                        HidTaskKind.TEXT -> R.string.hid_text
                        HidTaskKind.RAW_KEY -> R.string.hid_task_key
                        HidTaskKind.CALIBRATION -> R.string.hid_mac_calibration
                    })
                    Text(stringResource(R.string.hid_task_target, task.id, kind, task.deviceName ?: stringResource(R.string.hid_not_connected)))
                    val status = stringResource(when (task.status) {
                        HidTaskStatus.QUEUED -> R.string.hid_task_queued
                        HidTaskStatus.SENDING -> R.string.hid_task_sending
                        HidTaskStatus.CANCELLING -> R.string.hid_task_cancelling
                        HidTaskStatus.SENT -> R.string.hid_task_sent
                        HidTaskStatus.FAILED -> R.string.hid_task_failed
                        HidTaskStatus.CANCELLED -> R.string.hid_task_cancelled
                    })
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.hid_task_progress, status, task.sentKeys, task.totalKeys),
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                        if (task.status.canCancel) {
                            TextButton(onClick = { cancelTask(task.id) }, modifier = Modifier.testTag("hid_cancel_${task.id}")) {
                                Text(stringResource(R.string.hid_cancel))
                            }
                        }
                    }
                    if (task.status == HidTaskStatus.SENDING || task.status == HidTaskStatus.CANCELLING) {
                        LinearProgressIndicator(progress = { if (task.totalKeys == 0) 0f else task.sentKeys.toFloat() / task.totalKeys }, modifier = Modifier.fillMaxWidth())
                    }
                    task.failure?.let { failure ->
                        Text(stringResource(when (failure) {
                            HidTaskFailure.UNSUPPORTED_TEXT -> R.string.hid_unsupported_text
                            HidTaskFailure.NOT_CONNECTED -> R.string.hid_not_connected
                            HidTaskFailure.CONNECTION_CHANGED -> R.string.hid_connection_changed
                            HidTaskFailure.SEND_FAILED -> R.string.hid_send_failed
                        }), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
