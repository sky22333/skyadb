package com.sky22333.skyadb.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FlatTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 76.dp,
    maxWidth: Dp = 98.dp,
) {
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        minWidth = minWidth,
        maxWidth = maxWidth,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = Color.Transparent,
            selectedBackgroundColor = Color.Transparent,
            selectedContentColor = MiuixTheme.colorScheme.primary,
        ),
    )
}
