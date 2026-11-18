package com.sky22333.skyadb.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.scrcpy.MirrorSpeed
import com.sky22333.skyadb.scrcpy.MirrorSpeedFormatter
import com.sky22333.skyadb.ui.components.Button
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SystemStatusScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: SystemStatusViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalLocale.current.platformLocale
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(bottom = bottomPadding),
    ) {
        SmallTopAppBar(
            title = stringResource(R.string.system_status_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            if (uiState.loading && uiState.updatedAtMillis == 0L) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            uiState.error?.let { error ->
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppDimens.ControlRadius) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(error, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1)
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            SectionHeader(
                title = stringResource(R.string.system_status_overview),
                description = if (uiState.updatedAtMillis > 0) {
                    stringResource(
                        R.string.system_status_updated_format,
                        SimpleDateFormat("HH:mm:ss", locale).format(Date(uiState.updatedAtMillis)),
                    )
                } else {
                    stringResource(R.string.system_status_reading)
                },
            )
            StatusCard(uiState.metrics)

            if (uiState.metrics.processes.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.system_status_processes))
                Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppDimens.ControlRadius) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        uiState.metrics.processes.forEach { process ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = process.pid.toString(),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                                Text(
                                    text = process.name,
                                    modifier = Modifier.weight(1f),
                                    style = MiuixTheme.textStyles.footnote1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(metrics: SystemStatusMetrics) {
    val batteryStatus = metrics.batteryStatus?.let { batteryStatusLabel(it) } ?: "—"
    val battery = metrics.batteryLevel?.let { "$it%" } ?: "—"
    val temperature = metrics.batteryTemperatureCelsius?.let {
        stringResource(R.string.system_status_temperature_format, it)
    } ?: "—"
    val cpu = metrics.cpuUsagePercent?.let {
        stringResource(R.string.system_status_percent_format, it)
    } ?: "—"
    val memory = usageText(metrics.memoryUsedBytes, metrics.memoryTotalBytes)
    val storage = usageText(metrics.storageUsedBytes, metrics.storageTotalBytes)
    val brightness = metrics.brightness?.let { value ->
        if (metrics.autoBrightness == true) {
            stringResource(R.string.system_status_brightness_auto_format, value)
        } else {
            value.toString()
        }
    } ?: "—"
    val rx = speedLabel(metrics.rxBytesPerSecond)
    val tx = speedLabel(metrics.txBytesPerSecond)
    val currentApp = metrics.currentApp ?: "—"

    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppDimens.ControlRadius) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusRow(stringResource(R.string.system_status_current_app), currentApp)
            StatusRow(
                stringResource(R.string.system_status_battery),
                stringResource(R.string.system_status_battery_format, battery, batteryStatus, temperature),
            )
            StatusRow(stringResource(R.string.system_status_cpu), cpu)
            StatusRow(stringResource(R.string.system_status_memory), memory)
            StatusRow(stringResource(R.string.system_status_storage), storage)
            StatusRow(
                stringResource(R.string.system_status_network),
                stringResource(R.string.system_status_network_format, rx, tx),
            )
            StatusRow(stringResource(R.string.system_status_brightness), brightness)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.35f),
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun usageText(used: Long?, total: Long?): String {
    if (used == null || total == null || total <= 0) return "—"
    val percent = (used.toDouble() / total * 100.0).coerceIn(0.0, 100.0)
    return "${formatBytes(used)} / ${formatBytes(total)}  ·  ${"%.1f".format(Locale.US, percent)}%"
}

private fun speedLabel(bytesPerSecond: Double): String =
    when (val speed = MirrorSpeedFormatter.format(bytesPerSecond)) {
        is MirrorSpeed.KilobytesPerSecond -> "${speed.value} KB/s"
        is MirrorSpeed.MegabytesPerSecond -> String.format(Locale.US, "%.2f MB/s", speed.value)
    }

private fun formatBytes(bytes: Long): String {
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return if (bytes >= gib) String.format(Locale.US, "%.1f GB", bytes / gib)
    else String.format(Locale.US, "%.0f MB", bytes / mib)
}

@Composable
private fun batteryStatusLabel(status: Int): String = stringResource(
    when (status) {
        2 -> R.string.system_status_battery_charging
        3 -> R.string.system_status_battery_discharging
        4 -> R.string.system_status_battery_not_charging
        5 -> R.string.system_status_battery_full
        else -> R.string.unknown
    }
)
