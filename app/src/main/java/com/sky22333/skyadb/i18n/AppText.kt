package com.sky22333.skyadb.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.sky22333.skyadb.AppServices

/**
 * Deferred UI text resolved against the active per-app locales.
 * Prefer [Res] for static copy; use [Plain] only for dynamic/user/device content.
 */
@Immutable
sealed interface AppText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : AppText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    data class Plain(val value: String) : AppText

    fun resolve(context: Context): String = when (this) {
        is Res -> {
            if (args.isEmpty()) {
                context.getString(id)
            } else {
                context.getString(id, *args.resolveNested(context))
            }
        }
        is Plain -> value
    }
}

/** Allows a [Res] arg (e.g. a nested label resource) to resolve before formatting. */
private fun List<Any>.resolveNested(context: Context): Array<Any> {
    return map { arg -> if (arg is AppText) arg.resolve(context) else arg }.toTypedArray()
}

/** Resolve using the application context (respects AppCompat per-app locales). */
fun appString(@StringRes id: Int): String = AppServices.context.getString(id)

fun appString(@StringRes id: Int, vararg formatArgs: Any): String =
    AppServices.context.getString(id, *formatArgs)

@Composable
@ReadOnlyComposable
fun AppText.resolve(): String {
    return when (this) {
        is AppText.Res -> {
            if (args.isEmpty()) {
                stringResource(id)
            } else {
                val resolvedArgs = args.map { arg -> if (arg is AppText) arg.resolve() else arg }
                stringResource(id, *resolvedArgs.toTypedArray())
            }
        }
        is AppText.Plain -> value
    }
}
