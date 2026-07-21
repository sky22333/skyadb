package com.sky22333.skyadb.ui.shared

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** 工具入口卡片 ↔ 详情顶栏 的共享键；镜像等重页刻意不设。 */
object SharedToolKeys {
    const val Apps = "tool/apps"
    const val LocalApps = "tool/local_apps"
    const val Install = "tool/install"
    const val Download = "tool/download"
    const val Files = "tool/files"
    const val Shell = "tool/shell"
    const val Remote = "tool/remote"
    const val Logs = "tool/logs"
    const val Screenshot = "tool/screenshot"
}

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalSharedToolKey = compositionLocalOf<String?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedToolBounds(key: String?): Modifier {
    if (key.isNullOrEmpty()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedToolBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            enter = fadeIn(animationSpec = tween(90)),
            exit = fadeOut(animationSpec = tween(90)),
            boundsTransform = { _, _ ->
                tween(durationMillis = 220, easing = FastOutSlowInEasing)
            },
        )
    }
}

fun NavGraphBuilder.appComposable(
    route: String,
    sharedToolKey: String? = null,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route) { entry ->
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides this,
            LocalSharedToolKey provides sharedToolKey,
        ) {
            content(entry)
        }
    }
}
