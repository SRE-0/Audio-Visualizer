package com.sre404.audiovisualizer.engine

import android.util.Log
import com.sre404.audiovisualizer.AudioConstants
import com.sre404.audiovisualizer.input.AudioInput

/**
 * Central coordinator between the audio input source and the native DSP processor.
 *
 * Responsibilities:
 * - Owning the [NativeAudioProcessor] instance
 * - Routing audio buffers from [AudioInput] to the correct processing path
 * - Managing start/stop lifecycle safely (prevents double-start)
 * - Propagating processed bar data via [onBytesReady]
 *
 * @param barCount     Number of output bars. Must be in [MIN_BAR_COUNT, MAX_BAR_COUNT].
 * @param onBytesReady Callback invoked on every processed frame with bar level data.
 *                     Called from a background thread — post to main thread before drawing.
 */
internal class AudioEngine(
    barCount: Int,
    private val onBytesReady: (ByteArray) -> Unit
) {

    private val tag = "AudioEngine"

    /**
     * Clamped bar count used for all buffer allocations.
     * Prevents division-by-zero in native code.
     */
    private val safeBarCount: Int = barCount.coerceIn(
        AudioConstants.MIN_BAR_COUNT,
        AudioConstants.MAX_BAR_COUNT
    )

    private val nativeProcessor = NativeAudioProcessor()

    /**
     * Pre-allocated output buffer for FFT in-place processing.
     * Avoids per-frame heap allocation on the audio thread.
     */
    private val fftOutputBuffer = ByteArray(safeBarCount)

    private var input: AudioInput? = null
    private var mode: AudioProcessingMode = AudioConstants.DEFAULT_PROCESSING_MODE
    private var gain: Float = AudioConstants.DEFAULT_GAIN

    /** Prevents calling [start] twice without an intervening [stop]. */
    @Volatile
    private var isRunning: Boolean = false

    /**
     * Configures the audio source and processing mode.
     * Stops any active capture before applying the new configuration.
     *
     * @param audioInput     The [AudioInput] implementation to use
     * @param processingMode [AudioProcessingMode.FFT] or [AudioProcessingMode.VOLUME]
     */
    fun configure(audioInput: AudioInput, processingMode: AudioProcessingMode) {
        if (isRunning) {
            Log.d(tag, "configure() called while running — stopping first")
            stop()
        }
        input = audioInput
        mode = processingMode
    }

    /**
     * Updates the DSP gain multiplier.
     * Safe to call at any time, including while running.
     *
     * @param gain Gain multiplier (1.0 = neutral). Clamped to [0.0, 10.0].
     */
    fun setGain(gain: Float) {
        this.gain = gain.coerceIn(AudioConstants.MIN_GAIN, AudioConstants.MAX_GAIN)
        nativeProcessor.applyGain(this.gain)
    }

    /**
     * Starts audio capture and processing.
     * No-op if already running or if no input has been configured.
     */
    fun start() {
        val currentInput = input

        if (currentInput == null) {
            Log.w(tag, "start() called but no input configured — call configure() first")
            return
        }

        if (isRunning) {
            Log.w(tag, "start() called while already running — ignoring")
            return
        }

        isRunning = true
        nativeProcessor.applyGain(gain)

        currentInput.start(
            processingMode = mode,
            onPcmData = { pcm ->
                if (isRunning && mode == AudioProcessingMode.VOLUME) {
                    val bytes = nativeProcessor.safePcm(pcm, safeBarCount)
                    onBytesReady(bytes)
                }
            },
            onFftData = { fft ->
                if (isRunning && mode == AudioProcessingMode.FFT) {
                    nativeProcessor.safeFftInPlace(fft, fftOutputBuffer, safeBarCount)
                    onBytesReady(fftOutputBuffer)
                }
            }
        )
    }

    /**
     * Stops audio capture and releases the input source.
     * Safe to call even if already stopped.
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        input?.stop()
    }
}