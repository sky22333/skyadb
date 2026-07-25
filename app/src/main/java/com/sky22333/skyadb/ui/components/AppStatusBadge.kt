package com.sky22333.skyadb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sky22333.skyadb.model.ConnectionState
import com.sky22333.skyadb.ui.theme.Success
import com.sky22333.skyadb.ui.theme.Warning

@Composable
fun AppStatusBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        ConnectionState.Connected -> Success
        ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
        ConnectionState.Failed -> MaterialTheme.colorScheme.error
        ConnectionState.Offline -> Warning
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .background(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = stringResource(state.labelRes),
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
