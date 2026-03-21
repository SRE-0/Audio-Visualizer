package com.sre404.audiovisualizer.view

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.sre404.audiovisualizer.AudioConstants
import com.sre404.audiovisualizer.engine.AudioEngine
import com.sre404.audiovisualizer.engine.AudioProcessingMode
import com.sre404.audiovisualizer.input.AudioInputType
import com.sre404.audiovisualizer.input.MicAudioInput
import com.sre404.audiovisualizer.input.SystemAudioInput

/**
 * Base class for all audio visualizer views.
 *
 * Responsibilities:
 * - Owns and manages the [AudioEngine] lifecycle
 * - Builds the correct [AudioInput] from the given [AudioInputType]
 * - Controls bar count, gain, color mode, and processing mode
 * - Posts processed audio frames to the UI thread for drawing
 * - Automatically stops the engine when the view is detached from the window
 *
 * Subclasses implement only [onDraw] — they receive bar data via [bytes]
 * and must never interact with the engine directly.
 *
 * Usage:
 * ```kotlin
 * barView.setup(
 *     inputType      = AudioInputType.MICROPHONE,
 *     processingMode = AudioProcessingMode.FFT
 * )
 * barView.start()
 * // ...
 * barView.stop()
 * ```
 *
 * Permissions:
 * The consuming app must hold [android.Manifest.permission.RECORD_AUDIO]
 * before calling [start]. For [AudioInputType.SYSTEM_AUDIO],
 * [android.Manifest.permission.MODIFY_AUDIO_SETTINGS] is also required.
 * This class does not request permissions — it trusts the caller.
 */
