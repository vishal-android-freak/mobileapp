package com.needle

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import needle.needle_complete
import needle.needle_init
import needle.needle_load
import needle.needle_reset

private const val RESPONSE_BUFFER_BYTES = 64 * 1024

actual fun needleInit(systemFacts: String?, toolsJson: String?, toolIndexPath: String?): Int =
    needle_init(systemFacts, toolsJson, toolIndexPath)

actual fun needleComplete(input: String, maxNewTokens: Int): String? = memScoped {
    val out = allocArray<ByteVar>(RESPONSE_BUFFER_BYTES)
    val rc = needle_complete(input, maxNewTokens, out, RESPONSE_BUFFER_BYTES)
    if (rc < 0) null else out.toKString()
}

actual fun needleReset() = needle_reset()

actual fun needleLoad(cact: ByteArray): Int = cact.usePinned { pinned ->
    needle_load(
        pinned.get().addressOf(0).reinterpret(),
        cact.size.toULong(),
    )
}
