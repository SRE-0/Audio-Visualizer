#include <jni.h>
#include <vector>
#include <cmath>
#include <algorithm>
#include <android/log.h>

/*
 * =========================================================
 * LOGGING
 * =========================================================
 */

#define LOG_TAG "AudioProcessor"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/*
 * =========================================================
 * GLOBAL DSP STATE
 * =========================================================
 *
 * g_gain  : user-defined gain multiplier (runtime-configurable)
 * lastPcm : decay state for volume mode bars
 * lastFft : decay state for FFT mode bars
 *
 * These vectors resize lazily when barCount changes.
 */

static float g_gain = 1.0f;

static std::vector<float> lastPcmLevels;
static std::vector<float> lastFftLevels;

/*
 * =========================================================
 * PCM MODE CONSTANTS
 * =========================================================
 */

static constexpr float MAX_PCM_VALUE    = 32768.0f;
static constexpr float VISUALIZER_SCALE = 180.0f;
static constexpr float DECAY_FACTOR_PCM = 0.48f;

/*
 * =========================================================
 * FFT MODE CONSTANTS
 * =========================================================
 */

static constexpr float DECAY_FACTOR_FFT    = 0.85f;
static constexpr float SMOOTHING_ALPHA_FFT = 0.40f;
static constexpr float MAX_FFT_LEVEL       = 127.0f;

/*
 * =========================================================
 * JNI: SET GAIN
 * =========================================================
 *
 * Sets the global DSP gain multiplier applied in both processing modes.
 *
 * @param gain  Gain multiplier. Values below 0 are clamped to 0.
 *              Values above 10 are clamped to 10 to prevent runaway amplification.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_sre404_audiovisualizer_engine_NativeAudioProcessor_setGain(
        JNIEnv*,
        jobject,
        jfloat gain
) {
g_gain = std::clamp(gain, 0.0f, 10.0f);
}

/*
 * =========================================================
 * JNI: PROCESS PCM (VOLUME MODE)
 * =========================================================
 *
 * Converts raw 16-bit PCM samples into per-bar energy levels.
 *
 * Processing pipeline:
 *   1. Split buffer into barCount segments
 *   2. Compute RMS energy per segment
 *   3. Normalize and scale to visualizer range
 *   4. Apply gain
 *   5. Decay smoothing (CAVA-style)
 *   6. Clamp and invert for Android Visualizer compatibility
 *
 * @param pcmBuffer  ShortArray of raw 16-bit signed PCM samples
 * @param barCount   Number of output bars (must be >= 1)
 * @return           ByteArray of length barCount, values in [0, 127]
 *                   where 127 = silence, 0 = maximum energy
 */
extern "C"
JNIEXPORT jbyteArray JNICALL
        Java_com_sre404_audiovisualizer_engine_NativeAudioProcessor_processPcm(
        JNIEnv* env,
        jobject,
        jshortArray pcmBuffer,
jint barCount
) {
// Validate barCount before any allocation
if (barCount <= 0) {
LOGE("processPcm: invalid barCount=%d", barCount);
return env->NewByteArray(0);
}

const jsize pcmLength = env->GetArrayLength(pcmBuffer);

if (pcmLength <= 0) {
LOGE("processPcm: empty PCM buffer");
return env->NewByteArray(barCount);
}

jshort* pcm = env->GetShortArrayElements(pcmBuffer, nullptr);
if (pcm == nullptr) {
LOGE("processPcm: failed to pin PCM buffer");
return env->NewByteArray(barCount);
}

// Resize decay state if bar count changed
if (static_cast<jint>(lastPcmLevels.size()) != barCount) {
lastPcmLevels.assign(barCount, 0.0f);
}

const int samplesPerBar = std::max(1, static_cast<int>(pcmLength) / barCount);
std::vector<jbyte> output(barCount, static_cast<jbyte>(127));

for (int bar = 0; bar < barCount; ++bar) {
float energySum = 0.0f;
const int start = bar * samplesPerBar;
int actualSamples = 0;

for (int i = 0; i < samplesPerBar && (start + i) < pcmLength; ++i) {
float sample = static_cast<float>(pcm[start + i]);
energySum += sample * sample;
actualSamples++;
}

if (actualSamples == 0) {
output[bar] = static_cast<jbyte>(127);
continue;
}

// RMS energy normalized to [0, VISUALIZER_SCALE]
float rms   = std::sqrt(energySum / actualSamples);
float level = (rms / MAX_PCM_VALUE) * VISUALIZER_SCALE * g_gain;

// Decay smoothing: level cannot drop faster than decay allows
level = std::max(level, lastPcmLevels[bar] * DECAY_FACTOR_PCM);
lastPcmLevels[bar] = level;

// Clamp and invert: 127 = silence, 0 = loudest
int inverted = 127 - static_cast<int>(std::clamp(level, 0.0f, 127.0f));
output[bar]  = static_cast<jbyte>(inverted);
}

env->ReleaseShortArrayElements(pcmBuffer, pcm, JNI_ABORT);

jbyteArray result = env->NewByteArray(barCount);
env->SetByteArrayRegion(result, 0, barCount, output.data());
return result;
}

