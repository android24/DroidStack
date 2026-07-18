package io.github.android24.droidstack.demo.chapter03

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal fun Activity.createLabContent(
    title: String,
    description: String
): LinearLayout {
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.dp, 18.dp, 20.dp, 28.dp)
    }

    content.addView(TextView(this).apply {
        text = title
        textSize = 26f
        setTextColor(Color.rgb(28, 32, 29))
        setTypeface(typeface, Typeface.BOLD)
    })
    content.addView(TextView(this).apply {
        text = description
        textSize = 15f
        setTextColor(Color.rgb(73, 82, 76))
        setPadding(0, 8.dp, 0, 18.dp)
    })

    setContentView(ScrollView(this).apply {
        addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    })
    return content
}

internal fun LinearLayout.addLabButton(
    text: String,
    onClick: () -> Unit
): Button {
    return Button(context).also { button ->
        button.text = text
        button.isAllCaps = false
        button.setOnClickListener { onClick() }
        addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp
            }
        )
    }
}

internal fun LinearLayout.addSectionTitle(text: String): TextView {
    return TextView(context).also { view ->
        view.text = text
        view.textSize = 18f
        view.setTextColor(Color.rgb(28, 32, 29))
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setPadding(0, 20.dp, 0, 6.dp)
        addView(view)
    }
}

internal fun LinearLayout.addBodyText(text: String): TextView {
    return TextView(context).also { view ->
        view.text = text
        view.textSize = 14f
        view.setTextColor(Color.rgb(73, 82, 76))
        view.setPadding(0, 4.dp, 0, 4.dp)
        addView(view)
    }
}

internal fun LinearLayout.addImageStrip(
    products: List<LabProduct>,
    onClick: ((LabProduct) -> Unit)? = null
): LinearLayout {
    val availableWidth = resources.displayMetrics.widthPixels - 56.dp
    val imageSize = (availableWidth / products.size).coerceAtMost(180.dp)
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    products.forEachIndexed { index, product ->
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = onClick != null
            setOnClickListener { onClick?.invoke(product) }
        }
        item.addView(
            ImageView(context).apply {
                setImageResource(product.drawableRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = product.name
            },
            LinearLayout.LayoutParams(
                imageSize,
                imageSize
            )
        )
        item.addView(TextView(context).apply {
            text = product.name
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(45, 53, 48))
            setPadding(2.dp, 6.dp, 2.dp, 0)
        })
        row.addView(
            item,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                if (index > 0) marginStart = 8.dp
            }
        )
    }

    addView(
        row,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )
    return row
}

internal val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