abstract class BaseVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // -------------------------------------------------------------------------
    // AUDIO DATA
    // -------------------------------------------------------------------------

    /**
     * Latest processed bar level data.
     * Set from the audio thread via [postInvalidate].
     * Read from the UI thread in [onDraw].
     *
     * Values are in [0, 127] where 127 = silence and 0 = maximum energy.
     */
    @Volatile
    protected var bytes: ByteArray? = null

    // -------------------------------------------------------------------------
    // DRAWING
    // -------------------------------------------------------------------------

    /** Shared [Paint] instance for all subclass drawing operations. */
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // -------------------------------------------------------------------------
    // CONFIGURATION STATE
    // -------------------------------------------------------------------------

    /**
     * Current number of visualizer bars.
     * Changing this recreates the [AudioEngine] if it is currently running.
     */
    protected var barCount: Int = AudioConstants.DEFAULT_BAR_COUNT
        private set

    /** Current color rendering mode applied in [onDraw]. */
    protected var colorMode: ColorMode = AudioConstants.DEFAULT_COLOR_MODE
        private set

    /** Current DSP gain multiplier. */
    private var gain: Float = AudioConstants.DEFAULT_GAIN

    /** Current audio input source type. */
    private var inputType: AudioInputType = AudioInputType.SYSTEM_AUDIO

    /** Current audio processing mode. */
    private var processingMode: AudioProcessingMode = AudioConstants.DEFAULT_PROCESSING_MODE

    // -------------------------------------------------------------------------
    // ENGINE
    // -------------------------------------------------------------------------

    /**
     * The active [AudioEngine] instance.
     * Recreated when [barCount] changes or when [setup] is called.
     */
    private var engine: AudioEngine? = null

    /** True if [start] has been called and [stop] has not yet been called. */
    @Volatile
    private var isRunning: Boolean = false

    // -------------------------------------------------------------------------
    // PUBLIC API
    // -------------------------------------------------------------------------

    /**
     * Configures the audio source and processing mode.
     * Must be called before [start].
     * Safe to call while stopped — does nothing if currently running.
     *
     * @param inputType      The audio capture source to use.
     *                       See [AudioInputType] for available options.
     * @param processingMode How audio data is analyzed.
     *                       [AudioProcessingMode.FFT] for frequency bars,
     *                       [AudioProcessingMode.VOLUME] for energy bars.
     */
    fun setup(
        inputType: AudioInputType = AudioInputType.SYSTEM_AUDIO,
        processingMode: AudioProcessingMode = AudioConstants.DEFAULT_PROCESSING_MODE
    ) {
        if (isRunning) {
            android.util.Log.w(TAG, "setup() called while running — stop first")
            return
        }
        this.inputType      = inputType
        this.processingMode = processingMode
    }

    /**
     * Starts audio capture and visualization.
     * Builds a fresh [AudioEngine] on every call.
     *
     * Requires the consuming app to have already obtained
     * [android.Manifest.permission.RECORD_AUDIO] before calling this.
     */
    fun start() {
        if (isRunning) {
            android.util.Log.w(TAG, "start() called while already running — ignoring")
            return
        }

        val newEngine = buildEngine()
        engine = newEngine
        isRunning = true
        newEngine.start()
    }

    /**
     * Stops audio capture and clears the current bar data.
     * Safe to call even if already stopped.
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        engine?.stop()
        engine = null
        bytes = null
        postInvalidate()
    }

    /**
     * Updates the number of visualizer bars.
     * If the visualizer is currently running, it is restarted automatically
     * so the new bar count takes effect immediately.
     *
     * @param count Number of bars. Clamped to [AudioConstants.MIN_BAR_COUNT]..[AudioConstants.MAX_BAR_COUNT].
     */
    fun setBarCount(count: Int) {
        val safe = count.coerceIn(AudioConstants.MIN_BAR_COUNT, AudioConstants.MAX_BAR_COUNT)
        if (safe == barCount) return

        val wasRunning = isRunning
        if (wasRunning) stop()

        barCount = safe
        onBarCountChanged(safe)

        if (wasRunning) start()
    }

    /**
     * Sets the DSP gain multiplier.
     * Safe to call at any time, including while running.
     *
     * @param gain Gain multiplier. 1.0 = neutral, 0.0 = silence.
     *             Clamped to [AudioConstants.MIN_GAIN]..[AudioConstants.MAX_GAIN].
     */
    fun setGain(gain: Float) {
        this.gain = gain.coerceIn(AudioConstants.MIN_GAIN, AudioConstants.MAX_GAIN)
        engine?.setGain(this.gain)
    }

    /**
     * Sets the color rendering mode for all bars.
     * Triggers a redraw immediately.
     *
     * @param mode [ColorMode.SOLID] or [ColorMode.RAINBOW]
     */
    fun setColorMode(mode: ColorMode) {
        colorMode = mode
        invalidate()
    }

    // -------------------------------------------------------------------------
    // LIFECYCLE — auto-stop on detach
    // -------------------------------------------------------------------------

    /**
     * Called when the view is detached from the window.
     * Automatically stops the engine to prevent audio capture
     * continuing after the view is no longer visible.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    // -------------------------------------------------------------------------
    // SUBCLASS HOOKS
    // -------------------------------------------------------------------------

    /**
     * Called when [barCount] changes via [setBarCount].
     * Subclasses can override this to update internal state that
     * depends on the bar count (e.g. color arrays, cached widths).
     *
     * @param count The new bar count, already clamped and validated.
     */
    protected open fun onBarCountChanged(count: Int) {}

    // -------------------------------------------------------------------------
    // INTERNAL HELPERS
    // -------------------------------------------------------------------------

    /**
     * Builds a new [AudioEngine] configured with the current
     * [inputType], [processingMode], [barCount], and [gain].
     *
     * The engine routes processed bar data back to [bytes] and
     * schedules a redraw via [postInvalidate] on every frame.
     *
     * @return A fully configured but not yet started [AudioEngine].
     */
    private fun buildEngine(): AudioEngine {
        val input = when (inputType) {
            AudioInputType.MICROPHONE   -> MicAudioInput()
            AudioInputType.SYSTEM_AUDIO -> SystemAudioInput()
        }

        return AudioEngine(
            barCount    = barCount,
            onBytesReady = { data ->
                bytes = data
                postInvalidate()
            }
        ).also { eng ->
            eng.configure(input, processingMode)
            eng.setGain(gain)
        }
    }

    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "BaseVisualizerView"
    }
}