package net.credflare.sdk

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.PathParser

class SpecIconDrawable(private val icon: SpecIcon, private val color: Int): Drawable() {
    private val paths: List<Pair<SpecIconPath, Path>> = icon.paths.map { it to PathParser.createPathFromPathData(it.d) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private var tint = color
    override fun draw(canvas: Canvas) {
        val scale = minOf(bounds.width(), bounds.height()) / icon.viewBox.toFloat()
        canvas.save()
        canvas.translate(bounds.left + (bounds.width() - icon.viewBox.toFloat() * scale) / 2, bounds.top + (bounds.height() - icon.viewBox.toFloat() * scale) / 2)
        canvas.scale(scale, scale)
        for((spec, path) in paths) {
            if(spec.fill) {
                paint.style = Paint.Style.FILL; paint.color = tint; paint.alpha = (255 * spec.fillOpacity).toInt()
                canvas.drawPath(path, paint)
            }
            if(spec.stroke) {
                paint.style = Paint.Style.STROKE; paint.color = tint; paint.alpha = (255 * spec.strokeOpacity).toInt(); paint.strokeWidth = spec.strokeWidth.toFloat()
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = -1
    override fun getIntrinsicHeight() = -1
}

internal class SpecUi(private val context: Context) {
    val dark get() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val palette get() = if(dark) CredflareSpec.Tokens.DARK else CredflareSpec.Tokens.LIGHT
    fun color(value: Long) = value.toInt()
    fun dp(value: Number) = (value.toFloat() * context.resources.displayMetrics.density).toInt()

    fun text(value: String, style: SpecTextStyle = CredflareSpec.Tokens.TYPE_BODY, color: Long = palette.ink): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, style.size.toFloat())
        if(android.os.Build.VERSION.SDK_INT >= 28) lineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.lineHeight.toFloat(), context.resources.displayMetrics).toInt()
        typeface = Typeface.create(Typeface.SANS_SERIF, if(style.weight >= 700) Typeface.BOLD else Typeface.NORMAL)
        if(style.weight in 500..699 && android.os.Build.VERSION.SDK_INT >= 28) typeface = Typeface.create(Typeface.SANS_SERIF, 500, false)
        gravity = Gravity.CENTER_HORIZONTAL
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        setTextColor(color(color))
    }

    @Suppress("DEPRECATION")
    fun systemBars(window: android.view.Window) {
        val light = !dark
        if(android.os.Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if(light) mask else 0, mask)
        } else {
            val flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if(android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            window.decorView.systemUiVisibility = if(light) window.decorView.systemUiVisibility or flags else window.decorView.systemUiVisibility and flags.inv()
        }
    }

    fun rounded(fill: Long, stroke: Long? = null, radius: Int = CredflareSpec.Tokens.RADIUS_LG) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat(); setColor(color(fill)); stroke?.let { setStroke(dp(1), color(it)) }
    }

    fun button(label: String, primary: Boolean = true, enabled: Boolean = true, action: () -> Unit): Button = Button(context).apply {
        text = label; isAllCaps = false; isEnabled = enabled
        setTextSize(TypedValue.COMPLEX_UNIT_SP, CredflareSpec.Tokens.TYPE_BUTTON.size.toFloat())
        gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
        minHeight = dp(CredflareSpec.Tokens.SIZE_BUTTON_HEIGHT); minimumHeight = minHeight
        stateListAnimator = null
        val fill = if(primary) palette.primary else palette.surface
        background = RippleDrawable(ColorStateList.valueOf(color(palette.soft)), rounded(fill, if(primary) null else palette.border, CredflareSpec.Tokens.RADIUS_MD), null)
        setTextColor(color(if(primary) palette.onPrimary else palette.primary))
        alpha = if(enabled) 1f else 0.5f
        setOnClickListener { action() }
    }

    fun icon(spec: SpecIcon, size: Int = CredflareSpec.Tokens.SIZE_ICON, color: Long = palette.primary): ImageView = ImageView(context).apply {
        setImageDrawable(SpecIconDrawable(spec, color(color)))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    fun card(title: String, subtitle: String?, leading: SpecIcon?, enabled: Boolean = true, action: (() -> Unit)?): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        minimumHeight = dp(CredflareSpec.Tokens.SIZE_OPTION_HEIGHT)
        setPadding(dp(CredflareSpec.Tokens.SPACING_LG), dp(CredflareSpec.Tokens.SPACING_LG), dp(CredflareSpec.Tokens.SPACING_LG), dp(CredflareSpec.Tokens.SPACING_LG))
        background = RippleDrawable(ColorStateList.valueOf(color(palette.soft)), rounded(palette.surface, palette.border), null)
        leading?.let { addView(icon(it)) }
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER; setPadding(0, dp(CredflareSpec.Tokens.SPACING_SM), 0, 0) }
        row.addView(text(title, CredflareSpec.Tokens.TYPE_SUBTITLE), LinearLayout.LayoutParams(-2, -2).apply { weight = 0f })
        if(action != null) row.addView(icon(CredflareSpec.Icons.CHEVRON_RIGHT, 20, palette.muted).apply { layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(CredflareSpec.Tokens.SPACING_XS) } })
        addView(row, LinearLayout.LayoutParams(-1, -2))
        subtitle?.let { addView(text(it, CredflareSpec.Tokens.TYPE_SMALL, palette.muted).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END }) }
        if(action != null) {
            isClickable = enabled; isFocusable = enabled; alpha = if(enabled) 1f else 0.6f
            if(enabled) setOnClickListener { action() }
        }
        contentDescription = listOfNotNull(title, subtitle).joinToString(". ")
    }
}
