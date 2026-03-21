package com.sre404.audiovisualizer.input

import android.media.audiofx.Visualizer
import android.util.Log
import com.sre404.audiovisualizer.AudioConstants
import com.sre404.audiovisualizer.engine.AudioProcessingMode

/**
 * Captures internal system audio using the Android [Visualizer] API.
 *
 * Attaches to audio session ID 0 (global mix), which captures all system
 * audio output. Requires both RECORD_AUDIO and MODIFY_AUDIO_SETTINGS permissions.
 *
 * Important notes:
 * - [Visualizer] can only have one active instance per session ID system-wide.
 *   If another app or component holds it, [start] will fail gracefully.
 * - Waveform data from [Visualizer] is NOT raw PCM. Values are unsigned bytes
 *   (0..255) with silence at 128. This class converts them to signed short PCM
 *   before forwarding to the native processor.
 * - [Visualizer] requires audio to be actively playing to produce non-silent data.
 *
 * Threading:
 *   [Visualizer] delivers callbacks on its own internal thread.
 *   [stop] disables and releases the instance safely from any thread.
 */
internal class SystemAudioInput : AudioInput {

    private val tag = "SystemAudioInput"

    @Volatile
    private var visualizer: Visualizer? = null

    // -------------------------------------------------------------------------
    // START
    // -------------------------------------------------------------------------

    override fun start(
        processingMode: AudioProcessingMode,
        onPcmData: (ShortArray) -> Unit,
        onFftData: (ByteArray) -> Unit
    ) {
        if (visualizer != null) {
            Log.w(tag, "start() called while already running — ignoring")
            return
        }

        // Visualizer constructor throws RuntimeException if:
        // - RECORD_AUDIO or MODIFY_AUDIO_SETTINGS permission is missing
        // - Another Visualizer already holds this session
        // - The audio framework is unavailable
        val captureSize = try {
            Visualizer.getCaptureSizeRange()[1]
        } catch (e: Exception) {
            Log.e(tag, "Failed to get capture size range: ${e.message}")
            return
        }

        val instance = try {
            Visualizer(AudioConstants.VISUALIZER_SESSION_ID)
        } catch (e: RuntimeException) {
            Log.e(tag, "Failed to create Visualizer: ${e.message}")
            return
        }

        val initialized = tryConfigureVisualizer(
            instance       = instance,
            captureSize    = captureSize,
            processingMode = processingMode,
            onPcmData      = onPcmData,
            onFftData      = onFftData
        )

        if (!initialized) {
            instance.release()
        }
    }

    // -------------------------------------------------------------------------
    // STOP
    // -------------------------------------------------------------------------

    override fun stop() {
        visualizer?.let { v ->
            try {
                v.enabled = false
                v.release()
            } catch (e: Exception) {
                Log.e(tag, "Error releasing Visualizer: ${e.message}")
            } finally {
                visualizer = null
            }
        }
    }

    // -------------------------------------------------------------------------
    // CONFIGURATION
    // -------------------------------------------------------------------------

    /**
     * Configures and enables a [Visualizer] instance.
     * Separated from [start] so that any configuration failure can trigger
     * a clean release without leaving a partially initialized instance active.
     *
     * @param instance       The freshly created [Visualizer] to configure
     * @param captureSize    The capture buffer size to set
     * @param processingMode Determines which callback receives data
     * @param onPcmData      Callback for VOLUME mode waveform data
     * @param onFftData      Callback for FFT mode frequency data
     * @return               True if configuration succeeded and Visualizer is active
     */
    private fun tryConfigureVisualizer(
        instance: Visualizer,
        captureSize: Int,
        processingMode: AudioProcessingMode,
        onPcmData: (ShortArray) -> Unit,
        onFftData: (ByteArray) -> Unit
    ): Boolean {
        return try {
            instance.enabled     = false
            instance.captureSize = captureSize

            val captureRate = Visualizer.getMaxCaptureRate()
            val waveformOn  = processingMode == AudioProcessingMode.VOLUME
            val fftOn       = processingMode == AudioProcessingMode.FFT

            instance.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {

                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (!waveformOn || waveform == null) return

                        // Convert unsigned waveform bytes (0..255, silence=128)
                        // to signed 16-bit PCM centered at 0
                        val pcm = ShortArray(waveform.size) { i ->
                            val centered = waveform[i].toInt() and 0xFF - 128
                            (centered * 256).toShort()
                        }

                        onPcmData(pcm)
                    }

                    override fun onFftDataCapture(
                        v: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (!fftOn || fft == null) return
                        onFftData(fft)
                    }
                },
                captureRate,
                waveformOn,
                fftOn
            )

            instance.enabled = true
            visualizer = instance
            true

        } catch (e: Exception) {
            Log.e(tag, "Visualizer configuration failed: ${e.message}")
            false
        }
    }
}