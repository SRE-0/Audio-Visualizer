package com.sre404.audiovisualizer.input

import com.sre404.audiovisualizer.engine.AudioProcessingMode

/**
 * Contract for all audio capture sources used by the visualizer.
 *
 * Implementations must handle their own threading internally.
 * Callbacks [onPcmData] and [onFftData] are invoked from a background
 * thread — callers must not perform UI operations inside them directly.
 *
 * Lifecycle:
 *   1. Call [start] to begin capture
 *   2. Receive data via callbacks
 *   3. Call [stop] to release all resources
 *
 * [stop] must be idempotent — calling it multiple times must not crash.
 */
internal interface AudioInput {

    /**
     * Starts audio capture.
     *
     * @param processingMode Determines which callback receives data.
     *                       [AudioProcessingMode.VOLUME] triggers [onPcmData].
     *                       [AudioProcessingMode.FFT] triggers [onFftData].
     * @param onPcmData      Invoked with raw 16-bit PCM samples when in VOLUME mode.
     * @param onFftData      Invoked with FFT byte buffer when in FFT mode.
     */
    fun start(
        processingMode: AudioProcessingMode,
        onPcmData: (ShortArray) -> Unit,
        onFftData: (ByteArray) -> Unit
    )

    /**
     * Stops audio capture and releases all associated resources.
     * Must be safe to call even if [start] was never called.
     */
    fun stop()
}