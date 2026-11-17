package com.srakim.spitify.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/**
 * Press feedback the way Spotify does it: no Material ripple spreading out from your finger,
 * the thing you're touching just dims while you hold it and comes back when you let go.
 * Installed as the app-wide indication, so every clickable row, card and tile gets it.
 */
object PressDim : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressDimNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x5D1
}

private class PressDimNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val dim = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var held = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> held++
                    is PressInteraction.Release, is PressInteraction.Cancel -> held = (held - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val down = held > 0
                launch { dim.animateTo(if (down) 1f else 0f, tween(if (down) 70 else 240)) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val amount = dim.value
        if (amount > 0f) drawRect(Color.Black, alpha = 0.34f * amount)
    }
}
