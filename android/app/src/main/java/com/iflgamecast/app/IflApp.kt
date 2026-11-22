package com.iflgamecast.app

import android.app.Application
import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest

class IflApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Api.init(this)
        getSharedPreferences("ifl_settings", MODE_PRIVATE).getString("base", null)?.let { Api.base = it }

        // Team logos are PNG or SVG files served by the Gamecast server. They share the API's connection, are kept on disk
        // regardless of the server's cache headers (see Logos.request for the weekly refresh) and are decoded once per size class.
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(Http.client)
                .components { add(SvgDecoder.Factory()) }
                .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
                .diskCache { DiskCache.Builder().directory(cacheDir.resolve("logos")).maxSizeBytes(32L * 1024 * 1024).build() }
                .respectCacheHeaders(false)
                .crossfade(false)
                .build()
        )

        // Start the first screen's requests and the logo downloads now, while the UI is still being built.
        Prefetch.early(this)
    }
}

/** Team-logo image requests: one decode per size class (small / large) so every badge on screen reuses the same bitmap. */
object Logos {
    private fun memKey(url: String, big: Boolean) = "$url@${if (big) "L" else "S"}"

    fun request(ctx: Context, url: String, big: Boolean): ImageRequest =
        ImageRequest.Builder(ctx).data(url).size(if (big) 256 else 112)
            .memoryCacheKey(memKey(url, big))
            .diskCacheKey("$url#${System.currentTimeMillis() / 604_800_000L}")   // re-downloaded about once a week
            .build()

    /** True when the bitmap is already in memory (the badge can skip its placeholder). */
    fun isCached(ctx: Context, url: String, big: Boolean): Boolean =
        Coil.imageLoader(ctx).memoryCache?.get(MemoryCache.Key(memKey(url, big))) != null
}
