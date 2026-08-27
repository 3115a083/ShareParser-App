# Security and local data handling

ShareParser processes shared text locally and intentionally requests no network or broad storage permission.

## Local data

- Profiles are stored in the app-private internal files directory.
- The latest processing failure report is stored in the same app-private directory.
- Incoming share intents are cleared after they are converted into the active UI payload.
- Long-lived helpers retain only the Android application context, not an Activity context.

## Uninstall behavior

Android app backup is disabled and ShareParser does not request preservation of fragile user data. App-private files are therefore intended to be removed with the package on uninstall.

Files explicitly exported by the user, calendar entries the user saves, URLs opened in other apps, and messages sent to other apps are external user-owned data and are not deleted by uninstalling ShareParser.

## Continuous checks

CI fails if network or broad storage permissions are added, if backup/uninstall protections are removed, or if Android Lint reports a `StaticFieldLeak`. Unit tests and a debug APK build also run for every pull request.

Runtime memory profiling on physical devices is still recommended before each release because static analysis cannot prove the absence of every possible runtime leak.
