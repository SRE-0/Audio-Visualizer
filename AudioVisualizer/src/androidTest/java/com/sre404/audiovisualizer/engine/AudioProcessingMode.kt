package com.sre404.audiovisualizer.engine

/**
 * Defines how raw audio data is processed before reaching the visualizer bars.
 *
 * - [FFT]: Frequency-domain analysis. Each bar represents a frequency band.
 *          Best for music visualization.
 * - [VOLUME]: Time-domain RMS energy. All bars reflect overall loudness.
 *             Best for voice/ambient visualization.
 */
enum class AudioProcessingMode {
    FFT,
    VOLUME
}