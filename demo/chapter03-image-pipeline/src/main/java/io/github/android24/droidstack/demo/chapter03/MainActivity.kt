package io.github.android24.droidstack.demo.chapter03

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = createLabContent(
            title = "Image Pipeline Lab",
            description = "用三张真实图片观察请求、缓存、解码、Target 复用和错误恢复。"
        )

        content.addSectionTitle("本章实验图片")
        content.addImageStrip(labProducts)

        content.addSectionTitle("框架实验")
        content.addLabButton("Coil：请求、缓存与错误") {
            startActivity(Intent(this, CoilLabActivity::class.java))
        }
        content.addLabButton("Glide：请求、缓存与 Target") {
            startActivity(Intent(this, GlideLabActivity::class.java))
        }

        content.addSectionTitle("原理实验")
        content.addLabButton("Race Lab：制造并修复图片错位") {
            startActivity(Intent(this, RaceLabActivity::class.java))
        }

        content.addSectionTitle("实验方式")
        content.addBodyText("1. 先预测结果，再点击执行。")
        content.addBodyText("2. 一次只修改版本、尺寸或故障中的一个变量。")
        content.addBodyText("3. 根据日志解释 DataSource、缓存 Key 和结果交付。")
    }
}
