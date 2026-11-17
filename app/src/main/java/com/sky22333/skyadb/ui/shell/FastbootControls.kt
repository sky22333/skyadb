package com.sky22333.skyadb.ui.shell

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Refresh
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.sky22333.skyadb.ui.components.Button
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.FastbootDeviceSummary
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
internal fun FastbootControls(
    state: ShellUiState,
    onRefresh: () -> Unit,
    onReboot: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val busy = state.summaryLoading || state.operationStatus is OperationStatus.Running
    val labels =
        listOf(
                R.string.fastboot_product,
                R.string.fastboot_serial,
                R.string.fastboot_bootloader_version,
                R.string.fastboot_slot,
                R.string.fastboot_mode,
            )
            .map { stringResource(it) }
    val unavailable =
        if (state.summary == null && state.summaryLoading) "—"
        else stringResource(R.string.fastboot_not_provided)
    val values =
        FastbootDeviceSummary.Variables.map { key ->
            val raw = state.summary?.values?.get(key)
            if (key == "is-userspace")
                when (raw) {
                    "yes" -> "fastbootd"
                    "no" -> "Bootloader"
                    else -> unavailable
                }
            else raw ?: unavailable
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.fastboot_summary_title),
            description =
                stringResource(
                    if (state.connected) R.string.fastboot_connected
                    else R.string.fastboot_not_connected
                ),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.summaryLoading)
                        CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                    IconButton(onClick = onRefresh, enabled = state.connected && !busy) {
                        Icon(
                            MiuixIcons.Refresh,
                            stringResource(R.string.fastboot_refresh_summary),
                        )
                    }
                    IconButton(
                        onClick = {
                            onCopy(
                                labels.zip(values).joinToString("\n") {
                                    "${it.first}: ${it.second}"
                                }
                            )
                        },
                        enabled = state.summary != null,
                    ) {
                        Icon(
                            MiuixIcons.Copy,
                            stringResource(R.string.fastboot_copy_summary),
                        )
                    }
                }
            },
        )
        labels.zip(values).forEach { (label, value) ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    value,
                    Modifier.weight(1.6f),
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        state.summaryError?.let {
            Text(
                it,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
        Text(
            stringResource(R.string.fastboot_reboot_to),
            style = MiuixTheme.textStyles.body2,
        )
        RebootActions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, command) ->
                    Button(
                        onClick = { onReboot(command) },
                        enabled = state.connected && !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(label), maxLines = 1)
                    }
                }
            }
        }
    }
}

private val RebootActions =
    listOf(
        R.string.fastboot_reboot_system to "fastboot reboot",
        R.string.fastboot_reboot_bootloader to "fastboot reboot bootloader",
        R.string.fastboot_reboot_recovery to "fastboot reboot recovery",
        R.string.fastboot_reboot_fastbootd to "fastboot reboot fastboot",
    )
