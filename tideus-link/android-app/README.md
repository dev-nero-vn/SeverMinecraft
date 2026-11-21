# TIDEUS LINK Android companion

This Android app is a lightweight client for the Linux-hosted TIDEUS LINK Control Center.

## Setup

- Launch TIDEUS LINK.
- Enter your Linux Control Center URL.
- Use HTTPS for public/remote servers.
- HTTP is accepted only for localhost or private LAN IPs.
- Sign in with your Owner dashboard account.

The app remembers the server address. The dashboard login is handled by the Linux service; the Android app does not store API provider keys.

## Build

Run from this directory with JDK 17, Android SDK 35, and Gradle 8.9:

```bash
gradle --no-daemon :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow builds this APK and packages it with source and instructions in `TIDEUS-LINK-Complete.zip`.
