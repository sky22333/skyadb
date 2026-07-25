package com.sky22333.skyadb.ui.discovery

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
    val uiState by viewModel.uiState.collectAsState()

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

@OptIn(ExperimentalMaterial3Api::class)
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
            title = { Text(stringResource(R.string.discovery_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onRefreshNetworkClick, enabled = !uiState.scanning) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.discovery_refresh_network_desc))
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
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            item { SectionHeader(title = stringResource(R.string.discovery_auto_discovery_title)) }
            item {
                MdnsDiscoveryStatus(
                    running = uiState.mdnsRunning,
                    endpoints = uiState.mdnsEndpoints,
                    error = uiState.mdnsError,
                )
            }
            items(uiState.mdnsEndpoints, key = { it.id }) { endpoint ->
                MdnsEndpointCard(
                    endpoint = endpoint,
                    onUseEndpoint = onUseEndpoint,
                    onPairEndpoint = onPairEndpoint,
                )
            }
            item {
                ScanControlCard(
                    uiState = uiState,
                    onStartScanClick = onStartScanClick,
                    onStopScanClick = onStopScanClick,
                )
            }
            item { SectionHeader(title = stringResource(R.string.discovery_results_title)) }
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
                    ScanResultCard(result = result, onUseEndpoint = onUseEndpoint)
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
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        endpoints.isNotEmpty() -> Text(
            text = stringResource(R.string.discovery_mdns_found_format, endpoints.size),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.discovery_mdns_running),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> EmptyState(title = stringResource(R.string.discovery_mdns_none_found))
    }
}

@Composable
private fun MdnsEndpointCard(
    endpoint: AdbMdnsEndpoint,
    onUseEndpoint: (String, Int) -> Unit,
    onPairEndpoint: (String, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = endpoint.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(endpoint.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${stringResource(endpoint.type.labelRes)} · ${endpoint.endpoint}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
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
}

private fun AdbMdnsServiceType.icon(): ImageVector {
    return when (this) {
        AdbMdnsServiceType.Pairing -> Icons.Outlined.Key
        AdbMdnsServiceType.Connect -> Icons.Outlined.CheckCircle
        AdbMdnsServiceType.Legacy -> Icons.Outlined.SettingsEthernet
    }
}

@Composable
private fun ScanControlCard(
    uiState: DeviceDiscoveryUiState,
    onStartScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = stringResource(R.string.discovery_scan_range_title),
                description = scanRangeDescription(uiState),
            )
            if (uiState.scanning) {
                LinearProgressIndicator(
                    progress = {
                        if (uiState.totalCount == 0) 0f else uiState.scannedCount.toFloat() / uiState.totalCount
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(
                        R.string.discovery_scan_progress_format,
                        uiState.scannedCount,
                        uiState.totalCount,
                        uiState.results.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            DiscoveryStatus(status = uiState.status)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartScanClick,
                    enabled = uiState.network != null && !uiState.scanning,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_start_scan))
                }
                OutlinedButton(
                    onClick = onStopScanClick,
                    enabled = uiState.scanning,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun DiscoveryStatus(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Text(status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is OperationStatus.Success -> Text(status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ScanResultCard(
    result: AdbScanResult,
    onUseEndpoint: (String, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(result.endpoint, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${stringResource(result.state.labelRes)} · ${result.latencyMs}ms",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { onUseEndpoint(result.host, result.port) }) {
                Text(stringResource(R.string.action_use))
            }
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
    AdbManagerTheme(dynamicColor = false) {
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
