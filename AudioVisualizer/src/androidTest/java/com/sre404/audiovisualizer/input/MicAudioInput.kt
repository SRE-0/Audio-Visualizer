package com.sre404.audiovisualizer.input

import android.Manifest
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import com.sre404.audiovisualizer.AudioConstants
import com.sre404.audiovisualizer.engine.AudioProcessingMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Captures audio from the device microphone using [AudioRecord].
 *
 * Supports two processing modes:
 * - [AudioProcessingMode.VOLUME]: Raw PCM samples forwarded to the native RMS processor.
 * - [AudioProcessingMode.FFT]: A real-valued FFT (Cooley-Tukey radix-2) is computed
 *   in software and the magnitude spectrum is packed into a byte buffer compatible
 *   with the native FFT processor format (interleaved Re/Im pairs).
 *
 * Threading:
 *   Capture runs on a dedicated background thread created in [start].
 *   The thread exits cleanly when [stop] sets [running] to false.
 *   [AudioRecord] is always released on the capture thread before [stop] returns.
 *
 * Permissions:
 *   Requires [android.Manifest.permission.RECORD_AUDIO].
 *   The consuming app must request this permission before calling [start].
 *
 * @param sampleRate PCM sample rate in Hz. Defaults to [AudioConstants.DEFAULT_SAMPLE_RATE].
 */
