package coredevices.ring.agent

import com.needle.needleComplete
import com.needle.needleInit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises access to Needle's native API.
 *
 * The API is process-global — no handle to scope a call to, and `needle_reset` resets
 * whatever is loaded — while agents are built per conversation, so the lock has to
 * outlive any one agent or two conversations can interleave inside the same model.
 */
class NeedleRuntime {
    private val nativeLock = Mutex()

    /**
     * Loads [facts] and [toolsJson], then answers [input].
     *
     * Re-inits every turn rather than caching the catalogue. Caching looks tempting
     * because init re-embeds every tool schema, but [facts] carries the current time,
     * and serving a stale one resolves "remind me tomorrow at 7" to the wrong day.
     */
    suspend fun complete(facts: String, toolsJson: String, input: String): String =
        nativeLock.withLock {
            val rc = needleInit(facts, toolsJson, null)
            if (rc < 0) throw IllegalStateException("needle_init failed with $rc")
            needleComplete(input) ?: throw IllegalStateException("needle_complete failed")
        }
}
