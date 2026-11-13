package com.sky22333.skyadb.ui.discovery

import com.sky22333.skyadb.ui.components.AppIcons

import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.sky22333.skyadb.ui.components.FlatButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.discovery.AdbMdnsEndpoint
import com.sky22333.skyadb.discovery.AdbMdnsServiceType
import com.sky22333.skyadb.discovery.AdbProbeState
import com.sky22333.skyadb.discovery.AdbScanResult
import com.sky22333.skyadb.discovery.ScanRangeParser
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.EmptyState
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun DeviceDiscoveryScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    onUseEndpoint: (String, Int) -> Unit,
    onPairEndpoint: (String, Int) -> Unit,
    viewModel: DeviceDiscoveryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        viewModel.startDiscovery()
        onDispose {
            viewModel.stopDiscovery()
        }
    }

    DeviceDiscoveryContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onRefreshNetworkClick = viewModel::refreshNetwork,
        onStartScanClick = viewModel::startScan,
        onStopScanClick = viewModel::stopScan,
        onUseEndpoint = onUseEndpoint,
        onPairEndpoint = onPairEndpoint,
    )
}

@Composable
private fun DeviceDiscoveryContent(
    bottomPadding: Dp = 0.dp,
    uiState: DeviceDiscoveryUiState,
    onBackClick: () -> Unit,
    onRefreshNetworkClick: () -> Unit,
    onStartScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onUseEndpoint: (String, Int) -> Unit,
    onPairEndpoint: (String, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = stringResource(R.string.discovery_title),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onRefreshNetworkClick, enabled = !uiState.scanning) {
                    Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.discovery_refresh_network_desc))
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.ScreenPadding + bottomPadding,
            ),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AppDimens.CompactGap)) {
                    SectionHeader(title = stringResource(R.string.discovery_auto_discovery_title))
                    MdnsDiscoveryStatus(
                        running = uiState.mdnsRunning,
                        endpoints = uiState.mdnsEndpoints,
                        error = uiState.mdnsError,
                    )
                }
            }
            items(uiState.mdnsEndpoints, key = { it.id }) { endpoint ->
                MdnsEndpointRow(
                    endpoint = endpoint,
                    onUseEndpoint = onUseEndpoint,
                    onPairEndpoint = onPairEndpoint,
                )
            }
            item {
                Column(
                    modifier = Modifier.padding(top = AppDimens.SectionGap),
                    verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
                ) {
                    ScanControlSection(
                        uiState = uiState,
                        onStartScanClick = onStartScanClick,
                        onStopScanClick = onStopScanClick,
                    )
                    SectionHeader(title = stringResource(R.string.discovery_results_title))
                }
            }
            if (uiState.results.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(
                            if (uiState.scanning) R.string.discovery_searching else R.string.discovery_no_results,
                        ),
                    )
                }
            } else {
                items(uiState.results, key = { it.endpoint }) { result ->
                    ScanResultRow(result = result, onUseEndpoint = onUseEndpoint)
                }
            }
        }
    }
}

