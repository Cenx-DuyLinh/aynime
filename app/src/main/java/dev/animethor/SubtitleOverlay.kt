package dev.animethor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class SubtitleOverlay(private val textView: TextView, private val container: View) {
    private var style = OverlayStyle.DEFAULT

    fun applyStyle(newStyle: OverlayStyle) {
        style = newStyle
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newStyle.textSizeSp.toFloat())
        textView.minLines = 1
        textView.background = if (newStyle.softEdges) softBand(newStyle) else roundedBox(newStyle)
        textView.setShadowLayer(TEXT_SHADOW_RADIUS, 0f, TEXT_SHADOW_OFFSET, Color.argb(160, 0, 0, 0))
        textView.setTextColor(opaque(newStyle.textColor))
        updatePlacement()
    }

    fun updatePlacement() {
        val width = container.width
        val height = container.height
        if (width == 0 || height == 0) {
            container.post { updatePlacement() }
            return
        }
        val layoutParams = textView.layoutParams as FrameLayout.LayoutParams
        val newWidth = width * style.widthPercent / 100
        val newBottomMargin = height * style.bottomMarginPercent / 100
        val newBandHeight = height * style.bandHeightPercent / 100
        if (textView.minHeight != newBandHeight) {
            textView.minHeight = newBandHeight
        }
        if (layoutParams.width == newWidth && layoutParams.bottomMargin == newBottomMargin) {
            return
        }
        layoutParams.width = newWidth
        layoutParams.bottomMargin = newBottomMargin
        textView.layoutParams = layoutParams
    }

    fun show(text: String) {
        val showsEmptyBox = text.isBlank() && style.keepBoxBetweenLines
        if (text.isBlank() && !showsEmptyBox) {
            textView.visibility = View.GONE
            return
        }
        if (textView.text.toString() != text) {
            textView.text = text
        }
        textView.visibility = View.VISIBLE
    }

    fun hide() {
        textView.visibility = View.GONE
    }

    fun bandTopFromBottomPx(): Float {
        val bottomMargin = container.height * style.bottomMarginPercent / 100f
        val bandHeight = maxOf(textView.height, textView.minHeight).toFloat()
        return bottomMargin + bandHeight - textView.translationY
    }

    fun setLifted(lifted: Boolean) {
        val liftPixels = container.height * style.pausedLiftPercent / 100f
        val targetTranslation = if (lifted) -liftPixels else 0f
        if (textView.translationY == targetTranslation || isMovingTo == targetTranslation) {
            return
        }
        isMovingTo = targetTranslation
        textView.animate()
            .translationY(targetTranslation)
            .setDuration(LIFT_ANIMATION_MS)
            .withEndAction { isMovingTo = null }
            .start()
    }

    private var isMovingTo: Float? = null

    private fun opaque(rgb: Int): Int = rgb or 0xFF000000.toInt()

    private fun withAlpha(rgb: Int, alpha: Int): Int = Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))

    private fun boxColor(style: OverlayStyle): Int = withAlpha(style.bandColor, 255 * style.backgroundOpacityPercent / 100)

    private fun roundedBox(style: OverlayStyle): GradientDrawable = GradientDrawable().apply {
        setColor(boxColor(style))
        cornerRadius = textView.resources.displayMetrics.density * ROUNDED_BOX_CORNER_DP
    }

    private fun softBand(style: OverlayStyle): GradientDrawable {
        val solid = boxColor(style)
        val clear = withAlpha(style.bandColor, 0)
        return GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            setColors(intArrayOf(clear, solid, solid, clear), floatArrayOf(0f, SOFT_EDGE_FRACTION, 1f - SOFT_EDGE_FRACTION, 1f))
        }
    }

    private companion object {
        const val ROUNDED_BOX_CORNER_DP = 18
        const val SOFT_EDGE_FRACTION = 0.16f
        const val TEXT_SHADOW_RADIUS = 4f
        const val TEXT_SHADOW_OFFSET = 2f
        const val LIFT_ANIMATION_MS = 220L
    }
}

