package coredevices.ring.agent.builtin_servlets.googlehome

import android.content.Context
import androidx.activity.ComponentActivity
import com.google.home.FactoryRegistry
import com.google.home.ForcePermissionFlow
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeDevice
import com.google.home.PermissionsResult
import com.google.home.PermissionsState
import com.google.home.google.GoogleDisplayDevice
import com.google.home.google.GoogleTVDevice
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.FanDevice
import com.google.home.matter.standard.GenericSwitchDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import com.google.home.matter.standard.OnOffLightSwitchDevice
import com.google.home.matter.standard.OnOffPluginUnitDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class GoogleHomeController(context: Context) {
    private val client: HomeClient = Home.getClient(
        context = context.applicationContext,
        homeConfig = HomeConfig(
            coroutineContext = Dispatchers.IO,
            factoryRegistry = FactoryRegistry(
                types = listOf(
                    ColorTemperatureLightDevice,
                    DimmableLightDevice,
                    ExtendedColorLightDevice,
                    FanDevice,
                    GenericSwitchDevice,
                    GoogleDisplayDevice,
                    GoogleTVDevice,
                    OnOffLightDevice,
                    OnOffLightSwitchDevice,
                    OnOffPluginUnitDevice,
                ),
                traits = listOf(OnOff, LevelControl),
            ),
        ),
    )

    fun registerPermissionCaller(activity: ComponentActivity) {
        client.registerActivityResultCallerForPermissions(activity)
    }

    suspend fun permissionState(): PermissionsState = client.hasPermissions().first { state ->
        state != PermissionsState.PERMISSIONS_STATE_UNINITIALIZED
    }

    suspend fun requestPermissions(): PermissionsResult = client.requestPermissions(
        forcePermissionFlow = ForcePermissionFlow.FORCE_LAUNCH,
    )

    suspend fun deviceCount(): Int = devices().size

    internal suspend fun control(
        deviceName: String,
        roomName: String?,
        action: GoogleHomeAction,
        brightnessPercent: Int?,
    ): GoogleHomeControlOutcome {
        if (permissionState() != PermissionsState.GRANTED) {
            return GoogleHomeControlOutcome.Failure(
                "Google Home is not connected. Connect it in Index settings first."
            )
        }

        val devices = devices()
        val match = GoogleHomeDeviceMatcher.match(
            devices = devices.map(ResolvedDevice::candidate),
            deviceName = deviceName,
            roomName = roomName,
        )
        val selected = when (match) {
            is GoogleHomeDeviceMatch.Found -> devices.single { it.candidate.id == match.device.id }
            is GoogleHomeDeviceMatch.Ambiguous -> return GoogleHomeControlOutcome.Failure(
                "More than one device matches '$deviceName': " +
                    match.devices.joinToString { candidate ->
                        candidate.roomName?.let { "${candidate.name} in $it" } ?: candidate.name
                    } + ". Include the room name."
            )
            GoogleHomeDeviceMatch.NotFound -> return GoogleHomeControlOutcome.Failure(
                "No Google Home device matches '$deviceName'."
            )
        }

        val traits = selected.device.types().first().flatMap { it.traits() }
        when (action) {
            GoogleHomeAction.On, GoogleHomeAction.Off, GoogleHomeAction.Toggle -> {
                val trait = traits.filterIsInstance<OnOff>().firstOrNull()
                    ?: return unsupported(selected.candidate, "on/off")
                when (action) {
                    GoogleHomeAction.On -> trait.on()
                    GoogleHomeAction.Off -> trait.off()
                    GoogleHomeAction.Toggle -> if (trait.onOff == true) trait.off() else trait.on()
                    GoogleHomeAction.SetBrightness -> error("Handled below")
                }
            }
            GoogleHomeAction.SetBrightness -> {
                val percent = brightnessPercent?.takeIf { it in 0..100 }
                    ?: return GoogleHomeControlOutcome.Failure(
                        "brightness_percent must be between 0 and 100."
                    )
                val trait = traits.filterIsInstance<LevelControl>().firstOrNull()
                    ?: return unsupported(selected.candidate, "brightness")
                val matterLevel = (percent * 254f / 100f).roundToInt().toUByte()
                trait.moveToLevelWithOnOff(
                    level = matterLevel,
                    transitionTime = null,
                    optionsMask = LevelControlTrait.OptionsBitmap(),
                    optionsOverride = LevelControlTrait.OptionsBitmap(),
                )
            }
        }
        return GoogleHomeControlOutcome.Success(selected.candidate)
    }

    private suspend fun devices(): List<ResolvedDevice> = client.structures().list().flatMap { structure ->
        val roomsById = structure.rooms().list().associate { room -> room.id.id to room.name }
        structure.devices().list().map { device ->
            ResolvedDevice(
                candidate = GoogleHomeDeviceCandidate(
                    id = device.id.id,
                    name = device.name,
                    roomName = device.roomId?.id?.let(roomsById::get),
                ),
                device = device,
            )
        }
    }

    private fun unsupported(
        device: GoogleHomeDeviceCandidate,
        capability: String,
    ): GoogleHomeControlOutcome.Failure = GoogleHomeControlOutcome.Failure(
        "${device.name} does not expose $capability control through Google Home."
    )

    private data class ResolvedDevice(
        val candidate: GoogleHomeDeviceCandidate,
        val device: HomeDevice,
    )
}

sealed interface GoogleHomeControlOutcome {
    data class Success(val device: GoogleHomeDeviceCandidate) : GoogleHomeControlOutcome
    data class Failure(val message: String) : GoogleHomeControlOutcome
}
