package com.sky22333.skyadb.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

@Composable
fun PopupAnchor(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides NoAnchorIndication, content = content)
}

private object NoAnchorIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode(): Int = javaClass.hashCode()
    override fun equals(other: Any?): Boolean = other === this
}
