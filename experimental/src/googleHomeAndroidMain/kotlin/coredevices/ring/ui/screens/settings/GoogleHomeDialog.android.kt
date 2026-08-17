package coredevices.ring.ui.screens.settings

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import coredevices.ring.agent.builtin_servlets.googlehome.GoogleHomeController
import coredevices.ui.M3Dialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
actual fun GoogleHomeDialog(onDismiss: () -> Unit) {
    val controller = koinInject<GoogleHomeController>()
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<GoogleHomeUiState>(GoogleHomeUiState.Loading) }

    suspend fun refresh() {
        state = try {
            if (controller.permissionState() == PermissionsState.GRANTED) {
                GoogleHomeUiState.Connected(controller.deviceCount())
            } else {
                GoogleHomeUiState.Disconnected
            }
        } catch (e: Exception) {
            GoogleHomeUiState.Error(e.message ?: "Could not load Google Home")
        }
    }

    LaunchedEffect(Unit) { refresh() }

    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Home") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Close") }
            TextButton(
                enabled = state !is GoogleHomeUiState.Loading,
                onClick = {
                    state = GoogleHomeUiState.Loading
                    scope.launch {
                        state = try {
                            val result = controller.requestPermissions()
                            if (result.status == PermissionsResultStatus.SUCCESS) {
                                refresh()
                                return@launch
                            }
                            GoogleHomeUiState.Error(
                                result.errorMessage ?: "Google Home connection was cancelled"
                            )
                        } catch (e: Exception) {
                            GoogleHomeUiState.Error(e.message ?: "Could not connect Google Home")
                        }
                    }
                },
            ) {
                Text(if (state is GoogleHomeUiState.Connected) "Change home" else "Connect")
            }
        },
    ) {
        when (val current = state) {
            GoogleHomeUiState.Loading -> CircularProgressIndicator()
            GoogleHomeUiState.Disconnected -> Text(
                "Connect the Google account and home containing the third-party devices you want Index to control."
            )
            is GoogleHomeUiState.Connected -> Text(
                "Connected. Index can access ${current.deviceCount} device" +
                    if (current.deviceCount == 1) "." else "s."
            )
            is GoogleHomeUiState.Error -> Text(current.message)
        }
    }
}

private sealed interface GoogleHomeUiState {
    data object Loading : GoogleHomeUiState
    data object Disconnected : GoogleHomeUiState
    data class Connected(val deviceCount: Int) : GoogleHomeUiState
    data class Error(val message: String) : GoogleHomeUiState
}