class OverlayStyleDialog(
    private val context: Context,
    initialStyle: OverlayStyle,
    private val onStyleChanged: (OverlayStyle) -> Unit,
) {
    private var style = initialStyle

    fun show(onClosed: () -> Unit) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(8))
        }
        content.addView(slider("Box darkness", 0, 100, style.backgroundOpacityPercent, "%") {
            style = style.copy(backgroundOpacityPercent = it)
        })
        content.addView(slider("Band height", 5, 40, style.bandHeightPercent, "% of screen") {
            style = style.copy(bandHeightPercent = it)
        })
        content.addView(slider("Text size", 14, 44, style.textSizeSp, " sp") {
            style = style.copy(textSizeSp = it)
        })
        content.addView(slider("Distance from bottom", 0, 80, style.bottomMarginPercent, "%") {
            style = style.copy(bottomMarginPercent = it)
        })
        content.addView(slider("Box width", 30, 100, style.widthPercent, "%") {
            style = style.copy(widthPercent = it)
        })
        content.addView(slider("Lift when paused", 0, 40, style.pausedLiftPercent, "%") {
            style = style.copy(pausedLiftPercent = it)
        })
        content.addView(colorRow("Bar colour", BAR_COLOR_CHOICES, style.bandColor) {
            style = style.copy(bandColor = it)
        })
        content.addView(colorRow("Text colour", TEXT_COLOR_CHOICES, style.textColor) {
            style = style.copy(textColor = it)
        })
        content.addView(softEdgesSwitch())
        content.addView(keepBoxSwitch())
        content.addView(note(BURNED_IN_SUBTITLE_TIP))
        MaterialAlertDialogBuilder(context)
            .setTitle("Subtitle box")
            .setView(content)
            .setPositiveButton("Done", null)
            .setNeutralButton("Reset") { _, _ ->
                style = OverlayStyle.DEFAULT
                onStyleChanged(style)
            }
            .setOnDismissListener { onClosed() }
            .show()
    }

    private fun softEdgesSwitch(): View = MaterialSwitch(context).apply {
        text = "Soft band (faded sides) instead of a box"
        isChecked = style.softEdges
        setPadding(0, dp(12), 0, 0)
        setOnCheckedChangeListener { _, isChecked ->
            style = style.copy(softEdges = isChecked)
            onStyleChanged(style)
        }
    }

    private fun keepBoxSwitch(): View = MaterialSwitch(context).apply {
        text = "Keep the box up between lines"
        isChecked = style.keepBoxBetweenLines
        setPadding(0, dp(12), 0, 0)
        setOnCheckedChangeListener { _, isChecked ->
            style = style.copy(keepBoxBetweenLines = isChecked)
            onStyleChanged(style)
        }
    }

    private fun slider(
        title: String,
        minimum: Int,
        maximum: Int,
        startValue: Int,
        unit: String,
        onValue: (Int) -> Unit,
    ): View {
        val label = TextView(context).apply {
            text = "$title: $startValue$unit"
            setPadding(0, dp(12), 0, 0)
        }
        val seekBar = SeekBar(context).apply {
            max = maximum - minimum
            progress = startValue - minimum
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress + minimum
                    label.text = "$title: $value$unit"
                    onValue(value)
                    onStyleChanged(style)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(seekBar)
        }
    }

    private fun note(text: String): View = TextView(context).apply {
        this.text = text
        alpha = 0.7f
        setPadding(0, dp(12), 0, 0)
    }

    private fun colorRow(title: String, choices: List<Int>, selectedColor: Int, onPick: (Int) -> Unit): View {
        val label = TextView(context).apply {
            text = title
            setPadding(0, dp(14), 0, dp(6))
        }
        val swatches = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val swatchViews = choices.map { color -> swatch(color, color == selectedColor) }
        swatchViews.forEachIndexed { index, swatchView ->
            swatchView.setOnClickListener {
                onPick(choices[index])
                onStyleChanged(style)
                swatchViews.forEachIndexed { otherIndex, otherView ->
                    otherView.background = swatchShape(choices[otherIndex], otherIndex == index)
                }
            }
            swatches.addView(swatchView, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(swatches)
        }
    }

    private fun swatch(color: Int, isSelected: Boolean): View = View(context).apply {
        background = swatchShape(color, isSelected)
        isClickable = true
        isFocusable = true
    }

    private fun swatchShape(color: Int, isSelected: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color or 0xFF000000.toInt())
        val ringColor = if (isSelected) SELECTED_RING_COLOR else UNSELECTED_RING_COLOR
        setStroke(dp(if (isSelected) 3 else 1), ringColor)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        val BAR_COLOR_CHOICES = listOf(0x141210, 0x000000, 0x10182A, 0x0F2018, 0x22142A, 0x3A2A22)
        val TEXT_COLOR_CHOICES = listOf(0xFFFDF8, 0xFFFFFF, 0xFFF4DC, 0xFFE45C, 0xCFE8FF, 0xFFC9D6)
        const val SELECTED_RING_COLOR = 0xFFD97757.toInt()
        const val UNSELECTED_RING_COLOR = 0xFF3A3833.toInt()
        const val BURNED_IN_SUBTITLE_TIP =
            "Burned-in English subtitles: move the box over them with Distance from bottom, and set PlayTranslate's " +
                "capture region to the box only. Below 100% darkness the English shows through and PlayTranslate may read it."
    }
}
