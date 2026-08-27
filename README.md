# ShareParser

ShareParser is a privacy-focused Android utility for turning shared text into useful actions.

It receives text from Android's Sharesheet, selects a reusable profile, extracts structured values and transforms the result into a calendar event, parameterized URL or rebuilt message for another app.

## Current features

### Profile overview

A normal app launch opens the profile overview first. From there users can:

- create profiles
- activate or deactivate profiles
- edit or delete profiles
- import shared profile files
- open app settings

### Guided profile builder

Regex is not required for the normal workflow.

When an example mail or message is shared to ShareParser, the editor can use that example directly:

- select a changing part of the subject or message text and turn it into a named variable
- use one-tap suggestions for common `Label: value` lines such as `Datum: 14.12.2026`
- let ShareParser generate the reusable extraction rule from the text around the selected value
- choose stable text fragments from the example as profile recognition criteria
- preview extracted values against the shared example
- mark variables as required or optional
- add transformation blocks such as trim, replace/remove text, prefix, suffix and case conversion

Regex details remain available only in the advanced mode. The versioned profile JSON can also be edited directly by advanced users.

Built-in variables are available for the mail subject, full message and their combination. Extracted variables are displayed as selectable chips in processing fields, so users do not need to memorize template syntax.

Profiles can be copied as JSON, exported to a file, shared and imported again.

### Share flow

- receives `ACTION_SEND` for `text/*` and JSON
- keeps mail subject and body separate
- shows the shared information before processing
- matches only enabled profiles
- lets the user choose between multiple matching profiles
- shows an action picker when a profile has multiple processing actions
- supports creating a new profile directly from an unmatched shared example
- handles new shares while the Activity is already open

### Calendar actions

ShareParser opens Android's calendar event editor and prefills as many fields as possible. The user remains in control of saving the event.

Supported fields include:

- title
- description
- location
- start
- end
- all-day state
- optional target calendar name, for example `Arbeit`

When a target calendar name is configured, ShareParser can request calendar read access and look for a visible calendar with that name. It passes the matching calendar ID to the calendar insert intent. Calendar apps differ in how completely they honor this hint, so ShareParser warns when manual calendar selection may still be necessary.

German date and time parsing accepts common variants such as:

- `14.12.2026`
- `14/12/26`
- `14.12.` using the current year
- `heute`, `morgen`, `übermorgen`
- German weekdays such as `nächsten Montag`
- `12-14`
- `12 Uhr bis 14 Uhr`
- `12:00 Uhr bis 14:00 Uhr`

If a value cannot be recognized, ShareParser still opens the calendar and shows a Toast plus an optional silent notification telling the user which fields should be completed manually.

Date and time interpretation can be selected in Settings from the main profile overview.

### URL actions

URLs can be assembled from fixed text and extracted variables. Variable chips in URL fields insert URL-encoded values automatically.

Each URL action can choose between:

- the Android system browser or matching external app
- an optional in-app WebView

The in-app WebView only accepts `http` and `https`. JavaScript, DOM storage, file/content access, geolocation, mixed content and third-party cookies are disabled.

Other external URL schemes supported by browser mode are `geo`, `mailto` and `tel`.

### Share actions

A profile can build a new subject and message from fixed text and variables and send the result through the Android Sharesheet to another app.

### Failure handling

Processing errors create an immediate Toast plus a silent low-priority notification. The notification expires after 20 seconds and opens a local diagnostic report. From that report the affected profile can be opened with the failing field highlighted.

Missing optional calendar information is handled as a warning rather than a hard failure.

## Architecture

```text
Android Sharesheet
      |
      v
SharedPayload(subject, text)
      |
Profile recognition criteria
      |
Generated/manual extractors -> transformation blocks -> named values
      |
Action picker
      |-------------------|------------------|
Calendar insert       URL action       transformed Share
```

Profiles, settings and failure reports are stored locally as JSON. No ShareParser server is required.

## Privacy and energy use

ShareParser has no analytics, ads, trackers, Play Services dependency, background service or periodic jobs. Processing only runs when the user opens ShareParser or explicitly shares content to it.

The `INTERNET` permission exists for the optional user-selected in-app WebView. Text parsing and profile processing remain local. `READ_CALENDAR` is requested on demand only when a profile wants to resolve a target calendar by name. Broad storage permissions are not requested.

Android app backup is disabled so local profile, settings and failure data are not restored through app backup after uninstall/reinstall.

See `SECURITY.md` for details.

## Android support

- minimum: Android 8.0 / API 26
- target/compile SDK: 36
- Jetpack Compose + Material 3
- dynamic color on Android 12+

## Build

Requirements:

- JDK 17
- Android SDK 36
- Gradle 8.13

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

GitHub Actions runs privacy checks, unit tests, Android Lint and creates a debug APK for pull requests and pushes to `main`.

## F-Droid direction

The project is deliberately structured for F-Droid:

- Apache-2.0 source license
- no proprietary SDKs
- no telemetry or remote profile service
- no background synchronization
- plain, inspectable profile files
- Fastlane-compatible store metadata under `fastlane/metadata/android`

Before the first F-Droid submission, release signing, reproducible release builds, screenshots, changelogs, tagged releases and final metadata still need to be completed.

## Provisional icon

The adaptive launcher icon combines Android's share-node motif with a processing gear to represent share, parse and transform. It is intentionally provisional and can be replaced without changing app identity.

## License

Apache-2.0
