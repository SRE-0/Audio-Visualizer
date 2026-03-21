package com.sre404.audiovisualizer.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import com.sre404.audiovisualizer.AudioConstants
import com.sre404.audiovisualizer.R

/**
 * Bar-based audio visualizer view.
 *
 * Renders vertical bars whose heights are driven by processed audio data
 * provided by [BaseVisualizerView]. Each bar represents either a frequency
 * band (FFT mode) or overall energy level (VOLUME mode).
 *
 * This view handles only rendering — all audio logic lives in [BaseVisualizerView].
 *
 * XML attributes (all optional):
 * - app:barCount   : number of bars (default [AudioConstants.DEFAULT_BAR_COUNT])
 * - app:solidColor : bar color when colorMode is SOLID (default Color.CYAN)
 * - app:colorMode  : "solid" or "rainbow" (default "rainbow")
 *
 * Example XML usage:
 * ```xml
 * <com.sre404.audiovisualizer.view.BarVisualizerView
 *     android:id="@+id/visualizer"
 *     android:layout_width="match_parent"
 *     android:layout_height="200dp"
 *     app:barCount="48"
 *     app:solidColor="#FF4081"
 *     app:colorMode="rainbow" />
 * ```
 */
class BarVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseVisualizerView(context, attrs) {

    // -------------------------------------------------------------------------
    // CONFIGURATION
    // -------------------------------------------------------------------------

    /** Color used when [colorMode] is [ColorMode.SOLID]. */
    private var solidColor: Int = AudioConstants.DEFAULT_SOLID_COLOR

    /** Pixel gap between adjacent bars. Float for sub-pixel precision. */
    private val gap: Float = AudioConstants.DEFAULT_BAR_GAP

    // -------------------------------------------------------------------------
    // RAINBOW ANIMATION STATE
    // -------------------------------------------------------------------------

    /**
     * Current hue offset in degrees [0, 360).
     * Incremented each frame when [colorMode] is [ColorMode.RAINBOW].
     */
    private var hueOffset: Float = 0f

    // -------------------------------------------------------------------------
    // INIT — read XML attributes
    // -------------------------------------------------------------------------

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BarVisualizerView)
            try {
                val xmlBarCount = typedArray.getInt(
                    R.styleable.BarVisualizerView_barCount,
                    AudioConstants.DEFAULT_BAR_COUNT
                )
                val xmlSolidColor = typedArray.getColor(
                    R.styleable.BarVisualizerView_solidColor,
                    AudioConstants.DEFAULT_SOLID_COLOR
                )
                val xmlColorMode = typedArray.getInt(
                    R.styleable.BarVisualizerView_colorMode,
                    COLOR_MODE_RAINBOW
                )

                setBarCount(xmlBarCount)
                setSolidColor(xmlSolidColor)
                setColorMode(if (xmlColorMode == COLOR_MODE_SOLID) ColorMode.SOLID else ColorMode.RAINBOW)

            } finally {
                typedArray.recycle()
            }
        }
    }

    // -------------------------------------------------------------------------
    // PUBLIC API
    // -------------------------------------------------------------------------

    /**
     * Sets the solid bar color used when [colorMode] is [ColorMode.SOLID].
     * Has no visual effect in [ColorMode.RAINBOW] mode.
     *
     * @param color ARGB color integer (e.g. [Color.CYAN])
     */
    fun setSolidColor(color: Int) {
        solidColor = color
        if (colorMode == ColorMode.SOLID) invalidate()
    }

    // -------------------------------------------------------------------------
    // SUBCLASS HOOKS
    // -------------------------------------------------------------------------

    override fun onBarCountChanged(count: Int) {
        // Nothing to cache per bar count in this implementation.
        // Override hook available for subclasses that precompute bar data.
    }

    // -------------------------------------------------------------------------
    // DRAWING
    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val data = bytes ?: return
        if (data.isEmpty() || width == 0 || height == 0) return

        val barWidth = width / barCount.toFloat()
        paint.strokeWidth = (barWidth - gap).coerceAtLeast(1f)

        // Advance hue offset every frame in rainbow mode
        if (colorMode == ColorMode.RAINBOW) {
            hueOffset = (hueOffset + AudioConstants.RAINBOW_HUE_SPEED) % 360f
        }

        for (i in 0 until barCount) {
            // Clamp index in case data.size < barCount (e.g. on first frame)
            val index      = i.coerceIn(0, data.size - 1)
            val value      = (127 - data[index]).coerceIn(0, 127)
            val barHeight  = (value / 127f) * height
            val centerX    = i * barWidth + barWidth / 2f

            paint.color = when (colorMode) {
                ColorMode.SOLID   -> solidColor
                ColorMode.RAINBOW -> {
                    // Each bar gets a hue offset proportional to its position,
                    // creating a traveling wave effect across the spectrum
                    val hue = (hueOffset + (i.toFloat() / barCount) * 120f) % 360f
                    Color.HSVToColor(floatArrayOf(
                        hue,
                        AudioConstants.RAINBOW_SATURATION,
                        AudioConstants.RAINBOW_BRIGHTNESS
                    ))
                }
            }

            canvas.drawLine(
                centerX,
                height.toFloat(),
                centerX,
                height - barHeight,
                paint
            )
        }

        // Schedule next frame only if animation is active
        if (colorMode == ColorMode.RAINBOW) invalidate()
    }

    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    companion object {
        private const val COLOR_MODE_SOLID   = 0
        private const val COLOR_MODE_RAINBOW = 1
    }
}