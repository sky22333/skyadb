package com.sky22333.skyadb.ui.device

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.ui.components.Button
import com.sky22333.skyadb.ui.components.OperationProgressIndicator
import com.sky22333.skyadb.ui.components.PopupAnchor
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun FastbootActions(panel: FastbootPanel?, currentSlot: String?, onClosed: () -> Unit, onRefresh: () -> Unit, model: FastbootActionsViewModel = viewModel()) {
    val state by model.uiState.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.select(it) }
    var exportEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        exportEntry?.let { model.exportEntry(it, uri) }
        exportEntry = null
    }
    LaunchedEffect(panel) { if (panel != null && state.panel == null) model.open(panel) }
    LaunchedEffect(state.completed, state.panel) { if (state.completed && state.panel == FastbootPanel.Slot) onRefresh() }
    val active = state.panel ?: return
    val context = LocalContext.current
    val title = stringResource(when (active) { FastbootPanel.Flash -> R.string.fb_flash; FastbootPanel.Boot -> R.string.fb_boot; FastbootPanel.Archive -> R.string.fb_archive; FastbootPanel.Slot -> R.string.fb_slot })
    fun close() { if (!state.busy) { model.close(); onClosed() } }
    val target = fastbootPartitionTarget(state.partition, state.slot)
    val invalidPartition = target == null
    WindowDialog(show = true, onDismissRequest = ::close, title = title) {
        Column(modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (active) {
                FastbootPanel.Flash, FastbootPanel.Boot -> {
                    Text(state.name.ifEmpty { stringResource(R.string.fb_file_empty) }, style = MiuixTheme.textStyles.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (state.size >= 0) Text(Formatter.formatFileSize(context, state.size), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fb_choose_image)) }
                    if (active == FastbootPanel.Flash) {
                        TextField(value = state.partition, onValueChange = model::partition, label = stringResource(R.string.fb_partition), modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy)
                        val options = listOf(stringResource(R.string.fb_slot_default), "A", "B")
                        PopupAnchor {
                            WindowDropdownPreference(items = options, selectedIndex = listOf("", "a", "b").indexOf(state.slot).coerceAtLeast(0), title = stringResource(R.string.fb_select_slot), enabled = !state.busy, onSelectedIndexChange = { model.slot(listOf("", "a", "b")[it]) })
                        }
                        if (state.partition.isNotEmpty() && invalidPartition) Text(stringResource(R.string.fb_invalid_partition), color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1)
                    }
                }
                FastbootPanel.Archive -> {
                    Text(state.name.ifEmpty { stringResource(R.string.fb_file_empty) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fb_choose_zip)) }
                    if (state.name.isNotEmpty() && state.entries.isEmpty() && !state.busy && !state.failed) Text(stringResource(R.string.fb_archive_empty))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(state.entries) { entry ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MiuixTheme.textStyles.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (entry.size >= 0) Text(Formatter.formatFileSize(context, entry.size), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                val choices = DropdownEntry(listOf(
                                    DropdownItem(text = stringResource(R.string.fb_flash), onClick = { model.selectEntry(entry, FastbootPanel.Flash) }),
                                    DropdownItem(text = stringResource(R.string.fb_boot), onClick = { model.selectEntry(entry, FastbootPanel.Boot) }),
                                    DropdownItem(text = stringResource(R.string.fb_export), onClick = { exportEntry = entry; exporter.launch(com.sky22333.skyadb.validation.FileNamePolicy.sanitize(entry.name.substringAfterLast('/'))) }),
                                ))
                                PopupAnchor { WindowIconDropdownMenu(entry = choices, enabled = !state.busy) { Text(stringResource(R.string.fb_open)) } }
                            }
                        }
                    }
                }
                FastbootPanel.Slot -> {
                    Text(stringResource(R.string.fb_current_slot, currentSlot?.uppercase() ?: "—"))
                    PopupAnchor {
                        WindowDropdownPreference(items = listOf("A", "B"), selectedIndex = when (state.slot) { "a" -> 0; "b" -> 1; else -> -1 }, title = stringResource(R.string.fb_select_slot), showValue = state.slot.isNotEmpty(), enabled = !state.busy, onSelectedIndexChange = { model.slot(listOf("a", "b")[it]) })
                    }
                }
            }
            if (state.busy) {
                OperationProgressIndicator(state.progress)
                val progress = state.progress
                val detail = if (progress == null) stringResource(R.string.fb_preparing) else stringResource(R.string.fb_progress, (progress * 100).toInt(), Formatter.formatFileSize(context, state.bytesPerSecond))
                Text(detail, style = MiuixTheme.textStyles.footnote1)
            }
            if (state.status.isNotBlank()) Text(state.status, modifier = Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState()), color = if (state.failed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (state.busy) model.cancel() else close() }, modifier = Modifier.weight(1f)) { Text(stringResource(if (state.busy) R.string.action_cancel else R.string.fb_done)) }
                if (active != FastbootPanel.Archive) Button(
                    onClick = { confirm = true },
                    enabled = !state.busy && !state.completed && when (active) { FastbootPanel.Flash -> state.image != null && !invalidPartition; FastbootPanel.Boot -> state.image != null; FastbootPanel.Slot -> state.slot.isNotEmpty() && state.slot != currentSlot?.lowercase(); FastbootPanel.Archive -> false },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.fb_start)) }
            }
        }
    }
    if (confirm) WindowDialog(show = true, onDismissRequest = { confirm = false }, title = title, summary = when (active) {
        FastbootPanel.Flash -> stringResource(R.string.fb_confirm_flash, target.orEmpty()) + "\n" + state.name
        FastbootPanel.Boot -> stringResource(R.string.fb_confirm_boot) + "\n" + state.name
        FastbootPanel.Slot -> stringResource(R.string.fb_slot_confirm, state.slot.uppercase())
        FastbootPanel.Archive -> ""
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { confirm = false }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = { confirm = false; if (active == FastbootPanel.Slot) model.switchSlot(state.slot) else model.start() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_confirm)) }
        }
    }
}
