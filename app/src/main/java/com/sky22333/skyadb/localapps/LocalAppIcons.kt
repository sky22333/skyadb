package com.sky22333.skyadb.localapps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 本机应用图标：限流加载，避免 MIUI 图标管线洪峰。 */
object LocalAppIcons {
    private const val IconSize = 96
    private const val MaxConcurrentLoads = 3
    private val cache = LruCache<String, Bitmap>(96)
    private val loadGate = Semaphore(MaxConcurrentLoads)

    fun peek(packageName: String): Bitmap? = synchronized(cache) { cache.get(packageName) }

    suspend fun load(context: Context, packageName: String): Bitmap? {
        peek(packageName)?.let { return it }
        return loadGate.withPermit {
            peek(packageName)?.let { return@withPermit it }
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.applicationContext.packageManager
                        .getApplicationIcon(packageName)
                        .toScaledBitmap(IconSize)
                }.getOrNull()
            } ?: return@withPermit null
            synchronized(cache) { cache.put(packageName, bitmap) }
            bitmap
        }
    }

    private fun Drawable.toScaledBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable) {
            val source = bitmap
            if (source != null) {
                if (source.width == size && source.height == size) {
                    return source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
                }
                return Bitmap.createScaledBitmap(source, size, size, true)
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { out ->
            val canvas = Canvas(out)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }
}
