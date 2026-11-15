package com.sky22333.skyadb.ui.device

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sky22333.skyadb.AppServices
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastbootArchiveFlowTest {
    @Test fun selectedArchiveCanPrepareAnImageAndReleaseTemporaryFile() = runBlocking {
        val zip = File.createTempFile("firmware-test", ".zip", AppServices.context.cacheDir)
        try {
            ZipOutputStream(zip.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("images/boot.img"))
                output.write(byteArrayOf(1, 2, 3))
                output.closeEntry()
            }
            val vm = withContext(Dispatchers.Main) { FastbootActionsViewModel().also { it.open(FastbootPanel.Archive); it.select(Uri.fromFile(zip)) } }
            val listed = withTimeout(10_000) { vm.uiState.first { it.entries.isNotEmpty() || it.failed } }
            assertFalse(listed.status, listed.failed)
            assertEquals("images/boot.img", listed.entries.single().name)
            withContext(Dispatchers.Main) { vm.selectEntry(listed.entries.single(), FastbootPanel.Flash) }
            val selected = withTimeout(10_000) { vm.uiState.first { it.panel == FastbootPanel.Flash || it.failed } }
            assertFalse(selected.status, selected.failed)
            val image = File(requireNotNull(selected.image).path!!)
            assertArrayEquals(byteArrayOf(1, 2, 3), image.readBytes())
            withContext(Dispatchers.Main) { vm.close() }
            assertFalse(image.exists())
        } finally { zip.delete() }
    }
}
