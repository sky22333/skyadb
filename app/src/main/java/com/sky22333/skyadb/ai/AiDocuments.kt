package com.sky22333.skyadb.ai

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Access only the document tree selected by the user. Paths are relative to that tree. */
internal class AiDocuments(private val context: Context, private val tree: Uri) {
    private val resolver = context.contentResolver
    private val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    private data class Entry(val id: String, val name: String, val mime: String)
    private fun children(id: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        return requireNotNull(resolver.query(uri, columns, null, null, null)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Entry(cursor.getString(0), cursor.getString(1), cursor.getString(2).orEmpty()))
            }
        }
    }
    private fun parts(path: String): List<String> {
        val parts = path.split('/').filter(String::isNotEmpty)
        require(parts.none { it == "." || it == ".." || '\u0000' in it }) { "Invalid relative path" }
        return parts
    }
    private fun resolve(parts: List<String>): String {
        var id = DocumentsContract.getTreeDocumentId(tree)
        for (part in parts) id = children(id).firstOrNull { it.name == part }?.id ?: error("File not found: $part")
        return id
    }
    fun list(path: String): String = children(resolve(parts(path))).joinToString("\n") {
        "${if (it.mime == DocumentsContract.Document.MIME_TYPE_DIR) "dir" else "file"} ${it.name.replace('\n', ' ')}"
    }
    fun read(path: String): java.io.InputStream = requireNotNull(resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, resolve(parts(path)))))
    suspend fun write(path: String, content: String) = replace(path) { it.write(content.toByteArray(Charsets.UTF_8)) }

    suspend fun replace(path: String, write: suspend (java.io.OutputStream) -> Unit) {
        val parts = parts(path)
        require(parts.isNotEmpty()) { "File name required" }
        val name = parts.last()
        val parent = resolve(parts.dropLast(1))
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parent)
        val existing = children(parent).firstOrNull { it.name == name }
            ?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.id) }
        val extension = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        val id = UUID.randomUUID()
        var staging: Uri? = requireNotNull(DocumentsContract.createDocument(resolver, parentUri, mime, ".skyadb-transfer-$id$suffix"))
        var backup: Uri? = null
        try {
            requireNotNull(resolver.openOutputStream(requireNotNull(staging), "wt")).use { write(it) }
            currentCoroutineContext().ensureActive()
            if (existing != null) backup = requireNotNull(DocumentsContract.renameDocument(resolver, existing, ".skyadb-backup-$id$suffix"))
            try {
                requireNotNull(DocumentsContract.renameDocument(resolver, requireNotNull(staging), name))
                staging = null
            } catch (error: Throwable) {
                backup?.let { DocumentsContract.renameDocument(resolver, it, name) }
                throw error
            }
            backup?.let { DocumentsContract.deleteDocument(resolver, it) }
        } finally {
            staging?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        }
    }
}
