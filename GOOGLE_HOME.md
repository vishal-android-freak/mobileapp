# Google Home development setup

Google distributes the Home APIs Android SDK through an authenticated download rather than a
public Maven repository. The app therefore keeps this Android-only integration disabled unless the
SDK is available locally.

1. Sign in and download the current Home APIs Android SDK from the
   [Google Home developer site](https://developers.home.google.com/apis/android/sdk).
2. Follow the SDK bundle's instructions to install these artifacts in the local Maven repository:
   `com.google.android.gms:play-services-home:17.1.0` and
   `com.google.android.gms:play-services-home-types:17.1.0`.
3. Configure an Android OAuth client for package `coredevices.coreapp` and the SHA-1 of the signing
   certificate used by the build. Add development accounts as OAuth test users.
4. Add `GOOGLE_HOME_ENABLED=true` to `local.properties`.
5. Build and open **Index settings → Add integration → Google Home** to grant access to a home.

Once connected, the built-in `control_google_home_device` tool is included in the default MCP
sandbox and is available to Needle 2. It supports on, off, toggle, and light brightness commands.
Device names are matched deterministically; a room name is required when multiple devices share a
name.