internal class MicAudioInput(
    private val sampleRate: Int = AudioConstants.DEFAULT_SAMPLE_RATE
) : AudioInput {

    private val tag = "MicAudioInput"

    @Volatile
    private var running = false

    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    // -------------------------------------------------------------------------
    // START
    // -------------------------------------------------------------------------

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(
        processingMode: AudioProcessingMode,
        onPcmData: (ShortArray) -> Unit,
        onFftData: (ByteArray) -> Unit
    ) {
        if (running) {
            Log.w(tag, "start() called while already running — ignoring")
            return
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioConstants.CHANNEL_CONFIG,
            AudioConstants.AUDIO_FORMAT
        )

        // getMinBufferSize returns ERROR or ERROR_BAD_VALUE on failure
        if (minBuffer <= 0) {
            Log.e(tag, "AudioRecord.getMinBufferSize failed: $minBuffer")
            return
        }

        // Use at least 2x the minimum to reduce underrun risk
        val bufferSize = maxOf(minBuffer * 2, MIN_BUFFER_SIZE)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioConstants.CHANNEL_CONFIG,
            AudioConstants.AUDIO_FORMAT,
            bufferSize
        )

        // Validate that AudioRecord initialized correctly before starting
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(tag, "AudioRecord failed to initialize (state=${record.state})")
            record.release()
            return
        }

        audioRecord = record
        running = true

        worker = Thread({
            captureLoop(record, bufferSize, processingMode, onPcmData, onFftData)
        }, THREAD_NAME).apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    // -------------------------------------------------------------------------
    // STOP
    // -------------------------------------------------------------------------

    override fun stop() {
        running = false

        // Join the worker thread so AudioRecord is fully released before returning
        worker?.join(STOP_TIMEOUT_MS)
        worker = null
    }

    // -------------------------------------------------------------------------
    // CAPTURE LOOP
    // -------------------------------------------------------------------------

    /**
     * Main capture loop running on the worker thread.
     * Reads PCM samples and routes them according to [processingMode].
     * Releases [AudioRecord] before exiting regardless of how the loop ends.
     *
     * @param record         The initialized [AudioRecord] instance
     * @param bufferSize     Read buffer size in samples
     * @param processingMode Determines output path (PCM or FFT)
     * @param onPcmData      Callback for VOLUME mode
     * @param onFftData      Callback for FFT mode
     */
    private fun captureLoop(
        record: AudioRecord,
        bufferSize: Int,
        processingMode: AudioProcessingMode,
        onPcmData: (ShortArray) -> Unit,
        onFftData: (ByteArray) -> Unit
    ) {
        val buffer = ShortArray(bufferSize)

        try {
            record.startRecording()

            while (running) {
                val samplesRead = record.read(buffer, 0, buffer.size)

                // Negative values indicate a read error — log and skip
                if (samplesRead < 0) {
                    Log.w(tag, "AudioRecord.read returned error: $samplesRead")
                    continue
                }

                if (samplesRead == 0) continue

                when (processingMode) {
                    AudioProcessingMode.VOLUME -> {
                        onPcmData(buffer.copyOf(samplesRead))
                    }

                    AudioProcessingMode.FFT -> {
                        val fftSize = nearestPowerOfTwo(samplesRead)
                        val real    = FloatArray(fftSize)
                        val imag    = FloatArray(fftSize)

                        // Copy samples into float array, zero-pad if needed
                        for (i in 0 until fftSize) {
                            real[i] = if (i < samplesRead) {
                                buffer[i] / 32768f
                            } else {
                                0f
                            }
                        }

                        computeFft(real, imag)
                        onFftData(packFftToBytes(real, imag, fftSize))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Capture loop error: ${e.message}")
        } finally {
            record.stop()
            record.release()
            audioRecord = null
        }
    }

    // -------------------------------------------------------------------------
    // FFT
    // -------------------------------------------------------------------------

    /**
     * In-place Cooley-Tukey radix-2 DIT FFT.
     * Operates on [real] and [imag] arrays of equal length (must be a power of two).
     *
     * After this call:
     * - real[k] contains the real part of bin k
     * - imag[k] contains the imaginary part of bin k
     *
     * @param real Input signal (time domain). Overwritten with real part of spectrum.
     * @param imag Must be zero-initialized before call. Overwritten with imaginary part.
     */
    private fun computeFft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n <= 1) return

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit

            if (i < j) {
                var tmp = real[i]; real[i] = real[j]; real[j] = tmp
                tmp = imag[i]; imag[i] = imag[j]; imag[j] = tmp
            }
        }

        // Cooley-Tukey iterative FFT
        var length = 2
        while (length <= n) {
            val halfLen  = length / 2
            val angleStep = -2.0 * PI / length

            for (i in 0 until n step length) {
                for (k in 0 until halfLen) {
                    val angle = angleStep * k
                    val wReal = cos(angle).toFloat()
                    val wImag = sin(angle).toFloat()

                    val uReal = real[i + k]
                    val uImag = imag[i + k]
                    val vReal = real[i + k + halfLen] * wReal - imag[i + k + halfLen] * wImag
                    val vImag = real[i + k + halfLen] * wImag + imag[i + k + halfLen] * wReal

                    real[i + k]         = uReal + vReal
                    imag[i + k]         = uImag + vImag
                    real[i + k + halfLen] = uReal - vReal
                    imag[i + k + halfLen] = uImag - vImag
                }
            }
            length = length shl 1
        }
    }

    /**
     * Packs FFT output into an interleaved Re/Im byte buffer
     * compatible with the native [NativeAudioProcessor.processFftInPlace] format.
     *
     * Only the first [fftSize]/2 bins (positive frequencies) are packed.
     * Each bin contributes two bytes: Re byte and Im byte, both scaled to [-127, 127].
     *
     * @param real    Real part of FFT spectrum
     * @param imag    Imaginary part of FFT spectrum
     * @param fftSize Total number of FFT bins (must be power of two)
     * @return        ByteArray of length fftSize (interleaved Re/Im pairs)
     */
    private fun packFftToBytes(real: FloatArray, imag: FloatArray, fftSize: Int): ByteArray {
        val halfSize = fftSize / 2
        val output   = ByteArray(fftSize)

        for (i in 0 until halfSize) {
            val magnitude = sqrt(real[i] * real[i] + imag[i] * imag[i])
            val scaled    = (ln(1f + magnitude * 10f) * 20f).toInt().coerceIn(-127, 127)

            output[2 * i]     = scaled.toByte()          // Re byte
            output[2 * i + 1] = 0.toByte()               // Im byte (magnitude only)
        }

        return output
    }

    /**
     * Returns the largest power of two that is <= [n].
     * Used to fit the read buffer into a valid FFT size.
     *
     * @param n Input size
     * @return  Nearest power of two <= n, minimum 2
     */
    private fun nearestPowerOfTwo(n: Int): Int {
        var power = 1
        while (power * 2 <= n) power *= 2
        return maxOf(power, 2)
    }

    // -------------------------------------------------------------------------
    // CONSTANTS
    // -------------------------------------------------------------------------

    companion object {
        private const val THREAD_NAME      = "MicAudioInput-Worker"
        private const val STOP_TIMEOUT_MS  = 2000L
        private const val MIN_BUFFER_SIZE  = 4096
    }
}