@Composable
private fun MdnsDiscoveryStatus(
    running: Boolean,
    endpoints: List<AdbMdnsEndpoint>,
    error: String?,
) {
    when {
        error != null -> Text(
            text = stringResource(R.string.discovery_mdns_error_suffix_format, error),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
        endpoints.isNotEmpty() -> Text(
            text = stringResource(R.string.discovery_mdns_found_format, endpoints.size),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
        running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.discovery_mdns_running),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        else -> Text(
            text = stringResource(R.string.discovery_mdns_none_found),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
internal fun MdnsEndpointRow(
    endpoint: AdbMdnsEndpoint,
    onUseEndpoint: (String, Int) -> Unit,
    onPairEndpoint: (String, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.CompactGap),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = endpoint.type.icon(),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(endpoint.name, style = MiuixTheme.textStyles.subtitle)
            Text(
                text = "${stringResource(endpoint.type.labelRes)} · ${endpoint.endpoint}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        FlatButton(
            onClick = {
                if (endpoint.type == AdbMdnsServiceType.Pairing) {
                    onPairEndpoint(endpoint.host, endpoint.port)
                } else {
                    onUseEndpoint(endpoint.host, endpoint.port)
                }
            },
        ) {
            Text(stringResource(endpoint.type.actionLabelRes))
        }
    }
}

private fun AdbMdnsServiceType.icon(): ImageVector {
    return when (this) {
        AdbMdnsServiceType.Pairing -> AppIcons.Wifi
        AdbMdnsServiceType.Connect -> MiuixIcons.Link
        AdbMdnsServiceType.Legacy -> MiuixIcons.Link
    }
}

@Composable
internal fun ScanControlSection(
    uiState: DeviceDiscoveryUiState,
    onStartScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.discovery_scan_range_title),
            description = scanRangeDescription(uiState),
        )
        if (uiState.scanning) {
            LinearProgressIndicator(
                progress = if (uiState.totalCount == 0) 0f else uiState.scannedCount.toFloat() / uiState.totalCount,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.discovery_scan_progress_format,
                    uiState.scannedCount,
                    uiState.totalCount,
                    uiState.results.size,
                ),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        DiscoveryStatus(status = uiState.status)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FlatButton(
                onClick = onStartScanClick,
                primary = true,
                enabled = uiState.network != null && !uiState.scanning,
                modifier = Modifier.weight(1f),
            ) {
                Icon(MiuixIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_start_scan))
            }
            FlatButton(
                onClick = onStopScanClick,
                enabled = uiState.scanning,
                modifier = Modifier.weight(1f),
            ) {
                Icon(MiuixIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun DiscoveryStatus(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        is OperationStatus.Success -> Text(status.text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
internal fun ScanResultRow(
    result: AdbScanResult,
    onUseEndpoint: (String, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.CompactGap),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Link,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(result.endpoint, style = MiuixTheme.textStyles.subtitle)
            Text(
                text = "${stringResource(result.state.labelRes)} · ${result.latencyMs}ms",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        FlatButton(onClick = { onUseEndpoint(result.host, result.port) }) {
            Text(stringResource(R.string.action_connect))
        }
    }
}

@Composable
private fun scanRangeDescription(uiState: DeviceDiscoveryUiState): String {
    if (uiState.networks.isEmpty()) return stringResource(R.string.discovery_connect_wifi_first)
    val parts = ArrayList<String>(uiState.networks.size)
    for (network in uiState.networks) {
        val source = stringResource(network.sourceLabelRes)
        parts += stringResource(R.string.discovery_network_with_source, network.subnetLabel, source)
    }
    return stringResource(
        R.string.discovery_scan_range_format,
        parts.joinToString(", "),
        uiState.ports.joinToString(" / "),
    )
}

@Preview(name = "局域网扫描", showBackground = true, widthDp = 390)
@Composable
private fun DeviceDiscoveryContentPreview() {
    AdbManagerTheme() {
        DeviceDiscoveryContent(
            uiState = DeviceDiscoveryUiState(
                networks = listOf(
                    requireNotNull(
                        ScanRangeParser.subnetForLocalAddress(
                            "10.71.180.42",
                            sourceLabelRes = R.string.discovery_source_current_network,
                        ),
                    ),
                    requireNotNull(
                        ScanRangeParser.subnetForHost(
                            "10.43.180.147",
                            sourceLabelRes = R.string.discovery_source_recent_device,
                        ),
                    ),
                ),
                scanning = true,
                scannedCount = 88,
                totalCount = 253,
                results = listOf(
                    AdbScanResult("192.168.1.86", 5555, AdbProbeState.AdbUnauthorized, 32),
                    AdbScanResult("192.168.1.98", 5555, AdbProbeState.AdbAvailable, 18),
                ),
                status = OperationStatus.Running("正在扫描 192.168.1.0/24"),
            ),
            onBackClick = {},
            onRefreshNetworkClick = {},
            onStartScanClick = {},
            onStopScanClick = {},
            onUseEndpoint = { _, _ -> },
            onPairEndpoint = { _, _ -> },
        )
    }
}
