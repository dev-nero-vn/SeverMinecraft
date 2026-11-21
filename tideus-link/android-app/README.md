# TIDEUS LINK Android app

TIDEUS LINK opens directly into its native in-app inbox. There is no Linux server URL entry screen.

## Current app screens

- Inbox list with search and platform filters.
- Per-conversation message view and composer UI.
- Plugin Center for Zalo Official Account, Messenger/Facebook Page, and TikTok Business.
- Live Activity and Settings screens.
- TIDEUS LINK launcher logo.

## Important integration status

The inbox rows and sample messages in this build are clearly marked as preview/demo data. This APK does not yet sign into provider accounts, sync real conversations, or deliver outgoing messages. The Plugin Center currently explains the backend/OAuth requirements instead of pretending an account is connected.

Real messaging requires provider-approved OAuth apps, secure server-side token storage, verified webhooks, a deployed backend, and end-to-end tests for each platform. No API keys or passwords should be placed in the APK or chat messages.

## Build

Use JDK 17, Android SDK 35, and Gradle 8.9:

```bash
gradle --no-daemon :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow builds the APK and bundles it with Android/Linux source and the logo in `TIDEUS-LINK-Complete.zip`.
