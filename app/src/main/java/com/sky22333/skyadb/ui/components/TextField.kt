package com.sky22333.skyadb.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix text field without an animated focus border. */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MiuixTheme.textStyles.main,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    insideMargin: DpSize = TextFieldDefaults.InsideMargin,
    cursorBrush: Brush = SolidColor(MiuixTheme.colorScheme.primary),
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    // Miuix animates its border from the background color even when the target is
    // transparent. Suppress only its decorative focus interactions; text focus and
    // cursor handling remain owned by BasicTextField.
    val interaction = remember {
        val source = MutableInteractionSource()
        object : MutableInteractionSource by source {
            override suspend fun emit(interaction: Interaction) {
                if (interaction !is FocusInteraction) source.emit(interaction)
            }
            override fun tryEmit(interaction: Interaction): Boolean =
                interaction is FocusInteraction || source.tryEmit(interaction)
        }
    }
    MiuixTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        useLabelAsPlaceholder = useLabelAsPlaceholder,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = textStyle,
        colors = colors.copy(borderColor = Color.Transparent),
        insideMargin = insideMargin,
        cursorBrush = cursorBrush,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        interactionSource = interaction,
    )
}
