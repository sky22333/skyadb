package com.sky22333.skyadb.ui.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.model.OperationStatus
import com.sky22333.skyadb.ui.components.AppTopBar as TopAppBar
import com.sky22333.skyadb.ui.components.SectionHeader
import com.sky22333.skyadb.ui.theme.AdbManagerTheme
import com.sky22333.skyadb.ui.theme.AppDimens

@Composable
fun PairingScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    onContinueToConnect: (host: String, connectPort: Int?) -> Unit = { _, _ -> },
    discoveredHost: String = "",
    discoveredPort: String = "",
    onDiscoveredEndpointConsumed: () -> Unit = {},
    viewModel: PairingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(discoveredHost, discoveredPort) {
        val port = discoveredPort.toIntOrNull()
        if (discoveredHost.isNotBlank() && port != null) {
            viewModel.onDiscoveredEndpointSelected(discoveredHost, port)
            onDiscoveredEndpointConsumed()
        }
    }

    PairingContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onIpChanged = viewModel::onIpChanged,
        onPairingPortChanged = viewModel::onPairingPortChanged,
        onPairingCodeChanged = viewModel::onPairingCodeChanged,
        onPairClick = viewModel::onPairClicked,
        onContinueToConnect = { onContinueToConnect(uiState.ip, uiState.connectPort) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingContent(
    bottomPadding: Dp = 0.dp,
    uiState: PairingUiState,
    onBackClick: () -> Unit,
    onIpChanged: (String) -> Unit,
    onPairingPortChanged: (String) -> Unit,
    onPairingCodeChanged: (String) -> Unit,
    onPairClick: () -> Unit,
    onContinueToConnect: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.action_wireless_pairing)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.ScreenPadding,
                    top = AppDimens.ScreenPadding,
                    end = AppDimens.ScreenPadding,
                    bottom = AppDimens.ScreenPadding + bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            PairingGuideCard()
            PairingFormCard(
                uiState = uiState,
                onIpChanged = onIpChanged,
                onPairingPortChanged = onPairingPortChanged,
                onPairingCodeChanged = onPairingCodeChanged,
                onPairClick = onPairClick,
                onContinueToConnect = onContinueToConnect,
            )
        }
    }
}

@Composable
private fun PairingGuideCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(AppDimens.CardPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.pairing_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuideStep("1", stringResource(R.string.pairing_guide_step1))
                    GuideStep("2", stringResource(R.string.pairing_guide_step2))
                    GuideStep("3", stringResource(R.string.pairing_guide_step3))
                }
            }
        }
    }
}

@Composable
private fun GuideStep(index: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = index,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PairingFormCard(
    uiState: PairingUiState,
    onIpChanged: (String) -> Unit,
    onPairingPortChanged: (String) -> Unit,
    onPairingCodeChanged: (String) -> Unit,
    onPairClick: () -> Unit,
    onContinueToConnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.pairing_info_title))

            OutlinedTextField(
                value = uiState.ip,
                onValueChange = onIpChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pairing_ip_label)) },
                singleLine = true,
                placeholder = { Text("192.168.1.86") },
                isError = uiState.ipError != null,
                supportingText = uiState.ipError?.let { { Text(it) } },
            )

            OutlinedTextField(
                value = uiState.pairingPort,
                onValueChange = onPairingPortChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pairing_port_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.portError != null,
                supportingText = uiState.portError?.let { { Text(it) } },
            )

            OutlinedTextField(
                value = uiState.pairingCode,
                onValueChange = onPairingCodeChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.pairing_code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = uiState.codeError != null,
                supportingText = uiState.codeError?.let { { Text(it) } },
            )

            PairingStatusMessage(status = uiState.operationStatus)

            if (uiState.readyToConnect) {
                Button(
                    onClick = onContinueToConnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.action_go_connect))
                }
            } else {
                Button(
                    onClick = onPairClick,
                    enabled = uiState.pairEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Outlined.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_start_pairing))
                }
            }
        }
    }
}

@Composable
private fun PairingStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = status.text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is OperationStatus.Success -> Text(
            text = status.text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
        )
        is OperationStatus.Failed -> Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppDimens.CardRadius),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = status.text,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = status.suggestion,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(name = "无线配对 - 空状态", showBackground = true, widthDp = 390)
@Composable
private fun PairingContentEmptyPreview() {
    AdbManagerTheme(dynamicColor = false) {
        PairingContent(
            uiState = PairingUiState(),
            onBackClick = {},
            onIpChanged = {},
            onPairingPortChanged = {},
            onPairingCodeChanged = {},
            onPairClick = {},
        )
    }
}

@Preview(name = "无线配对 - 错误态", showBackground = true, widthDp = 390)
@Composable
private fun PairingContentErrorPreview() {
    AdbManagerTheme(dynamicColor = false) {
        PairingContent(
            uiState = PairingUiState(
                ip = "192.168.1.999",
                pairingPort = "70000",
                pairingCode = "12",
                ipError = "请输入正确的 IPv4 地址",
                portError = "配对端口范围应为 1-65535",
                codeError = "配对码通常为 6 位数字",
                operationStatus = OperationStatus.Failed(
                    text = "无法发起配对",
                    suggestion = "请检查配对 IP、配对端口和 6 位配对码是否正确。",
                ),
            ),
            onBackClick = {},
            onIpChanged = {},
            onPairingPortChanged = {},
            onPairingCodeChanged = {},
            onPairClick = {},
        )
    }
}
