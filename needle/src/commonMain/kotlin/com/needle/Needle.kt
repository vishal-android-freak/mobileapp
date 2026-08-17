package com.needle

/**
 * Needle 2: a 45MB-on-disk, 26-45M parameter tool-calling model that runs entirely
 * on device. Text in, tool calls out; there is no free-text fallback.
 *
 * The underlying C API is a **singleton** -- no handle is passed and [needleReset] is
 * global -- so all four functions must be serialized by the caller. Model weights are
 * embedded in the static library, so no model file needs downloading or bundling;
 * [needleLoad] exists only to substitute different weights.
 */

/** Loads the tool catalogue and optional session facts. Returns <0 on failure. */
expect fun needleInit(
    systemFacts: String?,
    toolsJson: String?,
    toolIndexPath: String?,
): Int

/**
 * Answers one query. Returns the raw JSON response, or null when the call failed.
 *
 * The response carries `type` ("call" or "refuse"), `function_calls`, a calibrated
 * `confidence`, and throughput counters.
 */
expect fun needleComplete(input: String, maxNewTokens: Int = 256): String?

/** Rewinds the conversation, keeping the tool catalogue loaded. */
expect fun needleReset()

/** Replaces the embedded weights. Not needed for normal use. */
expect fun needleLoad(cact: ByteArray): Int
