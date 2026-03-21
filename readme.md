# 〈 Flux AudioVisualizer 〉

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=101010" />
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white&labelColor=101010" />
  <img src="https://img.shields.io/badge/Android_Studio-3DDC84?style=for-the-badge&logo=android-studio&logoColor=white&labelColor=101010" />
  <img src="https://img.shields.io/badge/NDK_C++-00599C?style=for-the-badge&logo=cplusplus&logoColor=white&labelColor=101010" />
  <img src="https://img.shields.io/badge/Min_SDK-24-7F52FF?style=for-the-badge&logo=android&logoColor=white&labelColor=101010" />
  <img src="https://img.shields.io/badge/License-MIT-F05032?style=for-the-badge&logo=opensourceinitiative&logoColor=white&labelColor=101010" />
</p>

<p align="center">
  <b>A real-time audio visualization library for Android.</b><br/>
  Powered by native C++ DSP, supporting both system audio and microphone input.<br/>
  Drop a view into your layout. Two lines of code. Done.
</p>

---

## ◈ What is Flux?

**Flux AudioVisualizer** is an Android library that turns raw audio into animated bar visualizations in real time. It captures audio from the system output or the device microphone, processes it through a native C++ DSP pipeline (FFT or RMS energy), and renders the result as smooth, animated bars directly on screen.

Everything runs on background threads. The view handles its own lifecycle. You focus on your app.

---

## ◈ Features

| Capability | Details |
|---|---|
| **Dual input source** | System audio (internal) or Microphone |
| **Two processing modes** | FFT (frequency bands) · VOLUME (RMS energy) |
| **Native DSP** | C++17 pipeline via JNI — logarithmic bin mapping, EMA smoothing, decay |
| **Real FFT** | Cooley-Tukey radix-2 DIT for microphone mode |
| **Color modes** | Solid color · Animated rainbow with hue wave |
| **XML configurable** | `barCount`, `solidColor`, `colorMode` from layout |
| **Safe lifecycle** | Auto-stops on `onDetachedFromWindow` · guard against double-start |
| **Crash resilient** | Native library load failures fall back to silence gracefully |
| **Min SDK 24** | Android 7.0+ |

---

## ◈ Architecture

```
┌─────────────────────────────────────────────────┐
│                 BarVisualizerView                │  ← Your layout
│            (extends BaseVisualizerView)          │
└────────────────────┬────────────────────────────┘
                     │ setup() · start() · stop()
┌────────────────────▼────────────────────────────┐
│                  AudioEngine                     │  ← Coordinator
│        routes buffers · manages lifecycle        │
└──────────┬─────────────────────┬────────────────┘
           │                     │
┌──────────▼──────────┐ ┌────────▼────────────────┐
│    MicAudioInput    │ │   SystemAudioInput       │
│  AudioRecord + FFT  │ │   Android Visualizer API │
└──────────┬──────────┘ └────────┬────────────────┘
           │                     │
┌──────────▼─────────────────────▼────────────────┐
│             NativeAudioProcessor (JNI)           │  ← C++17 DSP
│    processPcm()  ·  processFftInPlace()          │
└─────────────────────────────────────────────────┘
```

---

## ◈ Permissions

Both input sources require `RECORD_AUDIO`. This library **does not request permissions** — your app must handle this before calling `start()`.

```xml
<!-- AndroidManifest.xml of your app -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

> `MODIFY_AUDIO_SETTINGS` is only needed for `AudioInputType.SYSTEM_AUDIO`.

Runtime permission check example:

```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
    == PackageManager.PERMISSION_GRANTED) {
    visualizerView.start()
} else {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        REQUEST_CODE_AUDIO
    )
}
```

---

## ◈ Installation

### 1. Add the module to your project

Copy the `audiovisualizer` module into the root of your project, then declare it in `settings.gradle.kts`:

```kotlin
include(":audiovisualizer")
```

### 2. Add the dependency in your app's `build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":audiovisualizer"))
}
```

### 3. Sync and build

Android Studio will compile the native C++ layer automatically via CMake.

---

## ◈ Quick Start

### XML layout

```xml
<com.sre404.audiovisualizer.view.BarVisualizerView
    android:id="@+id/visualizerView"
    android:layout_width="match_parent"
    android:layout_height="180dp"
    app:barCount="48"
    app:colorMode="rainbow" />
```

### Kotlin — Activity or Fragment

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var visualizerView: BarVisualizerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        visualizerView = findViewById(R.id.visualizerView)

        // Configure input source and processing mode
        visualizerView.setup(
            inputType      = AudioInputType.SYSTEM_AUDIO,
            processingMode = AudioProcessingMode.FFT
        )
    }

    override fun onResume() {
        super.onResume()
        visualizerView.start()
    }

    override fun onPause() {
        super.onPause()
        visualizerView.stop()
    }
}
```

