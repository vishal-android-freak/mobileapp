package coredevices.ring.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import coredevices.ui.M3Dialog

@Composable
actual fun GoogleHomeDialog(onDismiss: () -> Unit) {
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Home") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    ) {
        Text("Google Home support is not enabled in this build.")
    }
}
