// JNI shim over Needle 2's four-function C API.
//
// Two things to know about the shape of that API:
//   * It is a singleton -- no handle is passed, and needle_reset() is global. Callers
//     must serialize; :needle documents that and IndexAgentNeedle holds a mutex.
//   * needle_complete writes into a caller-supplied buffer. We take the buffer from
//     Kotlin so the allocation stays on the JVM heap and is visible to GC.
#include <jni.h>
#include <string>
#include <vector>

#include "needle.h"

namespace {

// Copies a Java string into a std::string, tolerating null.
bool copy_string(JNIEnv* env, jstring value, std::string* out) {
    if (value == nullptr) return false;
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return false;
    out->assign(chars);
    env->ReleaseStringUTFChars(value, chars);
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL
Java_com_needle_NeedleJNI_nativeInit(JNIEnv* env, jclass, jstring systemPrompt,
                                     jstring toolsJson, jstring toolIndexPath) {
    std::string system, tools, index;
    const bool has_system = copy_string(env, systemPrompt, &system);
    const bool has_tools = copy_string(env, toolsJson, &tools);
    const bool has_index = copy_string(env, toolIndexPath, &index);
    return needle_init(has_system ? system.c_str() : nullptr,
                       has_tools ? tools.c_str() : nullptr,
                       has_index ? index.c_str() : nullptr);
}

JNIEXPORT jint JNICALL
Java_com_needle_NeedleJNI_nativeComplete(JNIEnv* env, jclass, jstring input,
                                         jint maxNewTokens, jbyteArray out) {
    std::string text;
    if (!copy_string(env, input, &text)) return -1;

    const jsize capacity = env->GetArrayLength(out);
    if (capacity <= 0) return -1;

    std::vector<char> buffer(static_cast<size_t>(capacity), 0);
    const int rc = needle_complete(text.c_str(), maxNewTokens, buffer.data(),
                                   static_cast<int>(capacity));
    // Guarantee termination whatever the return convention turns out to be, so the
    // Kotlin side can always decode up to the first NUL.
    buffer[static_cast<size_t>(capacity) - 1] = '\0';
    env->SetByteArrayRegion(out, 0, capacity, reinterpret_cast<const jbyte*>(buffer.data()));
    return rc;
}

JNIEXPORT void JNICALL
Java_com_needle_NeedleJNI_nativeReset(JNIEnv*, jclass) {
    needle_reset();
}

JNIEXPORT jint JNICALL
Java_com_needle_NeedleJNI_nativeLoad(JNIEnv* env, jclass, jbyteArray cact) {
    const jsize length = env->GetArrayLength(cact);
    if (length <= 0) return -1;
    jbyte* bytes = env->GetByteArrayElements(cact, nullptr);
    if (bytes == nullptr) return -1;
    const int rc = needle_load(reinterpret_cast<const unsigned char*>(bytes),
                               static_cast<unsigned long long>(length));
    env->ReleaseByteArrayElements(cact, bytes, JNI_ABORT);
    return rc;
}

}  // extern "C"
