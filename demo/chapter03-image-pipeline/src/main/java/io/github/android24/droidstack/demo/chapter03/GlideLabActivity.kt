package io.github.android24.droidstack.demo.chapter03

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey

class GlideLabActivity : BaseImageLabActivity() {

    override val engineName: String = "Glide"

    override fun loadImage(
        imageView: ImageView,
        model: Any,
        placeholderResId: Int,
        cacheKey: String,
        targetSizePx: Int,
        cropImage: Boolean,
        startedAt: Long
    ) {
        Glide.with(imageView)
            .load(model)
            .signature(ObjectKey(cacheKey))
            .override(targetSizePx, targetSizePx)
            .placeholder(placeholderResId)
            .error(android.R.drawable.ic_menu_report_image)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    val rootCause = e?.rootCauses?.firstOrNull()?.javaClass?.simpleName
                    trace(
                        "Glide ERROR type=${rootCause ?: e?.javaClass?.simpleName} " +
                            "elapsed=${elapsedSince(startedAt)}ms"
                    )
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    trace(
                        "Glide SUCCESS source=$dataSource " +
                            "elapsed=${elapsedSince(startedAt)}ms"
                    )
                    return false
                }
            })
            .let { request ->
                if (cropImage) request.centerCrop() else request.fitCenter()
            }
            .into(imageView)
    }

    override fun clearMemoryCache() {
        Glide.get(this).clearMemory()
    }

    override fun cancelRequests() {
        Glide.with(imageView).clear(imageView)
    }
}
