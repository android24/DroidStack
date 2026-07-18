package io.github.android24.droidstack.demo.chapter03

import android.widget.ImageView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.target
import coil3.size.Scale

class CoilLabActivity : BaseImageLabActivity() {

    override val engineName: String = "Coil"

    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .crossfade(250)
            .build()
    }

    override fun loadImage(
        imageView: ImageView,
        model: Any,
        placeholderResId: Int,
        cacheKey: String,
        targetSizePx: Int,
        cropImage: Boolean,
        startedAt: Long
    ) {
        val request = ImageRequest.Builder(this)
            .data(model)
            .size(targetSizePx, targetSizePx)
            .scale(if (cropImage) Scale.FILL else Scale.FIT)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .placeholder(placeholderResId)
            .error(android.R.drawable.ic_menu_report_image)
            .target(imageView)
            .listener(
                onStart = {
                    trace("Coil RUNNING")
                },
                onSuccess = { _, result ->
                    trace(
                        "Coil SUCCESS source=${result.dataSource} " +
                            "elapsed=${elapsedSince(startedAt)}ms"
                    )
                },
                onError = { _, result ->
                    trace(
                        "Coil ERROR type=${result.throwable::class.simpleName} " +
                            "elapsed=${elapsedSince(startedAt)}ms"
                    )
                },
                onCancel = {
                    trace("Coil CANCEL elapsed=${elapsedSince(startedAt)}ms")
                }
            )
            .build()

        imageLoader.enqueue(request)
    }

    override fun clearMemoryCache() {
        imageLoader.memoryCache?.clear()
    }

    override fun cancelRequests() {
        imageLoader.shutdown()
    }
}
