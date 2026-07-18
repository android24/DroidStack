package io.github.android24.droidstack.demo.chapter03

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

abstract class BaseImageLabActivity : Activity() {

    protected abstract val engineName: String

    protected lateinit var imageView: ImageView
    private lateinit var stateView: TextView
    private lateinit var logView: TextView
    private lateinit var sizeButton: Button
    private lateinit var sourceButton: Button
    private lateinit var scaleButton: Button
    private lateinit var failureButton: Button

    private var selectedProduct = labProducts.first()
    private var version = 1
    private var targetSizePx = 144
    private var useRemoteSource = false
    private var cropImage = true
    private var inject404 = false
    private var requestNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = createLabContent(
            title = "$engineName Lab",
            description = "选择图片和数据源，再修改版本、尺寸与故障条件，直接观察画面和 DataSource。"
        )

        content.addSectionTitle("选择实验图片")
        content.addImageStrip(labProducts) { product ->
            selectedProduct = product
            imageView.setImageResource(product.drawableRes)
            updateState()
            trace("切换为 ${product.name}；下一次请求使用 ${product.id} 模型")
        }

        content.addSectionTitle("当前 Target")
        imageView = ImageView(this).apply {
            setBackgroundColor(Color.rgb(232, 236, 233))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(selectedProduct.drawableRes)
            contentDescription = "$engineName 图片结果"
        }
        content.addView(
            imageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                280.dp
            ).apply {
                topMargin = 8.dp
            }
        )

        stateView = content.addBodyText("")
        updateState()

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        content.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        controls.addView(controlButton("版本 +1") {
            version += 1
            updateState()
            trace("版本更新为 v$version，下一次请求应使用新 Key")
        })
        sizeButton = controlButton("尺寸：144 px") {
            targetSizePx = if (targetSizePx == 144) 600 else 144
            sizeButton.text = "尺寸：$targetSizePx px"
            updateState()
            trace("目标尺寸改为 $targetSizePx px")
        }
        controls.addView(sizeButton)

        sourceButton = controlButton("来源：本地") {
            useRemoteSource = !useRemoteSource
            sourceButton.text = "来源：${if (useRemoteSource) "网络" else "本地"}"
            updateState()
            trace("数据源切换为 ${if (useRemoteSource) "HTTPS" else "drawable 资源"}")
        }
        controls.addView(sourceButton)

        scaleButton = content.addLabButton("Scale：CENTER_CROP") {
            cropImage = !cropImage
            imageView.scaleType = if (cropImage) {
                ImageView.ScaleType.CENTER_CROP
            } else {
                ImageView.ScaleType.FIT_CENTER
            }
            scaleButton.text = "Scale：${if (cropImage) "CENTER_CROP" else "FIT_CENTER"}"
            updateState()
            trace("Scale 切换为 ${if (cropImage) "CENTER_CROP" else "FIT_CENTER"}")
        }

        failureButton = content.addLabButton("注入 404：关") {
            inject404 = !inject404
            failureButton.text = "注入 404：${if (inject404) "开" else "关"}"
            updateState()
        }
        content.addLabButton("加载图片") { executeLoad() }
        content.addLabButton("清内存缓存") {
            clearMemoryCache()
            trace("内存缓存已清空；磁盘缓存保持不变")
        }

        content.addSectionTitle("请求日志")
        logView = content.addBodyText("尚未执行请求")
        logView.setTextIsSelectable(true)
    }

    private fun controlButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = 4.dp
            }
        }
    }

    private fun executeLoad() {
        requestNumber += 1
        val model: Any = if (inject404) {
            "https://httpstat.us/404"
        } else if (useRemoteSource) {
            selectedProduct.remoteUrl
        } else {
            selectedProduct.drawableRes
        }
        val source = if (useRemoteSource || inject404) "network" else "resource"
        val scale = if (cropImage) "crop" else "fit"
        val key = "${selectedProduct.id}:$source:v$version:${targetSizePx}px:$scale"
        val startedAt = SystemClock.elapsedRealtime()

        trace("#${requestNumber} START product=${selectedProduct.name} key=$key")
        loadImage(
            imageView = imageView,
            model = model,
            placeholderResId = selectedProduct.drawableRes,
            cacheKey = key,
            targetSizePx = targetSizePx,
            cropImage = cropImage,
            startedAt = startedAt
        )
    }

    protected fun trace(message: String) {
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            if (!::logView.isInitialized || isFinishing || isDestroyed) return@runOnUiThread
            val previous = logView.text.toString().takeUnless { it == "尚未执行请求" }
            logView.text = listOfNotNull(previous, message).joinToString("\n")
        }
    }

    protected fun elapsedSince(startedAt: Long): Long {
        return SystemClock.elapsedRealtime() - startedAt
    }

    private fun updateState() {
        if (!::stateView.isInitialized) return
        val bytes = targetSizePx.toLong() * targetSizePx * 4
        stateView.text = buildString {
            append("version=v$version · target=${targetSizePx}px · ")
            append("ARGB_8888≈${formatBytes(bytes)} · ")
            append("product=${selectedProduct.name} · ")
            append("source=${if (useRemoteSource) "network" else "resource"} · ")
            append("scale=${if (cropImage) "crop" else "fit"} · ")
            append("404=${if (inject404) "on" else "off"}")
        }
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            "%.2f MiB".format(bytes / 1024.0 / 1024.0)
        } else {
            "%.1f KiB".format(bytes / 1024.0)
        }
    }

    protected abstract fun loadImage(
        imageView: ImageView,
        model: Any,
        placeholderResId: Int,
        cacheKey: String,
        targetSizePx: Int,
        cropImage: Boolean,
        startedAt: Long
    )

    protected abstract fun clearMemoryCache()

    protected abstract fun cancelRequests()

    override fun onDestroy() {
        cancelRequests()
        super.onDestroy()
    }
}
