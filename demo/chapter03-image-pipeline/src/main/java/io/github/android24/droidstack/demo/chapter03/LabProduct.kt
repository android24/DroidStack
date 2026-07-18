package io.github.android24.droidstack.demo.chapter03

internal data class LabProduct(
    val id: String,
    val name: String,
    val drawableRes: Int,
    val remoteUrl: String
)

internal val labProducts = listOf(
    LabProduct(
        id = "backpack",
        name = "森林背包",
        drawableRes = R.drawable.product_backpack,
        remoteUrl = "https://images.unsplash.com/photo-1553062407-98eeb64c6a62" +
            "?auto=format&fit=crop&w=1200&q=85"
    ),
    LabProduct(
        id = "headphones",
        name = "珊瑚耳机",
        drawableRes = R.drawable.product_headphones,
        remoteUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e" +
            "?auto=format&fit=crop&w=1200&q=85"
    ),
    LabProduct(
        id = "camera",
        name = "黄色相机",
        drawableRes = R.drawable.product_camera,
        remoteUrl = "https://images.unsplash.com/photo-1516035069371-29a1b244cc32" +
            "?auto=format&fit=crop&w=1200&q=85"
    )
)
