# Security and local data handling

ShareParser parses and transforms shared text locally. It has no analytics, ads, trackers, remote profile service or background synchronization.

## Permissions

- `POST_NOTIFICATIONS` is used for short, silent processing warnings and failure notifications. Toast messages still work when notification permission is denied.
- `READ_CALENDAR` is requested only when a profile specifies a target calendar by name. It is used to look up visible calendars such as `Arbeit`. ShareParser does not request calendar write permission and does not save events itself.
- `INTERNET` is used only when the user configures a URL action to open inside ShareParser's optional WebView. Parsing, profiles, calendar preparation and external-browser URL actions do not require ShareParser to contact a server.
- Broad storage permissions are intentionally not requested.

## In-app WebView

The optional WebView is not exported and accepts only `http` and `https` URLs. JavaScript, DOM storage, file access, content access, geolocation, mixed content and third-party cookies are disabled. The WebView is stopped, detached and destroyed when its Activity is destroyed.

Users can instead configure each URL action to open in the system browser.

## Local data

- Profiles and app settings are stored in the app-private internal files directory.
- The latest processing failure report is stored in the same app-private directory.
- Incoming share intents are cleared after they are converted into the active UI payload.
- Long-lived helpers retain only the Android application context, not an Activity context.

## Uninstall behavior

Android app backup is disabled with `allowBackup=false` and `fullBackupContent=false`. ShareParser also sets `hasFragileUserData=false`, so it does not ask Android to preserve its private data during uninstall.

Files explicitly exported by the user, calendar entries the user saves, URLs opened in other apps, and messages sent to other apps are external user-owned data and are not deleted by uninstalling ShareParser.

## Continuous checks

CI verifies backup and uninstall protections, rejects broad storage permissions, runs unit tests, Android Lint and a debug APK build. `StaticFieldLeak` is configured as a fatal lint issue.

Runtime memory profiling on physical devices is still recommended before releases because static analysis cannot prove the absence of every possible runtime leak.
