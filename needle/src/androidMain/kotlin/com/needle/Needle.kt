@file:JvmName("Needle")
package com.needle

private object NeedleJNI {
    init { System.loadLibrary("needle_jni") }

    @JvmStatic external fun nativeInit(systemPrompt: String?, toolsJson: String?, toolIndexPath: String?): Int
    @JvmStatic external fun nativeComplete(input: String, maxNewTokens: Int, out: ByteArray): Int
    @JvmStatic external fun nativeReset()
    @JvmStatic external fun nativeLoad(cact: ByteArray): Int
}

/** Generous: a refusal is tiny but a multi-call response with reasoning is not. */
private const val RESPONSE_BUFFER_BYTES = 64 * 1024

actual fun needleInit(systemFacts: String?, toolsJson: String?, toolIndexPath: String?): Int =
    NeedleJNI.nativeInit(systemFacts, toolsJson, toolIndexPath)

actual fun needleComplete(input: String, maxNewTokens: Int): String? {
    val buffer = ByteArray(RESPONSE_BUFFER_BYTES)
    val rc = NeedleJNI.nativeComplete(input, maxNewTokens, buffer)
    if (rc < 0) return null
    // The shim always NUL-terminates, so decode to the first NUL rather than trusting
    // rc to be a byte count -- the C API's return convention is not documented.
    val end = buffer.indexOf(0).let { if (it < 0) buffer.size else it }
    return buffer.decodeToString(0, end)
}

actual fun needleReset() = NeedleJNI.nativeReset()

actual fun needleLoad(cact: ByteArray): Int = NeedleJNI.nativeLoad(cact)