/*
 * =========================================================
 * JNI: PROCESS FFT (FREQUENCY MODE)
 * =========================================================
 *
 * Converts an Android Visualizer FFT buffer into per-bar magnitude levels.
 *
 * Processing pipeline:
 *   1. Map bars to logarithmic frequency bins
 *   2. Average complex magnitude within each bin range
 *   3. Apply log10 scaling
 *   4. Apply gain
 *   5. EMA smoothing
 *   6. Decay
 *   7. Clamp and invert for rendering
 *
 * @param fftBuffer   ByteArray from Android Visualizer (interleaved Re/Im pairs)
 * @param outputArray Pre-allocated ByteArray of length barCount (written in-place)
 * @param barCount    Number of output bars (must be >= 1)
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_sre404_audiovisualizer_engine_NativeAudioProcessor_processFftInPlace(
        JNIEnv* env,
jobject,
jbyteArray fftBuffer,
        jbyteArray outputArray,
jint barCount
) {
// Validate barCount
if (barCount <= 0) {
LOGE("processFftInPlace: invalid barCount=%d", barCount);
return;
}

const jsize fftLength  = env->GetArrayLength(fftBuffer);
const jsize outputSize = env->GetArrayLength(outputArray);

// Output buffer must match barCount
if (outputSize < barCount) {
LOGE("processFftInPlace: outputArray size=%d < barCount=%d", (int)outputSize, barCount);
return;
}

if (fftLength < 4) {
LOGE("processFftInPlace: FFT buffer too small (%d bytes)", (int)fftLength);
return;
}

jbyte* fft    = env->GetByteArrayElements(fftBuffer, nullptr);
jbyte* output = env->GetByteArrayElements(outputArray, nullptr);

if (fft == nullptr || output == nullptr) {
LOGE("processFftInPlace: failed to pin buffers");
if (fft)    env->ReleaseByteArrayElements(fftBuffer,   fft,    JNI_ABORT);
if (output) env->ReleaseByteArrayElements(outputArray, output, JNI_ABORT);
return;
}

const int fftBins = static_cast<int>(fftLength) / 2;

// Resize decay state if bar count changed
if (static_cast<jint>(lastFftLevels.size()) != barCount) {
lastFftLevels.assign(barCount, 0.0f);
}

// Skip DC bin (index 0) and use a safe max bin
const int minBin = 2;
const int maxBin = fftBins - 1;

for (int bar = 0; bar < barCount; ++bar) {

// Logarithmic bin mapping: spreads bars across frequency spectrum
const float startRatio = static_cast<float>(bar)     / barCount;
const float endRatio   = static_cast<float>(bar + 1) / barCount;

int startBin = minBin + static_cast<int>(powf(startRatio, 2.0f) * (maxBin - minBin));
int endBin   = minBin + static_cast<int>(powf(endRatio,   2.0f) * (maxBin - minBin));

startBin = std::clamp(startBin, minBin, maxBin);
endBin   = std::clamp(endBin, startBin + 1, maxBin + 1);

// Guard against out-of-range bin access
if (startBin >= fftBins || endBin > fftBins) {
output[bar] = static_cast<jbyte>(127);
continue;
}

float energy = 0.0f;
int   count  = 0;

for (int bin = startBin; bin < endBin; ++bin) {
const int re_idx = 2 * bin;
const int im_idx = 2 * bin + 1;

// Extra safety: ensure both indices are within buffer bounds
if (re_idx >= static_cast<int>(fftLength) ||
im_idx >= static_cast<int>(fftLength)) {
break;
}

float re = static_cast<float>(fft[re_idx]);
float im = static_cast<float>(fft[im_idx]);
energy += std::sqrt(re * re + im * im);
count++;
}

if (count > 0) energy /= count;

// Log10 scaling compresses dynamic range for perceptual uniformity
float level = std::log10f(1.0f + energy) * 40.0f * g_gain;

// EMA smoothing: blends current reading with previous for visual stability
const float previous = lastFftLevels[bar];
float smoothed = previous * (1.0f - SMOOTHING_ALPHA_FFT)
                 + level    * SMOOTHING_ALPHA_FFT;

// Decay: level can only drop at controlled rate
smoothed = std::max(smoothed, previous * DECAY_FACTOR_FFT);
lastFftLevels[bar] = smoothed;

// Final clamp and invert
smoothed    = std::clamp(smoothed, 0.0f, MAX_FFT_LEVEL);
output[bar] = static_cast<jbyte>(127 - static_cast<int>(smoothed));
}

env->ReleaseByteArrayElements(fftBuffer,   fft,    JNI_ABORT);
env->ReleaseByteArrayElements(outputArray, output, 0);
}