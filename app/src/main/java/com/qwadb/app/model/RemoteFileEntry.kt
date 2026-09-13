package com.qwadb.app.model

data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
)

object RemoteFileListParser {
    fun parse(output: String, parentPath: String): List<RemoteFileEntry> {
        val parent = parentPath.trimEnd('/').ifBlank { "/" }
        return output
            .lineSequence()
            .mapNotNull { line -> parseLine(line, parent) }
            .sortedWith(compareBy<RemoteFileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    private fun parseLine(line: String, parentPath: String): RemoteFileEntry? {
        val parts = line.split("\t", limit = 3)
        if (parts.size != 3) return null
        val name = parts[1].takeIf { it.isNotBlank() && it != "." && it != ".." } ?: return null
        val isDirectory = when (parts[0]) {
            "D" -> true
            "F" -> false
            else -> return null
        }
        val path = if (parentPath == "/") "/$name" else "$parentPath/$name"
        return RemoteFileEntry(
            name = name,
            path = path,
            isDirectory = isDirectory,
            sizeBytes = parts[2].toLongOrNull() ?: 0L,
        )
    }
}
