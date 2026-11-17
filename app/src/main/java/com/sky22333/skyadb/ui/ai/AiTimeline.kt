package com.sky22333.skyadb.ui.ai

import android.animation.ValueAnimator
import android.icu.text.BreakIterator
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sky22333.skyadb.ai.AiMessage
import kotlinx.coroutines.delay
import kotlin.math.max

internal sealed interface AiTimelineEntry {
    val key: String
    data class Message(val message: AiMessage) : AiTimelineEntry { override val key = "message-${message.id}" }
    data class Work(val messages: List<AiMessage>) : AiTimelineEntry { override val key = "work-${messages.first().id}" }
}

internal fun List<AiMessage>.toTimeline(): List<AiTimelineEntry> = buildList {
    val work = mutableListOf<AiMessage>()
    fun flush() {
        if (work.isNotEmpty()) { add(AiTimelineEntry.Work(work.toList())); work.clear() }
    }
    for (message in this@toTimeline) {
        if (message.role == "tool") work.add(message) else { flush(); add(AiTimelineEntry.Message(message)) }
    }
    flush()
}

internal class GraphemeCache {
    private var previous = ""
    private var ends = intArrayOf(0)

    fun update(text: String): IntArray {
        if (text == previous) return ends
        val prefixCount = if (text.startsWith(previous)) (ends.size - 2).coerceAtLeast(0) else 0
        val start = ends[prefixCount]
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text.substring(start)) }
        val result = ArrayList<Int>(prefixCount + 16)
        for (i in 0 until prefixCount) result.add(ends[i])
        var end = iterator.first()
        while (end != BreakIterator.DONE) { result.add(start + end); end = iterator.next() }
        previous = text
        ends = result.toIntArray()
        return ends
    }
}

/** Keep the raw answer in the ViewModel; this only paces its presentation while visible. */
@Composable
internal fun smoothStreamingText(id: String, text: String, streaming: Boolean): String {
    var shown by remember(id) { mutableStateOf(text) }
    val target by rememberUpdatedState(text)
    val cache = remember(id) { GraphemeCache() }
    val boundaries by rememberUpdatedState(remember(text) { cache.update(text) })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(id, streaming) {
        if (!streaming) { shown = target; return@LaunchedEffect }
        var progress = 0f
        var previousFrame = 0L
        while (true) {
            val current = target
            if (!current.startsWith(shown) || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
                shown = current
                previousFrame = 0L
                progress = 0f
            } else if (shown.length < current.length) {
                val ends = boundaries
                val currentIndex = ends.binarySearch(shown.length).let { if (it >= 0) it else -it - 1 }
                val frame = withFrameNanos { it }
                val elapsed = if (previousFrame == 0L) 0.05f else ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
                previousFrame = frame
                val backlog = (ends.lastIndex - currentIndex).coerceAtLeast(0)
                progress += max(36f, backlog / 0.20f) * elapsed
                val advance = progress.toInt().coerceAtMost(backlog)
                if (advance > 0) {
                    shown = current.substring(0, ends[currentIndex + advance])
                    progress -= advance
                }
            } else {
                previousFrame = 0L
                progress = 0f
            }
            delay(50)
        }
    }
    return if (streaming) shown else text
}
