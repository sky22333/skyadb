package com.sky22333.skyadb.ui.pairing


import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.sky22333.skyadb.ui.components.FlatButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.sky22333.skyadb.ui.components.TextField
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as TopAppBar
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            title = stringResource(R.string.action_wireless_pairing),
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
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
            PairingGuideSection()
            PairingFormSection(
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
private fun PairingGuideSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null) { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.pairing_guide_title),
                style = MiuixTheme.textStyles.subtitle,
            )
            Icon(
                imageVector = MiuixIcons.ChevronBackward,
                modifier = Modifier.rotate(if (expanded) 90f else 270f),
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

@Composable
private fun GuideStep(index: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = index,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
internal fun PairingFormSection(
    uiState: PairingUiState,
    onIpChanged: (String) -> Unit,
    onPairingPortChanged: (String) -> Unit,
    onPairingCodeChanged: (String) -> Unit,
    onPairClick: () -> Unit,
    onContinueToConnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionHeader(title = stringResource(R.string.pairing_info_title))

        TextField(
            value = uiState.ip,
            onValueChange = onIpChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.pairing_ip_label),
            singleLine = true,
        )
        uiState.ipError?.let { Text(it, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1) }

        TextField(
            value = uiState.pairingPort,
            onValueChange = onPairingPortChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.pairing_port_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        uiState.portError?.let { Text(it, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1) }

        TextField(
            value = uiState.pairingCode,
            onValueChange = onPairingCodeChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.pairing_code_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        uiState.codeError?.let { Text(it, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1) }

        PairingStatusMessage(status = uiState.operationStatus)

        if (uiState.readyToConnect) {
            FlatButton(
                onClick = onContinueToConnect,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.action_go_connect))
            }
        } else {
            FlatButton(
                onClick = onPairClick,
                primary = true,
                enabled = uiState.pairEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.action_start_pairing))
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
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        is OperationStatus.Success -> Text(
            text = status.text,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Preview(name = "无线配对 - 空状态", showBackground = true, widthDp = 390)
@Composable
private fun PairingContentEmptyPreview() {
    AdbManagerTheme() {
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
    AdbManagerTheme() {
        PairingContent(
            uiState = PairingUiState(
                ip = "192.168.1.999",
                pairingPort = "70000",
                pairingCode = "12",
                ipError = "请输入正确的 IPv4/IPv6 地址",
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
