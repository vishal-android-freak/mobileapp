package coredevices.coreapp.ring.agent

import com.needle.needleComplete
import com.needle.needleInit
import com.needle.needleReset
import coredevices.ring.agent.NeedleRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the Needle 2 native path on a real device: the JNI shim, the Kotlin
 * bindings, and the weights embedded in `libneedle.a`.
 *
 * Runs against the tool catalogue shape the app actually builds, so it fails if the
 * JNI symbol names drift, if libc++ stops being linked, or if the embedded weights are
 * dropped by the linker — none of which show up at compile time.
 *
 * Model weights ship inside the native library, so there is nothing to download and
 * the test needs no network.
 */
class NeedleOnDeviceTest {

    /** Short names, as [coredevices.ring.agent.IndexAgentNeedle] sends them. */
    private val toolsJson = """
        [
         {"name":"lock_screen","description":"Lock the computer's screen immediately.",
          "parameters":{"type":"object","properties":{},"required":[]}},
         {"name":"set_volume","description":"Set the speaker volume to a percentage.",
          "parameters":{"type":"object","properties":{"percent":{"type":"integer","description":"Volume percentage, 0-150"}},"required":["percent"]}},
         {"name":"run_macro","description":"Run a named macro, a saved sequence of computer actions.",
          "parameters":{"type":"object","properties":{"name":{"type":"string","description":"Macro name, lowercase with underscores, e.g. wind_down"}},"required":["name"]}},
         {"name":"create_note","description":"Create a note with the user's text.",
          "parameters":{"type":"object","properties":{"text":{"type":"string","description":"The note content"}},"required":["text"]}}
        ]
    """.trimIndent()

    private fun init(): Int = needleInit("date: 2026-08-11 Tue 17:30; device: phone", toolsJson, null)

    @Test
    fun nativeLibraryLoadsAndInitialises() {
        // Also the first touch of NeedleJNI, so System.loadLibrary runs here: an
        // UnsatisfiedLinkError from a renamed symbol or missing libc++ surfaces now.
        assertTrue(init() >= 0, "needle_init should not fail")
    }

    @Test
    fun imperativeCommandProducesTheMatchingToolCall() {
        assertTrue(init() >= 0)
        needleReset()
        val raw = needleComplete("lock my screen")

        assertTrue(raw != null && raw.isNotBlank(), "expected a response, got $raw")
        assertTrue(raw.contains("\"lock_screen\""), "expected lock_screen in: $raw")
    }

    @Test
    fun argumentsAreExtractedFromTheUtterance() {
        assertTrue(init() >= 0)
        needleReset()
        val raw = needleComplete("turn the volume down to 15")

        assertTrue(raw != null, "expected a response")
        assertTrue(raw.contains("\"set_volume\""), "expected set_volume in: $raw")
        // The value has to come from the utterance, not be invented or omitted.
        assertTrue(raw.contains("15"), "expected percent 15 in: $raw")
    }

    @Test
    fun aRequestNoToolServesIsRefusedRatherThanForced() {
        assertTrue(init() >= 0)
        needleReset()
        val raw = needleComplete("what is the capital of France")

        assertTrue(raw != null, "expected a response")
        // Needle's contract for off-topic input is an empty call list, not a guess.
        assertTrue(
            raw.contains("\"function_calls\":[]") || raw.contains("\"type\":\"refuse\""),
            "expected an empty call list or a refusal, got: $raw",
        )
    }

    @Test
    fun theResponseCarriesAConfidenceScore() {
        assertTrue(init() >= 0)
        needleReset()
        val raw = needleComplete("lock my screen")

        // IndexAgentNeedle reads this to decide whether to escalate, so its presence
        // is part of the contract rather than a nicety.
        assertTrue(raw != null && raw.contains("\"confidence\""), "no confidence in: $raw")
    }

    @Test
    fun repeatedTurnsReuseTheLoadedCatalogue() {
        assertTrue(init() >= 0)
        repeat(3) {
            needleReset()
            val raw = needleComplete("lock my screen")
            assertTrue(raw != null && raw.contains("\"lock_screen\""), "turn $it gave: $raw")
        }
    }

    @Test
    fun aResponseIsValidJsonWithTheExpectedTopLevelKeys() {
        assertTrue(init() >= 0)
        needleReset()
        val raw = needleComplete("lock my screen")!!

        // Cheap structural check: the shim NUL-terminates a fixed buffer, so a
        // truncated or unterminated response would show up as unbalanced braces.
        assertEquals(
            raw.count { it == '{' },
            raw.count { it == '}' },
            "unbalanced braces, response likely truncated: $raw",
        )
        for (key in listOf("\"type\"", "\"function_calls\"", "\"confidence\"")) {
            assertTrue(raw.contains(key), "missing $key in: $raw")
        }
    }

    /**
     * Two conversations hitting the model at once must not interleave. The native API is
     * process-global, so without a shared lock one caller's `needle_init` lands between
     * the other's init and complete, and the second answer is drawn from the first's
     * catalogue — or the library aborts.
     */
    @Test
    fun concurrentCallersAreSerialised() = runBlocking {
        val runtime = NeedleRuntime()
        val facts = "date: 2026-08-11 Tue 17:30; device: phone"

        val results = listOf("lock my screen", "turn the volume down to 15")
            .map { prompt -> async { prompt to runtime.complete(facts, toolsJson, prompt) } }
            .awaitAll()

        for ((prompt, raw) in results) {
            assertTrue(raw.isNotBlank(), "empty response for '$prompt'")
            assertEquals(
                raw.count { it == '{' },
                raw.count { it == '}' },
                "unbalanced braces for '$prompt', likely a clobbered buffer: $raw",
            )
        }
        // Each answer has to match its own prompt, not the other coroutine's.
        assertTrue(results[0].second.contains("\"lock_screen\""), "got: ${results[0].second}")
        assertTrue(results[1].second.contains("\"set_volume\""), "got: ${results[1].second}")
    }
}