> The view also stops automatically when detached from the window — but calling `stop()` in `onPause` is recommended to release audio resources as early as possible.

---

## ◈ Configuration Reference

### `setup()` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `inputType` | `AudioInputType` | `SYSTEM_AUDIO` | Audio capture source |
| `processingMode` | `AudioProcessingMode` | `FFT` | DSP analysis mode |

### Runtime methods

| Method | Description |
|---|---|
| `start()` | Begins audio capture and rendering |
| `stop()` | Stops capture and clears bar data |
| `setBarCount(count: Int)` | Updates bar count. Restarts engine if running. |
| `setGain(gain: Float)` | DSP gain multiplier. `1.0` = neutral. Clamped to `[0.0, 10.0]`. |
| `setColorMode(mode: ColorMode)` | `ColorMode.SOLID` or `ColorMode.RAINBOW` |
| `setSolidColor(color: Int)` | ARGB color for `SOLID` mode |

### XML attributes (`BarVisualizerView`)

| Attribute | Format | Default | Description |
|---|---|---|---|
| `app:barCount` | integer | `32` | Number of bars |
| `app:solidColor` | color | `#00FFFF` | Bar color in SOLID mode |
| `app:colorMode` | enum | `rainbow` | `solid` or `rainbow` |

---

## ◈ Input Sources

### `AudioInputType.SYSTEM_AUDIO`
Captures all audio currently playing on the device via the Android `Visualizer` API attached to session `0` (global mix). Requires music or media to be actively playing to produce non-silent output.

### `AudioInputType.MICROPHONE`
Captures live audio from the device microphone via `AudioRecord`. Works without any audio playing. A real Cooley-Tukey FFT is computed in Kotlin before forwarding to the native processor.

---

## ◈ Processing Modes

### `AudioProcessingMode.FFT`
Each bar represents a frequency band mapped logarithmically across the spectrum. Low frequencies on the left, high frequencies on the right. Best for music visualization.

### `AudioProcessingMode.VOLUME`
Each bar reflects the RMS energy of a time-domain PCM segment. All bars react uniformly to overall loudness. Best for voice or ambient sound visualization.

---

## ◈ DSP Pipeline (Native C++)

```
Input buffer
    │
    ├─[PCM mode]──► Split into N segments
    │                 → RMS per segment
    │                 → Normalize to [0, SCALE]
    │                 → Apply gain
    │                 → Decay smoothing
    │                 → Clamp + invert → output[N]
    │
    └─[FFT mode]──► Log-frequency bin mapping
                      → Average magnitude per bin range
                      → log10 scaling
                      → Apply gain
                      → EMA smoothing (α = 0.40)
                      → Decay (factor = 0.85)
                      → Clamp + invert → output[N]
```

All output values are in `[0, 127]` where `127` = silence and `0` = maximum energy, matching the Android `Visualizer` byte convention.

---

## ◈ Project Structure

```
audiovisualizer/
├── src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt
│   │   └── audio_processor.cpp       # C++17 DSP (JNI)
│   ├── java/com/sre404/audiovisualizer/
│   │   ├── AudioConstants.kt         # All tunable parameters
│   │   ├── engine/
│   │   │   ├── AudioEngine.kt        # Central coordinator
│   │   │   ├── AudioProcessingMode.kt
│   │   │   └── NativeAudioProcessor.kt  # JNI wrapper
│   │   ├── input/
│   │   │   ├── AudioInput.kt         # Input contract
│   │   │   ├── AudioInputType.kt     # MICROPHONE / SYSTEM_AUDIO
│   │   │   ├── MicAudioInput.kt      # AudioRecord + real FFT
│   │   │   └── SystemAudioInput.kt   # Android Visualizer API
│   │   └── view/
│   │       ├── ColorMode.kt          # SOLID / RAINBOW
│   │       ├── BaseVisualizerView.kt # Engine owner + public API
│   │       └── BarVisualizerView.kt  # Bar renderer
│   ├── res/values/
│   │   └── attrs.xml                 # XML declare-styleable
│   └── AndroidManifest.xml
├── build.gradle.kts
└── consumer-rules.pro
```

---

## ◈ Requirements

| Item | Requirement |
|---|---|
| Android SDK | Min API 24 (Android 7.0) |
| Compile SDK | 35 |
| NDK | Required (CMake 3.22.1+) |
| Kotlin | 1.9+ |
| Permissions | `RECORD_AUDIO` · `MODIFY_AUDIO_SETTINGS` |

---

## ◈ License

```
MIT License

Copyright (c) 2025 sre404

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

<p align="center">
  Made with precision by <b>sre404</b> · Built for Android · Powered by C++
</p>