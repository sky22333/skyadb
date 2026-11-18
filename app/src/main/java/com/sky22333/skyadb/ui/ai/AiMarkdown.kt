package com.sky22333.skyadb.ui.ai

// MD4C treats a table immediately following a paragraph as part of that paragraph.
// Insert only the missing block boundary for a recognizable top-level GFM table.
internal fun separateMarkdownTables(markdown: String): String {
    if ('|' !in markdown || '-' !in markdown) return markdown
    val insertions = mutableListOf<Int>()
    var fenceChar = '\u0000'
    var fenceLength = 0
    var lineStart = 0
    var previousStart = -1
    var beforePrevious = ""
    var previous = ""
    while (lineStart < markdown.length) {
        val end = markdown.indexOf('\n', lineStart).let { if (it < 0) markdown.length else it }
        val line = markdown.substring(lineStart, end).removeSuffix("\r")
        val trimmed = line.trimStart(' ')
        val indent = line.length - trimmed.length
        val marker = trimmed.firstOrNull()
        val markerCount = if (marker == '`' || marker == '~') trimmed.takeWhile { it == marker }.length else 0
        if (indent <= 3 && markerCount >= 3) {
            if (fenceChar == '\u0000') { fenceChar = marker!!; fenceLength = markerCount }
            else if (marker == fenceChar && markerCount >= fenceLength && trimmed.drop(markerCount).isBlank()) fenceChar = '\u0000'
        } else if (fenceChar == '\u0000' && previousStart >= 0 && beforePrevious.isNotBlank() &&
            !beforePrevious.trimStart().startsWith('>') && !beforePrevious.trimStart().startsWith('|') &&
            isTableDelimiter(line) && tableColumnCount(previous) == tableColumnCount(line) && tableColumnCount(line) >= 1) {
            insertions.add(previousStart)
        }
        beforePrevious = previous
        previous = line
        previousStart = lineStart
        lineStart = if (end == markdown.length) markdown.length else end + 1
    }
    if (insertions.isEmpty()) return markdown
    return buildString(markdown.length + insertions.size) {
        var start = 0
        for (offset in insertions) { append(markdown, start, offset); append('\n'); start = offset }
        append(markdown, start, markdown.length)
    }
}

private val tableDelimiter = Regex("^ {0,3}\\|?\\s*:?-+:?\\s*(?:\\|\\s*:?-+:?\\s*)*\\|?\\s*$")
private fun isTableDelimiter(line: String): Boolean = '|' in line && tableDelimiter.matches(line)

// Ignore pipes escaped or inside code spans; trailing and leading pipes are optional in GFM.
private fun tableColumnCount(line: String): Int {
    if (line.startsWith("    ") || line.startsWith('\t')) return 0
    val text = line.trim()
    if (text.isEmpty() || text.startsWith('>')) return 0
    var pipes = 0
    var firstPipe = -1
    var lastPipe = -1
    var codeTicks = 0
    var i = 0
    while (i < text.length) {
        when (text[i]) {
            '\\' -> { i += 2; continue }
            '`' -> {
                val start = i
                while (i < text.length && text[i] == '`') i++
                val count = i - start
                if (codeTicks == 0) codeTicks = count else if (codeTicks == count) codeTicks = 0
                continue
            }
            '|' -> if (codeTicks == 0) { if (firstPipe < 0) firstPipe = i; lastPipe = i; pipes++ }
        }
        i++
    }
    if (pipes == 0) return 0
    return pipes + 1 - (if (firstPipe == 0) 1 else 0) - (if (lastPipe == text.lastIndex) 1 else 0)
}
