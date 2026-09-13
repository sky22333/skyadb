package com.qwadb.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteFileListParserTest {
    @Test
    fun parse_readsEntriesAndSortsDirectoriesFirst() {
        val entries = RemoteFileListParser.parse(
            output = """
                F	config.json	2048
                D	Pictures	0
                F	notes.txt	12
                D	Movies	0
            """.trimIndent(),
            parentPath = "/sdcard/Download",
        )

        assertEquals(listOf("Movies", "Pictures", "config.json", "notes.txt"), entries.map { it.name })
        assertEquals("/sdcard/Download/Movies", entries[0].path)
        assertEquals(true, entries[0].isDirectory)
        assertEquals(2048L, entries[2].sizeBytes)
    }

    @Test
    fun parse_ignoresInvalidAndDotEntries() {
        val entries = RemoteFileListParser.parse(
            output = """
                X	unknown	1
                D	.	0
                D	..	0
                F	ok.txt	5
                broken
            """.trimIndent(),
            parentPath = "/",
        )

        assertEquals(1, entries.size)
        assertEquals("/ok.txt", entries.single().path)
    }
}
