package com.sky22333.skyadb.ui.shared

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

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

/** 导航 / 共享元素 / 底栏统一节拍，避免多段动画互相抢戏。 */
object SharedMotion {
    const val BoundsMs = 220
    const val FadeMs = 90
    const val PageFadeMs = 180
    const val TabFadeInMs = 120
    const val TabFadeOutMs = 100
    val Easing = FastOutSlowInEasing

    fun <T> boundsTween(): FiniteAnimationSpec<T> = tween(durationMillis = BoundsMs, easing = Easing)

    fun sizeTween(): FiniteAnimationSpec<IntSize> = tween(durationMillis = BoundsMs, easing = Easing)
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
            enter = fadeIn(animationSpec = tween(SharedMotion.FadeMs)),
            exit = fadeOut(animationSpec = tween(SharedMotion.FadeMs)),
            boundsTransform = { _, _ -> SharedMotion.boundsTween() },
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
