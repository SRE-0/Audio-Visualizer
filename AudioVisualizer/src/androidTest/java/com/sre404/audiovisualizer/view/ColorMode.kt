package com.sre404.audiovisualizer.view

/**
 * Controls how bar colors are rendered in visualizer views.
 *
 * - [SOLID]: All bars share a single configurable color.
 * - [RAINBOW]: Each bar receives a hue offset that animates over time.
 */
enum class ColorMode {
    SOLID,
    RAINBOW
}