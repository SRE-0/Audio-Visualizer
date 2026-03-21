package com.sre404.audiovisualizer.input

/**
 * Defines the audio capture source used by the visualizer.
 *
 * Both options require [android.Manifest.permission.RECORD_AUDIO].
 * [SYSTEM_AUDIO] additionally requires [android.Manifest.permission.MODIFY_AUDIO_SETTINGS].
 *
 * The consuming app is responsible for requesting permissions before calling
 * [com.sre404.audiovisualizer.view.BaseVisualizerView.start].
 */
enum class AudioInputType {

    /**
     * Captures audio from the device microphone.
     * Works without any audio playing on the device.
     */
    MICROPHONE,

    /**
     * Captures internal system audio via the Android Visualizer API.
     * Requires audio to be actively playing on the device.
     */
    SYSTEM_AUDIO
}