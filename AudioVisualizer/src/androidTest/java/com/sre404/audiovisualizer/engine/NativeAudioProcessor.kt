package com.sre404.audiovisualizer.engine

import android.util.Log
import com.sre404.audiovisualizer.AudioConstants

/**
 * Kotlin wrapper for the native C++ audio DSP processor.
 *
 * Responsibilities:
 * - Loading the native shared library on first instantiation
 * - Validating all parameters before forwarding to native code
 * - Providing a safe fallback if the native library fails to load
 *
 * All public methods are no-ops if the native library is unavailable,
 * preventing crashes in the consuming app.
 */
internal class NativeAudioProcessor {

    companion object {

        private const val LIBRARY_NAME = "audio_processor"
        private const val TAG = "NativeAudioProcessor"

        /**
         * Whether the native library loaded successfully.
         * Checked before every native call to prevent [UnsatisfiedLinkError].
         */
        var isNativeAvailable: Boolean = false
            private set

        init {
            isNativeAvailable = try {
                System.loadLibrary(LIBRARY_NAME)
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library '$LIBRARY_NAME': ${e.message}")
                false
            }
        }
    }

    // ---------- NATIVE DECLARATIONS ----------

    /**
     * Sets the global DSP gain multiplier in native code.
     * @param gain Gain multiplier. Clamped to [0.0, 10.0] in C++.
     */
    private external fun setGain(gain: Float)

    /**
     * Processes raw PCM samples and returns visualizer-compatible bar levels.
     * @param pcmBuffer Raw 16-bit signed PCM samples from AudioRecord
     * @param barCount  Number of output bars
     * @return ByteArray of length barCount, values in [0, 127]
     */
    private external fun processPcm(
        pcmBuffer: ShortArray,
        barCount: Int
    ): ByteArray

    /**
     * Processes FFT data from Android Visualizer in-place.
     * @param fftBuffer   Interleaved Re/I'm byte pairs from Visualizer API
     * @param output      Pre-allocated output buffer of length barCount
     * @param barCount    Number of output bars
     */
    private external fun processFftInPlace(
        fftBuffer: ByteArray,
        output: ByteArray,
        barCount: Int
    )

    // ---------- PUBLIC SAFE WRAPPERS ----------

    /**
     * Safe wrapper for [setGain].
     * No-op if native library is unavailable.
     *
     * @param gain Gain multiplier (1.0 = neutral, 0.0 = silence)
     */
    fun applyGain(gain: Float) {
        if (!isNativeAvailable) return
        setGain(gain.coerceIn(AudioConstants.MIN_GAIN, AudioConstants.MAX_GAIN))
    }

    /**
     * Safe wrapper for [processPcm].
     * Returns a silent byte array if the native library is unavailable
     * or if inputs are invalid, so the visualizer renders silence
     * instead of crashing.
     *
     * @param pcmBuffer Raw PCM samples
     * @param barCount  Number of output bars
     * @return Processed bar levels, or silence array on failure
     */
    fun safePcm(pcmBuffer: ShortArray, barCount: Int): ByteArray {
        val safeBarCount = barCount.coerceIn(
            AudioConstants.MIN_BAR_COUNT,
            AudioConstants.MAX_BAR_COUNT
        )

        if (!isNativeAvailable || pcmBuffer.isEmpty()) {
            return ByteArray(safeBarCount) { 127.toByte() }
        }

        return try {
            processPcm(pcmBuffer, safeBarCount)
        } catch (e: Exception) {
            Log.e(TAG, "processPcm failed: ${e.message}")
            ByteArray(safeBarCount) { 127.toByte() }
        }
    }

    /**
     * Safe wrapper for [processFftInPlace].
     * Fills output with silence values if the native library is
     * unavailable or if inputs are invalid.
     *
     * @param fftBuffer   FFT byte buffer from Android Visualizer
     * @param output      Pre-allocated output buffer (modified in-place)
     * @param barCount    Number of output bars
     */
    fun safeFftInPlace(fftBuffer: ByteArray, output: ByteArray, barCount: Int) {
        val safeBarCount = barCount.coerceIn(
            AudioConstants.MIN_BAR_COUNT,
            AudioConstants.MAX_BAR_COUNT
        )

        if (!isNativeAvailable || fftBuffer.isEmpty() || output.size < safeBarCount) {
            output.fill(127.toByte())
            return
        }

        try {
            processFftInPlace(fftBuffer, output, safeBarCount)
        } catch (e: Exception) {
            Log.e(TAG, "processFftInPlace failed: ${e.message}")
            output.fill(127.toByte())
        }
    }
}