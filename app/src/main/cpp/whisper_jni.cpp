/*
 * Call_Record: FOSS call recording for Android, based on ShizuCallRecorder.
 *  Copyright (C) 2026-present kitsumed (Med)   (original ShizuCallRecorder)
 *  Copyright (C) 2026 Call_Record contributors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

// JNI bridge between the Kotlin transcription engine and whisper.cpp.
//
// The bridge keeps one loaded model per path, so repeated transcriptions of the same
// recording (or the next call) do not have to reload the model from disk.

#include <jni.h>
#include <android/log.h>

#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "CallRecordWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct ModelHolder {
    whisper_context * ctx = nullptr;
};

std::mutex g_mutex;
std::map<std::string, ModelHolder *> g_models;

std::string toStdString(JNIEnv * env, jstring value) {
    if (value == nullptr) return std::string();
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string escapeJson(const std::string & input) {
    std::string out;
    out.reserve(input.size() + 16);
    for (unsigned char c : input) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (c < 0x20) {
                    char buffer[8];
                    snprintf(buffer, sizeof(buffer), "\\u%04x", c);
                    out += buffer;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

} // namespace

extern "C" {

/**
 * Loads a whisper.cpp model (or returns the already loaded one) and transcribes the samples.
 *
 * @param modelPath   Absolute path of the ggml model file.
 * @param samples     16 kHz mono float samples in the range [-1, 1].
 * @param language    Language code, e.g. "ko".
 * @param numThreads  Number of threads to use.
 * @return A JSON document: { "text": "...", "segments": [ { "start": ms, "end": ms, "text": "..." } ] }
 *         or null when the model could not be loaded.
 */
JNIEXPORT jstring JNICALL
Java_com_declan_callrecord_transcription_WhisperBridge_nativeTranscribe(
        JNIEnv * env,
        jobject /* this */,
        jstring modelPath,
        jfloatArray samples,
        jstring language,
        jint numThreads) {

    const std::string path = toStdString(env, modelPath);
    if (path.empty() || samples == nullptr) {
        return nullptr;
    }

    const jsize sampleCount = env->GetArrayLength(samples);
    jfloat * sampleData = env->GetFloatArrayElements(samples, nullptr);
    if (sampleData == nullptr) {
        return nullptr;
    }
    std::vector<float> audio(sampleData, sampleData + sampleCount);
    env->ReleaseFloatArrayElements(samples, sampleData, JNI_ABORT);

    std::lock_guard<std::mutex> lock(g_mutex);

    ModelHolder * holder = nullptr;
    auto it = g_models.find(path);
    if (it != g_models.end()) {
        holder = it->second;
    } else {
        whisper_context_params params = whisper_context_default_params();
        params.use_gpu = false;
        whisper_context * ctx = whisper_init_from_file_with_params(path.c_str(), params);
        if (ctx == nullptr) {
            LOGE("whisper_init_from_file_with_params failed for %s", path.c_str());
            return nullptr;
        }
        holder = new ModelHolder();
        holder->ctx = ctx;
        g_models[path] = holder;
    }

    // The language string must stay alive for the whole call, so it is kept in a local variable.
    std::string languageCode = toStdString(env, language);
    if (languageCode.empty()) languageCode = "ko";

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.language = languageCode.c_str();
    wparams.translate = false;
    wparams.no_context = true;
    wparams.print_realtime = false;
    wparams.print_progress = false;
    wparams.print_timestamps = false;
    wparams.print_special = false;
    wparams.token_timestamps = false;
    wparams.single_segment = false;
    wparams.n_threads = numThreads > 0 ? numThreads : 4;

    if (whisper_full(holder->ctx, wparams, audio.data(), (int) audio.size()) != 0) {
        LOGE("whisper_full failed");
        return nullptr;
    }

    const int segmentCount = whisper_full_n_segments(holder->ctx);
    std::string fullText;
    std::string segments = "[";
    for (int i = 0; i < segmentCount; i++) {
        const char * text = whisper_full_get_segment_text(holder->ctx, i);
        if (text == nullptr) continue;
        const int64_t start = whisper_full_get_segment_t0(holder->ctx, i) * 10; // 10 ms units
        const int64_t end = whisper_full_get_segment_t1(holder->ctx, i) * 10;
        if (i > 0) segments += ",";
        segments += "{\"start\":" + std::to_string(start) +
                    ",\"end\":" + std::to_string(end) +
                    ",\"text\":\"" + escapeJson(std::string(text)) + "\"}";
        fullText += text;
    }
    segments += "]";

    std::string json = "{\"text\":\"" + escapeJson(fullText) + "\",\"segments\":" + segments + "}";

    jstring result = env->NewStringUTF(json.c_str());
    return result;
}

/**
 * Releases every loaded model. Used when the user deletes the model file.
 */
JNIEXPORT void JNICALL
Java_com_declan_callrecord_transcription_WhisperBridge_nativeReleaseModels(
        JNIEnv * /* env */,
        jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    for (auto & entry : g_models) {
        if (entry.second != nullptr && entry.second->ctx != nullptr) {
            whisper_free(entry.second->ctx);
        }
        delete entry.second;
    }
    g_models.clear();
}

} // extern "C"
