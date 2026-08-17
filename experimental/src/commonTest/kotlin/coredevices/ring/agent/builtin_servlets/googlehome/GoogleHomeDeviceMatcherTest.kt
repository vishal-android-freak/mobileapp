package coredevices.ring.agent.builtin_servlets.googlehome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GoogleHomeDeviceMatcherTest {
    private val devices = listOf(
        GoogleHomeDeviceCandidate("1", "Desk Lamp", "Office"),
        GoogleHomeDeviceCandidate("2", "Desk Lamp", "Bedroom"),
        GoogleHomeDeviceCandidate("3", "Kitchen Ceiling", "Kitchen"),
    )

    @Test
    fun roomDisambiguatesIdenticalDeviceNames() {
        val match = GoogleHomeDeviceMatcher.match(devices, "desk lamp", "office")

        assertEquals("1", assertIs<GoogleHomeDeviceMatch.Found>(match).device.id)
    }

    @Test
    fun punctuationAndCaseDoNotAffectMatching() {
        val match = GoogleHomeDeviceMatcher.match(devices, "KITCHEN-ceiling", null)

        assertEquals("3", assertIs<GoogleHomeDeviceMatch.Found>(match).device.id)
    }

    @Test
    fun ambiguousNameDoesNotSelectArbitrarily() {
        val match = GoogleHomeDeviceMatcher.match(devices, "desk lamp", null)

        assertEquals(2, assertIs<GoogleHomeDeviceMatch.Ambiguous>(match).devices.size)
    }

    @Test
    fun partialNameCanSelectUniqueDevice() {
        val match = GoogleHomeDeviceMatcher.match(devices, "kitchen", null)

        assertEquals("3", assertIs<GoogleHomeDeviceMatch.Found>(match).device.id)
    }

    @Test
    fun unknownDeviceDoesNotMatch() {
        assertIs<GoogleHomeDeviceMatch.NotFound>(
            GoogleHomeDeviceMatcher.match(devices, "porch light", null)
        )
    }
}
