package io.github.android24.droidstack.demo.chapter03

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class RaceLabActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val scheduled = mutableListOf<Runnable>()

    private lateinit var targetView: ImageView
    private lateinit var targetLabel: TextView
    private lateinit var logView: TextView
    private var currentOwner = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = createLabContent(
            title = "Race Lab",
            description = "背包请求 A 用 2 秒返回；耳机请求 B 在 300 ms 后接管同一个 ImageView，并在 800 ms 时先返回。"
        )

        content.addSectionTitle("请求身份")
        content.addImageStrip(labProducts.take(2))

        content.addSectionTitle("显示目标")
        targetView = ImageView(this).apply {
            setImageResource(R.drawable.product_camera)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "竞态实验图片 Target"
        }
        content.addView(
            targetView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                260.dp
            )
        )
        targetLabel = content.addBodyText("黄色相机是初始占位图；先预测最终会显示背包还是耳机。")
        targetLabel.gravity = Gravity.CENTER

        content.addLabButton("运行不安全模式") { runRace(safe = false) }
        content.addLabButton("运行安全模式") { runRace(safe = true) }

        content.addSectionTitle("时间线")
        logView = content.addBodyText("先预测最终显示商品 A 还是商品 B。")
        logView.setTextIsSelectable(true)
    }

    private fun runRace(safe: Boolean) {
        clearScheduled()
        logView.text = ""
        currentOwner = "A"
        showPlaceholder("A：森林背包加载中")
        appendLog("0 ms：ImageView 绑定 A（森林背包），Request-A 开始")

        schedule(300) {
            currentOwner = "B"
            showPlaceholder("B：珊瑚耳机加载中")
            appendLog("300 ms：同一 ImageView 被复用给 B（珊瑚耳机）")
        }

        schedule(800) {
            deliver(id = "B", safe = safe)
        }

        schedule(2_000) {
            deliver(id = "A", safe = safe)
        }
    }

    private fun deliver(id: String, safe: Boolean) {
        if (safe && currentOwner != id) {
            appendLog("${nowLabel(id)}：Request-$id 晚到，已失去 Target 所有权，丢弃结果")
            return
        }

        val product = if (id == "A") labProducts[0] else labProducts[1]
        targetView.alpha = 1f
        targetView.setImageResource(product.drawableRes)
        targetLabel.text = "当前画面：${product.name}（Request-$id）"
        appendLog("${nowLabel(id)}：Request-$id 交付 ${product.name}，ImageView 被更新")
    }

    private fun showPlaceholder(label: String) {
        targetView.setImageResource(R.drawable.product_camera)
        targetView.alpha = 0.42f
        targetLabel.text = "$label；黄色相机为占位图"
    }

    private fun nowLabel(id: String): String = if (id == "A") "2000 ms" else "800 ms"

    private fun schedule(delayMs: Long, block: () -> Unit) {
        val runnable = Runnable(block)
        scheduled += runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun appendLog(message: String) {
        logView.append(if (logView.text.isEmpty()) message else "\n$message")
    }

    private fun clearScheduled() {
        scheduled.forEach(handler::removeCallbacks)
        scheduled.clear()
    }

    override fun onDestroy() {
        clearScheduled()
        super.onDestroy()
    }
}
