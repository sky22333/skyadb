package com.sky22333.skyadb.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.ui.components.Button
import com.sky22333.skyadb.ui.components.PopupAnchor
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.components.TextField
import com.sky22333.skyadb.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ProcessSort(val labelRes: Int) {
    Cpu(R.string.system_status_sort_cpu),
    Memory(R.string.system_status_sort_memory),
    Io(R.string.system_status_sort_io),
    Network(R.string.system_status_sort_network),
}

@Composable
fun SystemStatusScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: SystemStatusViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val locale = LocalLocale.current.platformLocale
    var processQuery by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(ProcessSort.Cpu.name) }
    var sortDescending by rememberSaveable { mutableStateOf(true) }
    val sort = ProcessSort.entries.firstOrNull { it.name == sortName } ?: ProcessSort.Cpu

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
            actions = {
                IconButton(onClick = viewModel::refreshNow, enabled = !uiState.loading) {
                    Icon(
                        MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.system_status_refresh_now),
                    )
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

            SectionHeader(title = stringResource(R.string.system_status_monitor_settings))
            MonitorSettingsCard(
                uiState = uiState,
                showProcesses = uiState.processListVisible,
                onAutoRefreshChange = viewModel::setAutoRefresh,
                onIntervalSelected = viewModel::setRefreshInterval,
                onShowProcessesChange = viewModel::setProcessListVisible,
            )

            InlineSectionHeader(
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

            if (uiState.processListVisible) {
                ProcessSection(
                    processes = uiState.metrics.processes,
                    query = processQuery,
                    onQueryChange = { processQuery = it },
                    sort = sort,
                    sortDescending = sortDescending,
                    onSortClick = { selected ->
                        if (selected == sort) {
                            sortDescending = !sortDescending
                        } else {
                            sortName = selected.name
                            sortDescending = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MonitorSettingsCard(
    uiState: SystemStatusUiState,
    showProcesses: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit,
    onIntervalSelected: (Long) -> Unit,
    onShowProcessesChange: (Boolean) -> Unit,
) {
    val refreshItems = listOf(stringResource(R.string.system_status_refresh_off)) +
        SystemStatusRefreshInterval.entries.map { interval ->
            stringResource(R.string.system_status_interval_seconds_format, interval.seconds)
        }
    val intervalIndex = SystemStatusRefreshInterval.entries.indexOfFirst {
        it.millis == uiState.refreshIntervalMillis
    }.coerceAtLeast(0)
    val selectedRefreshIndex = if (uiState.autoRefresh) intervalIndex + 1 else 0

    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppDimens.ControlRadius) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PopupAnchor {
                OverlayDropdownPreference(
                    title = stringResource(R.string.system_status_realtime_title),
                    items = refreshItems,
                    selectedIndex = selectedRefreshIndex,
                    onSelectedIndexChange = { index ->
                        if (index == 0) {
                            onAutoRefreshChange(false)
                        } else {
                            onIntervalSelected(SystemStatusRefreshInterval.entries[index - 1].millis)
                            onAutoRefreshChange(true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.system_status_show_processes),
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.main,
                )
                Switch(checked = showProcesses, onCheckedChange = onShowProcessesChange)
            }
        }
    }
}

@Composable
private fun ProcessSection(
    processes: List<SystemProcess>,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: ProcessSort,
    sortDescending: Boolean,
    onSortClick: (ProcessSort) -> Unit,
) {
    val normalizedQuery = query.trim()
    val filtered = remember(processes, normalizedQuery, sort, sortDescending) {
        val matched = processes.filter { process ->
            normalizedQuery.isEmpty() ||
                process.name.contains(normalizedQuery, ignoreCase = true) ||
                process.pid.toString().contains(normalizedQuery) ||
                process.uid?.toString()?.contains(normalizedQuery) == true
        }
        val comparator = when (sort) {
            ProcessSort.Cpu -> compareBy<SystemProcess> { it.cpuPercent }
            ProcessSort.Memory -> compareBy<SystemProcess> { it.memoryBytes ?: 0L }
            ProcessSort.Io -> compareBy<SystemProcess> { it.ioBytesPerSecond }
            ProcessSort.Network -> compareBy<SystemProcess> {
                it.txBytesPerSecond + it.rxBytesPerSecond
            }
        }.thenBy { it.name }
        val sorted = matched.sortedWith(comparator)
        if (sortDescending) sorted.reversed() else sorted
    }

    InlineSectionHeader(
        title = stringResource(R.string.system_status_processes),
        description = stringResource(R.string.system_status_process_count_format, filtered.size),
    )
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.system_status_process_search),
        useLabelAsPlaceholder = true,
        singleLine = true,
        leadingIcon = {
            Box(modifier = Modifier.padding(start = 8.dp, end = 8.dp)) {
                Icon(MiuixIcons.Search, contentDescription = null)
            }
        },
    )
    ProcessListHeader(
        sort = sort,
        sortDescending = sortDescending,
        onSortClick = onSortClick,
    )

    if (filtered.isEmpty()) {
        Text(
            text = stringResource(R.string.system_status_process_empty),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    } else {
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = AppDimens.ControlRadius) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                filtered.take(MaxVisibleProcesses).forEach { process ->
                    ProcessRow(process)
                }
            }
        }
        if (filtered.size > MaxVisibleProcesses) {
            Text(
                text = stringResource(R.string.system_status_process_limit_format, MaxVisibleProcesses),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}

@Composable
private fun ProcessListHeader(
    sort: ProcessSort,
    sortDescending: Boolean,
    onSortClick: (ProcessSort) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(ProcessColumnSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.system_status_process_column_process),
            modifier = Modifier.weight(ProcessNameColumnWeight),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
        )
        ProcessSort.entries.forEach { option ->
            val active = option == sort
            val suffix = if (active) {
                if (sortDescending) " ↓" else " ↑"
            } else {
                ""
            }
            Text(
                text = stringResource(option.labelRes) + suffix,
                modifier = Modifier
                    .weight(option.columnWeight)
                    .clickable { onSortClick(option) }
                    .padding(vertical = 6.dp),
                color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ProcessRow(process: SystemProcess) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(ProcessColumnSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(ProcessNameColumnWeight)) {
            Text(
                text = process.name,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.system_status_process_pid_format,
                    process.pid,
                    process.uid?.toString() ?: "—",
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ProcessMetricText(formatPercent(process.cpuPercent), ProcessCpuColumnWeight)
        ProcessMetricText(process.memoryBytes?.let(::formatMemory) ?: "—", ProcessMemoryColumnWeight)
        ProcessMetricText(speedLabel(process.ioBytesPerSecond), ProcessIoColumnWeight)
        Column(
            modifier = Modifier.weight(ProcessNetworkColumnWeight),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "↑ ${speedLabel(process.txBytesPerSecond)}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "↓ ${speedLabel(process.rxBytesPerSecond)}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowScope.ProcessMetricText(value: String, weight: Float) {
    Text(
        text = value,
        modifier = Modifier.weight(weight),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.footnote1,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val ProcessSort.columnWeight: Float
    get() = when (this) {
        ProcessSort.Cpu -> ProcessCpuColumnWeight
        ProcessSort.Memory -> ProcessMemoryColumnWeight
        ProcessSort.Io -> ProcessIoColumnWeight
        ProcessSort.Network -> ProcessNetworkColumnWeight
    }

private val ProcessNameColumnWeight = 1.85f
private val ProcessCpuColumnWeight = 1.05f
private val ProcessMemoryColumnWeight = 1.45f
private val ProcessIoColumnWeight = 1.05f
private val ProcessNetworkColumnWeight = 1.7f
private val ProcessColumnSpacing = 4.dp

@Composable
private fun InlineSectionHeader(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MiuixTheme.textStyles.subtitle)
        Text(
            text = description,
            modifier = Modifier.padding(start = 12.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
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
    val brightness = brightnessText(metrics)
    val rx = speedLabel(metrics.rxBytesPerSecond)
    val tx = speedLabel(metrics.txBytesPerSecond)
    val network = metrics.networkInterface?.let { interfaceName ->
        stringResource(R.string.system_status_network_detail_format, interfaceName, rx, tx)
    } ?: stringResource(R.string.system_status_network_format, rx, tx)
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
            StatusRow(stringResource(R.string.system_status_network), network)
            StatusRow(stringResource(R.string.system_status_brightness), brightness)
        }
    }
}

@Composable
private fun brightnessText(metrics: SystemStatusMetrics): String {
    val current = metrics.backlightCurrent
    val max = metrics.backlightMax
    if (current != null && max != null && max > 0) {
        val percent = (current.toDouble() / max * 100.0).roundToInt().coerceIn(0, 100)
        val value = stringResource(R.string.system_status_brightness_sysfs_format, current, max, percent)
        return if (metrics.autoBrightness == true) {
            stringResource(R.string.system_status_brightness_auto_suffix_format, value)
        } else {
            value
        }
    }
    val fallback = metrics.brightness ?: return "—"
    return if (metrics.autoBrightness == true) {
        stringResource(R.string.system_status_brightness_auto_format, fallback)
    } else {
        fallback.toString()
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

private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)

private fun speedLabel(bytesPerSecond: Double): String {
    val bytes = bytesPerSecond.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    return when {
        bytes < 1024.0 -> String.format(Locale.US, "%.0f B/s", bytes)
        bytes < 1024.0 * 1024.0 -> String.format(Locale.US, "%.0f KB/s", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB/s", bytes / (1024.0 * 1024.0))
    }
}

private fun formatBytes(bytes: Long): String {
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return if (bytes >= gib) String.format(Locale.US, "%.1f GB", bytes / gib)
    else String.format(Locale.US, "%.0f MB", bytes / mib)
}

private fun formatMemory(bytes: Long): String {
    val kib = 1024.0
    val mib = kib * 1024.0
    return when {
        bytes >= 100 * mib -> String.format(Locale.US, "%.0f MB", bytes / mib)
        bytes >= 10 * mib -> String.format(Locale.US, "%.1f MB", bytes / mib)
        bytes >= mib -> String.format(Locale.US, "%.2f MB", bytes / mib)
        bytes >= kib -> String.format(Locale.US, "%.0f KB", bytes / kib)
        else -> "$bytes B"
    }
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

private const val MaxVisibleProcesses = 20
