package com.sre404.audiovisualizer

import android.media.AudioFormat
import android.graphics.Color
import com.sre404.audiovisualizer.engine.AudioProcessingMode
import com.sre404.audiovisualizer.view.ColorMode

/**
 * Centralized configuration values for the audio visualizer library.
 *
 * All tunable parameters live here to avoid magic numbers scattered
 * across the codebase. Library consumers can reference these defaults
 * when configuring [com.sre404.flux.audiovisualizer.view.BaseVisualizerView].
 */
internal object AudioConstants {

    // ---------- VISUALIZER ----------

    /** Default number of frequency/volume bars rendered */
    const val DEFAULT_BAR_COUNT = 32

    /** Pixel gap between adjacent bars */
    const val DEFAULT_BAR_GAP = 4f

    /** Default color rendering mode */
    val DEFAULT_COLOR_MODE = ColorMode.RAINBOW

    // ---------- AUDIO CAPTURE ----------

    /** PCM sample rate used for microphone capture */
    const val DEFAULT_SAMPLE_RATE = 44100

    /** Mono channel config for AudioRecord */
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

    /** 16-bit PCM encoding for AudioRecord */
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /** Default DSP gain (neutral) */
    const val DEFAULT_GAIN = 1.0f

    /** Default processing mode applied when none is specified */
    val DEFAULT_PROCESSING_MODE = AudioProcessingMode.FFT

    // ---------- SYSTEM AUDIO ----------

    /**
     * Session ID 0 attaches the Visualizer to the global audio mix,
     * capturing all system audio output.
     */
    const val VISUALIZER_SESSION_ID = 0

    // ---------- COLORS ----------

    /** Default solid bar color */
    const val DEFAULT_SOLID_COLOR = Color.CYAN

    // ---------- RAINBOW MODE ----------

    /** Degrees per frame that the hue shifts in rainbow mode */
    const val RAINBOW_HUE_SPEED = 0.6f

    /** HSV saturation for rainbow bars */
    const val RAINBOW_SATURATION = 0.6f

    /** HSV brightness for rainbow bars */
    const val RAINBOW_BRIGHTNESS = 0.8f

    // ---------- VALIDATION LIMITS ----------

    /** Minimum allowed bar count to prevent division by zero in native code */
    const val MIN_BAR_COUNT = 1

    /** Maximum allowed bar count to cap memory allocation */
    const val MAX_BAR_COUNT = 256

    /** Minimum allowed gain value */
    const val MIN_GAIN = 0.0f

    /** Maximum allowed gain value to prevent audio clipping artifacts */
    const val MAX_GAIN = 10.0f
}