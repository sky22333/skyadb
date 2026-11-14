package com.sky22333.skyadb.ai

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import java.io.ByteArrayOutputStream
import com.sky22333.skyadb.R
import com.sky22333.skyadb.i18n.appString

internal const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
internal const val MAX_CONTEXT_IMAGE_BYTES = 20 * 1024 * 1024

internal fun readImage(context: Context, uri: Uri, name: String, remainingBytes: Int = MAX_CONTEXT_IMAGE_BYTES): AttachmentSource.Image? {
    val mime = imageMime(context.contentResolver.getType(uri), name) ?: return null
    if (!mime.startsWith("image/")) return null
    require(mime in setOf("image/png", "image/jpeg", "image/webp", "image/gif")) { appString(R.string.ai_image_unsupported) }
    val limit = minOf(MAX_IMAGE_BYTES, remainingBytes)
    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(out.size() + count <= limit) { appString(if (remainingBytes < MAX_IMAGE_BYTES) R.string.ai_images_too_large else R.string.ai_image_too_large) }
            out.write(buffer, 0, count)
        }
        out.toByteArray()
    }
    return AttachmentSource.Image(AttachmentContent.Binary.Bytes(bytes), mime.substringAfter('/'), mime, name)
}

internal fun imageMime(declaredType: String?, name: String): String? {
    val declared = declaredType?.lowercase()
    val mime = if (declared == null || declared == "application/octet-stream") {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
    } else declared
    return if (mime == "image/jpg") "image/jpeg" else mime
}
