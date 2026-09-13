package com.qwadb.app.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qwadb.app.R
import com.qwadb.app.status.SystemStatus
import com.qwadb.app.ui.components.AppTopBar as TopAppBar
import com.qwadb.app.ui.components.EmptyState
import com.qwadb.app.ui.theme.AppDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: StatusViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    StatusContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onRefreshClick = viewModel::refreshNow,
    )
}

@Composable
private fun StatusContent(
    bottomPadding: Dp,
    uiState: StatusUiState,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.status_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onRefreshClick,
                    enabled = !uiState.refreshing,
                    modifier = Modifier.size(36.dp),
                ) {
                    if (uiState.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.status_refresh_desc),
                            modifier = Modifier.size(18.dp),
                        )
                    }
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
            if (uiState.status.isEmpty && uiState.errorMessage == null) {
                item {
                    EmptyState(
                        title = stringResource(R.string.status_no_data),
                        message = stringResource(R.string.status_connect_hint),
                    )
                }
                return@LazyColumn
            }

            item {
                StatusHeader(uiState = uiState)
            }

            uiState.errorMessage?.let { message ->
                item {
                    StatusErrorCard(message = message, suggestion = uiState.errorSuggestion)
                }
            }

            item {
                InfoGrid(
                    items = listOf(
                        stringResource(R.string.status_network) to uiState.status.networkStatus,
                        stringResource(R.string.status_battery) to uiState.status.batteryStatus,
                        stringResource(R.string.status_storage) to uiState.status.storageStatus,
                        stringResource(R.string.status_cpu) to uiState.status.cpuUsage,
                        stringResource(R.string.status_memory) to uiState.status.memoryUsage,
                        stringResource(R.string.status_brightness) to uiState.status.screenBrightness,
                        stringResource(R.string.status_system_time) to uiState.status.systemTime,
                        stringResource(R.string.status_current_app) to uiState.status.currentApp,
                    ),
                )
            }

            item {
                StatusListCard(
                    title = stringResource(R.string.status_app_processes),
                    countText = stringResource(R.string.status_processes_format, uiState.status.appProcesses.size),
                    entries = uiState.status.appProcesses,
                )
            }

            item {
                StatusListCard(
                    title = stringResource(R.string.status_system_processes),
                    countText = stringResource(R.string.status_processes_format, uiState.status.systemProcesses.size),
                    entries = uiState.status.systemProcesses,
                )
            }

            item {
                StatusListCard(
                    title = stringResource(R.string.status_running_services),
                    countText = stringResource(R.string.status_services_format, uiState.status.runningServices.size),
                    entries = uiState.status.runningServices,
                )
            }
        }
    }
}

@Composable
private fun StatusHeader(uiState: StatusUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        uiState.lastUpdatedAt?.let { updatedAt ->
            Text(
                text = stringResource(
                    R.string.status_updated_at,
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(updatedAt)),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = stringResource(
                R.string.settings_status_refresh_desc,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusErrorCard(message: String, suggestion: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!suggestion.isNullOrBlank()) {
                Text(
                    text = suggestion,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AppDimens.CardRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.CardPadding, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusListCard(
    title: String,
    countText: String,
    entries: List<String>,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = countText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.unknown),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entries.forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
