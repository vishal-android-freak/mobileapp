package coredevices.ring.agent.builtin_servlets.googlehome

data class GoogleHomeDeviceCandidate(
    val id: String,
    val name: String,
    val roomName: String?,
)

sealed interface GoogleHomeDeviceMatch {
    data class Found(val device: GoogleHomeDeviceCandidate) : GoogleHomeDeviceMatch
    data class Ambiguous(val devices: List<GoogleHomeDeviceCandidate>) : GoogleHomeDeviceMatch
    data object NotFound : GoogleHomeDeviceMatch
}

object GoogleHomeDeviceMatcher {
    fun match(
        devices: List<GoogleHomeDeviceCandidate>,
        deviceName: String,
        roomName: String?,
    ): GoogleHomeDeviceMatch {
        val requestedDevice = normalize(deviceName)
        val requestedRoom = roomName?.let(::normalize)?.takeIf { it.isNotEmpty() }
        if (requestedDevice.isEmpty()) return GoogleHomeDeviceMatch.NotFound

        val inRoom = devices.filter { candidate ->
            requestedRoom == null || normalize(candidate.roomName.orEmpty()) == requestedRoom
        }
        val exact = inRoom.filter { normalize(it.name) == requestedDevice }
        if (exact.size == 1) return GoogleHomeDeviceMatch.Found(exact.single())
        if (exact.size > 1) return GoogleHomeDeviceMatch.Ambiguous(exact)

        val partial = inRoom.filter { candidate ->
            val candidateName = normalize(candidate.name)
            candidateName.contains(requestedDevice) || requestedDevice.contains(candidateName)
        }
        return when (partial.size) {
            0 -> GoogleHomeDeviceMatch.NotFound
            1 -> GoogleHomeDeviceMatch.Found(partial.single())
            else -> GoogleHomeDeviceMatch.Ambiguous(partial)
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
        .joinToString(" ")
}
